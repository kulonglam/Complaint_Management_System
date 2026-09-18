<template>
  <section class="space-y-6">
    <PageHeader title="Resolution" description="Recommend a resolution. Supervisors with approval permission can accept or reject it." />
    <form class="max-w-3xl space-y-4 rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="save">
      <FormField v-model="summary" label="Resolution summary" type="textarea" required />
      <FormField v-model="corrective" label="Corrective action" type="textarea" />
      <FormField v-model="notes" label="Notes" type="textarea" />
      <FormField v-model="approval" label="Approval status" type="select" :options="approvalOptions" />
      <AppButton type="submit" :loading="loading">Save resolution</AppButton>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue';
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
const corrective = ref('');
const notes = ref('');
const approval = ref('SUBMITTED');
const loading = ref(false);
const approvalOptions = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'SUBMITTED', label: 'Submitted' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
];

async function save() {
  if (approval.value === 'APPROVED' && !auth.can('complaints:approve_resolution')) {
    toast.error('You do not have permission to approve a resolution.');
    return;
  }
  loading.value = true;
  try {
    const { error } = await supabase.from('complaint_resolutions').insert({
      complaint_id: route.params.id,
      organization_id: auth.state.profile.organization_id,
      summary: summary.value,
      corrective_action: corrective.value,
      notes: notes.value,
      resolved_by: auth.state.profile.id,
      resolution_date: new Date().toISOString().slice(0, 10),
      approval_status: approval.value,
      reviewed_by: approval.value === 'APPROVED' ? auth.state.profile.id : null,
      reviewed_at: approval.value === 'APPROVED' ? new Date().toISOString() : null,
    });
    if (error) throw error;
    if (approval.value === 'APPROVED') {
      await supabase.rpc('transition_complaint_status', {
        p_complaint_id: route.params.id,
        p_new_status: 'RESOLVED',
        p_reason: 'Resolution approved',
      }).catch(() => {});
    }
    toast.success('Resolution saved');
    router.push(`/complaints/${route.params.id}`);
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to save the resolution.'));
  } finally {
    loading.value = false;
  }
}
</script>
