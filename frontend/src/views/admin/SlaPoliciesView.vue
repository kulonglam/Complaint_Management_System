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
            <th v-if="auth.can('sla:update')" class="px-4 py-3" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3"><PriorityBadge :value="item.priority" /></td>
            <td class="px-4 py-3"><input v-model.number="item.response_hours" class="w-20 rounded border px-2 py-1" :disabled="!auth.can('sla:update')" /></td>
            <td class="px-4 py-3"><input v-model.number="item.resolution_hours" class="w-20 rounded border px-2 py-1" :disabled="!auth.can('sla:update')" /></td>
            <td class="px-4 py-3"><input v-model.number="item.escalation_hours" class="w-20 rounded border px-2 py-1" :disabled="!auth.can('sla:update')" /></td>
            <td class="px-4 py-3"><input v-model.number="item.reminder_hours" class="w-20 rounded border px-2 py-1" :disabled="!auth.can('sla:update')" /></td>
            <td v-if="auth.can('sla:update')" class="px-4 py-3">
              <AppButton variant="secondary" @click="save(item)">Save</AppButton>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const { data: items } = useQuery({
  queryKey: ['sla'],
  queryFn: async () => {
    const { data, error } = await supabase.from('sla_policies').select('*').order('resolution_hours');
    if (error) throw error;
    return data;
  },
});

async function save(item) {
  const { error } = await supabase.from('sla_policies').update({
    response_hours: item.response_hours,
    resolution_hours: item.resolution_hours,
    escalation_hours: item.escalation_hours,
    reminder_hours: item.reminder_hours,
  }).eq('id', item.id);
  if (error) return toast.error(getErrorMessage(error));
  toast.success('SLA policy updated');
  queryClient.invalidateQueries({ queryKey: ['sla'] });
}
</script>
