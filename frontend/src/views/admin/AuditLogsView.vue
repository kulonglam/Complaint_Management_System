<template>
  <section class="space-y-6">
    <PageHeader title="Audit logs" description="Immutable activity history for this organization. Ordinary users cannot edit or delete these records." />
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">When</th>
            <th class="px-4 py-3">Action</th>
            <th class="px-4 py-3">Entity</th>
            <th class="px-4 py-3">User</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ formatDate(item.created_at) }}</td>
            <td class="px-4 py-3">{{ item.action }}</td>
            <td class="px-4 py-3">{{ item.entity_type }}</td>
            <td class="px-4 py-3">{{ item.user_id }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import { supabase } from '@/lib/supabase';
import { formatDate } from '@/lib/utils';

const { data: items } = useQuery({
  queryKey: ['audit'],
  queryFn: async () => {
    const { data, error } = await supabase.from('audit_logs').select('*').order('created_at', { ascending: false }).limit(200);
    if (error) throw error;
    return data;
  },
});
</script>
