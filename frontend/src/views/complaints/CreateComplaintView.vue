<template>
  <section class="space-y-6">
    <PageHeader title="Create complaint" description="Internal intake for staff. Organization is taken from your session, not the form." />
    <form class="surface max-w-2xl space-y-4 rounded-3xl p-6" @submit.prevent="onSubmit">
      <FormField v-model="title" label="Title" required />
      <FormField v-model="description" label="Description" type="textarea" required />
      <FormField v-model="categoryId" label="Category" type="select" :options="categoryOptions" />
      <FormField v-if="subcategoryOptions.length" v-model="subcategoryId" label="Subcategory" type="select" :options="subcategoryOptions" />
      <FormField v-model="priority" label="Priority" type="select" :options="PRIORITY_OPTIONS" />
      <p v-if="error" class="text-sm text-[var(--danger)]">{{ error }}</p>
      <AppButton type="submit" :loading="loading">Create</AppButton>
    </form>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { PRIORITY_OPTIONS } from '@/lib/constants';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { getErrorMessage } from '@/lib/utils';

const title = ref('');
const description = ref('');
const categoryId = ref('');
const subcategoryId = ref('');
const priority = ref('MEDIUM');
const loading = ref(false);
const error = ref('');
const auth = useAuth();
const router = useRouter();

const { data: categories } = useQuery({
  queryKey: ['categories'],
  queryFn: async () => {
    const { data, error: queryError } = await supabase.from('complaint_categories').select('id, name, complaint_subcategories(id, name)').eq('status', 'ACTIVE');
    if (queryError) throw queryError;
    return data;
  },
});
const categoryOptions = computed(() => (categories.value || []).map((item) => ({ value: item.id, label: item.name })));
const subcategoryOptions = computed(() => {
  const selected = (categories.value || []).find((item) => item.id === categoryId.value);
  return (selected?.complaint_subcategories || []).map((item) => ({ value: item.id, label: item.name }));
});

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
      p_category_id: categoryId.value || null,
      p_subcategory_id: subcategoryId.value || null,
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
