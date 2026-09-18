-- Complaint Management SaaS — core schema
-- Requires Supabase (auth schema, storage). Enable pgcrypto.

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------------
-- Utility
-- ---------------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- Organizations / tenancy
-- ---------------------------------------------------------------------------

create table public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique,
  logo_url text,
  description text,
  email text,
  phone text,
  address text,
  country text,
  timezone text not null default 'UTC',
  primary_color text,
  public_portal_enabled boolean not null default true,
  status text not null default 'ACTIVE'
    check (status in ('ACTIVE', 'SUSPENDED', 'TRIAL', 'INACTIVE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger organizations_updated_at
before update on public.organizations
for each row execute function public.set_updated_at();

create table public.organization_settings (
  organization_id uuid primary key references public.organizations(id) on delete cascade,
  retention_days integer,
  allow_anonymous boolean not null default true,
  require_resolution_approval boolean not null default true,
  email_sender_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger organization_settings_updated_at
before update on public.organization_settings
for each row execute function public.set_updated_at();

create table public.organization_counters (
  organization_id uuid not null references public.organizations(id) on delete cascade,
  year integer not null,
  last_number integer not null default 0,
  primary key (organization_id, year)
);

-- ---------------------------------------------------------------------------
-- Departments
-- ---------------------------------------------------------------------------

create table public.departments (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null,
  code text,
  description text,
  manager_id uuid,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, name)
);

create trigger departments_updated_at
before update on public.departments
for each row execute function public.set_updated_at();

create index departments_org_idx on public.departments (organization_id);

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  organization_id uuid references public.organizations(id) on delete set null,
  first_name text,
  last_name text,
  email text not null,
  phone text,
  avatar_url text,
  job_title text,
  department_id uuid references public.departments(id) on delete set null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE', 'INVITED')),
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create index profiles_org_idx on public.profiles (organization_id);
create index profiles_department_idx on public.profiles (department_id);
create unique index profiles_email_idx on public.profiles (lower(email));

alter table public.departments
  add constraint departments_manager_fk
  foreign key (manager_id) references public.profiles(id) on delete set null;

-- ---------------------------------------------------------------------------
-- RBAC
-- ---------------------------------------------------------------------------

create table public.permissions (
  id uuid primary key default gen_random_uuid(),
  key text not null unique,
  description text,
  created_at timestamptz not null default now()
);

create table public.roles (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references public.organizations(id) on delete cascade,
  key text not null,
  name text not null,
  description text,
  is_system boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, key)
);

create trigger roles_updated_at
before update on public.roles
for each row execute function public.set_updated_at();

-- Platform-level roles have null organization_id; enforce unique key in that case
create unique index roles_platform_key_idx on public.roles (key) where organization_id is null;

create table public.role_permissions (
  role_id uuid not null references public.roles(id) on delete cascade,
  permission_id uuid not null references public.permissions(id) on delete cascade,
  primary key (role_id, permission_id)
);

create table public.user_roles (
  user_id uuid not null references public.profiles(id) on delete cascade,
  role_id uuid not null references public.roles(id) on delete cascade,
  organization_id uuid references public.organizations(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, role_id)
);

create index user_roles_org_idx on public.user_roles (organization_id);

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------

create table public.complaint_categories (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null,
  description text,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, name)
);

create trigger complaint_categories_updated_at
before update on public.complaint_categories
for each row execute function public.set_updated_at();

create table public.complaint_subcategories (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  category_id uuid not null references public.complaint_categories(id) on delete cascade,
  name text not null,
  status text not null default 'ACTIVE' check (status in ('ACTIVE', 'INACTIVE')),
  created_at timestamptz not null default now(),
  unique (category_id, name)
);

-- ---------------------------------------------------------------------------
-- SLA
-- ---------------------------------------------------------------------------

create table public.sla_policies (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  name text not null,
  priority text not null check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  response_hours integer not null,
  resolution_hours integer not null,
  escalation_hours integer,
  reminder_hours integer,
  is_default boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, priority)
);

create trigger sla_policies_updated_at
before update on public.sla_policies
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Complaints
-- ---------------------------------------------------------------------------

