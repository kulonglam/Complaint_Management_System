<template>
  <div class="mx-auto max-w-3xl px-4 py-12">
    <p class="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--accent)]">Public intake</p>
    <h1 class="mt-2 font-display text-4xl">Submit a complaint</h1>
    <p class="mt-2 text-muted">Tell us what happened. You will receive a reference number and a tracking code to keep.</p>

    <form class="surface relative mt-8 space-y-6 rounded-3xl p-6" @submit.prevent="onSubmit">
      <ol class="grid grid-cols-4 gap-2 text-xs font-semibold">
        <li v-for="(label, index) in steps" :key="label" class="flex items-center gap-2" :class="step > index ? 'text-[var(--accent)]' : 'text-muted'">
          <span class="step-dot" :style="step > index ? { background: 'var(--accent)', color: '#fff' } : { background: 'var(--paper)', color: 'var(--muted)' }">{{ index + 1 }}</span>
          <span class="hidden sm:inline">{{ label }}</span>
        </li>
      </ol>

      <div class="hidden" aria-hidden="true">
        <label>Company website
          <input v-model="honeypot" tabindex="-1" autocomplete="off" />
        </label>
      </div>

      <div v-if="step === 1" class="grid gap-4">
        <FormField v-model="orgSlug" label="Organization" type="select" :options="orgOptions" required />
        <FormField v-model="form.title" label="Title" required />
        <FormField v-model="form.description" label="Description" type="textarea" required />
        <FormField v-model="form.category_id" label="Category" type="select" :options="categoryOptions" />
        <FormField v-if="subcategoryOptions.length" v-model="form.subcategory_id" label="Subcategory" type="select" :options="subcategoryOptions" />
        <FormField v-model="form.priority" label="Priority" type="select" :options="PRIORITY_OPTIONS" />
        <FormField v-model="form.incident_date" label="Incident date" type="date" />
        <FormField v-model="form.location" label="Location" />
      </div>

      <div v-else-if="step === 2" class="grid gap-4">
        <label class="flex items-start gap-2 text-sm">
          <input v-model="form.is_anonymous" type="checkbox" class="mt-1" :disabled="!allowAnonymous" />
          Submit anonymously
        </label>
        <p v-if="!allowAnonymous" class="text-sm text-muted">This organization does not accept anonymous complaints.</p>
        <p v-if="form.is_anonymous" class="rounded-xl bg-[color-mix(in_srgb,var(--warn)_14%,var(--surface))] p-3 text-sm text-[var(--warn)]">
          Anonymous complaints may limit the organization's ability to follow up with you.
        </p>
        <template v-else>
          <FormField v-model="form.name" label="Name" required />
          <FormField v-model="form.email" label="Email" type="email" required :error="emailError" />
          <FormField v-model="form.phone" label="Phone" :error="phoneError" />
        </template>
      </div>

      <div v-else-if="step === 3" class="grid gap-4">
        <label class="text-sm font-medium">Supporting documents
          <input class="mt-1 block w-full text-sm" type="file" multiple @change="onFiles" />
        </label>
        <p class="text-xs text-muted">PDF, Word, Excel, JPG, or PNG. 10MB maximum per file.</p>
        <ul class="text-xs text-muted">
          <li v-for="file in files" :key="file.name">{{ file.name }}</li>
        </ul>
      </div>

      <div v-else class="space-y-3 text-sm">
        <p><strong>Organization:</strong> {{ orgSlug }}</p>
        <p><strong>Title:</strong> {{ form.title }}</p>
        <p><strong>Anonymous:</strong> {{ form.is_anonymous ? 'Yes' : 'No' }}</p>
        <p><strong>Files:</strong> {{ files.length }}</p>
        <p class="whitespace-pre-wrap">{{ form.description }}</p>
      </div>

      <p v-if="error" class="text-sm text-[var(--danger)]">{{ error }}</p>
      <div class="flex justify-between">
        <AppButton v-if="step > 1" variant="secondary" @click="step -= 1">Back</AppButton>
        <AppButton v-if="step < 4" class="ml-auto" @click="nextStep">Continue</AppButton>
        <AppButton v-else type="submit" :loading="loading">Submit complaint</AppButton>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import { PRIORITY_OPTIONS } from '@/lib/constants';
