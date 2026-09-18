<template>
  <section class="space-y-6">
    <PageHeader title="Notifications">
      <AppButton variant="secondary" @click="markAll">Mark all read</AppButton>
    </PageHeader>
    <EmptyState v-if="!(items || []).length" title="No notifications" message="Assignment, SLA, and status events will appear here." />
    <ul v-else class="space-y-3">
      <li v-for="item in items" :key="item.id" class="rounded-2xl border bg-white p-4" :class="item.read_at ? 'opacity-70' : ''">
        <p class="font-medium">{{ item.title }}</p>
        <p class="text-sm text-slate-600">{{ item.message }}</p>
        <router-link v-if="item.related_complaint_id" class="text-sm text-blue-700" :to="`/complaints/${item.related_complaint_id}`">Open complaint</router-link>
      </li>
    </ul>
  </section>
</template>

<script setup>
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import { supabase } from '@/lib/supabase';

const queryClient = useQueryClient();
const { data: items } = useQuery({
  queryKey: ['notifications'],
  queryFn: async () => {
    const { data, error } = await supabase.from('notifications').select('*').order('created_at', { ascending: false }).limit(50);
    if (error) throw error;
    return data;
  },
});

async function markAll() {
  await supabase.from('notifications').update({ read_at: new Date().toISOString() }).is('read_at', null);
  queryClient.invalidateQueries({ queryKey: ['notifications'] });
}
</script>
