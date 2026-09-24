<template>
  <section class="space-y-6">
    <PageHeader title="Email log" description="Every notification is stored here. Messages stay pending until SMTP or Resend is configured." />
    <LoadingSkeleton v-if="isLoading" />
    <EmptyState v-else-if="!(items || []).length" title="No emails yet" message="Complaint and invite emails will appear here after they are queued." />
    <DataTable v-else :columns="columns" :rows="items || []">
      <template #created_at="{ row }">{{ formatDate(row.created_at) }}</template>
      <template #payload="{ row }">{{ JSON.stringify(row.payload || {}) }}</template>
      <template #card="{ row }">
        <p class="font-semibold">{{ row.template }} · {{ row.status }}</p>
        <p class="text-sm text-slate-500">{{ row.to_email }}</p>
      </template>
    </DataTable>
  </section>
</template>

<script setup>
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import DataTable from '@/components/common/DataTable.vue';
import { supabase } from '@/lib/supabase';
import { formatDate } from '@/lib/utils';

const columns = [
  { key: 'created_at', label: 'When' },
  { key: 'to_email', label: 'To' },
  { key: 'template', label: 'Template' },
  { key: 'status', label: 'Status' },
  { key: 'payload', label: 'Details' },
];

const { data: items, isLoading } = useQuery({
  queryKey: ['email-outbox'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('email_outbox')
      .select('id, to_email, template, payload, status, created_at, error_message')
      .order('created_at', { ascending: false })
      .limit(200);
    if (error) throw error;
    return data;
  },
});
</script>
