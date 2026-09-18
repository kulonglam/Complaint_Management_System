<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="$emit('close')">
      <div role="dialog" aria-modal="true" class="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
        <div class="mb-4 flex items-start justify-between gap-4">
          <h2 class="text-lg font-semibold">{{ title }}</h2>
          <button class="text-slate-500" type="button" @click="$emit('close')">Close</button>
        </div>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineProps({
  open: Boolean,
  title: String,
});
defineEmits(['close']);
</script>
