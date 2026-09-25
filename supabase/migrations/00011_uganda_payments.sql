-- Uganda billing: UGX prices and payment intents for MTN, Airtel, and Pesapal.

alter table public.subscription_plans
  add column if not exists currency text not null default 'UGX';

alter table public.organization_subscriptions
  add column if not exists payment_provider text,
  add column if not exists last_payment_id uuid;

update public.subscription_plans
set currency = 'UGX', price_cents = 0
where name = 'Trial';

update public.subscription_plans
set currency = 'UGX', price_cents = 180000
where name = 'Standard';

update public.subscription_plans
set currency = 'UGX', price_cents = 550000
where name = 'Enterprise';

create table if not exists public.payment_intents (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  plan_id uuid not null references public.subscription_plans(id),
  created_by uuid references public.profiles(id),
  provider text not null check (provider in ('MTN', 'AIRTEL', 'PESAPAL')),
  amount integer not null check (amount >= 0),
  currency text not null default 'UGX',
  phone text,
  status text not null default 'PENDING' check (status in ('PENDING', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED')),
  merchant_ref text not null unique,
  provider_ref text,
  checkout_url text,
  failure_reason text,
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists payment_intents_org_idx on public.payment_intents (organization_id, created_at desc);
create index if not exists payment_intents_provider_ref_idx on public.payment_intents (provider_ref);

alter table public.payment_intents enable row level security;

drop policy if exists "payment intents select own org" on public.payment_intents;
create policy "payment intents select own org"
on public.payment_intents for select
using (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

grant select on public.payment_intents to authenticated;
grant all on public.payment_intents to service_role;
