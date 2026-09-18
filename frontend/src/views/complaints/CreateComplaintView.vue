<template>
  <section class="space-y-6">
    <PageHeader title="Create complaint" description="Internal intake for staff. Organization is taken from your session, not the form." />
    <form class="max-w-2xl space-y-4 rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
      <FormField v-model="title" label="Title" required />
      <FormField v-model="description" label="Description" type="textarea" required />
      <FormField v-model="priority" label="Priority" type="select" :options="PRIORITY_OPTIONS" />
      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
      <AppButton type="submit" :loading="loading">Create</AppButton>
    </form>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { PRIORITY_OPTIONS } from '@/lib/constants';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { getErrorMessage } from '@/lib/utils';

const title = ref('');
const description = ref('');
const priority = ref('MEDIUM');
const loading = ref(false);
const error = ref('');
const auth = useAuth();
const router = useRouter();

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    const orgId = auth.state.profile.organization_id;
    const { data, error: rpcError } = await supabase.rpc('submit_public_complaint', {
      p_org_slug: auth.state.organization.slug,
      p_title: title.value,
      p_description: description.value,
      p_is_anonymous: false,
      p_name: `${auth.state.profile.first_name} ${auth.state.profile.last_name}`.trim(),
      p_email: auth.state.profile.email,
      p_phone: auth.state.profile.phone,
      p_category_id: null,
      p_subcategory_id: null,
      p_incident_date: null,
      p_location: null,
      p_priority: priority.value,
    });
    if (rpcError) throw rpcError;
    const { data: created } = await supabase
      .from('complaints')
      .select('id')
      .eq('organization_id', orgId)
      .eq('reference_number', data.reference_number)
      .single();
    router.push(`/complaints/${created.id}`);
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to create the complaint.');
  } finally {
    loading.value = false;
  }
}
</script>
