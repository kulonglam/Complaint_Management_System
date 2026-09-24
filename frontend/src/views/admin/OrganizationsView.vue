<template>
  <section class="space-y-6">
    <PageHeader title="Organizations" description="Platform administration. Organization users cannot see this page.">
      <AppButton @click="open = true">Create organization</AppButton>
    </PageHeader>
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">Name</th>
            <th class="px-4 py-3">Slug</th>
            <th class="px-4 py-3">Status</th>
            <th class="px-4 py-3" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ item.name }}</td>
            <td class="px-4 py-3">{{ item.slug }}</td>
            <td class="px-4 py-3">
              <select class="rounded border px-2 py-1" :value="item.status" @change="setStatus(item, $event.target.value)">
                <option value="ACTIVE">ACTIVE</option>
                <option value="TRIAL">TRIAL</option>
                <option value="SUSPENDED">SUSPENDED</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </td>
            <td class="px-4 py-3 text-slate-500">{{ item.email || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Modal :open="open" title="New organization" @close="open = false">
      <form class="grid gap-3" @submit.prevent="create">
        <FormField v-model="form.name" label="Name" required />
        <FormField v-model="form.slug" label="Slug" required />
        <FormField v-model="form.email" label="Contact email" type="email" />
        <AppButton type="submit" :loading="saving">Create</AppButton>
      </form>
    </Modal>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const saving = ref(false);
const form = reactive({ name: '', slug: '', email: '' });

const { data: items } = useQuery({
  queryKey: ['organizations'],
  queryFn: async () => {
    const { data, error } = await supabase.from('organizations').select('*').order('name');
    if (error) throw error;
    return data;
  },
});

async function create() {
  saving.value = true;
  try {
    const { error } = await supabase.rpc('create_organization', {
      p_name: form.name,
      p_slug: form.slug,
      p_email: form.email || null,
    });
    if (error) throw error;
    open.value = false;
    queryClient.invalidateQueries({ queryKey: ['organizations'] });
    toast.success('Organization created');
  } catch (err) {
    toast.error(getErrorMessage(err));
  } finally {
    saving.value = false;
  }
}

async function setStatus(item, status) {
  const { error } = await supabase.from('organizations').update({ status }).eq('id', item.id);
  if (error) return toast.error(getErrorMessage(error));
  queryClient.invalidateQueries({ queryKey: ['organizations'] });
}
</script>
