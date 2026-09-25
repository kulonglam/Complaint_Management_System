<template>
  <section class="space-y-6">
    <PageHeader title="Email log" description="Every notification is stored here. Resend test mode can only deliver to the account owner's email until you verify a domain." />
    <LoadingSkeleton v-if="isLoading" />
    <EmptyState v-else-if="!(items || []).length" title="No emails yet" message="Complaint and invite emails will appear here after they are queued." />
    <DataTable v-else :columns="columns" :rows="items || []">
      <template #created_at="{ row }">{{ formatDate(row.created_at) }}</template>
      <template #template="{ row }">{{ humanize(row.template) }}</template>
      <template #payload="{ row }">{{ emailSummary(row) }}</template>
      <template #card="{ row }">
        <p class="font-semibold">{{ humanize(row.template) }} · {{ row.status }}</p>
        <p class="text-sm text-muted">{{ row.to_email }}</p>
        <p class="mt-1 text-xs text-muted">{{ emailSummary(row) }}</p>
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
import { formatDate, formatRecordSummary } from '@/lib/utils';

function humanize(value) {
  return String(value || '').replace(/[_-]+/g, ' ');
}

function emailSummary(row) {
  const payload = row.payload || {};
  if (payload.subject) return payload.subject;
  if (payload.reference_number) return `Ref ${payload.reference_number}`;
  if (row.error_message) return row.error_message;
  return formatRecordSummary(payload);
}

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
