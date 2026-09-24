-- Plan limits, durable rate-limit helper, email visibility, usage metrics.

alter table public.subscription_plans
  add column if not exists max_users integer,
  add column if not exists max_complaints integer,
  add column if not exists price_cents integer not null default 0,
  add column if not exists billing_interval text not null default 'month';

alter table public.organization_subscriptions
  add column if not exists current_period_end timestamptz,
  add column if not exists stripe_customer_id text,
  add column if not exists stripe_subscription_id text;

update public.subscription_plans
set max_users = 15, max_complaints = 100, price_cents = 0
where name = 'Trial';

update public.subscription_plans
set max_users = 80, max_complaints = 2000, price_cents = 4900
where name = 'Standard';

update public.subscription_plans
set max_users = null, max_complaints = null, price_cents = 14900
where name = 'Enterprise';

insert into public.organization_subscriptions (organization_id, plan_id, status)
select o.id, p.id, 'ACTIVE'
from public.organizations o
join public.subscription_plans p on p.name = 'Trial'
where not exists (
  select 1 from public.organization_subscriptions s
  where s.organization_id = o.id and s.status = 'ACTIVE'
);

create or replace function public.assert_named_rate_limit(
  p_bucket text,
  p_actor text,
  p_limit integer default 60
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  window_start timestamptz := date_trunc('minute', now());
  hits integer;
begin
  insert into public.rate_limits (bucket, actor, window_start, hit_count)
  values (p_bucket, left(coalesce(p_actor, 'anon'), 120), window_start, 1)
  on conflict (bucket, actor, window_start)
  do update set hit_count = public.rate_limits.hit_count + 1
  returning hit_count into hits;

  delete from public.rate_limits where window_start < now() - interval '10 minutes';

  if hits > greatest(p_limit, 1) then
    raise exception 'Too many attempts. Please wait a minute and try again.';
  end if;
end;
$$;

create or replace function public.assert_plan_capacity(p_org uuid, p_kind text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  plan public.subscription_plans;
  used integer := 0;
begin
  if p_org is null then
    return;
  end if;

  select sp.* into plan
  from public.organization_subscriptions s
  join public.subscription_plans sp on sp.id = s.plan_id
  where s.organization_id = p_org
    and s.status = 'ACTIVE'
  order by s.created_at desc
  limit 1;

  if plan.id is null then
    return;
  end if;

  if p_kind = 'profiles' and plan.max_users is not null then
    select count(*) into used from public.profiles where organization_id = p_org;
    if used >= plan.max_users then
      raise exception 'This organization has reached its user limit for the % plan', plan.name;
    end if;
  end if;

  if p_kind = 'complaints' and plan.max_complaints is not null then
    select count(*) into used from public.complaints where organization_id = p_org;
    if used >= plan.max_complaints then
      raise exception 'This organization has reached its complaint limit for the % plan', plan.name;
    end if;
  end if;
end;
$$;

create or replace function public.enforce_org_quotas()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_table_name = 'profiles' then
    perform public.assert_plan_capacity(new.organization_id, 'profiles');
  elsif tg_table_name = 'complaints' then
    perform public.assert_plan_capacity(new.organization_id, 'complaints');
  end if;
  return new;
end;
$$;

drop trigger if exists enforce_profile_quota on public.profiles;
create trigger enforce_profile_quota
before insert on public.profiles
for each row execute function public.enforce_org_quotas();

drop trigger if exists enforce_complaint_quota on public.complaints;
create trigger enforce_complaint_quota
before insert on public.complaints
for each row execute function public.enforce_org_quotas();

create or replace function public.organization_usage()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  org uuid := public.current_organization_id();
begin
  if org is null and not public.is_platform_admin() then
    raise exception 'You are not assigned to an organization';
  end if;

  return (
    select jsonb_build_object(
      'organization_id', o.id,
      'organization_name', o.name,
      'users', (select count(*) from public.profiles p where p.organization_id = o.id),
      'complaints', (select count(*) from public.complaints c where c.organization_id = o.id),
      'plan', case
        when sp.id is null then null
        else jsonb_build_object(
          'id', sp.id,
          'name', sp.name,
          'max_users', sp.max_users,
          'max_complaints', sp.max_complaints,
          'price_cents', sp.price_cents,
          'billing_interval', sp.billing_interval
        )
      end
    )
    from public.organizations o
    left join lateral (
      select s.plan_id
      from public.organization_subscriptions s
      where s.organization_id = o.id and s.status = 'ACTIVE'
      order by s.created_at desc
      limit 1
    ) sub on true
    left join public.subscription_plans sp on sp.id = sub.plan_id
    where o.id = coalesce(org, o.id)
      and (org is not null or public.is_platform_admin())
    limit 1
  );
end;
$$;

drop policy if exists "email outbox select" on public.email_outbox;
create policy "email outbox select"
on public.email_outbox for select
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('settings:view'))
);

drop policy if exists "plans readable" on public.subscription_plans;
create policy "plans readable"
on public.subscription_plans for select
using (true);

grant execute on function public.assert_named_rate_limit(text, text, integer) to service_role;
grant execute on function public.organization_usage() to authenticated;
grant execute on function public.assert_plan_capacity(uuid, text) to authenticated, service_role;
