<template>
  <div class="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 md:grid-cols-3 lg:grid-cols-6">
    <input
      :value="modelValue.search"
      class="rounded-lg border border-slate-300 px-3 py-2 text-sm md:col-span-2"
      placeholder="Search reference, title, category, status, department"
      @input="patch('search', $event.target.value)"
    />
    <select :value="modelValue.status" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('status', $event.target.value)">
      <option value="">All statuses</option>
      <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <select :value="modelValue.priority" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('priority', $event.target.value)">
      <option value="">All priorities</option>
      <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <select :value="modelValue.departmentId" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('departmentId', $event.target.value)">
      <option value="">All departments</option>
      <option v-for="item in departments" :key="item.id" :value="item.id">{{ item.name }}</option>
    </select>
    <select :value="modelValue.categoryId" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('categoryId', $event.target.value)">
      <option value="">All categories</option>
      <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
    </select>
    <select v-if="showOfficer" :value="modelValue.assignedTo" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('assignedTo', $event.target.value)">
      <option value="">All officers</option>
      <option v-for="item in staff" :key="item.id" :value="item.id">{{ displayName(item) }}</option>
    </select>
    <input :value="modelValue.from" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" type="date" @input="patch('from', $event.target.value)" />
    <input :value="modelValue.to" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" type="date" @input="patch('to', $event.target.value)" />
    <label v-if="showOverdue" class="flex items-center gap-2 text-sm">
      <input :checked="modelValue.overdue" type="checkbox" @change="patch('overdue', $event.target.checked)" />
      Overdue / SLA breached
    </label>
    <select v-if="showSort" :value="modelValue.sort" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('sort', $event.target.value)">
      <option value="created_at">Sort: created</option>
      <option value="due_date">Sort: due date</option>
      <option value="priority">Sort: priority</option>
      <option value="status">Sort: status</option>
      <option value="reference_number">Sort: reference</option>
      <option value="title">Sort: title</option>
    </select>
    <select v-if="showPageSize" :value="modelValue.pageSize" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" @change="patch('pageSize', Number($event.target.value))">
      <option :value="12">12 / page</option>
      <option :value="25">25 / page</option>
      <option :value="50">50 / page</option>
    </select>
  </div>
</template>

<script setup>
import { STATUS_OPTIONS, PRIORITY_OPTIONS } from '@/lib/constants';
import { displayName } from '@/lib/utils';

const props = defineProps({
  modelValue: { type: Object, required: true },
  departments: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] },
  staff: { type: Array, default: () => [] },
  showOfficer: { type: Boolean, default: true },
  showOverdue: { type: Boolean, default: true },
  showSort: { type: Boolean, default: true },
  showPageSize: { type: Boolean, default: true },
});

const emit = defineEmits(['update:modelValue']);

function patch(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value });
}
</script>
