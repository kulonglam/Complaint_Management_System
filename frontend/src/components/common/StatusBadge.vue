<template>
  <span :class="classNames('inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold', toneClass)">
    <component :is="icon" class="h-3.5 w-3.5" aria-hidden="true" />
    {{ STATUS_LABELS[value] || value }}
  </span>
</template>

<script setup>
import { computed } from 'vue';
import { CircleDot, CheckCircle2, AlertTriangle, PauseCircle, RotateCcw, XCircle } from 'lucide-vue-next';
import { STATUS_LABELS } from '@/lib/constants';
import { classNames } from '@/lib/utils';

const props = defineProps({
  value: { type: String, required: true },
});

const map = {
  SUBMITTED: { icon: CircleDot, class: 'bg-[#efe8dc] text-ink' },
  RECEIVED: { icon: CircleDot, class: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
  UNDER_REVIEW: { icon: CircleDot, class: 'bg-[#e8e4f4] text-[#4338ca]' },
  ASSIGNED: { icon: CircleDot, class: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
  UNDER_INVESTIGATION: { icon: AlertTriangle, class: 'bg-[#f8ead3] text-[var(--warn)]' },
  PENDING_ACTION: { icon: AlertTriangle, class: 'bg-[#f6e0c8] text-[#9a4b12]' },
  RESOLVED: { icon: CheckCircle2, class: 'bg-[#e3efe4] text-[var(--ok)]' },
  CLOSED: { icon: CheckCircle2, class: 'bg-[#d7e8d9] text-[#2f5436]' },
  REJECTED: { icon: XCircle, class: 'bg-[#f8e4e1] text-[var(--danger)]' },
  ESCALATED: { icon: AlertTriangle, class: 'bg-[#f8e4e1] text-[var(--danger)]' },
  ON_HOLD: { icon: PauseCircle, class: 'bg-[#efe8dc] text-muted' },
  REOPENED: { icon: RotateCcw, class: 'bg-[#efe4f4] text-[#6b21a8]' },
  DUPLICATE: { icon: CircleDot, class: 'bg-[#efe8dc] text-muted' },
};

const icon = computed(() => map[props.value]?.icon || CircleDot);
const toneClass = computed(() => map[props.value]?.class || 'bg-[#efe8dc] text-ink');
</script>
