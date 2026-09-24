<template>
  <section class="space-y-6">
    <PageHeader title="Billing" description="Tenant plan assignment. Payment collection is reserved for a later integration.">
      <AppButton @click="open = true">Assign plan</AppButton>
    </PageHeader>
    <div class="grid gap-4 md:grid-cols-3">
      <article v-for="plan in plans || []" :key="plan.id" class="rounded-2xl border bg-white p-4">
        <h3 class="font-semibold">{{ plan.name }}</h3>
        <p class="text-sm text-slate-500">Created {{ formatDate(plan.created_at, false) }}</p>
      </article>
    </div>
    <div class="overflow-x-auto rounded-2xl border bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">Organization</th>
            <th class="px-4 py-3">Plan</th>
            <th class="px-4 py-3">Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in subscriptions || []" :key="item.id" class="border-t">
            <td class="px-4 py-3">{{ item.organization?.name || item.organization_id }}</td>
            <td class="px-4 py-3">{{ item.plan?.name || '—' }}</td>
            <td class="px-4 py-3">{{ item.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Modal :open="open" title="Assign subscription plan" @close="open = false">
      <form class="grid gap-3" @submit.prevent="assign">
        <FormField v-model="form.organization_id" label="Organization" type="select" :options="orgOptions" required />
        <FormField v-model="form.plan_id" label="Plan" type="select" :options="planOptions" required />
        <AppButton type="submit" :loading="saving">Save assignment</AppButton>
      </form>
    </Modal>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useToast } from '@/composables/useToast';
import { formatDate, getErrorMessage } from '@/lib/utils';

const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const saving = ref(false);
const form = reactive({ organization_id: '', plan_id: '' });

const { data: plans } = useQuery({
  queryKey: ['plans'],
  queryFn: async () => {
    const { data, error } = await supabase.from('subscription_plans').select('*').order('name');
    if (error) throw error;
    return data;
  },
});
const { data: subscriptions } = useQuery({
  queryKey: ['org-subscriptions'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('organization_subscriptions')
      .select('*, organization:organizations(name), plan:subscription_plans(name)');
    if (error) throw error;
    return data;
  },
});
const { data: organizations } = useQuery({
  queryKey: ['organizations'],
  queryFn: async () => {
    const { data, error } = await supabase.from('organizations').select('id, name').order('name');
    if (error) throw error;
    return data;
  },
});
const orgOptions = computed(() => (organizations.value || []).map((item) => ({ value: item.id, label: item.name })));
const planOptions = computed(() => (plans.value || []).map((item) => ({ value: item.id, label: item.name })));

async function assign() {
  saving.value = true;
  try {
    const { error } = await supabase.from('organization_subscriptions').insert({
      organization_id: form.organization_id,
      plan_id: form.plan_id,
      status: 'ACTIVE',
    });
    if (error) throw error;
    toast.success('Plan assigned');
    open.value = false;
    queryClient.invalidateQueries({ queryKey: ['org-subscriptions'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to assign this plan.'));
  } finally {
    saving.value = false;
  }
}
</script>
