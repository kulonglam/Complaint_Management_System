<template>
  <span class="inline-flex items-center gap-2.5">
    <span
      class="grid h-8 w-8 place-items-center rounded-xl text-sm font-semibold text-white shadow-sm"
      style="background: var(--accent)"
      aria-hidden="true"
    >
      C
    </span>
    <span v-if="label" class="font-display text-lg leading-none tracking-tight">{{ label }}</span>
  </span>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
});
</script>
