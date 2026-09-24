<template>
  <section class="space-y-6">
    <PageHeader title="Dashboard" description="Live metrics for your organization. Figures come from complaint records, not placeholders.">
      <div class="flex flex-wrap gap-2">
        <select v-model="preset" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="today">Today</option>
          <option value="week">This week</option>
          <option value="month">This month</option>
          <option value="30">Last 30 days</option>
          <option value="quarter">This quarter</option>
          <option value="year">This year</option>
          <option value="custom">Custom range</option>
          <option value="all">All time</option>
        </select>
        <input v-if="preset === 'custom'" v-model="customFrom" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" type="date" />
        <input v-if="preset === 'custom'" v-model="customTo" class="rounded-lg border border-slate-300 px-3 py-2 text-sm" type="date" />
        <select v-model="departmentId" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">All departments</option>
          <option v-for="item in departments || []" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <select v-model="categoryId" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">All categories</option>
          <option v-for="item in categories || []" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <select v-model="priority" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">All priorities</option>
          <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <select v-model="status" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
          <option value="">All statuses</option>
          <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </div>
    </PageHeader>
    <LoadingSkeleton v-if="isLoading" />
    <ErrorState v-else-if="isError" :message="getErrorMessage(error)" />
    <template v-else>
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Total complaints" :value="metrics.total || 0" />
        <StatCard label="Open" :value="metrics.open || 0" />
        <StatCard label="Under investigation" :value="metrics.under_investigation || 0" />
        <StatCard label="Resolved" :value="metrics.resolved || 0" />
        <StatCard label="Closed" :value="metrics.closed || 0" />
        <StatCard label="Overdue" :value="metrics.overdue || 0" />
        <StatCard label="Escalated" :value="metrics.escalated || 0" />
        <StatCard label="Avg resolution hours" :value="Number(metrics.avg_resolution_hours || 0).toFixed(1)" />
        <StatCard label="Resolution rate" :value="`${metrics.resolution_rate || 0}%`" />
        <StatCard label="SLA compliance" :value="`${metrics.sla_compliance || 0}%`" />
        <StatCard label="Overdue rate" :value="`${metrics.overdue_rate || 0}%`" />
        <StatCard label="Escalation rate" :value="`${metrics.escalation_rate || 0}%`" />
      </div>
      <div class="grid gap-4 lg:grid-cols-2">
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">Complaints over time</h2>
          <SimpleChart
            type="line"
            :labels="(metrics.over_time || []).map((row) => row.date)"
            :values="(metrics.over_time || []).map((row) => row.count)"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">Resolution trend</h2>
          <SimpleChart
            type="line"
            :labels="(metrics.resolution_trend || []).map((row) => row.date)"
            :values="(metrics.resolution_trend || []).map((row) => row.count)"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">By status</h2>
          <SimpleChart
            :labels="Object.keys(metrics.by_status || {})"
            :values="Object.values(metrics.by_status || {})"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">By priority</h2>
          <SimpleChart
            :labels="Object.keys(metrics.by_priority || {})"
            :values="Object.values(metrics.by_priority || {})"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">By category</h2>
          <SimpleChart
            :labels="(metrics.by_category || []).map((row) => row.name)"
            :values="(metrics.by_category || []).map((row) => row.count)"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">By department</h2>
          <SimpleChart
            :labels="(metrics.by_department || []).map((row) => row.name)"
            :values="(metrics.by_department || []).map((row) => row.count)"
          />
        </div>
        <div class="h-72 rounded-2xl border border-slate-200 bg-white p-4">
          <h2 class="mb-3 font-semibold">SLA compliance</h2>
          <SimpleChart
            :labels="['On time', 'Breached']"
            :values="[Number(metrics.sla_compliance || 0), Math.max(0, 100 - Number(metrics.sla_compliance || 0))]"
          />
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import StatCard from '@/components/dashboard/StatCard.vue';
import SimpleChart from '@/components/dashboard/SimpleChart.vue';
import { fetchDashboard } from '@/services/complaint.service';
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from '@/lib/constants';
import { dateRangePreset, getErrorMessage } from '@/lib/utils';
import { supabase } from '@/lib/supabase';

const preset = ref('30');
const customFrom = ref('');
const customTo = ref('');
const departmentId = ref('');
const categoryId = ref('');
const priority = ref('');
const status = ref('');
const range = computed(() => dateRangePreset(preset.value, customFrom.value, customTo.value));
const { data: departments } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error } = await supabase.from('departments').select('id, name');
    if (error) throw error;
    return data;
  },
});
const { data: categories } = useQuery({
  queryKey: ['categories-lite'],
  queryFn: async () => {
    const { data, error } = await supabase.from('complaint_categories').select('id, name');
    if (error) throw error;
    return data;
  },
});
const { data, isLoading, isError, error } = useQuery({
  queryKey: computed(() => ['dashboard', range.value, departmentId.value, categoryId.value, priority.value, status.value]),
  queryFn: () => fetchDashboard({
    ...range.value,
    departmentId: departmentId.value || null,
    categoryId: categoryId.value || null,
    priority: priority.value || null,
    status: status.value || null,
  }),
});
const metrics = computed(() => data.value || {});
</script>
