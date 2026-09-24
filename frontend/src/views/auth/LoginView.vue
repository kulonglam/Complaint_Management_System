<template>
  <div class="mx-auto max-w-md px-4 py-16">
    <form class="rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
      <h1 class="text-2xl font-semibold">Sign in</h1>
      <p class="mt-1 text-sm text-slate-500">Staff and administrators only.</p>
      <div v-if="!mfa.needed" class="mt-6 grid gap-4">
        <FormField v-model="email" label="Email" type="email" required />
        <FormField v-model="password" label="Password" type="password" required />
      </div>
      <div v-else class="mt-6 grid gap-4">
        <p class="text-sm text-slate-600">Enter the 6-digit code from your authenticator app.</p>
        <FormField v-model="mfa.code" label="Authentication code" required />
      </div>
      <p v-if="error" class="mt-3 text-sm text-red-600">{{ error }}</p>
      <AppButton class="mt-6 w-full" type="submit" :loading="loading">
        {{ mfa.needed ? 'Verify' : 'Continue' }}
      </AppButton>
      <div v-if="!mfa.needed" class="mt-4 grid gap-2">
        <AppButton variant="secondary" class="w-full" type="button" @click="oauth('google')">Continue with Google</AppButton>
        <AppButton variant="secondary" class="w-full" type="button" @click="oauth('github')">Continue with GitHub</AppButton>
      </div>
      <router-link to="/forgot-password" class="mt-4 block text-center text-sm text-blue-700">Forgot password</router-link>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
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
const mfa = reactive({ needed: false, factorId: '', code: '' });

async function finishLogin() {
  await auth.refresh();
  router.push(route.query.redirect?.toString() || '/dashboard');
}

async function onSubmit() {
  error.value = '';
  loading.value = true;
  try {
    if (mfa.needed) {
      const { data: challenge, error: challengeError } = await supabase.auth.mfa.challenge({ factorId: mfa.factorId });
      if (challengeError) throw challengeError;
      const { error: verifyError } = await supabase.auth.mfa.verify({
        factorId: mfa.factorId,
        challengeId: challenge.id,
        code: mfa.code,
      });
      if (verifyError) throw verifyError;
      await finishLogin();
      return;
    }

    const { error: signError } = await supabase.auth.signInWithPassword({
      email: email.value,
      password: password.value,
    });
    if (signError) throw signError;

    const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
    if (aal?.nextLevel === 'aal2' && aal.currentLevel !== 'aal2') {
      const { data: factors } = await supabase.auth.mfa.listFactors();
      const totp = factors?.totp?.[0];
      if (totp) {
        mfa.needed = true;
        mfa.factorId = totp.id;
        return;
      }
    }
    await finishLogin();
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to sign in with those details.');
  } finally {
    loading.value = false;
  }
}

async function oauth(provider) {
  error.value = '';
  const { error: oauthError } = await supabase.auth.signInWithOAuth({
    provider,
    options: { redirectTo: `${window.location.origin}/auth/callback` },
  });
  if (oauthError) {
    error.value = getErrorMessage(oauthError, 'This sign-in provider is not enabled for the project yet.');
  }
}
</script>
