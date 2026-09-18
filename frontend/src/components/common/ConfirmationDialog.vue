<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" @click.self="onCancel">
      <div role="dialog" aria-modal="true" class="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <h2 class="text-lg font-semibold">{{ title }}</h2>
        <p class="mt-2 text-sm text-slate-600">{{ message }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <AppButton variant="secondary" @click="onCancel">Cancel</AppButton>
          <AppButton :variant="tone" :loading="loading" @click="$emit('confirm')">{{ confirmLabel }}</AppButton>
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
