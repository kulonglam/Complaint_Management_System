<template>
  <section class="space-y-6">
    <PageHeader title="SLA policies" description="Response and resolution hours by priority. Due dates are calculated from these values." />
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">Priority</th>
            <th class="px-4 py-3">Response hours</th>
            <th class="px-4 py-3">Resolution hours</th>
            <th class="px-4 py-3">Escalation</th>
            <th class="px-4 py-3">Reminder</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3"><PriorityBadge :value="item.priority" /></td>
            <td class="px-4 py-3">{{ item.response_hours }}</td>
            <td class="px-4 py-3">{{ item.resolution_hours }}</td>
            <td class="px-4 py-3">{{ item.escalation_hours }}</td>
            <td class="px-4 py-3">{{ item.reminder_hours }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import { supabase } from '@/lib/supabase';

const { data: items } = useQuery({
  queryKey: ['sla'],
  queryFn: async () => {
    const { data, error } = await supabase.from('sla_policies').select('*').order('resolution_hours');
    if (error) throw error;
    return data;
  },
});
</script>
