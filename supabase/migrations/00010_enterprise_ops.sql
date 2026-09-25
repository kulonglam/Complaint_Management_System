-- Enterprise ops: admin MFA policy, email retries, audit export,
-- retention/erasure, and attachment scan status.

alter table public.organization_settings
  add column if not exists require_admin_mfa boolean not null default true;

alter table public.email_outbox
  add column if not exists attempt_count integer not null default 0,
  add column if not exists next_attempt_at timestamptz;

create index if not exists email_outbox_retry_idx
  on public.email_outbox (status, next_attempt_at);

alter table public.complaint_attachments
  add column if not exists scan_status text not null default 'SKIPPED';

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'complaint_attachments_scan_status_check'
  ) then
    alter table public.complaint_attachments
      add constraint complaint_attachments_scan_status_check
      check (scan_status in ('PENDING', 'CLEAN', 'BLOCKED', 'SKIPPED'));
  end if;
end $$;

create or replace function public.list_pending_emails(p_limit integer default 25)
returns setof public.email_outbox
language sql
security definer
set search_path = public
as $$
  select *
  from public.email_outbox
  where status = 'PENDING'
    and (next_attempt_at is null or next_attempt_at <= now())
  order by created_at
  limit greatest(1, least(coalesce(p_limit, 25), 100));
$$;

create or replace function public.export_audit_logs(
  p_from timestamptz default null,
  p_to timestamptz default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_org uuid := public.current_organization_id();
  v_rows jsonb;
begin
  if v_org is null then
    raise exception 'Not authenticated';
  end if;
  if not public.has_permission('audit_logs:view') and not public.has_permission('reports:export') then
    raise exception 'Access denied';
  end if;

  select coalesce(jsonb_agg(row_to_json(x)::jsonb order by x.created_at desc), '[]'::jsonb)
  into v_rows
  from (
    select
      id,
      created_at,
      action,
      entity_type,
      entity_id,
      user_id,
      old_values,
      new_values,
      metadata
    from public.audit_logs
    where organization_id = v_org
      and (p_from is null or created_at >= p_from)
      and (p_to is null or created_at <= p_to)
    order by created_at desc
    limit 10000
  ) x;

  insert into public.audit_logs (organization_id, user_id, action, entity_type, metadata)
  values (v_org, auth.uid(), 'audit.export', 'audit_logs', jsonb_build_object('from', p_from, 'to', p_to));

  return v_rows;
end;
$$;

create or replace function public.redact_complaint_pii(p_complaint_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.complaints
  set
    complainant_name = case when complainant_name is null then null else '[redacted]' end,
    complainant_email = null,
    complainant_phone = null
  where id = p_complaint_id;

  update public.complaint_attachments
  set scan_status = 'BLOCKED'
  where complaint_id = p_complaint_id;
end;
$$;

create or replace function public.purge_expired_records()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
begin
  with due as (
    select c.id
    from public.complaints c
    join public.organization_settings s on s.organization_id = c.organization_id
    where s.retention_days is not null
      and s.retention_days > 0
      and c.status in ('CLOSED', 'REJECTED', 'DUPLICATE')
      and coalesce(c.closed_at, c.updated_at, c.created_at) < now() - make_interval(days => s.retention_days)
      and (
        c.complainant_email is not null
        or c.complainant_phone is not null
        or coalesce(c.complainant_name, '') not in ('', '[redacted]')
      )
  ),
  wiped as (
    update public.complaints c
    set
      complainant_name = case when c.complainant_name is null then null else '[redacted]' end,
      complainant_email = null,
      complainant_phone = null
    from due
    where c.id = due.id
    returning c.id
  )
  select count(*) into v_count from wiped;

  insert into public.audit_logs (organization_id, action, entity_type, metadata)
  select s.organization_id, 'privacy.retention_purge', 'complaint', jsonb_build_object('count', v_count)
  from public.organization_settings s
  where s.retention_days is not null
  limit 1;

  return v_count;
end;
$$;

create or replace function public.request_subject_erasure(p_email text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_org uuid := public.current_organization_id();
  v_count integer := 0;
begin
  if v_org is null then
    raise exception 'Not authenticated';
  end if;
  if not public.has_permission('settings:update') then
    raise exception 'Access denied';
  end if;
  if p_email is null or length(trim(p_email)) < 5 then
    raise exception 'Email is required';
  end if;

  with matched as (
    select id
    from public.complaints
    where organization_id = v_org
      and lower(complainant_email) = lower(trim(p_email))
  ),
  wiped as (
    update public.complaints c
    set
      complainant_name = case when c.complainant_name is null then null else '[redacted]' end,
      complainant_email = null,
      complainant_phone = null
    from matched
    where c.id = matched.id
    returning c.id
  )
  select count(*) into v_count from wiped;

  insert into public.audit_logs (organization_id, user_id, action, entity_type, metadata)
  values (
    v_org,
    auth.uid(),
    'privacy.erasure',
    'complaint',
    jsonb_build_object('email_hash', encode(digest(convert_to(lower(trim(p_email)), 'UTF8'), 'sha256'::text), 'hex'), 'count', v_count)
  );

  return v_count;
end;
$$;

grant execute on function public.list_pending_emails(integer) to postgres, service_role;
grant execute on function public.export_audit_logs(timestamptz, timestamptz) to authenticated;
grant execute on function public.purge_expired_records() to postgres, service_role;
grant execute on function public.request_subject_erasure(text) to authenticated;
grant execute on function public.redact_complaint_pii(uuid) to postgres, service_role;
