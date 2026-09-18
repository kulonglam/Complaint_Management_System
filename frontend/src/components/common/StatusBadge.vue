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
  SUBMITTED: { icon: CircleDot, class: 'bg-slate-100 text-slate-700' },
  RECEIVED: { icon: CircleDot, class: 'bg-sky-50 text-sky-700' },
  UNDER_REVIEW: { icon: CircleDot, class: 'bg-indigo-50 text-indigo-700' },
  ASSIGNED: { icon: CircleDot, class: 'bg-blue-50 text-blue-700' },
  UNDER_INVESTIGATION: { icon: AlertTriangle, class: 'bg-amber-50 text-amber-800' },
  PENDING_ACTION: { icon: AlertTriangle, class: 'bg-orange-50 text-orange-800' },
  RESOLVED: { icon: CheckCircle2, class: 'bg-emerald-50 text-emerald-800' },
  CLOSED: { icon: CheckCircle2, class: 'bg-emerald-100 text-emerald-900' },
  REJECTED: { icon: XCircle, class: 'bg-red-50 text-red-700' },
  ESCALATED: { icon: AlertTriangle, class: 'bg-red-100 text-red-800' },
  ON_HOLD: { icon: PauseCircle, class: 'bg-slate-200 text-slate-700' },
  REOPENED: { icon: RotateCcw, class: 'bg-purple-50 text-purple-700' },
  DUPLICATE: { icon: CircleDot, class: 'bg-slate-100 text-slate-600' },
};

const icon = computed(() => map[props.value]?.icon || CircleDot);
const toneClass = computed(() => map[props.value]?.class || 'bg-slate-100 text-slate-700');
</script>
