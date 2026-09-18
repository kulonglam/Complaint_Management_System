<template>
  <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div>
      <p v-if="eyebrow" class="text-sm font-medium text-blue-700">{{ eyebrow }}</p>
      <h1 class="text-2xl font-semibold tracking-tight">{{ title }}</h1>
      <p v-if="description" class="mt-1 max-w-2xl text-sm text-slate-500">{{ description }}</p>
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
