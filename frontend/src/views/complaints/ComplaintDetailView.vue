<template>
  <LoadingSkeleton v-if="isLoading" />
  <ErrorState v-else-if="isError" :message="getErrorMessage(error)" />
  <section v-else-if="complaint" class="space-y-6">
    <PageHeader :eyebrow="complaint.reference_number" :title="complaint.title">
      <StatusBadge :value="complaint.status" />
      <PriorityBadge :value="complaint.priority" />
    </PageHeader>

    <div class="flex flex-wrap gap-2">
      <AppButton v-if="auth.can('complaints:assign')" variant="secondary" @click="assignOpen = true">Assign</AppButton>
      <AppButton v-if="auth.can('complaints:investigate')" variant="secondary" @click="$router.push(`/complaints/${complaint.id}/investigation`)">Investigation</AppButton>
      <AppButton v-if="auth.can('complaints:resolve')" variant="secondary" @click="$router.push(`/complaints/${complaint.id}/resolution`)">Resolution</AppButton>
      <AppButton v-if="auth.can('complaints:escalate')" variant="secondary" @click="escalateOpen = true">Escalate</AppButton>
      <AppButton
        v-for="status in nextStatuses"
        :key="status"
        variant="secondary"
        @click="askStatus(status)"
      >
        {{ STATUS_LABELS[status] }}
      </AppButton>
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <article class="rounded-2xl border border-slate-200 bg-white p-4 lg:col-span-2">
        <h2 class="font-semibold">Summary</h2>
        <p class="mt-3 whitespace-pre-wrap text-sm text-slate-700">{{ complaint.description }}</p>
      </article>
      <dl class="rounded-2xl border border-slate-200 bg-white p-4 text-sm">
        <div class="flex justify-between py-1"><dt>Complainant</dt><dd>{{ complaint.is_anonymous ? 'Anonymous' : (complaint.complainant_name || '—') }}</dd></div>
        <div class="flex justify-between py-1"><dt>Category</dt><dd>{{ complaint.category?.name || '—' }}</dd></div>
        <div class="flex justify-between py-1"><dt>Department</dt><dd>{{ complaint.department?.name || '—' }}</dd></div>
        <div class="flex justify-between py-1"><dt>Assigned officer</dt><dd>{{ displayName(complaint.assignee) }}</dd></div>
        <div class="flex justify-between py-1"><dt>Incident date</dt><dd>{{ formatDate(complaint.incident_date, false) }}</dd></div>
        <div class="flex justify-between py-1"><dt>Submitted</dt><dd>{{ formatDate(complaint.submitted_at) }}</dd></div>
        <div class="flex justify-between py-1"><dt>Due</dt><dd>{{ remaining.label }}</dd></div>
      </dl>
    </div>

    <div class="flex gap-2 overflow-x-auto text-sm">
      <button v-for="item in tabs" :key="item" class="rounded-full px-3 py-1" :class="tab === item ? 'bg-slate-900 text-white' : 'bg-white border'" @click="tab = item">{{ item }}</button>
    </div>

    <div class="rounded-2xl border border-slate-200 bg-white p-4">
      <div v-if="tab === 'Overview'" class="text-sm text-slate-600">Use the tabs to review timeline, investigation, comments, files, resolution, and audit history.</div>
      <ComplaintTimeline v-else-if="tab === 'Timeline'" :events="timelineEvents" />
      <div v-else-if="tab === 'Investigation'" class="space-y-3 text-sm">
        <article v-for="item in complaint.investigations || []" :key="item.id" class="rounded-xl border p-3">
          <p class="font-medium">{{ item.status }} · {{ displayName(item.investigator) }}</p>
          <p class="mt-2 whitespace-pre-wrap">{{ item.findings || item.summary || 'No findings yet.' }}</p>
        </article>
        <EmptyState v-if="!(complaint.investigations || []).length" title="No investigation yet" message="Start an investigation from the action bar." />
      </div>
      <div v-else-if="tab === 'Comments'">
        <form class="mb-4 grid gap-2" @submit.prevent="addComment">
          <textarea v-model="comment" class="rounded-lg border px-3 py-2 text-sm" rows="3" placeholder="Add a note" />
          <label class="text-sm"><input v-model="commentPublic" type="checkbox" /> Visible to complainant</label>
          <AppButton type="submit" :loading="savingComment">Add comment</AppButton>
        </form>
        <ul class="space-y-3 text-sm">
          <li v-for="item in complaint.comments || []" :key="item.id" class="rounded-xl border p-3">
            <p class="font-medium">{{ displayName(item.author) }} · {{ item.visibility }}</p>
            <p class="mt-1 whitespace-pre-wrap">{{ item.content }}</p>
          </li>
        </ul>
      </div>
      <div v-else-if="tab === 'Attachments'">
        <div class="mb-4 flex flex-wrap items-center gap-3 text-sm">
          <input type="file" @change="uploadFile" />
          <label class="flex items-center gap-2">
            <input v-model="publicFile" type="checkbox" /> Visible on public tracking
          </label>
        </div>
        <ul class="space-y-2 text-sm">
          <li v-for="file in complaint.attachments || []" :key="file.id" class="flex items-center justify-between rounded-lg border px-3 py-2">
            <button class="text-left text-blue-700" type="button" @click="openFile(file)">{{ file.file_name }} ({{ file.visibility }})</button>
            <div class="flex gap-2">
              <button class="text-xs" type="button" @click="toggleVisibility(file)">{{ file.visibility === 'PUBLIC' ? 'Make internal' : 'Make public' }}</button>
              <button class="text-xs text-red-600" type="button" @click="removeFile(file)">Delete</button>
            </div>
          </li>
        </ul>
      </div>
      <div v-else-if="tab === 'Resolution'" class="text-sm space-y-3">
        <article v-for="item in complaint.resolutions || []" :key="item.id" class="rounded-xl border p-3">
          <p class="font-medium">{{ item.approval_status }}</p>
          <p class="mt-2">{{ item.summary }}</p>
          <div v-if="auth.can('complaints:approve_resolution') && item.approval_status === 'SUBMITTED'" class="mt-3 flex gap-2">
            <AppButton @click="review(item.id, 'APPROVED')">Approve</AppButton>
            <AppButton variant="secondary" @click="review(item.id, 'REJECTED')">Reject</AppButton>
          </div>
        </article>
        <EmptyState v-if="!(complaint.resolutions || []).length" title="No resolution yet" />
      </div>
      <div v-else-if="tab === 'Feedback'" class="text-sm">
        <p v-for="item in complaint.feedback || []" :key="item.id">Rating {{ item.rating }}/5 — {{ item.comment }}</p>
        <EmptyState v-if="!(complaint.feedback || []).length" title="No feedback yet" />
      </div>
      <div v-else class="text-sm text-slate-600">
        Status changes, assignments, and investigation updates are recorded automatically in audit logs.
      </div>
    </div>

    <Modal :open="assignOpen" title="Assign complaint" @close="assignOpen = false">
      <form class="grid gap-3" @submit.prevent="doAssign">
        <FormField
          v-model="assignForm.assignee"
          label="Officer"
          type="select"
          required
          :options="staffOptions"
        />
        <FormField
          v-model="assignForm.departmentId"
          label="Department"
          type="select"
          :options="departmentOptions"
        />
        <FormField v-model="assignForm.dueDate" label="Deadline" type="datetime-local" />
        <FormField v-model="assignForm.notes" label="Notes" type="textarea" />
        <AppButton type="submit" :loading="assigning">Assign</AppButton>
      </form>
    </Modal>
    <Modal :open="escalateOpen" title="Escalate complaint" @close="escalateOpen = false">
      <form class="grid gap-3" @submit.prevent="doEscalate">
        <FormField v-model="escalateForm.reason" label="Reason" type="textarea" required />
        <FormField
          v-model="escalateForm.departmentId"
          label="New department"
          type="select"
          :options="departmentOptions"
        />
        <FormField
          v-model="escalateForm.assignee"
          label="New assignee"
          type="select"
          :options="staffOptions"
        />
        <FormField v-model="escalateForm.notes" label="Notes" type="textarea" />
        <AppButton type="submit">Escalate</AppButton>
      </form>
    </Modal>
    <ConfirmationDialog
      :open="Boolean(pendingStatus)"
      :title="`Change status to ${pendingStatus ? STATUS_LABELS[pendingStatus] : ''}`"
      message="This action is recorded in the complaint timeline and audit log."
      confirm-label="Confirm"
      tone="primary"
      :loading="statusLoading"
      @cancel="pendingStatus = ''"
      @confirm="doStatus"
    />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import Modal from '@/components/common/Modal.vue';
