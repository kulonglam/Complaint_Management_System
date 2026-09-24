<template>
  <div class="mx-auto max-w-md px-4 py-16 text-sm text-slate-600">Completing sign in…</div>
</template>

<script setup>
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';

const router = useRouter();
const auth = useAuth();

onMounted(async () => {
  await supabase.auth.getSession();
  await auth.refresh();
  router.replace(auth.state.session ? '/dashboard' : '/login');
});
</script>
