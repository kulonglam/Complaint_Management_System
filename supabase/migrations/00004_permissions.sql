-- Canonical permissions and platform role

insert into public.permissions (key, description) values
  ('complaints:create', 'Create complaints'),
  ('complaints:view', 'View own or limited complaints'),
  ('complaints:view_all', 'View all organization complaints'),
  ('complaints:view_assigned', 'View assigned complaints'),
  ('complaints:update', 'Update complaint fields and status'),
  ('complaints:assign', 'Assign complaints'),
  ('complaints:reassign', 'Reassign complaints'),
  ('complaints:investigate', 'Investigate complaints'),
  ('complaints:resolve', 'Submit resolutions'),
  ('complaints:approve_resolution', 'Approve or reject resolutions'),
  ('complaints:close', 'Close complaints'),
  ('complaints:reopen', 'Reopen complaints'),
  ('complaints:escalate', 'Escalate complaints'),
  ('complaints:delete', 'Delete complaints'),
  ('users:create', 'Create users'),
  ('users:view', 'View users'),
  ('users:update', 'Update users'),
  ('users:disable', 'Disable users'),
  ('users:delete', 'Delete users'),
  ('departments:create', 'Create departments'),
  ('departments:view', 'View departments'),
  ('departments:update', 'Update departments'),
  ('departments:delete', 'Delete departments'),
  ('categories:create', 'Create categories'),
  ('categories:view', 'View categories'),
  ('categories:update', 'Update categories'),
  ('categories:delete', 'Delete categories'),
  ('reports:view', 'View reports'),
  ('reports:export', 'Export reports'),
  ('sla:create', 'Create SLA policies'),
  ('sla:view', 'View SLA policies'),
  ('sla:update', 'Update SLA policies'),
  ('sla:delete', 'Delete SLA policies'),
  ('audit_logs:view', 'View audit logs'),
  ('settings:view', 'View settings'),
  ('settings:update', 'Update settings'),
  ('roles:create', 'Create roles'),
  ('roles:view', 'View roles'),
  ('roles:update', 'Update roles'),
  ('roles:delete', 'Delete roles')
on conflict (key) do nothing;

insert into public.roles (organization_id, key, name, description, is_system)
values (null, 'platform_administrator', 'Platform Administrator', 'Manages all tenants', true)
on conflict do nothing;

insert into public.role_permissions (role_id, permission_id)
select r.id, p.id
from public.roles r
cross join public.permissions p
where r.key = 'platform_administrator' and r.organization_id is null
on conflict do nothing;

create or replace function public.provision_organization(p_org uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  role_keys text[] := array[
    'organization_administrator',
    'complaint_officer',
    'investigator',
    'department_manager',
    'supervisor',
    'auditor',
    'complainant'
  ];
  role_names text[] := array[
    'Organization Administrator',
    'Complaint Officer',
    'Investigator',
    'Department Manager',
    'Supervisor',
    'Auditor',
    'Complainant'
  ];
  i int;
  rid uuid;
begin
  if auth.uid() is not null and not public.is_platform_admin() then
    raise exception 'Not allowed to provision an organization';
  end if;

  insert into public.organization_settings (organization_id)
  values (p_org)
  on conflict (organization_id) do nothing;

  insert into public.sla_policies (organization_id, name, priority, response_hours, resolution_hours, escalation_hours, reminder_hours)
  values
    (p_org, 'Low priority', 'LOW', 72, 336, 300, 48),
    (p_org, 'Medium priority', 'MEDIUM', 24, 168, 144, 24),
    (p_org, 'High priority', 'HIGH', 8, 72, 48, 12),
    (p_org, 'Critical priority', 'CRITICAL', 2, 24, 12, 4)
  on conflict (organization_id, priority) do nothing;

  for i in 1..array_length(role_keys, 1) loop
    insert into public.roles (organization_id, key, name, is_system)
    values (p_org, role_keys[i], role_names[i], true)
    on conflict (organization_id, key) do update set name = excluded.name
    returning id into rid;

    delete from public.role_permissions where role_id = rid;

    if role_keys[i] = 'organization_administrator' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions;
    elsif role_keys[i] = 'complaint_officer' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in (
        'complaints:create','complaints:view','complaints:view_all','complaints:update',
        'complaints:assign','complaints:reassign','complaints:close','complaints:reopen',
        'departments:view','categories:view','reports:view','sla:view'
      );
    elsif role_keys[i] = 'investigator' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in (
        'complaints:view','complaints:view_assigned','complaints:update',
        'complaints:investigate','complaints:resolve','categories:view','departments:view'
      );
    elsif role_keys[i] = 'department_manager' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in (
        'complaints:view_all','complaints:update','complaints:assign','complaints:reassign',
        'complaints:escalate','complaints:approve_resolution','complaints:close',
        'departments:view','departments:update','categories:view','reports:view','users:view'
      );
    elsif role_keys[i] = 'supervisor' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in (
        'complaints:view_all','complaints:update','complaints:assign','complaints:reassign',
        'complaints:escalate','complaints:approve_resolution','complaints:close','complaints:reopen',
        'reports:view','reports:export','audit_logs:view','sla:view','users:view','departments:view'
      );
    elsif role_keys[i] = 'auditor' then
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in (
        'complaints:view_all','reports:view','reports:export','audit_logs:view',
        'departments:view','categories:view','sla:view','users:view'
      );
    else
      insert into public.role_permissions (role_id, permission_id)
      select rid, id from public.permissions
      where key in ('complaints:create','complaints:view');
    end if;
  end loop;
end;
$$;

grant execute on function public.provision_organization(uuid) to service_role, authenticated;
