<template>
  <article
    class="surface rounded-2xl p-4"
    :class="tone === 'alert' ? 'ring-1 ring-[var(--danger)]/20' : ''"
  >
    <div class="flex items-center justify-between gap-3">
      <p class="text-sm text-muted">{{ label }}</p>
      <component :is="icon" v-if="icon" class="h-4 w-4 text-muted" />
    </div>
    <p class="mt-2 font-display text-3xl tracking-tight">{{ value }}</p>
    <p v-if="hint" class="mt-1 text-xs text-muted">{{ hint }}</p>
  </article>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  hint: String,
  icon: Object,
  tone: { type: String, default: 'default' },
});
</script>
