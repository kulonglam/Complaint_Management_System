<template>
  <span :class="classNames('inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold', toneClass)">
    <Flag class="h-3.5 w-3.5" aria-hidden="true" />
    {{ PRIORITY_LABELS[value] || value }}
  </span>
</template>

<script setup>
import { computed } from 'vue';
import { Flag } from 'lucide-vue-next';
import { PRIORITY_LABELS } from '@/lib/constants';
import { classNames } from '@/lib/utils';

const props = defineProps({
  value: { type: String, required: true },
});

const toneClass = computed(() => ({
  LOW: 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-muted',
  MEDIUM: 'bg-[var(--accent-soft)] text-[var(--accent)]',
  HIGH: 'bg-[color-mix(in_srgb,var(--warn)_16%,var(--surface))] text-[var(--warn)]',
  CRITICAL: 'bg-[color-mix(in_srgb,var(--danger)_16%,var(--surface))] text-[var(--danger)]',
}[props.value] || 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-ink'));
</script>
