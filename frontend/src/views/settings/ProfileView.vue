<template>
  <section class="space-y-6">
    <PageHeader title="Profile" />
    <form class="max-w-xl space-y-4 rounded-2xl border bg-white p-6" @submit.prevent="save">
      <FormField v-model="firstName" label="First name" />
      <FormField v-model="lastName" label="Last name" />
      <FormField v-model="phone" label="Phone" :error="phoneError" />
      <FormField v-model="jobTitle" label="Job title" />
      <p class="text-sm text-slate-500">Email and organization membership cannot be changed here.</p>
      <AppButton type="submit">Save profile</AppButton>
    </form>

    <article class="max-w-xl space-y-3 rounded-2xl border bg-white p-6">
      <h2 class="font-semibold">Two-factor authentication</h2>
      <p class="text-sm text-slate-500">Use an authenticator app. This is enforced at sign-in after enrollment.</p>
      <p class="text-sm">Status: {{ mfa.enabled ? 'Enabled' : 'Not enabled' }}</p>
      <div v-if="mfa.qr" class="space-y-3">
        <img :src="mfa.qr" alt="Authenticator QR code" class="h-40 w-40 border" />
        <FormField v-model="mfa.code" label="Enter the 6-digit code" />
        <AppButton :loading="mfa.saving" @click="verifyFactor">Confirm and enable</AppButton>
      </div>
      <div v-else class="flex gap-2">
        <AppButton v-if="!mfa.enabled" variant="secondary" :loading="mfa.saving" @click="startEnroll">Enable 2FA</AppButton>
        <AppButton v-else variant="danger" :loading="mfa.saving" @click="disableFactor">Disable 2FA</AppButton>
      </div>
    </article>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage, isValidPhone } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const firstName = ref('');
const lastName = ref('');
const phone = ref('');
const jobTitle = ref('');
const phoneError = computed(() => (phone.value && !isValidPhone(phone.value) ? 'Enter a valid phone number.' : ''));
const mfa = reactive({ enabled: false, factorId: '', qr: '', code: '', saving: false });

watch(
  () => auth.state.profile,
  (profile) => {
    if (!profile) return;
    firstName.value = profile.first_name || '';
    lastName.value = profile.last_name || '';
    phone.value = profile.phone || '';
    jobTitle.value = profile.job_title || '';
  },
  { immediate: true }
);

async function loadFactors() {
  const { data } = await supabase.auth.mfa.listFactors();
  const totp = data?.totp?.find((item) => item.status === 'verified') || data?.totp?.[0];
  mfa.enabled = totp?.status === 'verified';
  mfa.factorId = totp?.id || '';
}

onMounted(loadFactors);

async function save() {
  if (phoneError.value) return toast.error(phoneError.value);
  const { error } = await supabase
    .from('profiles')
    .update({
      first_name: firstName.value,
      last_name: lastName.value,
      phone: phone.value,
      job_title: jobTitle.value,
    })
    .eq('id', auth.state.profile.id);
  if (error) toast.error(getErrorMessage(error));
  else {
    toast.success('Profile updated');
    auth.refresh();
  }
}

async function startEnroll() {
  mfa.saving = true;
  try {
    const { data, error } = await supabase.auth.mfa.enroll({ factorType: 'totp', friendlyName: 'Authenticator' });
    if (error) throw error;
    mfa.factorId = data.id;
    mfa.qr = data.totp.qr_code;
    toast.info('Scan the QR code, then enter the 6-digit code.');
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to start two-factor setup.'));
  } finally {
    mfa.saving = false;
  }
}

async function verifyFactor() {
  mfa.saving = true;
  try {
    const { data: challenge, error: challengeError } = await supabase.auth.mfa.challenge({ factorId: mfa.factorId });
    if (challengeError) throw challengeError;
    const { error } = await supabase.auth.mfa.verify({
      factorId: mfa.factorId,
      challengeId: challenge.id,
      code: mfa.code,
    });
    if (error) throw error;
    mfa.qr = '';
    mfa.code = '';
    toast.success('Two-factor authentication is enabled.');
    await loadFactors();
  } catch (err) {
    toast.error(getErrorMessage(err, 'That code was not accepted.'));
  } finally {
    mfa.saving = false;
  }
}

async function disableFactor() {
  mfa.saving = true;
  try {
    const { error } = await supabase.auth.mfa.unenroll({ factorId: mfa.factorId });
    if (error) throw error;
    toast.success('Two-factor authentication is disabled.');
    await loadFactors();
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to disable two-factor authentication.'));
  } finally {
    mfa.saving = false;
  }
}
</script>
