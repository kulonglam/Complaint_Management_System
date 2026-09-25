<template>
  <section class="space-y-6">
    <PageHeader title="Today’s work" description="What needs attention in this organization. Figures come from live complaint records.">
      <div class="flex flex-wrap gap-2">
        <select v-model="preset" class="field-input w-auto">
          <option value="today">Today</option>
          <option value="week">This week</option>
          <option value="month">This month</option>
          <option value="30">Last 30 days</option>
          <option value="quarter">This quarter</option>
          <option value="year">This year</option>
          <option value="custom">Custom range</option>
          <option value="all">All time</option>
        </select>
        <input v-if="preset === 'custom'" v-model="customFrom" class="field-input w-auto" type="date" />
        <input v-if="preset === 'custom'" v-model="customTo" class="field-input w-auto" type="date" />
        <select v-model="departmentId" class="field-input w-auto">
          <option value="">All departments</option>
          <option v-for="item in departments || []" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
        <select v-model="categoryId" class="field-input w-auto">
          <option value="">All categories</option>
          <option v-for="item in categories || []" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </div>
    </PageHeader>
    <LoadingSkeleton v-if="isLoading" />
    <ErrorState v-else-if="isError" :message="getErrorMessage(error)" />
    <template v-else>
      <div v-if="Number(metrics.overdue || 0) > 0" class="surface flex flex-wrap items-center justify-between gap-3 rounded-2xl p-4 ring-1 ring-[var(--danger)]/15">
        <div>
          <p class="font-display text-lg">{{ metrics.overdue }} case{{ Number(metrics.overdue) === 1 ? '' : 's' }} past SLA</p>
          <p class="text-sm text-muted">Start with overdue and escalated work before opening new intake.</p>
        </div>
        <AppButton @click="$router.push({ path: '/complaints', query: { overdue: '1' } })">Review overdue</AppButton>
      </div>
      <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Open" :value="metrics.open || 0" hint="Still in the workflow" />
        <StatCard label="Overdue" :value="metrics.overdue || 0" hint="Past the SLA due date" tone="alert" />
        <StatCard label="SLA on time" :value="`${metrics.sla_compliance || 0}%`" />
        <StatCard label="Avg resolution" :value="`${Number(metrics.avg_resolution_hours || 0).toFixed(1)}h`" />
      </div>
      <div class="grid gap-4 lg:grid-cols-3">
        <article class="surface rounded-2xl p-4 lg:col-span-2">
          <div class="mb-3 flex items-center justify-between">
            <h2 class="font-display text-xl">Volume over time</h2>
            <p class="text-xs text-muted">{{ metrics.total || 0 }} total in range</p>
          </div>
          <div class="h-72">
            <SimpleChart
              type="line"
              :labels="(metrics.over_time || []).map((row) => row.date)"
              :values="(metrics.over_time || []).map((row) => row.count)"
            />
          </div>
        </article>
        <article class="surface rounded-2xl p-4">
          <h2 class="font-display text-xl">By status</h2>
          <div class="mt-3 h-72">
            <SimpleChart
              :labels="Object.keys(metrics.by_status || {})"
              :values="Object.values(metrics.by_status || {})"
            />
          </div>
        </article>
        <article class="surface rounded-2xl p-4">
          <h2 class="font-display text-xl">By category</h2>
          <div class="mt-3 h-64">
            <SimpleChart
              :labels="(metrics.by_category || []).map((row) => row.name)"
              :values="(metrics.by_category || []).map((row) => row.count)"
            />
          </div>
        </article>
        <article class="surface rounded-2xl p-4 lg:col-span-2">
          <h2 class="font-display text-xl">Secondary measures</h2>
          <dl class="mt-4 grid gap-4 sm:grid-cols-3 text-sm">
            <div><dt class="text-muted">Under investigation</dt><dd class="font-display text-2xl">{{ metrics.under_investigation || 0 }}</dd></div>
            <div><dt class="text-muted">Escalated</dt><dd class="font-display text-2xl">{{ metrics.escalated || 0 }}</dd></div>
            <div><dt class="text-muted">Resolved</dt><dd class="font-display text-2xl">{{ metrics.resolved || 0 }}</dd></div>
            <div><dt class="text-muted">Closed</dt><dd class="font-display text-2xl">{{ metrics.closed || 0 }}</dd></div>
            <div><dt class="text-muted">Resolution rate</dt><dd class="font-display text-2xl">{{ metrics.resolution_rate || 0 }}%</dd></div>
            <div><dt class="text-muted">Escalation rate</dt><dd class="font-display text-2xl">{{ metrics.escalation_rate || 0 }}%</dd></div>
          </dl>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import StatCard from '@/components/dashboard/StatCard.vue';
import SimpleChart from '@/components/dashboard/SimpleChart.vue';
import { fetchDashboard } from '@/services/complaint.service';
import { dateRangePreset, getErrorMessage } from '@/lib/utils';
import { supabase } from '@/lib/supabase';

const preset = ref('30');
const customFrom = ref('');
const customTo = ref('');
const departmentId = ref('');
const categoryId = ref('');
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
  queryKey: computed(() => ['dashboard', range.value, departmentId.value, categoryId.value]),
  queryFn: () => fetchDashboard({
    ...range.value,
    departmentId: departmentId.value || null,
    categoryId: categoryId.value || null,
  }),
});
const metrics = computed(() => data.value || {});
</script>
