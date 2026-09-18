<template>
  <section class="space-y-6">
    <PageHeader title="Investigation" description="Internal workspace. This content is never shown on the public tracking page." />
    <form class="max-w-3xl space-y-4 rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="save">
      <FormField v-model="summary" label="Summary" type="textarea" />
      <FormField v-model="findings" label="Findings" type="textarea" />
      <FormField v-model="recommendations" label="Recommendations" type="textarea" />
      <FormField v-model="status" label="Status" type="select" :options="statusOptions" />
      <AppButton type="submit" :loading="loading">Save investigation</AppButton>
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
const findings = ref('');
const recommendations = ref('');
const status = ref('IN_PROGRESS');
const loading = ref(false);
const statusOptions = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'SUBMITTED', label: 'Submitted for review' },
  { value: 'COMPLETED', label: 'Completed' },
];

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
    const { error } = await supabase.from('complaint_investigations').insert(payload);
    if (error) throw error;
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
</script>
