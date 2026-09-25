-- Close remaining spec gaps: rate limits, email outbox, richer metrics,
-- comment/resolution notifications, login/logout audit, search, timeline, branding.

create table if not exists public.rate_limits (
  bucket text not null,
  actor text not null,
  window_start timestamptz not null default date_trunc('minute', now()),
  hit_count integer not null default 0,
  primary key (bucket, actor, window_start)
);

create table if not exists public.email_outbox (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid references public.organizations(id) on delete cascade,
  to_email text not null,
  template text not null,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED')),
  error_message text,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);

create index if not exists email_outbox_pending_idx
  on public.email_outbox (status, created_at)
  where status = 'PENDING';

alter table public.rate_limits enable row level security;
alter table public.email_outbox enable row level security;

create or replace function public.client_ip()
returns text
language sql
stable
as $$
  select coalesce(
    nullif(current_setting('request.headers', true)::json->>'x-forwarded-for', ''),
    nullif(current_setting('request.headers', true)::json->>'cf-connecting-ip', ''),
    'anon'
  );
$$;

create or replace function public.assert_rate_limit(
  p_bucket text,
  p_limit integer default 8,
  p_window interval default interval '1 minute'
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor text := left(public.client_ip(), 120);
  v_window_start timestamptz := date_trunc('minute', now());
  hits integer;
begin
  insert into public.rate_limits (bucket, actor, window_start, hit_count)
  values (p_bucket, v_actor, v_window_start, 1)
  on conflict (bucket, actor, window_start)
  do update set hit_count = public.rate_limits.hit_count + 1
  returning public.rate_limits.hit_count into hits;

  delete from public.rate_limits
  where public.rate_limits.window_start < now() - p_window;

  if hits > p_limit then
    raise exception 'Too many attempts. Please wait a minute and try again.';
  end if;
end;
$$;

create or replace function public.enqueue_email(
  p_org uuid,
  p_to text,
  p_template text,
  p_payload jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if coalesce(trim(p_to), '') = '' then
    return;
  end if;
  insert into public.email_outbox (organization_id, to_email, template, payload)
  values (p_org, trim(p_to), p_template, coalesce(p_payload, '{}'::jsonb));
end;
$$;

create or replace function public.notify_role_keys(
  p_org uuid,
  p_keys text[],
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
  insert into public.notifications (organization_id, user_id, type, title, message, related_complaint_id)
  select distinct p_org, p.id, p_type, p_title, p_message, p_complaint
  from public.profiles p
  join public.user_roles ur on ur.user_id = p.id
  join public.roles r on r.id = ur.role_id
  where p.organization_id = p_org
    and p.status = 'ACTIVE'
    and r.key = any(p_keys);
end;
$$;

create or replace function public.notify_permission_holders(
  p_org uuid,
  p_permission text,
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
  insert into public.notifications (organization_id, user_id, type, title, message, related_complaint_id)
  select distinct p_org, p.id, p_type, p_title, p_message, p_complaint
  from public.profiles p
  join public.user_roles ur on ur.user_id = p.id
  join public.roles r on r.id = ur.role_id
  join public.role_permissions rp on rp.role_id = r.id
  join public.permissions perm on perm.id = rp.permission_id
  where p.organization_id = p_org
    and p.status = 'ACTIVE'
    and perm.key = p_permission;
end;
$$;

create or replace function public.add_complaint_comment(
  p_complaint_id uuid,
  p_content text,
  p_visibility text default 'INTERNAL'
)
returns public.complaint_comments
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  row public.complaint_comments;
  vis text := upper(coalesce(p_visibility, 'INTERNAL'));
begin
  if coalesce(trim(p_content), '') = '' then
    raise exception 'Comment content is required';
  end if;
  if vis not in ('INTERNAL', 'COMPLAINANT_VISIBLE') then
    raise exception 'Invalid comment visibility';
  end if;

  select * into c from public.complaints
  where id = p_complaint_id
    and (organization_id = public.current_organization_id() or public.is_platform_admin());
  if c.id is null then
    raise exception 'Complaint not found';
  end if;

  insert into public.complaint_comments (
    complaint_id, organization_id, author_id, content, visibility
  ) values (
    c.id, c.organization_id, auth.uid(), trim(p_content), vis
  ) returning * into row;

  perform public.log_audit(
    c.organization_id, auth.uid(), 'complaint.comment_added', 'complaint', c.id,
    null, jsonb_build_object('visibility', vis)
  );

  if c.assigned_to is not null and c.assigned_to is distinct from auth.uid() then
    perform public.notify_user(
      c.organization_id, c.assigned_to, 'COMMENT_ADDED',
      'New comment',
      'A comment was added to ' || c.reference_number || '.',
      c.id
    );
  end if;

  return row;
end;
$$;

create or replace function public.submit_resolution(
  p_complaint_id uuid,
  p_summary text,
  p_corrective_action text default null,
  p_notes text default null
)
returns public.complaint_resolutions
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  row public.complaint_resolutions;
begin
  if not public.has_permission('complaints:resolve') and not public.is_platform_admin() then
    raise exception 'You do not have permission to submit a resolution';
  end if;
  if length(trim(coalesce(p_summary, ''))) < 5 then
    raise exception 'A resolution summary is required';
  end if;

  select * into c from public.complaints
  where id = p_complaint_id
    and (organization_id = public.current_organization_id() or public.is_platform_admin());
  if c.id is null then
    raise exception 'Complaint not found';
  end if;

  insert into public.complaint_resolutions (
    complaint_id, organization_id, summary, corrective_action, notes,
    resolved_by, resolution_date, approval_status
  ) values (
    c.id, c.organization_id, trim(p_summary), p_corrective_action, p_notes,
    auth.uid(), current_date, 'SUBMITTED'
  ) returning * into row;

  perform public.log_audit(
    c.organization_id, auth.uid(), 'complaint.resolution_submitted', 'complaint', c.id,
    null, jsonb_build_object('resolution_id', row.id)
  );

  perform public.notify_permission_holders(
    c.organization_id, 'complaints:approve_resolution',
    'RESOLUTION_SUBMITTED', 'Resolution submitted',
    c.reference_number || ' has a resolution waiting for review.',
    c.id
  );
  perform public.notify_role_keys(
    c.organization_id, array['supervisor', 'organization_administrator'],
    'RESOLUTION_SUBMITTED', 'Resolution submitted',
    c.reference_number || ' has a resolution waiting for review.',
    c.id
  );

  return row;
end;
$$;

create or replace function public.review_resolution(
  p_resolution_id uuid,
  p_status text,
  p_notes text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  res public.complaint_resolutions;
  c public.complaints;
begin
  if not public.has_permission('complaints:approve_resolution') and not public.is_platform_admin() then
    raise exception 'You do not have permission to review resolutions';
  end if;
  if p_status not in ('APPROVED', 'REJECTED') then
    raise exception 'Review status must be APPROVED or REJECTED';
  end if;

  select * into res from public.complaint_resolutions where id = p_resolution_id;
  if res.id is null then
    raise exception 'Resolution not found';
  end if;
  select * into c from public.complaints where id = res.complaint_id;

  update public.complaint_resolutions
  set approval_status = p_status,
      reviewed_by = auth.uid(),
      reviewed_at = now(),
      review_notes = p_notes
  where id = p_resolution_id;

  perform public.log_audit(
    res.organization_id, auth.uid(),
    case when p_status = 'APPROVED' then 'complaint.resolution_approved' else 'complaint.resolution_rejected' end,
    'complaint', res.complaint_id,
    jsonb_build_object('approval_status', res.approval_status),
    jsonb_build_object('approval_status', p_status)
  );

  if c.assigned_to is not null then
    perform public.notify_user(
      c.organization_id, c.assigned_to,
      case when p_status = 'APPROVED' then 'RESOLUTION_APPROVED' else 'RESOLUTION_REJECTED' end,
      case when p_status = 'APPROVED' then 'Resolution approved' else 'Resolution rejected' end,
      c.reference_number || ' resolution was ' || lower(p_status) || '.',
      c.id
    );
  end if;

  if p_status = 'APPROVED' then
    perform public.transition_complaint_status(res.complaint_id, 'RESOLVED', coalesce(p_notes, 'Resolution approved'));
    perform public.enqueue_email(
      c.organization_id, c.complainant_email, 'complaint-resolved',
      jsonb_build_object('reference_number', c.reference_number)
    );
  end if;
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
      c.organization_id, c.assigned_to,
      case
        when p_new_status = 'CLOSED' then 'COMPLAINT_CLOSED'
        when p_new_status = 'REOPENED' then 'COMPLAINT_REOPENED'
        else 'STATUS_CHANGE'
      end,
      'Complaint status updated',
      c.reference_number || ' is now ' || replace(p_new_status, '_', ' ') || '.',
      c.id
    );
  end if;

  if p_new_status = 'CLOSED' then
    perform public.enqueue_email(
      c.organization_id, c.complainant_email, 'complaint-closed',
      jsonb_build_object('reference_number', c.reference_number)
    );
  elsif p_new_status = 'REOPENED' then
    perform public.enqueue_email(
      c.organization_id, c.complainant_email, 'complaint-reopened',
      jsonb_build_object('reference_number', c.reference_number)
    );
  elsif p_new_status = 'RESOLVED' then
    perform public.enqueue_email(
      c.organization_id, c.complainant_email, 'complaint-resolved',
      jsonb_build_object('reference_number', c.reference_number)
    );
  else
    perform public.enqueue_email(
      c.organization_id, c.complainant_email, 'complaint-status-updated',
      jsonb_build_object('reference_number', c.reference_number, 'status', p_new_status)
    );
  end if;

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
    jsonb_build_object('department_id', c.department_id, 'assigned_to', c.assigned_to),
    jsonb_build_object('reason', p_reason, 'new_department_id', p_new_department_id, 'new_assignee_id', p_new_assignee_id)
  );

  if c.assigned_to is not null then
    perform public.notify_user(
      c.organization_id, c.assigned_to, 'ESCALATION',
      'Complaint escalated',
      c.reference_number || ' was escalated.',
      c.id
    );
  end if;

  perform public.notify_role_keys(
    c.organization_id,
    array['supervisor', 'organization_administrator', 'department_manager'],
    'ESCALATION',
    'Complaint escalated',
    c.reference_number || ' was escalated: ' || left(p_reason, 120),
    c.id
  );

  return c;
end;
$$;

create or replace function public.mark_login()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  prev timestamptz;
  org uuid;
begin
  select last_login_at, organization_id into prev, org
  from public.profiles
  where id = auth.uid();

  update public.profiles set last_login_at = now() where id = auth.uid();

  if prev is null or prev < now() - interval '15 minutes' then
    perform public.log_audit(org, auth.uid(), 'auth.login', 'profile', auth.uid(), null, null);
  end if;
end;
$$;

create or replace function public.mark_logout()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  org uuid;
begin
  select organization_id into org from public.profiles where id = auth.uid();
  perform public.log_audit(org, auth.uid(), 'auth.logout', 'profile', auth.uid(), null, null);
end;
$$;

create or replace function public.audit_profile_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    perform public.log_audit(
      new.organization_id, coalesce(auth.uid(), new.id), 'user.created', 'profile', new.id,
      null, jsonb_build_object('email', new.email, 'status', new.status)
    );
    return new;
  end if;

  if new.status is distinct from old.status
     or new.department_id is distinct from old.department_id
     or new.job_title is distinct from old.job_title then
    perform public.log_audit(
      new.organization_id, auth.uid(), 'user.updated', 'profile', new.id,
      jsonb_build_object('status', old.status, 'department_id', old.department_id),
      jsonb_build_object('status', new.status, 'department_id', new.department_id, 'job_title', new.job_title)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists audit_profile_changes on public.profiles;
create trigger audit_profile_changes
after insert or update on public.profiles
for each row execute function public.audit_profile_changes();

create or replace function public.audit_role_changes()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    perform public.log_audit(new.organization_id, auth.uid(), 'user.role_changed', 'user_role', new.user_id,
      null, jsonb_build_object('role_id', new.role_id));
    return new;
  end if;
  if tg_op = 'DELETE' then
    perform public.log_audit(old.organization_id, auth.uid(), 'user.role_changed', 'user_role', old.user_id,
      jsonb_build_object('role_id', old.role_id), null);
    return old;
  end if;
  return new;
end;
$$;

drop trigger if exists audit_role_changes on public.user_roles;
create trigger audit_role_changes
after insert or delete on public.user_roles
for each row execute function public.audit_role_changes();

create or replace function public.search_complaints(
  p_search text default null,
  p_status text default null,
  p_priority text default null,
  p_department_id uuid default null,
  p_category_id uuid default null,
  p_assigned_to uuid default null,
  p_from timestamptz default null,
  p_to timestamptz default null,
  p_overdue boolean default false,
  p_page integer default 1,
  p_page_size integer default 12,
  p_sort text default 'created_at',
  p_ascending boolean default false
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  org uuid := public.current_organization_id();
  sort_col text := case p_sort
    when 'reference_number' then 'reference_number'
    when 'title' then 'title'
    when 'priority' then 'priority'
    when 'status' then 'status'
    when 'due_date' then 'due_date'
    when 'created_at' then 'created_at'
    else 'created_at'
  end;
  needle text := nullif(trim(coalesce(p_search, '')), '');
  result jsonb;
begin
  if not (
    public.has_permission('complaints:view')
    or public.has_permission('complaints:view_all')
    or public.has_permission('complaints:view_assigned')
    or public.is_platform_admin()
  ) then
    raise exception 'You do not have permission to view complaints';
  end if;

  with filtered as (
    select
      c.*,
      cc.name as category_name,
      d.name as department_name,
      p.first_name as assignee_first_name,
      p.last_name as assignee_last_name,
      p.email as assignee_email
    from public.complaints c
    left join public.complaint_categories cc on cc.id = c.category_id
    left join public.departments d on d.id = c.department_id
    left join public.profiles p on p.id = c.assigned_to
    where (c.organization_id = org or public.is_platform_admin())
      and (p_status is null or c.status = p_status)
      and (p_priority is null or c.priority = p_priority)
      and (p_department_id is null or c.department_id = p_department_id)
      and (p_category_id is null or c.category_id = p_category_id)
      and (p_assigned_to is null or c.assigned_to = p_assigned_to)
      and (p_from is null or c.created_at >= p_from)
      and (p_to is null or c.created_at <= p_to)
      and (
        p_overdue is not true
        or c.sla_breached
        or (c.due_date < now() and c.status not in ('RESOLVED', 'CLOSED', 'REJECTED', 'DUPLICATE'))
      )
      and (
        public.has_permission('complaints:view_all')
        or public.is_platform_admin()
        or c.assigned_to = auth.uid()
        or c.complainant_id = auth.uid()
      )
      and (
        needle is null
        or c.reference_number ilike '%' || needle || '%'
        or c.title ilike '%' || needle || '%'
        or c.description ilike '%' || needle || '%'
        or c.status ilike '%' || needle || '%'
        or c.priority ilike '%' || needle || '%'
        or coalesce(cc.name, '') ilike '%' || needle || '%'
        or coalesce(d.name, '') ilike '%' || needle || '%'
      )
  ),
  counted as (
    select count(*) as total from filtered
  ),
  paged as (
    select *
    from filtered
    order by
      case when not p_ascending then null
           when sort_col = 'reference_number' then reference_number end asc,
      case when not p_ascending then null
           when sort_col = 'title' then title end asc,
      case when not p_ascending then null
           when sort_col = 'priority' then priority end asc,
      case when not p_ascending then null
           when sort_col = 'status' then status end asc,
      case when p_ascending then null
           when sort_col = 'reference_number' then reference_number end desc,
      case when p_ascending then null
           when sort_col = 'title' then title end desc,
      case when p_ascending then null
           when sort_col = 'priority' then priority end desc,
      case when p_ascending then null
           when sort_col = 'status' then status end desc,
      case when sort_col = 'due_date' and p_ascending then due_date end asc nulls last,
      case when sort_col = 'due_date' and not p_ascending then due_date end desc nulls last,
      case when sort_col = 'created_at' and p_ascending then created_at end asc,
      case when sort_col = 'created_at' and not p_ascending then created_at end desc,
      created_at desc
    offset greatest(p_page - 1, 0) * greatest(p_page_size, 1)
    limit greatest(p_page_size, 1)
  )
  select jsonb_build_object(
    'total', (select total from counted),
    'items', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', paged.id,
        'reference_number', paged.reference_number,
        'title', paged.title,
        'status', paged.status,
        'priority', paged.priority,
        'due_date', paged.due_date,
        'created_at', paged.created_at,
        'sla_breached', paged.sla_breached,
        'category', case when paged.category_name is null then null else jsonb_build_object('name', paged.category_name) end,
        'department', case when paged.department_name is null then null else jsonb_build_object('name', paged.department_name) end,
        'assignee', case
          when paged.assignee_email is null then null
          else jsonb_build_object(
            'first_name', paged.assignee_first_name,
            'last_name', paged.assignee_last_name,
            'email', paged.assignee_email
          )
        end
      )) from paged
    ), '[]'::jsonb)
  ) into result;

  return result;
end;
$$;

create or replace function public.complaint_timeline(p_complaint_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  org uuid := public.current_organization_id();
begin
  if not exists (
    select 1 from public.complaints c
    where c.id = p_complaint_id
      and (c.organization_id = org or public.is_platform_admin())
  ) then
    raise exception 'Complaint not found';
  end if;

  return (
    select coalesce(jsonb_agg(event order by (event->>'at')::timestamptz, event->>'event'), '[]'::jsonb)
    from (
      select jsonb_build_object(
        'event', 'status_' || lower(h.new_status),
        'label', initcap(replace(h.new_status, '_', ' ')),
        'at', h.created_at,
        'actor_id', h.changed_by,
        'description', h.reason,
        'visibility', 'STAFF'
      ) as event
      from public.complaint_status_history h
      where h.complaint_id = p_complaint_id

      union all
      select jsonb_build_object(
        'event', 'assigned',
        'label', 'Assigned',
        'at', a.assigned_at,
        'actor_id', a.assigned_by,
        'description', coalesce(a.notes, 'Complaint assigned'),
        'visibility', 'STAFF'
      )
      from public.complaint_assignments a
      where a.complaint_id = p_complaint_id

      union all
      select jsonb_build_object(
        'event', 'escalated',
        'label', 'Escalated',
        'at', e.created_at,
        'actor_id', e.escalated_by,
        'description', e.reason,
        'visibility', 'STAFF'
      )
      from public.complaint_escalations e
      where e.complaint_id = p_complaint_id

      union all
      select jsonb_build_object(
        'event', case when i.completed_at is null then 'investigation_started' else 'investigation_completed' end,
        'label', case when i.completed_at is null then 'Investigation started' else 'Investigation completed' end,
        'at', coalesce(i.completed_at, i.started_at, i.created_at),
        'actor_id', i.investigator_id,
        'description', coalesce(i.summary, i.findings),
        'visibility', 'STAFF'
      )
      from public.complaint_investigations i
      where i.complaint_id = p_complaint_id

      union all
      select jsonb_build_object(
        'event', 'resolution_' || lower(r.approval_status),
        'label', 'Resolution ' || lower(r.approval_status),
        'at', coalesce(r.reviewed_at, r.created_at),
        'actor_id', coalesce(r.reviewed_by, r.resolved_by),
        'description', r.summary,
        'visibility', 'STAFF'
      )
      from public.complaint_resolutions r
      where r.complaint_id = p_complaint_id
    ) events
  );
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
    'avg_response_hours', (
      select coalesce(avg(extract(epoch from (h.created_at - f.submitted_at)) / 3600.0), 0)
      from filtered f
      join public.complaint_status_history h on h.complaint_id = f.id and h.new_status = 'RECEIVED'
    ),
    'resolution_rate', (
      select case when count(*) = 0 then 0
        else round(100.0 * count(*) filter (where status in ('RESOLVED', 'CLOSED')) / count(*), 1) end
      from filtered
    ),
    'overdue_rate', (
      select case when count(*) = 0 then 0
        else round(100.0 * count(*) filter (where sla_breached) / count(*), 1) end
      from filtered
    ),
    'escalation_rate', (
      select case when count(*) = 0 then 0
        else round(100.0 * count(*) filter (where status = 'ESCALATED') / count(*), 1) end
      from filtered
    ),
    'reopening_rate', (
      select case when count(*) = 0 then 0
        else round(100.0 * count(*) filter (where status = 'REOPENED') / count(*), 1) end
      from filtered
    ),
    'sla_compliance', (
      select case
        when count(*) filter (where status in ('RESOLVED', 'CLOSED')) = 0 then 0
        else round(
          100.0 * count(*) filter (where status in ('RESOLVED', 'CLOSED') and not sla_breached)
          / count(*) filter (where status in ('RESOLVED', 'CLOSED')),
          1
        )
      end
      from filtered
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
    ),
    'resolution_trend', (
      select coalesce(jsonb_agg(jsonb_build_object('date', day, 'count', cnt) order by day), '[]'::jsonb)
      from (
        select date_trunc('day', resolved_at)::date as day, count(*) as cnt
        from filtered
        where resolved_at is not null
        group by 1
        order by 1
      ) t
    )
  ) into result;

  return result;
end;
$$;

create or replace function public.report_metrics(
  p_from timestamptz default null,
  p_to timestamptz default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  org uuid := public.current_organization_id();
begin
  if not public.has_permission('reports:view') and not public.is_platform_admin() then
    raise exception 'You do not have permission to view reports';
  end if;

  return jsonb_build_object(
    'sla_breached', (
      select count(*) from public.complaints c
      where (public.is_platform_admin() or c.organization_id = org)
        and c.sla_breached
        and (p_from is null or c.created_at >= p_from)
        and (p_to is null or c.created_at <= p_to)
    ),
    'on_time', (
      select count(*) from public.complaints c
      where (public.is_platform_admin() or c.organization_id = org)
        and c.status in ('RESOLVED', 'CLOSED')
        and not c.sla_breached
        and (p_from is null or c.created_at >= p_from)
        and (p_to is null or c.created_at <= p_to)
    ),
    'aging', (
      select coalesce(jsonb_object_agg(bucket, cnt), '{}'::jsonb)
      from (
        select
          case
            when now() - c.created_at < interval '3 days' then '0-3 days'
            when now() - c.created_at < interval '7 days' then '3-7 days'
            when now() - c.created_at < interval '30 days' then '7-30 days'
            else '30+ days'
          end as bucket,
          count(*) as cnt
        from public.complaints c
        where (public.is_platform_admin() or c.organization_id = org)
          and c.status not in ('CLOSED', 'REJECTED', 'DUPLICATE')
        group by 1
      ) rows
    ),
    'by_assignee', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'name', coalesce(trim(p.first_name || ' ' || p.last_name), p.email, 'Unassigned'),
        'open', cnt
      ) order by cnt desc), '[]'::jsonb)
      from (
        select c.assigned_to, count(*) as cnt
        from public.complaints c
        where (public.is_platform_admin() or c.organization_id = org)
          and c.status not in ('CLOSED', 'REJECTED', 'DUPLICATE', 'RESOLVED')
        group by c.assigned_to
      ) q
      left join public.profiles p on p.id = q.assigned_to
    ),
    'monthly_trends', (
      select coalesce(jsonb_agg(jsonb_build_object('month', month, 'count', cnt) order by month), '[]'::jsonb)
      from (
        select to_char(date_trunc('month', c.created_at), 'YYYY-MM') as month, count(*) as cnt
        from public.complaints c
        where (public.is_platform_admin() or c.organization_id = org)
          and (p_from is null or c.created_at >= p_from)
          and (p_to is null or c.created_at <= p_to)
        group by 1
      ) t
    ),
    'avg_resolution_hours', (
      select coalesce(avg(extract(epoch from (c.resolved_at - c.submitted_at)) / 3600.0), 0)
      from public.complaints c
      where (public.is_platform_admin() or c.organization_id = org)
        and c.resolved_at is not null
        and (p_from is null or c.created_at >= p_from)
        and (p_to is null or c.created_at <= p_to)
    ),
    'reopen_count', (
      select count(*) from public.complaints c
      where (public.is_platform_admin() or c.organization_id = org)
        and c.status = 'REOPENED'
        and (p_from is null or c.created_at >= p_from)
        and (p_to is null or c.created_at <= p_to)
    )
  );
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
  perform public.assert_rate_limit('public_submit', 5);

  if length(trim(coalesce(p_title, ''))) < 3 then
    raise exception 'A complaint title is required';
  end if;
  if length(trim(coalesce(p_description, ''))) < 10 then
    raise exception 'Please provide a more detailed description';
  end if;
  if p_is_anonymous is not true and coalesce(trim(p_email), '') <> ''
     and p_email !~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$' then
    raise exception 'Enter a valid email address';
  end if;
  if coalesce(trim(p_phone), '') <> '' and p_phone !~* '^\+?[0-9\s().-]{7,20}$' then
    raise exception 'Enter a valid phone number';
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

  if p_is_anonymous is not true then
    perform public.enqueue_email(
      org.id, p_email, 'complaint-received',
      jsonb_build_object('reference_number', ref, 'organization', org.name)
    );
  end if;

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
  sub_name text;
  resolution_summary text;
