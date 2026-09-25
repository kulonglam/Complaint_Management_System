<template>
  <section class="space-y-6">
    <PageHeader title="Investigation" description="Internal workspace. This content is never shown on the public tracking page." />
    <form class="surface max-w-3xl space-y-4 rounded-3xl p-6" @submit.prevent="save">
      <FormField v-model="summary" label="Summary" type="textarea" />
      <FormField v-model="findings" label="Findings" type="textarea" />
      <FormField v-model="recommendations" label="Recommendations" type="textarea" />
      <FormField v-model="status" label="Status" type="select" :options="statusOptions" />
      <AppButton type="submit" :loading="loading">Save investigation</AppButton>
    </form>

    <div class="surface max-w-3xl space-y-3 rounded-3xl p-6">
      <div class="flex items-center justify-between">
        <h2 class="font-display text-xl">Tasks</h2>
      </div>
      <form class="grid gap-3 md:grid-cols-3" @submit.prevent="addTask">
        <FormField v-model="taskTitle" label="Task" required />
        <FormField v-model="taskDue" label="Due" type="date" />
        <AppButton type="submit" :disabled="!investigationId">Add task</AppButton>
      </form>
      <ul class="space-y-2 text-sm">
        <li v-for="task in tasks" :key="task.id" class="flex items-center justify-between rounded-xl border border-[var(--line)] px-3 py-2">
          <span>{{ task.title }} · {{ task.status }}</span>
          <select class="field-input w-auto" :value="task.status" @change="updateTask(task, $event.target.value)">
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="DONE">Done</option>
          </select>
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const toast = useToast();
const summary = ref('');
const findings = ref('');
const recommendations = ref('');
const status = ref('IN_PROGRESS');
const loading = ref(false);
const investigationId = ref('');
const tasks = ref([]);
const taskTitle = ref('');
const taskDue = ref('');
const statusOptions = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'SUBMITTED', label: 'Submitted for review' },
  { value: 'COMPLETED', label: 'Completed' },
];

onMounted(async () => {
  const { data } = await supabase
    .from('complaint_investigations')
    .select('*, tasks:complaint_tasks(*)')
    .eq('complaint_id', route.params.id)
    .order('created_at', { ascending: false })
    .limit(1);
  let current = data?.[0];
  if (!current) {
    const { data: created, error } = await supabase.from('complaint_investigations').insert({
      complaint_id: route.params.id,
      organization_id: auth.state.profile.organization_id,
      investigator_id: auth.state.profile.id,
      status: 'DRAFT',
    }).select('*, tasks:complaint_tasks(*)').single();
    if (error) return toast.error(getErrorMessage(error));
    current = created;
  }
  investigationId.value = current.id;
  summary.value = current.summary || '';
  findings.value = current.findings || '';
  recommendations.value = current.recommendations || '';
  status.value = current.status;
  tasks.value = current.tasks || [];
});

async function save() {
  loading.value = true;
  try {
    const payload = {
      complaint_id: route.params.id,
      organization_id: auth.state.profile.organization_id,
      investigator_id: auth.state.profile.id,
      summary: summary.value,
      findings: findings.value,
      recommendations: recommendations.value,
      status: status.value,
      completed_at: status.value === 'COMPLETED' ? new Date().toISOString() : null,
    };
    if (investigationId.value) {
      const { error } = await supabase.from('complaint_investigations').update(payload).eq('id', investigationId.value);
      if (error) throw error;
    } else {
      const { data, error } = await supabase.from('complaint_investigations').insert(payload).select('id').single();
      if (error) throw error;
      investigationId.value = data.id;
    }
    if (status.value === 'IN_PROGRESS') {
      await supabase.rpc('transition_complaint_status', {
        p_complaint_id: route.params.id,
        p_new_status: 'UNDER_INVESTIGATION',
        p_reason: 'Investigation started',
      }).catch(() => {});
    }
    toast.success('Investigation saved');
    router.push(`/complaints/${route.params.id}`);
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to save the investigation.'));
  } finally {
    loading.value = false;
  }
}

async function addTask() {
  const { data, error } = await supabase.from('complaint_tasks').insert({
    investigation_id: investigationId.value,
    complaint_id: route.params.id,
    organization_id: auth.state.profile.organization_id,
    title: taskTitle.value,
    due_date: taskDue.value || null,
    assignee_id: auth.state.profile.id,
  }).select('*').single();
  if (error) return toast.error(getErrorMessage(error));
  tasks.value = [...tasks.value, data];
  taskTitle.value = '';
}

async function updateTask(task, nextStatus) {
  const { error } = await supabase.from('complaint_tasks').update({ status: nextStatus }).eq('id', task.id);
  if (error) return toast.error(getErrorMessage(error));
  tasks.value = tasks.value.map((item) => (item.id === task.id ? { ...item, status: nextStatus } : item));
}
</script>
