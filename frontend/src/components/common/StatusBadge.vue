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
  SUBMITTED: { icon: CircleDot, class: 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-ink' },
  RECEIVED: { icon: CircleDot, class: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
  UNDER_REVIEW: { icon: CircleDot, class: 'bg-[color-mix(in_srgb,#4338ca_16%,var(--surface))] text-[#6d63d6]' },
  ASSIGNED: { icon: CircleDot, class: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
  UNDER_INVESTIGATION: { icon: AlertTriangle, class: 'bg-[color-mix(in_srgb,var(--warn)_16%,var(--surface))] text-[var(--warn)]' },
  PENDING_ACTION: { icon: AlertTriangle, class: 'bg-[color-mix(in_srgb,#9a4b12_16%,var(--surface))] text-[#c46a28]' },
  RESOLVED: { icon: CheckCircle2, class: 'bg-[color-mix(in_srgb,var(--ok)_16%,var(--surface))] text-[var(--ok)]' },
  CLOSED: { icon: CheckCircle2, class: 'bg-[color-mix(in_srgb,var(--ok)_22%,var(--surface))] text-[var(--ok)]' },
  REJECTED: { icon: XCircle, class: 'bg-[color-mix(in_srgb,var(--danger)_16%,var(--surface))] text-[var(--danger)]' },
  ESCALATED: { icon: AlertTriangle, class: 'bg-[color-mix(in_srgb,var(--danger)_16%,var(--surface))] text-[var(--danger)]' },
  ON_HOLD: { icon: PauseCircle, class: 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-muted' },
  REOPENED: { icon: RotateCcw, class: 'bg-[color-mix(in_srgb,#6b21a8_16%,var(--surface))] text-[#a855f7]' },
  DUPLICATE: { icon: CircleDot, class: 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-muted' },
};

const icon = computed(() => map[props.value]?.icon || CircleDot);
const toneClass = computed(() => map[props.value]?.class || 'bg-[color-mix(in_srgb,var(--muted)_16%,var(--surface))] text-ink');
</script>