begin
  perform public.assert_rate_limit('public_track', 12);

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
  select name into sub_name from public.complaint_subcategories where id = c.subcategory_id;

  select summary into resolution_summary
  from public.complaint_resolutions
  where complaint_id = c.id and approval_status = 'APPROVED'
  order by updated_at desc
  limit 1;

  return jsonb_build_object(
    'id', c.id,
    'organization_id', c.organization_id,
    'reference_number', c.reference_number,
    'title', c.title,
    'submitted_at', c.submitted_at,
    'updated_at', c.updated_at,
    'category', cat_name,
    'subcategory', sub_name,
    'status', c.status,
    'priority', c.priority,
    'resolved_at', c.resolved_at,
    'closed_at', c.closed_at,
    'can_feedback', c.status in ('RESOLVED', 'CLOSED')
      and not exists (select 1 from public.complaint_feedback f where f.complaint_id = c.id),
    'resolution_summary', case when c.status in ('RESOLVED', 'CLOSED') then resolution_summary else null end,
    'timeline', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'status', h.new_status,
        'reason', h.reason,
        'created_at', h.created_at
      ) order by h.created_at), '[]'::jsonb)
      from public.complaint_status_history h
      where h.complaint_id = c.id
    ),
    'comments', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'content', cm.content,
        'created_at', cm.created_at
      ) order by cm.created_at), '[]'::jsonb)
      from public.complaint_comments cm
      where cm.complaint_id = c.id and cm.visibility = 'COMPLAINANT_VISIBLE'
    ),
    'attachments', (
      select coalesce(jsonb_agg(jsonb_build_object(
        'id', a.id,
        'file_name', a.file_name,
        'file_path', a.file_path,
        'file_type', a.file_type
      ) order by a.created_at), '[]'::jsonb)
      from public.complaint_attachments a
      where a.complaint_id = c.id and a.visibility = 'PUBLIC'
    )
  );
