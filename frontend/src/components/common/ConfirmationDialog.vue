<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-[#161310]/45 p-4 backdrop-blur-[2px]" @click.self="onCancel">
      <div role="dialog" aria-modal="true" class="surface w-full max-w-md rounded-3xl p-6">
        <h2 class="font-display text-xl">{{ title }}</h2>
        <p class="mt-2 text-sm leading-6 text-muted">{{ message }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <AppButton variant="secondary" @click="onCancel">Cancel</AppButton>
          <AppButton :variant="tone === 'primary' ? 'primary' : 'danger'" :loading="loading" @click="$emit('confirm')">{{ confirmLabel }}</AppButton>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import AppButton from '@/components/common/AppButton.vue';

defineProps({
  open: Boolean,
  title: String,
  message: String,
  confirmLabel: { type: String, default: 'Confirm' },
  tone: { type: String, default: 'danger' },
  loading: Boolean,
});

const emit = defineEmits(['confirm', 'cancel']);
function onCancel() {
  emit('cancel');
}
</script>
