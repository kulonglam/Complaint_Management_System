<template>
  <section class="space-y-6">
    <PageHeader title="Departments">
      <AppButton v-if="auth.can('departments:create')" @click="open = true">Add department</AppButton>
    </PageHeader>
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr><th class="px-4 py-3">Name</th><th class="px-4 py-3">Code</th><th class="px-4 py-3">Status</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ item.name }}</td>
            <td class="px-4 py-3">{{ item.code || '—' }}</td>
            <td class="px-4 py-3">{{ item.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Modal :open="open" title="New department" @close="open = false">
      <form class="grid gap-3" @submit.prevent="create">
        <FormField v-model="name" label="Name" required />
        <FormField v-model="code" label="Code" />
        <AppButton type="submit">Save</AppButton>
      </form>
    </Modal>
  </section>
</template>

<script setup>
import { ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const name = ref('');
const code = ref('');
const { data: items } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error } = await supabase.from('departments').select('*').order('name');
    if (error) throw error;
    return data;
  },
});

async function create() {
  const { error } = await supabase.from('departments').insert({
    organization_id: auth.state.profile.organization_id,
    name: name.value,
    code: code.value || null,
  });
  if (error) {
    toast.error(getErrorMessage(error));
    return;
  }
  open.value = false;
  queryClient.invalidateQueries({ queryKey: ['departments'] });
}
</script>
