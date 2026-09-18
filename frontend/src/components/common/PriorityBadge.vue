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
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-blue-50 text-blue-700',
  HIGH: 'bg-amber-50 text-amber-800',
  CRITICAL: 'bg-red-50 text-red-700',
}[props.value] || 'bg-slate-100 text-slate-700'));
</script>
