<template>
  <section class="space-y-6">
    <PageHeader title="Departments">
      <AppButton v-if="auth.can('departments:create')" @click="openCreate()">Add department</AppButton>
    </PageHeader>
    <div class="surface overflow-x-auto rounded-2xl">
      <table class="cms-table">
        <thead>
          <tr><th class="px-4 py-3">Name</th><th class="px-4 py-3">Code</th><th class="px-4 py-3">Status</th><th class="px-4 py-3" /></tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ item.name }}</td>
            <td class="px-4 py-3">{{ item.code || '—' }}</td>
            <td class="px-4 py-3">{{ item.status }}</td>
            <td class="px-4 py-3">
              <AppButton v-if="auth.can('departments:update')" variant="secondary" @click="openEdit(item)">Edit</AppButton>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <Modal :open="open" :title="editing ? 'Edit department' : 'New department'" @close="open = false">
      <form class="grid gap-3" @submit.prevent="save">
        <FormField v-model="name" label="Name" required />
        <FormField v-model="code" label="Code" />
        <FormField v-if="editing" v-model="status" label="Status" type="select" :options="statusOptions" />
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
const editing = ref(null);
const name = ref('');
const code = ref('');
const status = ref('ACTIVE');
const statusOptions = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];
const { data: items } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error } = await supabase.from('departments').select('*').order('name');
    if (error) throw error;
    return data;
  },
});

function openCreate() {
  editing.value = null;
  name.value = '';
  code.value = '';
  status.value = 'ACTIVE';
  open.value = true;
}

function openEdit(item) {
  editing.value = item;
  name.value = item.name;
  code.value = item.code || '';
  status.value = item.status;
  open.value = true;
}

async function save() {
  const payload = {
    organization_id: auth.state.profile.organization_id,
    name: name.value,
    code: code.value || null,
    status: status.value,
  };
  const { error } = editing.value
    ? await supabase.from('departments').update(payload).eq('id', editing.value.id)
    : await supabase.from('departments').insert(payload);
  if (error) {
    toast.error(getErrorMessage(error));
    return;
  }
  open.value = false;
  queryClient.invalidateQueries({ queryKey: ['departments'] });
}
</script>
