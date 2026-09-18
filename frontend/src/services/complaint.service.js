import { supabase } from '@/lib/supabase';

export async function fetchComplaints({ page = 1, pageSize = 12, search = '', status, priority, departmentId, categoryId, assignedTo, from, to, sort = 'created_at', ascending = false }) {
  const fromRow = (page - 1) * pageSize;
  const toRow = fromRow + pageSize - 1;

  let query = supabase
    .from('complaints')
    .select(
      '*, category:complaint_categories(name), department:departments(name), assignee:profiles!complaints_assigned_to_fkey(first_name, last_name, email)',
      { count: 'exact' }
    )
    .order(sort, { ascending })
    .range(fromRow, toRow);

  if (search) {
    query = query.or(`reference_number.ilike.%${search}%,title.ilike.%${search}%,description.ilike.%${search}%`);
  }
  if (status) query = query.eq('status', status);
  if (priority) query = query.eq('priority', priority);
  if (departmentId) query = query.eq('department_id', departmentId);
  if (categoryId) query = query.eq('category_id', categoryId);
  if (assignedTo) query = query.eq('assigned_to', assignedTo);
  if (from) query = query.gte('created_at', from);
  if (to) query = query.lte('created_at', to);

  const { data, error, count } = await query;
  if (error) throw error;
  return { items: data || [], total: count || 0 };
}

export async function fetchComplaint(id) {
  const { data, error } = await supabase
    .from('complaints')
    .select(
      `*,
      category:complaint_categories(id, name),
      subcategory:complaint_subcategories(id, name),
      department:departments(id, name),
      assignee:profiles!complaints_assigned_to_fkey(id, first_name, last_name, email),
      history:complaint_status_history(id, old_status, new_status, reason, created_at, changed_by),
      assignments:complaint_assignments(*, assignee:profiles!complaint_assignments_assigned_to_fkey(first_name, last_name)),
      comments:complaint_comments(*, author:profiles(first_name, last_name)),
      attachments:complaint_attachments(*),
      investigations:complaint_investigations(*, investigator:profiles(first_name, last_name)),
      resolutions:complaint_resolutions(*, resolver:profiles!complaint_resolutions_resolved_by_fkey(first_name, last_name)),
      feedback:complaint_feedback(*),
      escalations:complaint_escalations(*)`
    )
    .eq('id', id)
    .single();
  if (error) throw error;
  return data;
}

export async function submitPublicComplaint(payload) {
  const { data, error } = await supabase.rpc('submit_public_complaint', payload);
  if (error) throw error;
  return data;
}

export async function trackPublicComplaint(reference, trackingCode) {
  const { data, error } = await supabase.rpc('track_public_complaint', {
    p_reference: reference,
    p_tracking_code: trackingCode,
  });
  if (error) throw error;
  return data;
}

export async function transitionStatus(complaintId, status, reason) {
  const { data, error } = await supabase.rpc('transition_complaint_status', {
    p_complaint_id: complaintId,
    p_new_status: status,
    p_reason: reason || null,
  });
  if (error) throw error;
  return data;
}

export async function assignComplaint(payload) {
  const { data, error } = await supabase.rpc('assign_complaint', payload);
  if (error) throw error;
  return data;
}

export async function escalateComplaint(payload) {
  const { data, error } = await supabase.rpc('escalate_complaint', payload);
  if (error) throw error;
  return data;
}

export async function fetchDashboard(filters) {
  const { data, error } = await supabase.rpc('dashboard_metrics', {
    p_from: filters.from || null,
    p_to: filters.to || null,
    p_department_id: filters.departmentId || null,
    p_category_id: filters.categoryId || null,
    p_priority: filters.priority || null,
    p_status: filters.status || null,
  });
  if (error) throw error;
  return data;
}
