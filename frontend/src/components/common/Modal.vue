<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-50 flex items-center justify-center bg-[#161310]/45 p-4 backdrop-blur-[2px]"
      @click.self="$emit('close')"
    >
      <div role="dialog" aria-modal="true" class="surface w-full max-w-lg rounded-3xl p-6">
        <div class="mb-4 flex items-start justify-between gap-4">
          <h2 class="font-display text-xl">{{ title }}</h2>
          <button class="text-sm text-muted hover:text-ink" type="button" @click="$emit('close')">Close</button>
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
