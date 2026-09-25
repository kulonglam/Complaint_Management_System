<template>
  <div class="surface rounded-2xl px-8 py-14 text-center">
    <span class="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
      <component :is="icon" class="h-6 w-6" />
    </span>
    <h3 class="font-display text-xl">{{ title }}</h3>
    <p class="mt-1 text-sm text-muted">{{ message }}</p>
    <div v-if="$slots.default" class="mt-5 flex justify-center">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { Inbox } from 'lucide-vue-next';

defineProps({
  title: { type: String, default: 'Nothing to show' },
  message: { type: String, default: 'Try changing your filters or creating a new record.' },
  icon: { type: Object, default: () => Inbox },
});
</script>
