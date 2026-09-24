-- Public tracking, feedback, attachments, org create, resolution review, reports

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

create or replace function public.submit_public_feedback(
  p_reference text,
  p_tracking_code text,
  p_rating integer,
  p_comment text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
begin
  if p_rating is null or p_rating < 1 or p_rating > 5 then
    raise exception 'Rating must be between 1 and 5';
  end if;

  select * into c
  from public.complaints
  where reference_number = upper(trim(p_reference))
    and tracking_code_hash = public.hash_tracking_code(p_tracking_code);

  if c.id is null then
    raise exception 'No complaint matched those details';
  end if;
  if c.status not in ('RESOLVED', 'CLOSED') then
    raise exception 'Feedback is available after the complaint is resolved';
  end if;
  if exists (select 1 from public.complaint_feedback f where f.complaint_id = c.id) then
    raise exception 'Feedback has already been submitted';
  end if;

  insert into public.complaint_feedback (complaint_id, organization_id, rating, comment)
  values (c.id, c.organization_id, p_rating, nullif(trim(p_comment), ''));
end;
$$;

create or replace function public.link_public_attachments(
  p_reference text,
  p_tracking_code text,
  p_files jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  c public.complaints;
  item jsonb;
begin
  select * into c
  from public.complaints
  where reference_number = upper(trim(p_reference))
    and tracking_code_hash = public.hash_tracking_code(p_tracking_code);

  if c.id is null then
    raise exception 'No complaint matched those details';
  end if;

  for item in select * from jsonb_array_elements(coalesce(p_files, '[]'::jsonb))
  loop
    if coalesce(item->>'file_path', '') not like 'public-inbox/%' then
      raise exception 'Invalid attachment path';
    end if;
    insert into public.complaint_attachments (
      complaint_id, organization_id, file_name, file_path, file_type, file_size, visibility
    ) values (
      c.id,
      c.organization_id,
      coalesce(item->>'file_name', 'attachment'),
      item->>'file_path',
      coalesce(item->>'file_type', 'application/octet-stream'),
      coalesce((item->>'file_size')::integer, 0),
      'PUBLIC'
    );
  end loop;
end;
$$;

create or replace function public.create_organization(
  p_name text,
  p_slug text,
  p_email text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  org_id uuid;
begin
  if not public.is_platform_admin() then
    raise exception 'Only a platform administrator can create organizations';
  end if;
  if length(trim(coalesce(p_name, ''))) < 2 then
    raise exception 'Organization name is required';
  end if;

  insert into public.organizations (name, slug, email, status)
  values (trim(p_name), lower(trim(p_slug)), nullif(trim(p_email), ''), 'ACTIVE')
  returning id into org_id;

  perform public.provision_organization(org_id);
  return org_id;
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

  update public.complaint_resolutions
  set approval_status = p_status,
      reviewed_by = auth.uid(),
      reviewed_at = now(),
      review_notes = p_notes
  where id = p_resolution_id;

  if p_status = 'APPROVED' then
    perform public.transition_complaint_status(res.complaint_id, 'RESOLVED', coalesce(p_notes, 'Resolution approved'));
  end if;
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
    )
  );
end;
$$;

grant execute on function public.track_public_complaint(text, text) to anon, authenticated;
grant execute on function public.submit_public_feedback(text, text, integer, text) to anon, authenticated;
grant execute on function public.link_public_attachments(text, text, jsonb) to anon, authenticated;
grant execute on function public.create_organization(text, text, text) to authenticated;
grant execute on function public.review_resolution(uuid, text, text) to authenticated;
grant execute on function public.report_metrics(timestamptz, timestamptz) to authenticated;

drop policy if exists "attachments update" on public.complaint_attachments;
create policy "attachments update"
on public.complaint_attachments for update
using (public.is_platform_admin() or organization_id = public.current_organization_id());

drop policy if exists "attachments delete" on public.complaint_attachments;
create policy "attachments delete"
on public.complaint_attachments for delete
using (public.is_platform_admin() or organization_id = public.current_organization_id());

drop policy if exists "public inbox insert" on storage.objects;
create policy "public inbox insert"
on storage.objects for insert
to anon, authenticated
with check (
  bucket_id = 'complaint-attachments'
  and (storage.foldername(name))[1] = 'public-inbox'
);

drop policy if exists "public attachments readable" on storage.objects;
create policy "public attachments readable"
on storage.objects for select
to anon, authenticated
using (
  bucket_id = 'complaint-attachments'
  and (
    (storage.foldername(name))[1] = 'public-inbox'
    or exists (
      select 1 from public.complaint_attachments a
      where a.file_path = name and a.visibility = 'PUBLIC'
    )
  )
);

insert into public.subscription_plans (name)
values ('Trial'), ('Standard'), ('Enterprise')
on conflict (name) do nothing;

insert into public.system_settings (key, value)
values ('app', jsonb_build_object('maintenance', false, 'support_email', 'support@cms.local'))
on conflict (key) do nothing;

do $$
begin
  alter publication supabase_realtime add table public.notifications;
exception
  when duplicate_object then null;
  when undefined_object then null;
end $$;
