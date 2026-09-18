<template>
  <div class="rounded-2xl border border-slate-200 bg-white px-8 py-12 text-center">
    <component :is="icon" class="mx-auto mb-3 h-10 w-10 text-slate-400" />
    <h3 class="text-lg font-semibold">{{ title }}</h3>
    <p class="mt-1 text-sm text-slate-500">{{ message }}</p>
    <div v-if="$slots.default" class="mt-4 flex justify-center">
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
