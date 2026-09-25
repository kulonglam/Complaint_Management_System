<template>
  <a class="skip-link" href="#main">Skip to content</a>
  <div class="pointer-events-none fixed right-4 top-4 z-[80] space-y-2">
    <div
      v-for="item in toast.toasts"
      :key="item.id"
      class="pointer-events-auto max-w-sm rounded-2xl px-4 py-3 text-sm text-white shadow-lg"
      :style="{ background: item.tone === 'danger' ? 'var(--danger)' : item.tone === 'success' ? 'var(--ok)' : 'var(--ink)' }"
    >
      {{ item.message }}
    </div>
  </div>
  <router-view />
</template>

<script setup>
import { useToast } from '@/composables/useToast';
import { useTheme } from '@/composables/useTheme';

useTheme();
const toast = useToast();
</script>
