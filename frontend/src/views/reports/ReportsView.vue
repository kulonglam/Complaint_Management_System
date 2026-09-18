<template>
  <section class="space-y-6">
    <PageHeader title="Reports" description="Operational reports calculated from live complaint records.">
      <AppButton v-if="auth.can('reports:export')" variant="secondary" @click="exportCsv">Export CSV</AppButton>
    </PageHeader>
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard label="Total" :value="metrics.total || 0" />
      <StatCard label="Open" :value="metrics.open || 0" />
      <StatCard label="Overdue" :value="metrics.overdue || 0" />
      <StatCard label="Escalated" :value="metrics.escalated || 0" />
    </div>
    <div class="rounded-2xl border bg-white p-4 text-sm">
      <h2 class="font-semibold">By status</h2>
      <ul class="mt-3 space-y-1">
        <li v-for="(count, status) in metrics.by_status || {}" :key="status">{{ status }}: {{ count }}</li>
      </ul>
    </div>
  </section>
</template>

<script setup>
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import StatCard from '@/components/dashboard/StatCard.vue';
import { fetchComplaints, fetchDashboard } from '@/services/complaint.service';
import { downloadText, toCsv } from '@/lib/utils';
import { useAuth } from '@/composables/useAuth';

const auth = useAuth();
const { data: metrics } = useQuery({
  queryKey: ['reports'],
  queryFn: () => fetchDashboard({}),
});

async function exportCsv() {
  const { items } = await fetchComplaints({ page: 1, pageSize: 500 });
  downloadText(
    'complaints.csv',
    toCsv(
      items.map((item) => ({
        reference: item.reference_number,
        title: item.title,
        status: item.status,
        priority: item.priority,
        department: item.department?.name,
        created: item.created_at,
      }))
    )
  );
}
</script>