import ConfirmationDialog from '@/components/common/ConfirmationDialog.vue';
import FormField from '@/components/forms/FormField.vue';
import ComplaintTimeline from '@/components/complaints/ComplaintTimeline.vue';
import {
  fetchComplaint,
  fetchComplaintTimeline,
  assignComplaint,
  escalateComplaint,
  transitionStatus,
  attachmentUrl,
  reviewResolution,
  addComplaintComment,
} from '@/services/complaint.service';
import { ALLOWED_FILE_TYPES, ALLOWED_TRANSITIONS, MAX_FILE_SIZE, STATUS_LABELS } from '@/lib/constants';
import { displayName, formatDate, getErrorMessage, remainingTime } from '@/lib/utils';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { supabase } from '@/lib/supabase';

const route = useRoute();
const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const tab = ref('Overview');
const tabs = ['Overview', 'Timeline', 'Investigation', 'Comments', 'Attachments', 'Resolution', 'Feedback', 'Audit History'];
const comment = ref('');
const commentPublic = ref(false);
const savingComment = ref(false);
const assignOpen = ref(false);
const escalateOpen = ref(false);
const assigning = ref(false);
const pendingStatus = ref('');
const statusLoading = ref(false);
const assignForm = reactive({ assignee: '', departmentId: '', dueDate: '', notes: '' });
const escalateForm = reactive({ reason: '', departmentId: '', assignee: '', notes: '' });
const publicFile = ref(false);

