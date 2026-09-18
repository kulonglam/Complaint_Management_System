<template>
  <section class="space-y-6">
    <PageHeader title="Categories">
      <AppButton v-if="auth.can('categories:create')" @click="open = true">Add category</AppButton>
    </PageHeader>
    <div class="grid gap-4 md:grid-cols-2">
      <article v-for="item in items || []" :key="item.id" class="rounded-2xl border bg-white p-4">
        <h3 class="font-semibold">{{ item.name }}</h3>
        <ul class="mt-2 list-disc pl-5 text-sm text-slate-600">
          <li v-for="sub in item.complaint_subcategories" :key="sub.id">{{ sub.name }}</li>
        </ul>
      </article>
    </div>
    <Modal :open="open" title="New category" @close="open = false">
      <form class="grid gap-3" @submit.prevent="create">
        <FormField v-model="name" label="Name" required />
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
const { data: items } = useQuery({
  queryKey: ['categories'],
  queryFn: async () => {
    const { data, error } = await supabase.from('complaint_categories').select('*, complaint_subcategories(*)').order('name');
    if (error) throw error;
    return data;
  },
});
async function create() {
  const { error } = await supabase.from('complaint_categories').insert({
    organization_id: auth.state.profile.organization_id,
    name: name.value,
  });
  if (error) return toast.error(getErrorMessage(error));
  open.value = false;
  queryClient.invalidateQueries({ queryKey: ['categories'] });
}
</script>
