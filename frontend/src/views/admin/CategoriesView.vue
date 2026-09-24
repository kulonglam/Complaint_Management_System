<template>
  <section class="space-y-6">
    <PageHeader title="Categories">
      <AppButton v-if="auth.can('categories:create')" @click="open = true">Add category</AppButton>
    </PageHeader>
    <div class="grid gap-4 md:grid-cols-2">
      <article v-for="item in items || []" :key="item.id" class="rounded-2xl border bg-white p-4">
        <div class="flex items-start justify-between gap-3">
          <div>
            <h3 class="font-semibold">{{ item.name }}</h3>
            <p class="text-xs text-slate-500">{{ item.status }}</p>
          </div>
          <div class="flex gap-2">
            <AppButton v-if="auth.can('categories:update')" variant="secondary" @click="toggle(item)">{{ item.status === 'ACTIVE' ? 'Disable' : 'Enable' }}</AppButton>
            <AppButton v-if="auth.can('categories:create')" variant="secondary" @click="addSub(item)">Add subcategory</AppButton>
          </div>
        </div>
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
    <Modal :open="Boolean(subParent)" title="New subcategory" @close="subParent = null">
      <form class="grid gap-3" @submit.prevent="createSub">
        <FormField v-model="subName" label="Name" required />
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
const subParent = ref(null);
const subName = ref('');
const { data: items } = useQuery({
  queryKey: ['categories'],
  queryFn: async () => {
    const { data, error } = await supabase.from('complaint_categories').select('*, complaint_subcategories(*)').order('name');
    if (error) throw error;
    return data;
  },
});

function reload() {
  queryClient.invalidateQueries({ queryKey: ['categories'] });
}

async function create() {
  const { error } = await supabase.from('complaint_categories').insert({
    organization_id: auth.state.profile.organization_id,
    name: name.value,
  });
  if (error) return toast.error(getErrorMessage(error));
  open.value = false;
  name.value = '';
  reload();
}

function addSub(item) {
  subParent.value = item;
  subName.value = '';
}

async function createSub() {
  const { error } = await supabase.from('complaint_subcategories').insert({
    organization_id: auth.state.profile.organization_id,
    category_id: subParent.value.id,
    name: subName.value,
  });
  if (error) return toast.error(getErrorMessage(error));
  subParent.value = null;
  reload();
}

async function toggle(item) {
  const { error } = await supabase.from('complaint_categories').update({
    status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
  }).eq('id', item.id);
  if (error) return toast.error(getErrorMessage(error));
  reload();
}
</script>