const { data: complaint, isLoading, isError, error } = useQuery({
  queryKey: computed(() => ['complaint', route.params.id]),
  queryFn: () => fetchComplaint(route.params.id),
});

const { data: staff } = useQuery({
  queryKey: ['assignable-users'],
  queryFn: async () => {
    const { data, error: staffError } = await supabase
      .from('profiles')
      .select('id, first_name, last_name, email')
      .eq('status', 'ACTIVE');
    if (staffError) throw staffError;
    return data;
  },
});
const { data: departments } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error: deptError } = await supabase.from('departments').select('id, name').eq('status', 'ACTIVE');
    if (deptError) throw deptError;
    return data;
  },
});
const { data: timeline } = useQuery({
  queryKey: computed(() => ['complaint-timeline', route.params.id]),
  queryFn: () => fetchComplaintTimeline(route.params.id),
});

const staffOptions = computed(() =>
  (staff.value || []).map((user) => ({
    value: user.id,
    label: displayName(user),
  }))
);
const departmentOptions = computed(() =>
  (departments.value || []).map((item) => ({ value: item.id, label: item.name }))
);

const remaining = computed(() => remainingTime(complaint.value?.due_date));
const nextStatuses = computed(() => ALLOWED_TRANSITIONS[complaint.value?.status] || []);
const timelineEvents = computed(() => {
  if (timeline.value?.length) return timeline.value;
  return [...(complaint.value?.history || [])]
    .sort((a, b) => new Date(a.created_at) - new Date(b.created_at))
    .map((event) => ({
      event: event.new_status,
      label: STATUS_LABELS[event.new_status] || event.new_status,
      at: event.created_at,
      description: event.reason,
    }));
});

