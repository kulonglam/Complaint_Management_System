-- Auth helpers, workflow, public submit/track, SLA, notifications, audit

create or replace function public.current_profile()
returns public.profiles
language sql
stable
security definer
set search_path = public
as $$
  select * from public.profiles where id = auth.uid();
$$;

create or replace function public.current_organization_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select organization_id from public.profiles where id = auth.uid();
$$;

create or replace function public.is_platform_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.user_roles ur
    join public.roles r on r.id = ur.role_id
    where ur.user_id = auth.uid()
      and r.key = 'platform_administrator'
  );
$$;

create or replace function public.has_permission(p_permission text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select public.is_platform_admin()
    or exists (
      select 1
      from public.user_roles ur
      join public.role_permissions rp on rp.role_id = ur.role_id
      join public.permissions p on p.id = rp.permission_id
      where ur.user_id = auth.uid()
        and p.key = p_permission
    );
$$;

create or replace function public.hash_tracking_code(p_code text)
returns text
language sql
immutable
set search_path = public, extensions
as $$
  select encode(digest(convert_to(upper(trim(p_code)), 'UTF8'), 'sha256'::text), 'hex');
$$;

create or replace function public.generate_tracking_code()
returns text
language plpgsql
as $$
declare
  chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  result text := '';
  i int;
begin
  for i in 1..8 loop
    result := result || substr(chars, 1 + floor(random() * length(chars))::int, 1);
    if i = 4 then
      result := result || '-';
    end if;
  end loop;
  return result;
end;
$$;

create or replace function public.next_complaint_reference(p_org uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  y int := extract(year from now())::int;
  n int;
begin
  insert into public.organization_counters (organization_id, year, last_number)
  values (p_org, y, 1)
  on conflict (organization_id, year)
  do update set last_number = public.organization_counters.last_number + 1
  returning last_number into n;

  return 'CMP-' || y::text || '-' || lpad(n::text, 6, '0');
end;
$$;

create or replace function public.due_date_for_priority(p_org uuid, p_priority text)
returns timestamptz
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  hours int;
begin
  select resolution_hours into hours
  from public.sla_policies
  where organization_id = p_org and priority = p_priority
  limit 1;

  if hours is null then
    hours := case p_priority
      when 'CRITICAL' then 24
      when 'HIGH' then 72
      when 'MEDIUM' then 168
      else 336
    end;
  end if;

  return now() + make_interval(hours => hours);
end;
$$;

create or replace function public.log_audit(
  p_org uuid,
  p_user uuid,
  p_action text,
  p_entity_type text,
  p_entity_id uuid,
  p_old jsonb default null,
  p_new jsonb default null,
  p_meta jsonb default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.audit_logs (
    organization_id, user_id, action, entity_type, entity_id, old_values, new_values, metadata
  ) values (
    p_org, p_user, p_action, p_entity_type, p_entity_id, p_old, p_new, p_meta
  );
end;
$$;

create or replace function public.notify_user(
  p_org uuid,
  p_user uuid,
  p_type text,
  p_title text,
  p_message text,
  p_complaint uuid default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_user is null then
    return;
  end if;
  insert into public.notifications (
    organization_id, user_id, type, title, message, related_complaint_id
  ) values (
    p_org, p_user, p_type, p_title, p_message, p_complaint
  );
end;
$$;

create or replace function public.can_transition_status(p_from text, p_to text)
returns boolean
language plpgsql
immutable
as $$
begin
  if p_from = p_to then
    return false;
  end if;

  return case p_from
    when 'SUBMITTED' then p_to in ('RECEIVED', 'REJECTED', 'DUPLICATE')
    when 'RECEIVED' then p_to in ('UNDER_REVIEW', 'REJECTED', 'DUPLICATE', 'ON_HOLD')
    when 'UNDER_REVIEW' then p_to in ('ASSIGNED', 'REJECTED', 'DUPLICATE', 'ON_HOLD', 'ESCALATED')
    when 'ASSIGNED' then p_to in ('UNDER_INVESTIGATION', 'ESCALATED', 'ON_HOLD', 'REJECTED')
    when 'UNDER_INVESTIGATION' then p_to in ('PENDING_ACTION', 'ESCALATED', 'ON_HOLD')
    when 'PENDING_ACTION' then p_to in ('RESOLVED', 'ON_HOLD', 'ESCALATED')
    when 'RESOLVED' then p_to in ('CLOSED', 'REOPENED')
    when 'CLOSED' then p_to in ('REOPENED')
    when 'REJECTED' then p_to in ('REOPENED')
    when 'ESCALATED' then p_to in ('ASSIGNED', 'UNDER_REVIEW', 'UNDER_INVESTIGATION', 'ON_HOLD')
    when 'ON_HOLD' then p_to in ('UNDER_REVIEW', 'ASSIGNED', 'UNDER_INVESTIGATION', 'PENDING_ACTION')
    when 'REOPENED' then p_to in ('UNDER_REVIEW', 'ASSIGNED')
    when 'DUPLICATE' then p_to in ('CLOSED', 'REOPENED')
    else false
  end;
end;
$$;

create or replace function public.submit_public_complaint(
  p_org_slug text,
  p_title text,
  p_description text,
  p_is_anonymous boolean,
  p_name text default null,
  p_email text default null,
  p_phone text default null,
  p_category_id uuid default null,
  p_subcategory_id uuid default null,
  p_incident_date date default null,
  p_location text default null,
  p_priority text default 'MEDIUM'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  org public.organizations;
  settings public.organization_settings;
  complaint_id uuid;
  ref text;
  tracking text;
  pri text;
begin
  if length(trim(coalesce(p_title, ''))) < 3 then
    raise exception 'A complaint title is required';
  end if;
  if length(trim(coalesce(p_description, ''))) < 10 then
    raise exception 'Please provide a more detailed description';
  end if;

  select * into org from public.organizations
  where slug = p_org_slug and status = 'ACTIVE' and public_portal_enabled = true;

  if org.id is null then
    raise exception 'Organization is not available for public submissions';
  end if;

  select * into settings from public.organization_settings where organization_id = org.id;

  if p_is_anonymous is true and coalesce(settings.allow_anonymous, true) is false then
    raise exception 'Anonymous complaints are not enabled for this organization';
  end if;

  if p_is_anonymous is not true then
    if coalesce(trim(p_name), '') = '' or coalesce(trim(p_email), '') = '' then
      raise exception 'Name and email are required unless the complaint is anonymous';
    end if;
  end if;

  pri := coalesce(nullif(p_priority, ''), 'MEDIUM');
  if pri not in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') then
    pri := 'MEDIUM';
  end if;

  tracking := public.generate_tracking_code();
  ref := public.next_complaint_reference(org.id);
  complaint_id := gen_random_uuid();

  insert into public.complaints (
    id, organization_id, reference_number, tracking_code_hash,
    complainant_name, complainant_email, complainant_phone, is_anonymous,
    title, description, category_id, subcategory_id, priority, status,
    incident_date, location, due_date, submitted_at
  ) values (
    complaint_id, org.id, ref, public.hash_tracking_code(tracking),
    case when p_is_anonymous then null else p_name end,
    case when p_is_anonymous then null else p_email end,
    case when p_is_anonymous then null else p_phone end,
    coalesce(p_is_anonymous, false),
    trim(p_title), trim(p_description), p_category_id, p_subcategory_id, pri, 'SUBMITTED',
    p_incident_date, p_location, public.due_date_for_priority(org.id, pri), now()
  );

  insert into public.complaint_status_history (
    complaint_id, organization_id, old_status, new_status, reason
  ) values (
    complaint_id, org.id, null, 'SUBMITTED', 'Public submission'
  );

  perform public.log_audit(
    org.id, null, 'complaint.created', 'complaint', complaint_id, null,
    jsonb_build_object('reference_number', ref, 'anonymous', coalesce(p_is_anonymous, false))
  );

  insert into public.notifications (organization_id, user_id, type, title, message, related_complaint_id)
  select org.id, p.id, 'COMPLAINT_SUBMITTED',
         'New complaint submitted',
         'Complaint ' || ref || ' was submitted.',
         complaint_id
  from public.profiles p
  join public.user_roles ur on ur.user_id = p.id
  join public.roles r on r.id = ur.role_id
  join public.role_permissions rp on rp.role_id = r.id
  join public.permissions perm on perm.id = rp.permission_id
  where p.organization_id = org.id
    and perm.key = 'complaints:view_all';

  return jsonb_build_object(
    'reference_number', ref,
    'tracking_code', tracking,
    'organization', org.name
  );
end;
$$;

create or replace function public.track_public_complaint(
  p_reference text,
  p_tracking_code text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  cat_name text;
  resolution_summary text;
begin
  if coalesce(trim(p_reference), '') = '' or coalesce(trim(p_tracking_code), '') = '' then
    raise exception 'Reference number and tracking code are required';
  end if;

  select * into c
  from public.complaints
  where reference_number = upper(trim(p_reference))
    and tracking_code_hash = public.hash_tracking_code(p_tracking_code);

  if c.id is null then
    raise exception 'No complaint matched those details';
  end if;

  select name into cat_name from public.complaint_categories where id = c.category_id;

  select summary into resolution_summary
  from public.complaint_resolutions
  where complaint_id = c.id and approval_status = 'APPROVED'
  order by updated_at desc
  limit 1;

  return jsonb_build_object(
    'reference_number', c.reference_number,
    'submitted_at', c.submitted_at,
    'updated_at', c.updated_at,
    'category', cat_name,
    'status', c.status,
    'priority', c.priority,
    'resolved_at', c.resolved_at,
    'closed_at', c.closed_at,
    'resolution_summary', case when c.status in ('RESOLVED', 'CLOSED') then resolution_summary else null end
  );
end;
$$;

create or replace function public.transition_complaint_status(
  p_complaint_id uuid,
  p_new_status text,
  p_reason text default null
)
returns public.complaints
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  old_status text;
  org_id uuid := public.current_organization_id();
begin
  if not (
    public.has_permission('complaints:update')
    or public.has_permission('complaints:close')
    or public.has_permission('complaints:reopen')
    or public.has_permission('complaints:escalate')
  ) then
    raise exception 'You do not have permission to change complaint status';
  end if;

  select * into c from public.complaints
  where id = p_complaint_id
    and (organization_id = org_id or public.is_platform_admin());

  if c.id is null then
    raise exception 'Complaint not found';
  end if;

  old_status := c.status;

  if not public.can_transition_status(old_status, p_new_status) then
    raise exception 'Status cannot change from % to %', old_status, p_new_status;
  end if;

  if p_new_status in ('REJECTED', 'REOPENED', 'ON_HOLD', 'ESCALATED') and coalesce(trim(p_reason), '') = '' then
    raise exception 'A reason is required for this status change';
  end if;

  update public.complaints
  set status = p_new_status,
      resolved_at = case
        when p_new_status = 'RESOLVED' then now()
        when p_new_status = 'REOPENED' then null
        else resolved_at
      end,
      closed_at = case
        when p_new_status = 'CLOSED' then now()
        when p_new_status = 'REOPENED' then null
        else closed_at
      end
  where id = c.id
  returning * into c;

  insert into public.complaint_status_history (
    complaint_id, organization_id, old_status, new_status, changed_by, reason
  ) values (
    c.id, c.organization_id, old_status, p_new_status, auth.uid(), p_reason
  );

  perform public.log_audit(
    c.organization_id, auth.uid(), 'complaint.status_changed', 'complaint', c.id,
    jsonb_build_object('status', old_status),
    jsonb_build_object('status', p_new_status, 'reason', p_reason)
  );

  if c.assigned_to is not null then
    perform public.notify_user(
      c.organization_id, c.assigned_to, 'STATUS_CHANGE',
      'Complaint status updated',
      c.reference_number || ' is now ' || replace(p_new_status, '_', ' ') || '.',
      c.id
    );
  end if;

  return c;
end;
$$;

create or replace function public.assign_complaint(
  p_complaint_id uuid,
  p_assignee_id uuid,
  p_department_id uuid default null,
  p_due_date timestamptz default null,
  p_notes text default null
)
returns public.complaints
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  org_id uuid := public.current_organization_id();
  prev_assignee uuid;
begin
  if not (public.has_permission('complaints:assign') or public.has_permission('complaints:reassign')) then
    raise exception 'You do not have permission to assign complaints';
  end if;

  select * into c from public.complaints
  where id = p_complaint_id
    and (organization_id = org_id or public.is_platform_admin());

  if c.id is null then
    raise exception 'Complaint not found';
  end if;

  if not exists (
    select 1 from public.profiles
    where id = p_assignee_id and organization_id = c.organization_id and status = 'ACTIVE'
  ) then
    raise exception 'Assignee is not a member of this organization';
  end if;

  prev_assignee := c.assigned_to;

  if prev_assignee is not null then
    update public.complaint_assignments
    set status = 'REASSIGNED'
    where complaint_id = c.id and status in ('PENDING', 'ACCEPTED', 'IN_PROGRESS');
  end if;

  insert into public.complaint_assignments (
    complaint_id, organization_id, assigned_to, assigned_by, department_id, due_date, notes
  ) values (
    c.id, c.organization_id, p_assignee_id, auth.uid(),
    coalesce(p_department_id, c.department_id), coalesce(p_due_date, c.due_date), p_notes
  );

  update public.complaints
  set assigned_to = p_assignee_id,
      department_id = coalesce(p_department_id, department_id),
      due_date = coalesce(p_due_date, due_date),
      status = case
        when status in ('SUBMITTED', 'RECEIVED', 'UNDER_REVIEW', 'REOPENED', 'ESCALATED') then 'ASSIGNED'
        else status
      end
  where id = c.id
  returning * into c;

  if public.can_transition_status(
    (select old_status from (select c.status as old_status) s),
    'ASSIGNED'
  ) then
    -- status already updated above when appropriate
    null;
  end if;

  insert into public.complaint_status_history (
    complaint_id, organization_id, old_status, new_status, changed_by, reason
  )
  select c.id, c.organization_id, 'UNDER_REVIEW', 'ASSIGNED', auth.uid(), coalesce(p_notes, 'Assigned')
  where c.status = 'ASSIGNED'
    and not exists (
      select 1 from public.complaint_status_history
      where complaint_id = c.id and new_status = 'ASSIGNED'
        and created_at > now() - interval '2 seconds'
    );

  perform public.log_audit(
    c.organization_id, auth.uid(),
    case when prev_assignee is null then 'complaint.assigned' else 'complaint.reassigned' end,
    'complaint', c.id,
    jsonb_build_object('assigned_to', prev_assignee),
    jsonb_build_object('assigned_to', p_assignee_id, 'department_id', p_department_id)
  );

  perform public.notify_user(
    c.organization_id, p_assignee_id,
    case when prev_assignee is null then 'ASSIGNMENT' else 'REASSIGNMENT' end,
    'Complaint assigned',
    'You have been assigned ' || c.reference_number || '.',
    c.id
  );

  return c;
end;
$$;

create or replace function public.escalate_complaint(
  p_complaint_id uuid,
  p_reason text,
  p_new_department_id uuid default null,
  p_new_assignee_id uuid default null,
  p_notes text default null
)
returns public.complaints
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  org_id uuid := public.current_organization_id();
  old_status text;
begin
  if not public.has_permission('complaints:escalate') then
    raise exception 'You do not have permission to escalate complaints';
  end if;

  if coalesce(trim(p_reason), '') = '' then
    raise exception 'An escalation reason is required';
  end if;

  select * into c from public.complaints
  where id = p_complaint_id
    and (organization_id = org_id or public.is_platform_admin());

  if c.id is null then
    raise exception 'Complaint not found';
  end if;

  old_status := c.status;

  insert into public.complaint_escalations (
    complaint_id, organization_id, escalated_by, reason,
    previous_department_id, new_department_id,
    previous_assignee_id, new_assignee_id, notes
  ) values (
    c.id, c.organization_id, auth.uid(), p_reason,
    c.department_id, p_new_department_id,
    c.assigned_to, p_new_assignee_id, p_notes
  );

  update public.complaints
  set status = 'ESCALATED',
      department_id = coalesce(p_new_department_id, department_id),
      assigned_to = coalesce(p_new_assignee_id, assigned_to),
      priority = case when priority in ('LOW', 'MEDIUM') then 'HIGH' else priority end
  where id = c.id
  returning * into c;

  insert into public.complaint_status_history (
    complaint_id, organization_id, old_status, new_status, changed_by, reason
  ) values (
    c.id, c.organization_id, old_status, 'ESCALATED', auth.uid(), p_reason
  );

  perform public.log_audit(
    c.organization_id, auth.uid(), 'complaint.escalated', 'complaint', c.id,
    null, jsonb_build_object('reason', p_reason)
  );

  if c.assigned_to is not null then
    perform public.notify_user(
      c.organization_id, c.assigned_to, 'ESCALATION',
      'Complaint escalated',
      c.reference_number || ' was escalated.',
      c.id
    );
  end if;

  return c;
end;
$$;

create or replace function public.process_sla_jobs()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  rec record;
  processed int := 0;
  reminder_hours int;
begin
  for rec in
    select c.*, p.reminder_hours
    from public.complaints c
    left join public.sla_policies p
      on p.organization_id = c.organization_id and p.priority = c.priority
    where c.status not in ('RESOLVED', 'CLOSED', 'REJECTED', 'DUPLICATE')
      and c.due_date is not null
  loop
    if rec.due_date < now() and rec.sla_breached is false then
      update public.complaints set sla_breached = true, status = case
        when status not in ('ESCALATED') then 'ESCALATED' else status
      end
      where id = rec.id;

      insert into public.complaint_status_history (
        complaint_id, organization_id, old_status, new_status, reason
      ) values (
        rec.id, rec.organization_id, rec.status, 'ESCALATED', 'Automatic SLA breach'
      );

      if rec.assigned_to is not null then
        perform public.notify_user(
          rec.organization_id, rec.assigned_to, 'SLA_OVERDUE',
          'Complaint overdue',
          rec.reference_number || ' has exceeded its SLA.',
          rec.id
        );
      end if;
      processed := processed + 1;
    elsif rec.due_date > now()
      and rec.reminder_hours is not null
      and rec.due_date - make_interval(hours => rec.reminder_hours) < now()
      and rec.assigned_to is not null then
      perform public.notify_user(
        rec.organization_id, rec.assigned_to, 'SLA_WARNING',
        'SLA approaching',
        rec.reference_number || ' is approaching its due date.',
        rec.id
      );
      processed := processed + 1;
    end if;
  end loop;
  return processed;
end;
$$;

create or replace function public.dashboard_metrics(
  p_from timestamptz default null,
  p_to timestamptz default null,
  p_department_id uuid default null,
  p_category_id uuid default null,
  p_priority text default null,
  p_status text default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  org_id uuid := public.current_organization_id();
  result jsonb;
begin
  if not public.has_permission('reports:view') and not public.has_permission('complaints:view_all') then
    if not public.has_permission('complaints:view') then
      raise exception 'You do not have permission to view reports';
    end if;
  end if;

  with filtered as (
    select *
    from public.complaints c
    where (c.organization_id = org_id or public.is_platform_admin())
      and (p_from is null or c.created_at >= p_from)
      and (p_to is null or c.created_at < p_to)
      and (p_department_id is null or c.department_id = p_department_id)
      and (p_category_id is null or c.category_id = p_category_id)
      and (p_priority is null or c.priority = p_priority)
      and (p_status is null or c.status = p_status)
      and (
        public.has_permission('complaints:view_all')
        or public.is_platform_admin()
        or c.assigned_to = auth.uid()
        or c.complainant_id = auth.uid()
      )
  )
  select jsonb_build_object(
    'total', (select count(*) from filtered),
    'open', (select count(*) from filtered where status not in ('RESOLVED', 'CLOSED', 'REJECTED', 'DUPLICATE')),
    'under_investigation', (select count(*) from filtered where status = 'UNDER_INVESTIGATION'),
    'resolved', (select count(*) from filtered where status = 'RESOLVED'),
    'closed', (select count(*) from filtered where status = 'CLOSED'),
    'overdue', (select count(*) from filtered where sla_breached or (due_date < now() and status not in ('RESOLVED', 'CLOSED', 'REJECTED'))),
    'escalated', (select count(*) from filtered where status = 'ESCALATED'),
    'avg_resolution_hours', (
      select coalesce(avg(extract(epoch from (resolved_at - submitted_at)) / 3600.0), 0)
      from filtered where resolved_at is not null
    ),
    'by_status', (select coalesce(jsonb_object_agg(status, cnt), '{}'::jsonb) from (
      select status, count(*) as cnt from filtered group by status
    ) s),
    'by_priority', (select coalesce(jsonb_object_agg(priority, cnt), '{}'::jsonb) from (
      select priority, count(*) as cnt from filtered group by priority
    ) p),
    'by_category', (
      select coalesce(jsonb_agg(jsonb_build_object('name', name, 'count', cnt)), '[]'::jsonb)
      from (
        select coalesce(cc.name, 'Uncategorized') as name, count(*) as cnt
        from filtered f
        left join public.complaint_categories cc on cc.id = f.category_id
        group by coalesce(cc.name, 'Uncategorized')
        order by cnt desc
      ) x
    ),
    'by_department', (
      select coalesce(jsonb_agg(jsonb_build_object('name', name, 'count', cnt)), '[]'::jsonb)
      from (
        select coalesce(d.name, 'Unassigned') as name, count(*) as cnt
        from filtered f
        left join public.departments d on d.id = f.department_id
        group by coalesce(d.name, 'Unassigned')
        order by cnt desc
      ) x
    ),
    'over_time', (
      select coalesce(jsonb_agg(jsonb_build_object('date', day, 'count', cnt) order by day), '[]'::jsonb)
      from (
        select date_trunc('day', created_at)::date as day, count(*) as cnt
        from filtered
        group by 1
        order by 1
      ) t
    )
  ) into result;

  return result;
end;
$$;

-- Prevent clients from changing organization_id on profiles
create or replace function public.protect_profile_org()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' and new.organization_id is distinct from old.organization_id then
    if not public.is_platform_admin() then
      raise exception 'You cannot change organization membership';
    end if;
  end if;
  return new;
end;
$$;

create trigger protect_profile_org
before update on public.profiles
for each row execute function public.protect_profile_org();

create or replace function public.mark_login()
returns void
language sql
security definer
set search_path = public
as $$
  update public.profiles set last_login_at = now() where id = auth.uid();
$$;

grant execute on function public.submit_public_complaint(
  text, text, text, boolean, text, text, text, uuid, uuid, date, text, text
) to anon, authenticated;

grant execute on function public.track_public_complaint(text, text) to anon, authenticated;

grant execute on function public.transition_complaint_status(uuid, text, text) to authenticated;
grant execute on function public.assign_complaint(uuid, uuid, uuid, timestamptz, text) to authenticated;
grant execute on function public.escalate_complaint(uuid, text, uuid, uuid, text) to authenticated;
grant execute on function public.dashboard_metrics(timestamptz, timestamptz, uuid, uuid, text, text) to authenticated;
grant execute on function public.has_permission(text) to authenticated;
grant execute on function public.mark_login() to authenticated;
grant execute on function public.process_sla_jobs() to service_role;

create or replace function public.list_public_catalog(p_org_slug text default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  return jsonb_build_object(
    'organizations', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'name', o.name,
        'slug', o.slug,
        'logo_url', o.logo_url,
        'allow_anonymous', coalesce(s.allow_anonymous, true)
      ) order by o.name), '[]'::jsonb)
      from public.organizations o
      left join public.organization_settings s on s.organization_id = o.id
      where o.status = 'ACTIVE'
        and o.public_portal_enabled = true
        and (p_org_slug is null or o.slug = p_org_slug)
    ),
    'categories', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'id', c.id,
        'name', c.name,
        'organization_slug', o.slug,
        'subcategories', (
          select coalesce(jsonb_agg(jsonb_build_object('id', sc.id, 'name', sc.name) order by sc.name), '[]'::jsonb)
          from public.complaint_subcategories sc
          where sc.category_id = c.id and sc.status = 'ACTIVE'
        )
      ) order by c.name), '[]'::jsonb)
      from public.complaint_categories c
      join public.organizations o on o.id = c.organization_id
      where c.status = 'ACTIVE'
        and o.status = 'ACTIVE'
        and o.public_portal_enabled = true
        and (p_org_slug is null or o.slug = p_org_slug)
    )
  );
end;
$$;

grant execute on function public.list_public_catalog(text) to anon, authenticated;
