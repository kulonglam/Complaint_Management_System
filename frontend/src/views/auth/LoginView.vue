<template>
  <div class="mx-auto grid max-w-5xl items-center gap-10 px-4 py-12 lg:grid-cols-2 lg:py-20">
    <div class="hidden lg:block">
      <p class="text-xs font-semibold uppercase tracking-[0.2em] text-[var(--accent)]">Staff workspace</p>
      <h1 class="mt-3 font-display text-4xl leading-tight">Sign in to continue a case, not to hunt for it.</h1>
      <p class="mt-4 text-muted">Your organization, permissions, and assigned work load with the same record the public never sees.</p>
    </div>
    <form class="surface rounded-3xl p-7" @submit.prevent="onSubmit">
      <h2 class="font-display text-2xl">Sign in</h2>
      <p class="mt-1 text-sm text-muted">Staff and administrators only.</p>
      <div v-if="!mfa.needed" class="mt-6 grid gap-4">
        <FormField v-model="email" label="Email" type="email" required />
        <FormField v-model="password" label="Password" type="password" required />
      </div>
      <div v-else class="mt-6 grid gap-4">
        <p class="text-sm text-muted">Enter the 6-digit code from your authenticator app.</p>
        <FormField v-model="mfa.code" label="Authentication code" required />
      </div>
      <p v-if="error" class="mt-3 text-sm text-[var(--danger)]">{{ error }}</p>
      <AppButton class="mt-6 w-full" type="submit" :loading="loading">
        {{ mfa.needed ? 'Verify' : 'Continue' }}
      </AppButton>
      <div v-if="!mfa.needed" class="mt-4 grid gap-2">
        <AppButton variant="secondary" class="w-full" type="button" @click="oauth('google')">Continue with Google</AppButton>
        <AppButton variant="secondary" class="w-full" type="button" @click="oauth('github')">Continue with GitHub</AppButton>
      </div>
      <router-link to="/forgot-password" class="mt-4 block text-center text-sm font-medium text-[var(--accent)]">Forgot password</router-link>
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
import { adminMfaRequired, readMfaGate } from '@/lib/mfa';
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
  if (adminMfaRequired(auth.state.settings, auth.state.roles)) {
    const gate = await readMfaGate();
    if (!gate.enrolled) {
      router.push({ path: '/profile', query: { enrollMfa: '1' } });
      return;
    }
    if (!gate.verified) {
      router.push({ path: '/profile', query: { verifyMfa: '1' } });
      return;
    }
  }
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
