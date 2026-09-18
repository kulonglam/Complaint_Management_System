<template>
  <section class="space-y-6">
    <PageHeader :title="displayName(user)" :description="user?.email" />
    <div v-if="user" class="rounded-2xl border border-slate-200 bg-white p-6 text-sm">
      <p>Job title: {{ user.job_title || '—' }}</p>
      <p>Department: {{ user.department?.name || '—' }}</p>
      <p>Status: {{ user.status }}</p>
      <p>Last login: {{ formatDate(user.last_login_at) }}</p>
    </div>
  </section>
</template>

<script setup>
import { useRoute } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import { supabase } from '@/lib/supabase';
import { displayName, formatDate } from '@/lib/utils';

const route = useRoute();
const { data: user } = useQuery({
  queryKey: ['user', route.params.id],
  queryFn: async () => {
    const { data, error } = await supabase.from('profiles').select('*, department:departments(name)').eq('id', route.params.id).single();
    if (error) throw error;
    return data;
  },
});
</script>
