<template>
  <section class="space-y-6">
    <PageHeader title="Organization settings" />
    <form class="max-w-xl space-y-4 rounded-2xl border bg-white p-6" @submit.prevent="save">
      <div v-if="logoUrl" class="flex items-center gap-3">
        <img :src="logoUrl" alt="Organization logo" class="h-14 w-14 rounded-lg object-contain border" />
        <p class="text-sm text-slate-500">Current logo</p>
      </div>
      <label class="grid gap-1.5 text-sm font-medium text-slate-700">
        Logo
        <input class="text-sm font-normal" type="file" accept="image/png,image/jpeg,image/webp" @change="onLogo" />
      </label>
      <FormField v-model="name" label="Organization name" />
      <FormField v-model="email" label="Contact email" type="email" :error="emailError" />
      <FormField v-model="phone" label="Phone" :error="phoneError" />
      <FormField v-model="address" label="Address" type="textarea" />
      <FormField v-model="timezone" label="Timezone" />
      <FormField v-model="primaryColor" label="Primary color" />
      <label class="flex items-center gap-2 text-sm">
        <input v-model="publicPortal" type="checkbox" /> Public portal enabled
      </label>
      <label class="flex items-center gap-2 text-sm">
        <input v-model="allowAnonymous" type="checkbox" /> Allow anonymous complaints
      </label>
      <label class="flex items-center gap-2 text-sm">
        <input v-model="requireApproval" type="checkbox" /> Require resolution approval
      </label>
      <FormField v-model="retentionDays" label="Retention days" />
      <FormField v-model="emailSender" label="Email sender name" />
      <AppButton type="submit" :disabled="!auth.can('settings:update')">Save settings</AppButton>
    </form>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage, isValidEmail, isValidPhone } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const name = ref('');
const email = ref('');
const phone = ref('');
const address = ref('');
const timezone = ref('UTC');
const primaryColor = ref('');
const publicPortal = ref(true);
const allowAnonymous = ref(true);
const requireApproval = ref(true);
const retentionDays = ref('');
const emailSender = ref('');
const logoUrl = ref('');
const emailError = computed(() => (email.value && !isValidEmail(email.value) ? 'Enter a valid email address.' : ''));
const phoneError = computed(() => (phone.value && !isValidPhone(phone.value) ? 'Enter a valid phone number.' : ''));

const { data: settings } = useQuery({
  queryKey: ['org-settings'],
  queryFn: async () => {
    const { data, error } = await supabase.from('organization_settings').select('*').eq('organization_id', auth.state.profile.organization_id).maybeSingle();
    if (error) throw error;
    return data;
  },
});

watch(
  () => [auth.state.organization, settings.value],
  ([org, extra]) => {
    if (org) {
      name.value = org.name || '';
      email.value = org.email || '';
      phone.value = org.phone || '';
      address.value = org.address || '';
      timezone.value = org.timezone || 'UTC';
      primaryColor.value = org.primary_color || '';
      publicPortal.value = org.public_portal_enabled !== false;
      logoUrl.value = org.logo_url || '';
    }
    if (extra) {
      allowAnonymous.value = extra.allow_anonymous !== false;
      requireApproval.value = extra.require_resolution_approval !== false;
      retentionDays.value = extra.retention_days ? String(extra.retention_days) : '';
      emailSender.value = extra.email_sender_name || '';
    }
  },
  { immediate: true }
);

async function onLogo(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  const path = `${auth.state.profile.organization_id}/${crypto.randomUUID()}-${file.name}`;
  const { error } = await supabase.storage.from('organization-logos').upload(path, file, { upsert: true });
  if (error) return toast.error(getErrorMessage(error, 'Unable to upload the logo.'));
  const { data } = supabase.storage.from('organization-logos').getPublicUrl(path);
  logoUrl.value = data.publicUrl;
  toast.success('Logo uploaded. Save settings to apply it.');
}

async function save() {
  if (emailError.value || phoneError.value) return toast.error('Fix the highlighted fields before saving.');
  const orgId = auth.state.profile.organization_id;
  const { error } = await supabase
    .from('organizations')
    .update({
      name: name.value,
      email: email.value,
      phone: phone.value,
      address: address.value,
      timezone: timezone.value,
      primary_color: primaryColor.value || null,
      public_portal_enabled: publicPortal.value,
      logo_url: logoUrl.value || null,
    })
    .eq('id', orgId);
  if (error) return toast.error(getErrorMessage(error));
  const { error: settingsError } = await supabase.from('organization_settings').upsert({
    organization_id: orgId,
    allow_anonymous: allowAnonymous.value,
    require_resolution_approval: requireApproval.value,
    retention_days: retentionDays.value ? Number(retentionDays.value) : null,
    email_sender_name: emailSender.value || null,
  });
  if (settingsError) return toast.error(getErrorMessage(settingsError));
  toast.success('Settings saved');
  auth.refresh();
}
</script>
