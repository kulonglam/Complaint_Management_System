<template>
  <div class="mx-auto max-w-3xl px-4 py-12">
    <h1 class="text-3xl font-semibold">Submit a complaint</h1>
    <p class="mt-2 text-slate-600">Provide as much detail as you can. You will receive a reference number and tracking code.</p>

    <form class="mt-8 space-y-6 rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
      <ol class="flex gap-2 text-xs font-semibold text-slate-500">
        <li :class="step >= 1 ? 'text-blue-700' : ''">1. Complaint</li>
        <li :class="step >= 2 ? 'text-blue-700' : ''">2. Contact</li>
        <li :class="step >= 3 ? 'text-blue-700' : ''">3. Review</li>
      </ol>

      <div v-if="step === 1" class="grid gap-4">
        <FormField v-model="orgSlug" label="Organization" type="select" :options="orgOptions" required />
        <FormField v-model="form.title" label="Title" required />
        <FormField v-model="form.description" label="Description" type="textarea" required />
        <FormField v-model="form.category_id" label="Category" type="select" :options="categoryOptions" />
        <FormField v-model="form.priority" label="Priority" type="select" :options="PRIORITY_OPTIONS" />
        <FormField v-model="form.incident_date" label="Incident date" type="date" />
        <FormField v-model="form.location" label="Location" />
      </div>

      <div v-else-if="step === 2" class="grid gap-4">
        <label class="flex items-start gap-2 text-sm">
          <input v-model="form.is_anonymous" type="checkbox" class="mt-1" />
          Submit anonymously
        </label>
        <p v-if="form.is_anonymous" class="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
          Anonymous complaints may limit the organization's ability to follow up with you.
        </p>
        <template v-else>
          <FormField v-model="form.name" label="Name" required />
          <FormField v-model="form.email" label="Email" type="email" required />
          <FormField v-model="form.phone" label="Phone" />
        </template>
      </div>

      <div v-else class="space-y-3 text-sm">
        <p><strong>Organization:</strong> {{ orgSlug }}</p>
        <p><strong>Title:</strong> {{ form.title }}</p>
        <p><strong>Anonymous:</strong> {{ form.is_anonymous ? 'Yes' : 'No' }}</p>
        <p class="whitespace-pre-wrap">{{ form.description }}</p>
      </div>

      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
      <div class="flex justify-between">
        <AppButton v-if="step > 1" variant="secondary" @click="step -= 1">Back</AppButton>
        <AppButton v-if="step < 3" class="ml-auto" @click="step += 1">Continue</AppButton>
        <AppButton v-else type="submit" :loading="loading">Submit complaint</AppButton>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import { PRIORITY_OPTIONS } from '@/lib/constants';
import { supabase } from '@/lib/supabase';
import { submitPublicComplaint } from '@/services/complaint.service';
import { getErrorMessage } from '@/lib/utils';

const router = useRouter();
const step = ref(1);
const loading = ref(false);
const error = ref('');
const orgSlug = ref('demo');
const catalog = ref({ organizations: [], categories: [] });
const form = reactive({
  title: '',
  description: '',
  category_id: '',
  priority: 'MEDIUM',
  incident_date: '',
  location: '',
  is_anonymous: false,
  name: '',
  email: '',
  phone: '',
});

const orgOptions = computed(() => (catalog.value.organizations || []).map((org) => ({ value: org.slug, label: org.name })));
const categoryOptions = computed(() =>
  (catalog.value.categories || [])
    .filter((item) => item.organization_slug === orgSlug.value)
    .map((item) => ({ value: item.id, label: item.name }))
);

onMounted(async () => {
  const { data } = await supabase.rpc('list_public_catalog', { p_org_slug: null });
  if (data) catalog.value = data;
});

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    const result = await submitPublicComplaint({
      p_org_slug: orgSlug.value,
      p_title: form.title,
      p_description: form.description,
      p_is_anonymous: form.is_anonymous,
      p_name: form.name || null,
      p_email: form.email || null,
      p_phone: form.phone || null,
      p_category_id: form.category_id || null,
      p_subcategory_id: null,
      p_incident_date: form.incident_date || null,
      p_location: form.location || null,
      p_priority: form.priority,
    });
    router.push({
      path: '/complaint-submitted',
      query: { ref: result.reference_number, code: result.tracking_code },
    });
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to submit this complaint.');
  } finally {
    loading.value = false;
  }
}
</script>