function reload() {
  queryClient.invalidateQueries({ queryKey: ['complaint', route.params.id] });
  queryClient.invalidateQueries({ queryKey: ['complaint-timeline', route.params.id] });
}

function askStatus(status) {
  pendingStatus.value = status;
}

async function doStatus() {
  statusLoading.value = true;
  try {
    await transitionStatus(complaint.value.id, pendingStatus.value, pendingStatus.value === 'REJECTED' ? 'Rejected by staff' : 'Workflow update');
    pendingStatus.value = '';
    toast.success('Status updated');
    reload();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to change this status.'));
  } finally {
    statusLoading.value = false;
  }
}

async function doAssign() {
  assigning.value = true;
  try {
    await assignComplaint({
      p_complaint_id: complaint.value.id,
      p_assignee_id: assignForm.assignee,
      p_department_id: assignForm.departmentId || complaint.value.department_id,
      p_due_date: assignForm.dueDate ? new Date(assignForm.dueDate).toISOString() : complaint.value.due_date,
      p_notes: assignForm.notes,
    });
    assignOpen.value = false;
    toast.success('Complaint assigned');
    reload();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to assign this complaint. Please try again.'));
  } finally {
    assigning.value = false;
  }
}

async function doEscalate() {
  try {
    await escalateComplaint({
      p_complaint_id: complaint.value.id,
      p_reason: escalateForm.reason,
      p_new_department_id: escalateForm.departmentId || null,
      p_new_assignee_id: escalateForm.assignee || null,
      p_notes: escalateForm.notes || null,
    });
    escalateOpen.value = false;
    toast.success('Complaint escalated');
    reload();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to escalate this complaint.'));
  }
}

async function addComment() {
  savingComment.value = true;
  try {
    await addComplaintComment(
      complaint.value.id,
      comment.value,
      commentPublic.value ? 'COMPLAINANT_VISIBLE' : 'INTERNAL'
    );
    comment.value = '';
    toast.success('Comment added');
    reload();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to add the comment.'));
  } finally {
    savingComment.value = false;
  }
}

async function uploadFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (!ALLOWED_FILE_TYPES.includes(file.type)) {
    toast.error('That file type is not allowed.');
    return;
  }
  if (file.size > MAX_FILE_SIZE) {
    toast.error('Files must be 10MB or smaller.');
    return;
  }
  const path = `${auth.state.profile.organization_id}/${complaint.value.id}/${crypto.randomUUID()}-${file.name}`;
  const { error: uploadError } = await supabase.storage.from('complaint-attachments').upload(path, file);
  if (uploadError) {
    toast.error(getErrorMessage(uploadError, 'Unable to upload the file.'));
    return;
  }
  await supabase.from('complaint_attachments').insert({
    complaint_id: complaint.value.id,
    organization_id: auth.state.profile.organization_id,
    uploaded_by: auth.state.profile.id,
    file_name: file.name,
    file_path: path,
    file_type: file.type,
    file_size: file.size,
    visibility: publicFile.value ? 'PUBLIC' : 'INTERNAL',
  });
  toast.success('File uploaded');
  reload();
}

async function openFile(file) {
  const url = await attachmentUrl(file.file_path);
  window.open(url, '_blank');
}

async function toggleVisibility(file) {
  const visibility = file.visibility === 'PUBLIC' ? 'INTERNAL' : 'PUBLIC';
  const { error: updateError } = await supabase.from('complaint_attachments').update({ visibility }).eq('id', file.id);
  if (updateError) return toast.error(getErrorMessage(updateError));
  reload();
}

async function removeFile(file) {
  await supabase.storage.from('complaint-attachments').remove([file.file_path]).catch(() => {});
  const { error: deleteError } = await supabase.from('complaint_attachments').delete().eq('id', file.id);
  if (deleteError) return toast.error(getErrorMessage(deleteError));
  reload();
}

async function review(id, status) {
  try {
    await reviewResolution(id, status);
    toast.success(status === 'APPROVED' ? 'Resolution approved' : 'Resolution rejected');
    reload();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to review this resolution.'));
  }
}
</script>