create table public.complaints (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  reference_number text not null,
  tracking_code_hash text not null,
  complainant_id uuid references public.profiles(id) on delete set null,
  complainant_name text,
  complainant_email text,
  complainant_phone text,
  is_anonymous boolean not null default false,
  title text not null,
  description text not null,
  category_id uuid references public.complaint_categories(id) on delete set null,
  subcategory_id uuid references public.complaint_subcategories(id) on delete set null,
  priority text not null default 'MEDIUM' check (priority in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  status text not null default 'SUBMITTED' check (status in (
    'SUBMITTED', 'RECEIVED', 'UNDER_REVIEW', 'ASSIGNED', 'UNDER_INVESTIGATION',
    'PENDING_ACTION', 'RESOLVED', 'CLOSED', 'REJECTED', 'ESCALATED', 'ON_HOLD',
    'REOPENED', 'DUPLICATE'
  )),
  department_id uuid references public.departments(id) on delete set null,
  assigned_to uuid references public.profiles(id) on delete set null,
  incident_date date,
  location text,
  due_date timestamptz,
  sla_breached boolean not null default false,
  submitted_at timestamptz not null default now(),
  resolved_at timestamptz,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (organization_id, reference_number)
);

create trigger complaints_updated_at
before update on public.complaints
for each row execute function public.set_updated_at();

create index complaints_org_idx on public.complaints (organization_id);
create index complaints_status_idx on public.complaints (organization_id, status);
create index complaints_priority_idx on public.complaints (organization_id, priority);
create index complaints_department_idx on public.complaints (department_id);
create index complaints_assigned_idx on public.complaints (assigned_to);
create index complaints_category_idx on public.complaints (category_id);
create index complaints_created_idx on public.complaints (organization_id, created_at desc);
create index complaints_due_idx on public.complaints (due_date);
create index complaints_reference_idx on public.complaints (reference_number);
create index complaints_search_idx on public.complaints using gin (
  (title || ' ' || description) gin_trgm_ops
);

create table public.complaint_status_history (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  old_status text,
  new_status text not null,
  changed_by uuid references public.profiles(id) on delete set null,
  reason text,
  created_at timestamptz not null default now()
);

create index complaint_status_history_complaint_idx
  on public.complaint_status_history (complaint_id, created_at);

create table public.complaint_assignments (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  assigned_to uuid not null references public.profiles(id) on delete restrict,
  assigned_by uuid references public.profiles(id) on delete set null,
  department_id uuid references public.departments(id) on delete set null,
  assigned_at timestamptz not null default now(),
  due_date timestamptz,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'REASSIGNED')),
  notes text,
  created_at timestamptz not null default now()
);

create index complaint_assignments_complaint_idx on public.complaint_assignments (complaint_id);

create table public.complaint_comments (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  author_id uuid references public.profiles(id) on delete set null,
  content text not null,
  visibility text not null default 'INTERNAL'
    check (visibility in ('INTERNAL', 'COMPLAINANT_VISIBLE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger complaint_comments_updated_at
before update on public.complaint_comments
for each row execute function public.set_updated_at();

create table public.complaint_attachments (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  uploaded_by uuid references public.profiles(id) on delete set null,
  file_name text not null,
  file_path text not null,
  file_type text not null,
  file_size integer not null,
  visibility text not null default 'INTERNAL'
    check (visibility in ('INTERNAL', 'PUBLIC')),
  created_at timestamptz not null default now()
);

create table public.complaint_investigations (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  investigator_id uuid references public.profiles(id) on delete set null,
  summary text,
  findings text,
  recommendations text,
  started_at timestamptz default now(),
  completed_at timestamptz,
  status text not null default 'IN_PROGRESS'
    check (status in ('DRAFT', 'IN_PROGRESS', 'SUBMITTED', 'COMPLETED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger complaint_investigations_updated_at
before update on public.complaint_investigations
for each row execute function public.set_updated_at();

create table public.complaint_tasks (
  id uuid primary key default gen_random_uuid(),
  investigation_id uuid not null references public.complaint_investigations(id) on delete cascade,
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  title text not null,
  description text,
  assignee_id uuid references public.profiles(id) on delete set null,
  status text not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'DONE')),
  due_date date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger complaint_tasks_updated_at
before update on public.complaint_tasks
for each row execute function public.set_updated_at();

create table public.complaint_resolutions (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  summary text not null,
  corrective_action text,
  notes text,
  resolved_by uuid references public.profiles(id) on delete set null,
  resolution_date date,
  approval_status text not null default 'DRAFT'
    check (approval_status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  review_notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger complaint_resolutions_updated_at
before update on public.complaint_resolutions
for each row execute function public.set_updated_at();

create table public.complaint_feedback (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  rating integer not null check (rating between 1 and 5),
  comment text,
  submitted_at timestamptz not null default now()
);

create table public.complaint_escalations (
  id uuid primary key default gen_random_uuid(),
  complaint_id uuid not null references public.complaints(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  escalated_by uuid references public.profiles(id) on delete set null,
  reason text not null,
  previous_department_id uuid references public.departments(id) on delete set null,
  new_department_id uuid references public.departments(id) on delete set null,
  previous_assignee_id uuid references public.profiles(id) on delete set null,
  new_assignee_id uuid references public.profiles(id) on delete set null,
  notes text,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Notifications / audit / platform
-- ---------------------------------------------------------------------------

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null,
  title text not null,
  message text not null,
  related_complaint_id uuid references public.complaints(id) on delete cascade,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create index notifications_user_idx on public.notifications (user_id, created_at desc);

create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references public.organizations(id) on delete cascade,
  user_id uuid references public.profiles(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id uuid,
  old_values jsonb,
  new_values jsonb,
  metadata jsonb,
  created_at timestamptz not null default now()
);

create index audit_logs_org_idx on public.audit_logs (organization_id, created_at desc);

create table public.system_settings (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);

-- Future SaaS hooks (unused in v1, reserved)
create table public.subscription_plans (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  created_at timestamptz not null default now()
);

create table public.organization_subscriptions (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete cascade,
  plan_id uuid references public.subscription_plans(id),
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now()
);
