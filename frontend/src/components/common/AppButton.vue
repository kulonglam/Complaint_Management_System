<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="classNames(
      'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60',
      variants[variant]
    )"
  >
    <span v-if="loading" class="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
    <slot />
  </button>
</template>

<script setup>
import { classNames } from '@/lib/utils';

defineProps({
  type: { type: String, default: 'button' },
  variant: { type: String, default: 'primary' },
  loading: Boolean,
  disabled: Boolean,
});

const variants = {
  primary: 'bg-blue-600 text-white hover:bg-blue-700',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  ghost: 'text-slate-600 hover:bg-slate-100',
};
</script>
