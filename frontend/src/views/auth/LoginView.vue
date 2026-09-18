<template>
  <div class="mx-auto max-w-md px-4 py-16">
    <form class="rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
      <h1 class="text-2xl font-semibold">Sign in</h1>
      <p class="mt-1 text-sm text-slate-500">Staff and administrators only.</p>
      <div class="mt-6 grid gap-4">
        <FormField v-model="email" label="Email" type="email" required />
        <FormField v-model="password" label="Password" type="password" required />
      </div>
      <p v-if="error" class="mt-3 text-sm text-red-600">{{ error }}</p>
      <AppButton class="mt-6 w-full" type="submit" :loading="loading">Continue</AppButton>
      <router-link to="/forgot-password" class="mt-4 block text-center text-sm text-blue-700">Forgot password</router-link>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { getErrorMessage } from '@/lib/utils';

const email = ref('');
const password = ref('');
const error = ref('');
const loading = ref(false);
const router = useRouter();
const route = useRoute();
const auth = useAuth();

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    const { error: signError } = await supabase.auth.signInWithPassword({
      email: email.value,
      password: password.value,
    });
    if (signError) throw signError;
    await auth.refresh();
    router.push(route.query.redirect?.toString() || '/dashboard');
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to sign in with those details.');
  } finally {
    loading.value = false;
  }
}
</script>
