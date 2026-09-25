<template>
  <div class="mx-auto max-w-md px-4 py-16">
    <form class="surface rounded-3xl p-7" @submit.prevent="onSubmit">
      <h1 class="font-display text-2xl">Choose a new password</h1>
      <div class="mt-6 grid gap-4">
        <FormField v-model="password" label="New password" type="password" required />
      </div>
      <p v-if="error" class="mt-3 text-sm text-red-600">{{ error }}</p>
      <AppButton class="mt-6 w-full" type="submit" :loading="loading">Update password</AppButton>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { getErrorMessage } from '@/lib/utils';

const password = ref('');
const error = ref('');
const loading = ref(false);
const router = useRouter();

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    const { error: updateError } = await supabase.auth.updateUser({ password: password.value });
    if (updateError) throw updateError;
    router.push('/dashboard');
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to update the password.');
  } finally {
    loading.value = false;
  }
}
</script>
