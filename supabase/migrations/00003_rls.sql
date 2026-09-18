-- Row Level Security: tenant isolation + permission checks
-- organization_id is never taken from the client for authorization.

alter table public.organizations enable row level security;
alter table public.organization_settings enable row level security;
alter table public.organization_counters enable row level security;
alter table public.departments enable row level security;
alter table public.profiles enable row level security;
alter table public.permissions enable row level security;
alter table public.roles enable row level security;
alter table public.role_permissions enable row level security;
alter table public.user_roles enable row level security;
alter table public.complaint_categories enable row level security;
alter table public.complaint_subcategories enable row level security;
alter table public.sla_policies enable row level security;
alter table public.complaints enable row level security;
alter table public.complaint_status_history enable row level security;
alter table public.complaint_assignments enable row level security;
alter table public.complaint_comments enable row level security;
alter table public.complaint_attachments enable row level security;
alter table public.complaint_investigations enable row level security;
alter table public.complaint_tasks enable row level security;
alter table public.complaint_resolutions enable row level security;
alter table public.complaint_feedback enable row level security;
alter table public.complaint_escalations enable row level security;
alter table public.notifications enable row level security;
alter table public.audit_logs enable row level security;
alter table public.system_settings enable row level security;
alter table public.subscription_plans enable row level security;
alter table public.organization_subscriptions enable row level security;

-- Organizations
create policy "org members can view own organization"
on public.organizations for select
using (
  public.is_platform_admin()
  or id = public.current_organization_id()
);

create policy "org admins can update own organization"
on public.organizations for update
using (
  public.is_platform_admin()
  or (id = public.current_organization_id() and public.has_permission('settings:update'))
);

create policy "platform admin manages organizations"
on public.organizations for all
using (public.is_platform_admin())
with check (public.is_platform_admin());

create policy "org settings readable by members"
on public.organization_settings for select
using (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

create policy "org settings updatable by admins"
on public.organization_settings for update
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('settings:update'))
);

create policy "org settings insert by admins"
on public.organization_settings for insert
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('settings:update'))
);

-- Profiles
create policy "users can view profiles in their org"
on public.profiles for select
using (
  public.is_platform_admin()
  or id = auth.uid()
  or organization_id = public.current_organization_id()
);

create policy "users can update their own profile"
on public.profiles for update
using (id = auth.uid() or public.has_permission('users:update') or public.is_platform_admin())
with check (
  (id = auth.uid() and organization_id is not distinct from public.current_organization_id())
  or public.has_permission('users:update')
  or public.is_platform_admin()
);

create policy "admins can insert profiles"
on public.profiles for insert
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('users:create'))
);

-- Departments
create policy "departments select"
on public.departments for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "departments insert"
on public.departments for insert
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('departments:create'))
);

create policy "departments update"
on public.departments for update
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('departments:update'))
);

create policy "departments delete"
on public.departments for delete
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('departments:delete'))
);

-- Roles / permissions
create policy "permissions readable"
on public.permissions for select
using (auth.uid() is not null);

create policy "roles readable in org"
on public.roles for select
using (
  public.is_platform_admin()
  or organization_id is null
  or organization_id = public.current_organization_id()
);

create policy "roles mutate"
on public.roles for all
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('roles:update'))
)
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('roles:create'))
);

create policy "role_permissions readable"
on public.role_permissions for select
using (auth.uid() is not null);

create policy "role_permissions mutate"
on public.role_permissions for all
using (public.has_permission('roles:update') or public.is_platform_admin())
with check (public.has_permission('roles:update') or public.is_platform_admin());

create policy "user_roles readable"
on public.user_roles for select
using (
  public.is_platform_admin()
  or user_id = auth.uid()
  or organization_id = public.current_organization_id()
);

create policy "user_roles mutate"
on public.user_roles for all
using (public.has_permission('users:update') or public.is_platform_admin())
with check (public.has_permission('users:update') or public.is_platform_admin());

-- Categories
create policy "categories select"
on public.complaint_categories for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "categories insert"
on public.complaint_categories for insert
with check (
  organization_id = public.current_organization_id() and public.has_permission('categories:create')
  or public.is_platform_admin()
);

create policy "categories update"
on public.complaint_categories for update
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('categories:update'))
);

create policy "categories delete"
on public.complaint_categories for delete
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('categories:delete'))
);

create policy "subcategories select"
on public.complaint_subcategories for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "subcategories mutate"
on public.complaint_subcategories for all
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('categories:update'))
)
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('categories:create'))
);

-- SLA
create policy "sla select"
on public.sla_policies for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "sla mutate"
on public.sla_policies for all
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('sla:update'))
)
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('sla:create'))
);

-- Complaints: core tenant isolation
create policy "complaints select"
on public.complaints for select
using (
  public.is_platform_admin()
  or (
    organization_id = public.current_organization_id()
    and (
      public.has_permission('complaints:view_all')
      or (public.has_permission('complaints:view_assigned') and assigned_to = auth.uid())
      or (public.has_permission('complaints:view') and (assigned_to = auth.uid() or complainant_id = auth.uid()))
    )
  )
);

