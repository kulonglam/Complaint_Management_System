<template>
  <section class="space-y-6">
    <PageHeader title="Organizations" description="Platform administration. Organization users cannot see this page." />
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">Name</th>
            <th class="px-4 py-3">Slug</th>
            <th class="px-4 py-3">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ item.name }}</td>
            <td class="px-4 py-3">{{ item.slug }}</td>
            <td class="px-4 py-3">{{ item.status }}</td>
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

const { data: items } = useQuery({
  queryKey: ['organizations'],
  queryFn: async () => {
    const { data, error } = await supabase.from('organizations').select('*').order('name');
    if (error) throw error;
    return data;
  },
});
</script>
