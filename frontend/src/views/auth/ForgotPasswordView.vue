<template>
  <div class="mx-auto max-w-md px-4 py-16">
    <form class="surface rounded-3xl p-7" @submit.prevent="onSubmit">
      <h1 class="font-display text-2xl">Reset password</h1>
      <p class="mt-2 text-sm text-muted">We will email a reset link if the account exists.</p>
      <div class="mt-6 grid gap-4">
        <FormField v-model="email" label="Email" type="email" required />
      </div>
      <p v-if="message" class="mt-3 text-sm text-[var(--ok)]">{{ message }}</p>
      <p v-if="error" class="mt-3 text-sm text-[var(--danger)]">{{ error }}</p>
      <AppButton class="mt-6 w-full" type="submit" :loading="loading">Send reset link</AppButton>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { getErrorMessage } from '@/lib/utils';

const email = ref('');
const message = ref('');
const error = ref('');
const loading = ref(false);

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    const { error: resetError } = await supabase.auth.resetPasswordForEmail(email.value, {
      redirectTo: `${window.location.origin}/reset-password`,
    });
    if (resetError) throw resetError;
    message.value = 'If an account exists, a reset email has been sent.';
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to start a password reset.');
  } finally {
    loading.value = false;
  }
}
</script>
