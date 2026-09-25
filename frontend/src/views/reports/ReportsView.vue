<template>
  <section class="space-y-6">
    <PageHeader title="Reports" description="Operational reports calculated from live complaint records.">
      <select v-model="preset" class="field-input w-auto">
        <option value="30">Last 30 days</option>
        <option value="month">This month</option>
        <option value="quarter">This quarter</option>
        <option value="year">This year</option>
        <option value="all">All time</option>
      </select>
      <AppButton v-if="auth.can('reports:export')" variant="secondary" @click="exportCsv">Export CSV</AppButton>
    </PageHeader>
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard label="Total" :value="metrics.total || 0" />
      <StatCard label="Open" :value="metrics.open || 0" />
      <StatCard label="Resolved" :value="metrics.resolved || 0" />
      <StatCard label="Overdue" :value="metrics.overdue || 0" />
      <StatCard label="SLA breached" :value="extraSafe.sla_breached || 0" />
      <StatCard label="Resolved on time" :value="extraSafe.on_time || 0" />
      <StatCard label="Escalated" :value="metrics.escalated || 0" />
      <StatCard label="Reopened" :value="extraSafe.reopen_count || 0" />
      <StatCard label="Avg resolution hours" :value="Number(extraSafe.avg_resolution_hours || metrics.avg_resolution_hours || 0).toFixed(1)" />
      <StatCard label="SLA compliance" :value="`${metrics.sla_compliance || 0}%`" />
    </div>
    <div class="grid gap-4 lg:grid-cols-2">
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Complaints by status</h2>
        <div class="mt-3 h-48">
          <SimpleChart :labels="statusLabels" :values="statusValues" label="Cases" />
        </div>
      </div>
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Complaints by priority</h2>
        <div class="mt-3 h-48">
          <SimpleChart :labels="priorityLabels" :values="priorityValues" label="Cases" />
        </div>
      </div>
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Complaints by category</h2>
        <ul class="mt-3 space-y-1 text-muted">
          <li v-for="row in metrics.by_category || []" :key="row.name">{{ row.name }}: {{ row.count }}</li>
        </ul>
      </div>
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Complaints by department</h2>
        <ul class="mt-3 space-y-1 text-muted">
          <li v-for="row in metrics.by_department || []" :key="row.name">{{ row.name }}: {{ row.count }}</li>
        </ul>
      </div>
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Aging (open cases)</h2>
        <ul class="mt-3 space-y-1 text-muted">
          <li v-for="(count, bucket) in extraSafe.aging || {}" :key="bucket">{{ bucket }}: {{ count }}</li>
        </ul>
      </div>
      <div class="surface rounded-2xl p-4 text-sm">
        <h2 class="font-display text-xl">Monthly trends</h2>
        <div class="mt-3 h-48">
          <SimpleChart type="line" :labels="monthLabels" :values="monthValues" label="Cases" />
        </div>
      </div>
      <div class="surface rounded-2xl p-4 text-sm lg:col-span-2">
        <h2 class="font-display text-xl">Officer workload</h2>
        <ul class="mt-3 space-y-1 text-muted">
          <li v-for="row in extraSafe.by_assignee || []" :key="row.name">{{ row.name }}: {{ row.open }} open</li>
        </ul>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import StatCard from '@/components/dashboard/StatCard.vue';
import SimpleChart from '@/components/dashboard/SimpleChart.vue';
import { fetchComplaints, fetchDashboard, fetchReports } from '@/services/complaint.service';
import { dateRangePreset, downloadText, toCsv } from '@/lib/utils';
import { useAuth } from '@/composables/useAuth';

const auth = useAuth();
const preset = ref('30');
const range = computed(() => dateRangePreset(preset.value));
const { data: metrics } = useQuery({
  queryKey: computed(() => ['reports', range.value]),
  queryFn: () => fetchDashboard(range.value),
});
const { data: extra } = useQuery({
  queryKey: computed(() => ['report-metrics', range.value]),
  queryFn: () => fetchReports(range.value),
});
const extraSafe = computed(() => extra.value || {});
const statusLabels = computed(() => Object.keys(metrics.value?.by_status || {}));
const statusValues = computed(() => Object.values(metrics.value?.by_status || {}));
const priorityLabels = computed(() => Object.keys(metrics.value?.by_priority || {}));
const priorityValues = computed(() => Object.values(metrics.value?.by_priority || {}));
const monthLabels = computed(() => (extraSafe.value.monthly_trends || []).map((row) => row.month));
const monthValues = computed(() => (extraSafe.value.monthly_trends || []).map((row) => row.count));

async function exportCsv() {
  const { items } = await fetchComplaints({ page: 1, pageSize: 500, from: range.value.from, to: range.value.to });
  downloadText(
    'complaints.csv',
    toCsv(
      items.map((item) => ({
        reference: item.reference_number,
        title: item.title,
        status: item.status,
        priority: item.priority,
        department: item.department?.name,
        category: item.category?.name,
        assignee: item.assignee ? `${item.assignee.first_name || ''} ${item.assignee.last_name || ''}`.trim() : '',
        sla_breached: item.sla_breached,
        created: item.created_at,
        due: item.due_date,
      }))
    )
  );
}
</script>
