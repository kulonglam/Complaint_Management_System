<template>
  <section class="space-y-6">
    <PageHeader title="Audit logs" description="Immutable activity history for this organization. Ordinary users cannot edit or delete these records.">
      <AppButton v-if="auth.can('reports:export')" variant="secondary" @click="exportCsv">Export CSV</AppButton>
    </PageHeader>
    <div class="grid gap-3 rounded-2xl border bg-white p-4 md:grid-cols-3">
      <input v-model="action" class="rounded-lg border px-3 py-2 text-sm" placeholder="Filter action" />
      <input v-model="entity" class="rounded-lg border px-3 py-2 text-sm" placeholder="Filter entity type" />
    </div>
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">When</th>
            <th class="px-4 py-3">Action</th>
            <th class="px-4 py-3">Entity</th>
            <th class="px-4 py-3">User</th>
            <th class="px-4 py-3">Details</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filtered" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ formatDate(item.created_at) }}</td>
            <td class="px-4 py-3">{{ item.action }}</td>
            <td class="px-4 py-3">{{ item.entity_type }}</td>
            <td class="px-4 py-3">{{ displayName(item.actor) }}</td>
            <td class="px-4 py-3 text-xs text-slate-500">{{ JSON.stringify(item.new_values || item.metadata || {}) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { displayName, downloadText, formatDate, toCsv } from '@/lib/utils';

const auth = useAuth();
const action = ref('');
const entity = ref('');
const { data: items } = useQuery({
  queryKey: ['audit'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('audit_logs')
      .select('*, actor:profiles!user_id(first_name, last_name, email)')
      .order('created_at', { ascending: false })
      .limit(200);
    if (error) throw error;
    return data;
  },
});
const filtered = computed(() =>
  (items.value || []).filter((item) =>
    (!action.value || item.action.toLowerCase().includes(action.value.toLowerCase()))
    && (!entity.value || item.entity_type.toLowerCase().includes(entity.value.toLowerCase()))
  )
);

function exportCsv() {
  downloadText('audit-logs.csv', toCsv(filtered.value.map((item) => ({
    when: item.created_at,
    action: item.action,
    entity: item.entity_type,
    user: displayName(item.actor),
  }))));
}
</script>
