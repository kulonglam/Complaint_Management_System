<template>
  <div class="mx-auto max-w-md px-4 py-16 text-sm text-slate-600">Completing sign in…</div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { adminMfaRequired, readMfaGate } from '@/lib/mfa';

const router = useRouter();
const auth = useAuth();

onMounted(async () => {
  await supabase.auth.getSession();
  await auth.refresh();
  if (!auth.state.session) {
    router.replace('/login');
    return;
  }
  if (adminMfaRequired(auth.state.settings, auth.state.roles)) {
    const gate = await readMfaGate();
    if (!gate.enrolled) {
      router.replace({ path: '/profile', query: { enrollMfa: '1' } });
      return;
    }
    if (!gate.verified) {
      router.replace({ path: '/profile', query: { verifyMfa: '1' } });
      return;
    }
  }
  router.replace('/dashboard');
});
</script>
