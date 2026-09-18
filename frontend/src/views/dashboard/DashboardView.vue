<template>
  <section class="space-y-6">
    <PageHeader title="Dashboard" description="Live metrics for your organization. Figures come from complaint records, not placeholders.">
      <select v-model="preset" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="30">Last 30 days</option>
        <option value="week">This week</option>
        <option value="month">This month</option>
        <option value="quarter">This quarter</option>
        <option value="year">This year</option>
        <option value="all">All time</option>
      </select>
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
          <h2 class="mb-3 font-semibold">By status</h2>
          <SimpleChart
            :labels="Object.keys(metrics.by_status || {})"
            :values="Object.values(metrics.by_status || {})"
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
import { dateRangePreset, getErrorMessage } from '@/lib/utils';

const preset = ref('30');
const range = computed(() => dateRangePreset(preset.value));
const { data, isLoading, isError, error } = useQuery({
  queryKey: computed(() => ['dashboard', range.value]),
  queryFn: () => fetchDashboard(range.value),
});
const metrics = computed(() => data.value || {});
</script>
