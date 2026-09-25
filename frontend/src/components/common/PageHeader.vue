<template>
  <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div>
      <p v-if="eyebrow" class="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--accent)]">{{ eyebrow }}</p>
      <h1 class="font-display text-3xl font-semibold tracking-tight text-ink">{{ title }}</h1>
      <p v-if="description" class="mt-1.5 max-w-2xl text-sm leading-6 text-muted">{{ description }}</p>
    </div>
    <div class="flex flex-wrap gap-2">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  eyebrow: String,
  title: { type: String, required: true },
  description: String,
});
</script>
