<template>
  <article class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
    <div class="flex items-center justify-between gap-3">
      <p class="text-sm text-slate-500">{{ label }}</p>
      <component :is="icon" v-if="icon" class="h-4 w-4 text-slate-400" />
    </div>
    <p class="mt-2 text-2xl font-semibold">{{ value }}</p>
    <p v-if="hint" class="mt-1 text-xs text-slate-500">{{ hint }}</p>
  </article>
</template>

<script setup>
defineProps({
  label: String,
  value: [String, Number],
  hint: String,
  icon: Object,
});
</script>
