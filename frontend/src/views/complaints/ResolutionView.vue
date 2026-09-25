<template>
  <section class="space-y-6">
    <PageHeader title="Resolution" description="Recommend a resolution. Supervisors with approval permission can accept or reject it." />
    <form class="surface max-w-3xl space-y-4 rounded-3xl p-6" @submit.prevent="save">
      <FormField v-model="summary" label="Resolution summary" type="textarea" required />
      <FormField v-model="corrective" label="Corrective action" type="textarea" />
      <FormField v-model="notes" label="Notes" type="textarea" />
      <p class="text-sm text-muted">Saving submits the resolution for review. Approval is a separate action.</p>
      <AppButton type="submit" :loading="loading">Submit for review</AppButton>
    </form>

    <div v-if="current" class="surface max-w-3xl space-y-3 rounded-3xl p-6 text-sm">
      <p><strong>Current status:</strong> {{ current.approval_status }}</p>
      <p>{{ current.summary }}</p>
      <div v-if="auth.can('complaints:approve_resolution') && current.approval_status === 'SUBMITTED'" class="flex gap-2">
        <AppButton @click="review('APPROVED')">Approve</AppButton>
        <AppButton variant="secondary" @click="review('REJECTED')">Reject</AppButton>
      </div>
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
import { reviewResolution, submitResolution } from '@/services/complaint.service';
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
const loading = ref(false);
const current = ref(null);

onMounted(async () => {
  const { data } = await supabase
    .from('complaint_resolutions')
    .select('*')
    .eq('complaint_id', route.params.id)
    .order('created_at', { ascending: false })
    .limit(1);
  current.value = data?.[0] || null;
  if (!current.value) return;
  summary.value = current.value.summary || '';
  corrective.value = current.value.corrective_action || '';
  notes.value = current.value.notes || '';
});

async function save() {
  loading.value = true;
  try {
    current.value = await submitResolution({
      p_complaint_id: route.params.id,
      p_summary: summary.value,
      p_corrective_action: corrective.value || null,
      p_notes: notes.value || null,
    });
    toast.success('Resolution submitted for review');
    router.push(`/complaints/${route.params.id}`);
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to save the resolution.'));
  } finally {
    loading.value = false;
  }
}

async function review(status) {
  try {
    await reviewResolution(current.value.id, status);
    toast.success(status === 'APPROVED' ? 'Resolution approved' : 'Resolution rejected');
    router.push(`/complaints/${route.params.id}`);
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to review this resolution.'));
  }
}
</script>