create policy "complaints insert"
on public.complaints for insert
with check (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('complaints:create'))
);

create policy "complaints update"
on public.complaints for update
using (
  public.is_platform_admin()
  or (
    organization_id = public.current_organization_id()
    and (
      public.has_permission('complaints:update')
      or public.has_permission('complaints:assign')
      or public.has_permission('complaints:investigate')
      or public.has_permission('complaints:resolve')
      or (public.has_permission('complaints:view_assigned') and assigned_to = auth.uid())
    )
  )
);

create policy "complaints delete"
on public.complaints for delete
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('complaints:delete'))
);

-- Child tables inherit tenant checks
create policy "status history select"
on public.complaint_status_history for select
using (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

create policy "status history insert"
on public.complaint_status_history for insert
with check (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

create policy "assignments select"
on public.complaint_assignments for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "assignments insert"
on public.complaint_assignments for insert
with check (
  organization_id = public.current_organization_id()
  and (public.has_permission('complaints:assign') or public.has_permission('complaints:reassign'))
);

create policy "assignments update"
on public.complaint_assignments for update
using (
  organization_id = public.current_organization_id()
  and (public.has_permission('complaints:assign') or public.has_permission('complaints:reassign'))
);

create policy "comments select"
on public.complaint_comments for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "comments insert"
on public.complaint_comments for insert
with check (organization_id = public.current_organization_id() and author_id = auth.uid());

create policy "comments update own"
on public.complaint_comments for update
using (author_id = auth.uid() and organization_id = public.current_organization_id());

create policy "attachments select"
on public.complaint_attachments for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "attachments insert"
on public.complaint_attachments for insert
with check (organization_id = public.current_organization_id());

create policy "investigations select"
on public.complaint_investigations for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "investigations mutate"
on public.complaint_investigations for all
using (
  public.is_platform_admin()
  or (
    organization_id = public.current_organization_id()
    and public.has_permission('complaints:investigate')
  )
)
with check (
  public.is_platform_admin()
  or (
    organization_id = public.current_organization_id()
    and public.has_permission('complaints:investigate')
  )
);

create policy "tasks select"
on public.complaint_tasks for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "tasks mutate"
on public.complaint_tasks for all
using (public.is_platform_admin() or organization_id = public.current_organization_id())
with check (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "resolutions select"
on public.complaint_resolutions for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "resolutions mutate"
on public.complaint_resolutions for all
using (
  public.is_platform_admin()
  or (
    organization_id = public.current_organization_id()
    and (
      public.has_permission('complaints:resolve')
      or public.has_permission('complaints:approve_resolution')
    )
  )
)
with check (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

create policy "feedback select"
on public.complaint_feedback for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "feedback insert"
on public.complaint_feedback for insert
with check (organization_id = public.current_organization_id());

create policy "escalations select"
on public.complaint_escalations for select
using (public.is_platform_admin() or organization_id = public.current_organization_id());

create policy "escalations insert"
on public.complaint_escalations for insert
with check (
  organization_id = public.current_organization_id()
  and public.has_permission('complaints:escalate')
);

-- Notifications: only own
create policy "notifications select own"
on public.notifications for select
using (user_id = auth.uid() or public.is_platform_admin());

create policy "notifications update own"
on public.notifications for update
using (user_id = auth.uid())
with check (user_id = auth.uid());

-- Audit logs immutable for ordinary users
create policy "audit logs select"
on public.audit_logs for select
using (
  public.is_platform_admin()
  or (organization_id = public.current_organization_id() and public.has_permission('audit_logs:view'))
);

create policy "system settings platform only"
on public.system_settings for all
using (public.is_platform_admin())
with check (public.is_platform_admin());

create policy "subscriptions platform only"
on public.subscription_plans for select
using (public.is_platform_admin());

create policy "org subscriptions platform or own admin"
on public.organization_subscriptions for select
using (
  public.is_platform_admin()
  or organization_id = public.current_organization_id()
);

-- Storage bucket for private attachments
insert into storage.buckets (id, name, public)
values ('complaint-attachments', 'complaint-attachments', false)
on conflict (id) do nothing;

create policy "attachment objects select"
on storage.objects for select
using (
  bucket_id = 'complaint-attachments'
  and (
    public.is_platform_admin()
    or (storage.foldername(name))[1] = public.current_organization_id()::text
  )
);

create policy "attachment objects insert"
on storage.objects for insert
with check (
  bucket_id = 'complaint-attachments'
  and (storage.foldername(name))[1] = public.current_organization_id()::text
);

create policy "attachment objects delete"
on storage.objects for delete
using (
  bucket_id = 'complaint-attachments'
  and (
    public.is_platform_admin()
    or (storage.foldername(name))[1] = public.current_organization_id()::text
  )
);
