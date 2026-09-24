<template>
  <section class="space-y-6">
    <PageHeader title="Billing" description="Plans and usage come from the database. Stripe checkout is optional and only runs when a secret key is configured.">
      <AppButton v-if="auth.isPlatformAdmin.value" @click="open = true">Assign plan</AppButton>
    </PageHeader>

    <div v-if="usage" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <article class="rounded-2xl border bg-white p-4">
        <p class="text-sm text-slate-500">Plan</p>
        <p class="mt-1 text-2xl font-semibold">{{ usage.plan?.name || 'None' }}</p>
      </article>
      <article class="rounded-2xl border bg-white p-4">
        <p class="text-sm text-slate-500">Users</p>
        <p class="mt-1 text-2xl font-semibold">{{ usage.users }} / {{ usage.plan?.max_users ?? '∞' }}</p>
      </article>
      <article class="rounded-2xl border bg-white p-4">
        <p class="text-sm text-slate-500">Complaints</p>
        <p class="mt-1 text-2xl font-semibold">{{ usage.complaints }} / {{ usage.plan?.max_complaints ?? '∞' }}</p>
      </article>
      <article class="rounded-2xl border bg-white p-4">
        <p class="text-sm text-slate-500">Price</p>
        <p class="mt-1 text-2xl font-semibold">{{ priceLabel(usage.plan) }}</p>
      </article>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <article v-for="plan in plans || []" :key="plan.id" class="rounded-2xl border bg-white p-4">
        <h3 class="font-semibold">{{ plan.name }}</h3>
        <p class="text-sm text-slate-500">{{ priceLabel(plan) }}</p>
        <p class="mt-2 text-sm">{{ plan.max_users ?? 'Unlimited' }} users · {{ plan.max_complaints ?? 'Unlimited' }} complaints</p>
      </article>
    </div>

    <div v-if="auth.isPlatformAdmin.value" class="overflow-x-auto rounded-2xl border bg-white">
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
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const saving = ref(false);
const form = reactive({ organization_id: '', plan_id: '' });

const { data: usage } = useQuery({
  queryKey: ['organization-usage'],
  queryFn: async () => {
    const { data, error } = await supabase.rpc('organization_usage');
    if (error) throw error;
    return data;
  },
});
const { data: plans } = useQuery({
  queryKey: ['plans'],
  queryFn: async () => {
    const { data, error } = await supabase.from('subscription_plans').select('*').order('price_cents');
    if (error) throw error;
    return data;
  },
});
const { data: subscriptions } = useQuery({
  queryKey: ['org-subscriptions'],
  enabled: computed(() => auth.isPlatformAdmin.value),
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
  enabled: computed(() => auth.isPlatformAdmin.value),
  queryFn: async () => {
    const { data, error } = await supabase.from('organizations').select('id, name').order('name');
    if (error) throw error;
    return data;
  },
});
const orgOptions = computed(() => (organizations.value || []).map((item) => ({ value: item.id, label: item.name })));
const planOptions = computed(() => (plans.value || []).map((item) => ({ value: item.id, label: item.name })));

function priceLabel(plan) {
  if (!plan) return '—';
  if (!plan.price_cents) return 'Free';
  return `$${(plan.price_cents / 100).toFixed(0)} / ${plan.billing_interval || 'month'}`;
}

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
    queryClient.invalidateQueries({ queryKey: ['organization-usage'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to assign this plan.'));
  } finally {
    saving.value = false;
  }
}
</script>
