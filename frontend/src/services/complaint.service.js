import { supabase } from '@/lib/supabase';
import { assertSafeUpload } from '@/lib/uploads';

export async function fetchComplaints({
  page = 1,
  pageSize = 12,
  search = '',
  status,
  priority,
  departmentId,
  categoryId,
  assignedTo,
  from,
  to,
  overdue,
  sort = 'created_at',
  ascending = false,
}) {
  const { data, error } = await supabase.rpc('search_complaints', {
    p_search: search || null,
    p_status: status || null,
    p_priority: priority || null,
    p_department_id: departmentId || null,
    p_category_id: categoryId || null,
    p_assigned_to: assignedTo || null,
    p_from: from || null,
    p_to: to || null,
    p_overdue: Boolean(overdue),
    p_page: page,
    p_page_size: pageSize,
    p_sort: sort,
    p_ascending: ascending,
  });
  if (error) throw error;
  return { items: data?.items || [], total: data?.total || 0 };
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
      investigations:complaint_investigations(*, investigator:profiles(first_name, last_name), tasks:complaint_tasks(*)),
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

export async function submitPublicFeedback(reference, trackingCode, rating, comment) {
  const { error } = await supabase.rpc('submit_public_feedback', {
    p_reference: reference,
    p_tracking_code: trackingCode,
    p_rating: rating,
    p_comment: comment || null,
  });
  if (error) throw error;
}

export async function uploadPublicFiles(files) {
  const uploaded = [];
  for (const file of files) {
    assertSafeUpload(file);
    const path = `public-inbox/${crypto.randomUUID()}-${file.name}`;
    const { error } = await supabase.storage.from('complaint-attachments').upload(path, file);
    if (error) throw error;
    uploaded.push({
      file_name: file.name,
      file_path: path,
      file_type: file.type,
      file_size: file.size,
    });
  }
  return uploaded;
}

export async function linkPublicAttachments(reference, trackingCode, files) {
  if (!files.length) return;
  const { error } = await supabase.rpc('link_public_attachments', {
    p_reference: reference,
    p_tracking_code: trackingCode,
    p_files: files,
  });
  if (error) throw error;
}

export async function attachmentUrl(path) {
  const { data, error } = await supabase.storage.from('complaint-attachments').createSignedUrl(path, 3600);
  if (error) throw error;
  return data.signedUrl;
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

export async function reviewResolution(resolutionId, status, notes) {
  const { error } = await supabase.rpc('review_resolution', {
    p_resolution_id: resolutionId,
    p_status: status,
    p_notes: notes || null,
  });
  if (error) throw error;
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

export async function fetchReports(filters) {
  const { data, error } = await supabase.rpc('report_metrics', {
    p_from: filters.from || null,
    p_to: filters.to || null,
  });
  if (error) throw error;
  return data;
}

export async function fetchComplaintTimeline(complaintId) {
  const { data, error } = await supabase.rpc('complaint_timeline', {
    p_complaint_id: complaintId,
  });
  if (error) throw error;
  return data || [];
}

export async function addComplaintComment(complaintId, content, visibility) {
  const { data, error } = await supabase.rpc('add_complaint_comment', {
    p_complaint_id: complaintId,
    p_content: content,
    p_visibility: visibility,
  });
  if (error) throw error;
  return data;
}

export async function submitResolution(payload) {
  const { data, error } = await supabase.rpc('submit_resolution', payload);
  if (error) throw error;
  return data;
}
