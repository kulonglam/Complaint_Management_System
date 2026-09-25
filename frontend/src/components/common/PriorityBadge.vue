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
  LOW: 'bg-[#efe8dc] text-muted',
  MEDIUM: 'bg-[var(--accent-soft)] text-[var(--accent)]',
  HIGH: 'bg-[#f8ead3] text-[var(--warn)]',
  CRITICAL: 'bg-[#f8e4e1] text-[var(--danger)]',
}[props.value] || 'bg-[#efe8dc] text-ink'));
</script>
