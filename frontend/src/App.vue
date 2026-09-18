<template>
  <div class="pointer-events-none fixed right-4 top-4 z-50 space-y-2">
    <div
      v-for="toast in toast.toasts"
      :key="toast.id"
      class="pointer-events-auto rounded-xl px-4 py-3 text-sm text-white shadow-lg"
      :class="toast.tone === 'danger' ? 'bg-red-600' : toast.tone === 'success' ? 'bg-emerald-600' : 'bg-slate-800'"
    >
      {{ toast.message }}
    </div>
  </div>
  <router-view />
</template>

<script setup>
import { useToast } from '@/composables/useToast';

const toast = useToast();
</script>
