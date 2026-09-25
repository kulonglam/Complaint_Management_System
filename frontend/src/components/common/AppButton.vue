<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="classNames(
      'inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-60',
      variants[variant]
    )"
    :style="variant === 'primary' ? { background: 'var(--accent)' } : undefined"
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
  primary: 'text-white hover:brightness-95',
  secondary: 'border border-[var(--line)] bg-[var(--surface)] text-ink hover:bg-[var(--paper)]',
  danger: 'bg-[var(--danger)] text-white hover:brightness-95',
  ghost: 'text-muted hover:bg-black/5 hover:text-ink',
};
</script>