end;
$$;

insert into storage.buckets (id, name, public)
values ('organization-logos', 'organization-logos', true)
on conflict (id) do nothing;

drop policy if exists "logo public read" on storage.objects;
create policy "logo public read"
on storage.objects for select
to anon, authenticated
using (bucket_id = 'organization-logos');

drop policy if exists "logo upload" on storage.objects;
create policy "logo upload"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'organization-logos'
  and (public.is_platform_admin() or public.has_permission('settings:update'))
);

drop policy if exists "logo update" on storage.objects;
create policy "logo update"
on storage.objects for update
to authenticated
using (
  bucket_id = 'organization-logos'
  and (public.is_platform_admin() or public.has_permission('settings:update'))
);

grant execute on function public.add_complaint_comment(uuid, text, text) to authenticated;
grant execute on function public.submit_resolution(uuid, text, text, text) to authenticated;
grant execute on function public.review_resolution(uuid, text, text) to authenticated;
grant execute on function public.mark_logout() to authenticated;
grant execute on function public.mark_login() to authenticated;
grant execute on function public.search_complaints(text, text, text, uuid, uuid, uuid, timestamptz, timestamptz, boolean, integer, integer, text, boolean) to authenticated;
grant execute on function public.complaint_timeline(uuid) to authenticated;
grant execute on function public.dashboard_metrics(timestamptz, timestamptz, uuid, uuid, text, text) to authenticated;
grant execute on function public.report_metrics(timestamptz, timestamptz) to authenticated;
grant execute on function public.submit_public_complaint(text, text, text, boolean, text, text, text, uuid, uuid, date, text, text) to anon, authenticated;
grant execute on function public.track_public_complaint(text, text) to anon, authenticated;
drop policy if exists "org subscriptions manage" on public.organization_subscriptions;
create policy "org subscriptions manage"
on public.organization_subscriptions for all
using (public.is_platform_admin())
with check (public.is_platform_admin());

grant execute on function public.transition_complaint_status(uuid, text, text) to authenticated;
grant execute on function public.escalate_complaint(uuid, text, uuid, uuid, text) to authenticated;