import { supabase } from '@/lib/supabase';
import { linkPublicAttachments, submitPublicComplaint, uploadPublicFiles } from '@/services/complaint.service';
import { getErrorMessage, isValidEmail, isValidPhone } from '@/lib/utils';

const router = useRouter();
const step = ref(1);
const loading = ref(false);
const error = ref('');
const orgSlug = ref('demo');
const files = ref([]);
const honeypot = ref('');
const steps = ['Complaint', 'Contact', 'Files', 'Review'];
const catalog = ref({ organizations: [], categories: [] });
const form = reactive({
  title: '',
  description: '',
  category_id: '',
  subcategory_id: '',
  priority: 'MEDIUM',
  incident_date: '',
  location: '',
  is_anonymous: false,
  name: '',
  email: '',
  phone: '',
});

const orgOptions = computed(() => (catalog.value.organizations || []).map((org) => ({ value: org.slug, label: org.name })));
const selectedOrg = computed(() => (catalog.value.organizations || []).find((org) => org.slug === orgSlug.value));
const allowAnonymous = computed(() => selectedOrg.value?.allow_anonymous !== false);
const categoryOptions = computed(() =>
  (catalog.value.categories || [])
    .filter((item) => item.organization_slug === orgSlug.value)
    .map((item) => ({ value: item.id, label: item.name }))
);
const selectedCategory = computed(() =>
  (catalog.value.categories || []).find((item) => item.id === form.category_id)
);
const subcategoryOptions = computed(() =>
  (selectedCategory.value?.subcategories || []).map((item) => ({ value: item.id, label: item.name }))
);
const emailError = computed(() => (!form.is_anonymous && form.email && !isValidEmail(form.email) ? 'Enter a valid email address.' : ''));
const phoneError = computed(() => (!form.is_anonymous && form.phone && !isValidPhone(form.phone) ? 'Enter a valid phone number.' : ''));

function nextStep() {
  error.value = '';
  if (step.value === 1 && (!form.title.trim() || form.description.trim().length < 10)) {
    error.value = 'Add a title and a description of at least 10 characters.';
    return;
  }
  if (step.value === 2 && !form.is_anonymous && (!form.name.trim() || !form.email.trim() || emailError.value || phoneError.value)) {
    error.value = 'Name and a valid email are required unless the complaint is anonymous.';
    return;
  }
  step.value += 1;
}

watch(orgSlug, () => {
  form.category_id = '';
  form.subcategory_id = '';
  if (!allowAnonymous.value) form.is_anonymous = false;
});
watch(() => form.category_id, () => {
  form.subcategory_id = '';
});

onMounted(async () => {
  const { data } = await supabase.rpc('list_public_catalog', { p_org_slug: null });
  if (data) catalog.value = data;
});

function onFiles(event) {
  files.value = [...(event.target.files || [])];
}

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    if (honeypot.value) {
      router.push({ path: '/complaint-submitted', query: { ref: 'CMP-HIDDEN', code: 'BOT' } });
      return;
    }
    const result = await submitPublicComplaint({
      p_org_slug: orgSlug.value,
      p_title: form.title,
      p_description: form.description,
      p_is_anonymous: form.is_anonymous,
      p_name: form.name || null,
      p_email: form.email || null,
      p_phone: form.phone || null,
      p_category_id: form.category_id || null,
      p_subcategory_id: form.subcategory_id || null,
      p_incident_date: form.incident_date || null,
      p_location: form.location || null,
      p_priority: form.priority,
    });
    if (files.value.length) {
      const uploaded = await uploadPublicFiles(files.value);
      await linkPublicAttachments(result.reference_number, result.tracking_code, uploaded);
    }
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
