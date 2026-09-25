<template>
  <span class="inline-flex items-center gap-2.5">
    <svg class="h-8 w-8 shrink-0" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--accent)" />
      <path d="M22.35 10.15A8.15 8.15 0 1 0 22.35 21.85" stroke="#fffdf8" stroke-width="3.15" stroke-linecap="round" />
      <path d="M22.35 10.15L24.7 8.2" stroke="#fffdf8" stroke-width="2.35" stroke-linecap="round" />
    </svg>
    <span v-if="label" class="font-display text-lg leading-none tracking-tight">{{ label }}</span>
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
});
</script>
