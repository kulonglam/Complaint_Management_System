<template>
  <section class="space-y-6">
    <PageHeader title="Roles and permissions" description="Authorization is based on permission keys, not hardcoded role names in the UI." />
    <div class="grid gap-4 lg:grid-cols-2">
      <article v-for="role in roles || []" :key="role.id" class="rounded-2xl border bg-white p-4">
        <h3 class="font-semibold">{{ role.name }}</h3>
        <p class="text-xs text-slate-500">{{ role.key }}</p>
        <div class="mt-3 flex flex-wrap gap-2">
          <span v-for="item in role.role_permissions" :key="item.permission.key" class="rounded-full bg-slate-100 px-2 py-1 text-xs">
            {{ item.permission.key }}
          </span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import { supabase } from '@/lib/supabase';

const { data: roles } = useQuery({
  queryKey: ['roles-detail'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('roles')
      .select('id, key, name, role_permissions(permission:permissions(key))')
      .order('name');
    if (error) throw error;
    return data;
  },
});
</script>
