<template>
  <section class="space-y-6">
    <PageHeader title="Billing" description="Plans are priced in Ugandan shillings. Pay with MTN Mobile Money, Airtel Money, or Pesapal.">
      <AppButton v-if="auth.isPlatformAdmin.value" variant="secondary" @click="assignOpen = true">Assign plan</AppButton>
    </PageHeader>

    <div v-if="usage" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <article class="surface rounded-2xl p-4">
        <p class="text-sm text-muted">Plan</p>
        <p class="mt-1 font-display text-2xl">{{ usage.plan?.name || 'None' }}</p>
      </article>
      <article class="surface rounded-2xl p-4">
        <p class="text-sm text-muted">Users</p>
        <p class="mt-1 font-display text-2xl">{{ usage.users }} / {{ usage.plan?.max_users ?? '∞' }}</p>
      </article>
      <article class="surface rounded-2xl p-4">
        <p class="text-sm text-muted">Complaints</p>
        <p class="mt-1 font-display text-2xl">{{ usage.complaints }} / {{ usage.plan?.max_complaints ?? '∞' }}</p>
      </article>
      <article class="surface rounded-2xl p-4">
        <p class="text-sm text-muted">Price</p>
        <p class="mt-1 font-display text-2xl">{{ priceLabel(usage.plan) }}</p>
      </article>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <article v-for="plan in plans || []" :key="plan.id" class="surface rounded-2xl p-5">
        <h3 class="font-display text-xl">{{ plan.name }}</h3>
        <p class="text-sm text-muted">{{ priceLabel(plan) }}</p>
        <p class="mt-2 text-sm">{{ plan.max_users ?? 'Unlimited' }} users · {{ plan.max_complaints ?? 'Unlimited' }} complaints</p>
        <AppButton
          v-if="plan.price_cents > 0 && (auth.can('settings:update') || auth.isPlatformAdmin.value)"
          class="mt-4"
          :disabled="paying && selectedPlan === plan.id"
          @click="selectedPlan = plan.id"
        >
          {{ selectedPlan === plan.id ? 'Selected' : 'Choose plan' }}
        </AppButton>
      </article>
    </div>

    <form v-if="selectedPlan" class="surface max-w-xl space-y-4 rounded-3xl p-6" @submit.prevent="pay">
      <h2 class="font-display text-xl">Pay in Uganda</h2>
      <p class="text-sm text-muted">{{ methods?.demo ? 'Live keys are not set, so you can confirm a demo payment after starting checkout.' : 'Approve the prompt on the phone, or finish on Pesapal.' }}</p>
      <div class="grid gap-3 sm:grid-cols-3">
        <button
          v-for="option in methodOptions"
          :key="option.value"
          class="rounded-2xl border px-3 py-3 text-left text-sm"
          :class="method === option.value ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-[var(--line)]'"
          type="button"
          @click="method = option.value"
        >
          <p class="font-semibold">{{ option.label }}</p>
          <p class="mt-1 text-xs text-muted">{{ option.hint }}</p>
        </button>
      </div>
      <FormField
        v-model="phone"
        label="Mobile number"
        :required="method !== 'PESAPAL'"
        placeholder="0770 123 456"
        :error="phoneError"
      />
      <p v-if="payMessage" class="text-sm text-muted">{{ payMessage }}</p>
      <div class="flex flex-wrap gap-2">
        <AppButton type="submit" :loading="paying">{{ method === 'PESAPAL' ? 'Continue to Pesapal' : 'Request payment' }}</AppButton>
        <AppButton v-if="demoPaymentId" variant="secondary" type="button" :loading="paying" @click="confirmDemo">Confirm demo payment</AppButton>
      </div>
    </form>

    <div v-if="auth.isPlatformAdmin.value" class="surface overflow-x-auto rounded-2xl">
      <table class="cms-table">
        <thead>
          <tr>
            <th>Organization</th>
            <th>Plan</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in subscriptions || []" :key="item.id">
            <td>{{ item.organization?.name || item.organization_id }}</td>
            <td>{{ item.plan?.name || '—' }}</td>
            <td>{{ item.status }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <Modal :open="assignOpen" title="Assign subscription plan" @close="assignOpen = false">
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
import { useRouter } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { api } from '@/lib/api';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const router = useRouter();
const queryClient = useQueryClient();
const assignOpen = ref(false);
const saving = ref(false);
const paying = ref(false);
const selectedPlan = ref('');
const method = ref('MTN');
const phone = ref('');
const payMessage = ref('');
const demoPaymentId = ref('');
const form = reactive({ organization_id: '', plan_id: '' });

const methodOptions = [
  { value: 'MTN', label: 'MTN MoMo', hint: 'USSD prompt on the handset' },
  { value: 'AIRTEL', label: 'Airtel Money', hint: 'Approve on the Airtel line' },
  { value: 'PESAPAL', label: 'Pesapal', hint: 'Hosted checkout, cards and mobile money' },
];

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
const { data: methods } = useQuery({
  queryKey: ['payment-methods'],
  queryFn: () => api('/api/v1/payments/methods'),
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
const phoneError = computed(() => {
  if (method.value === 'PESAPAL' && !phone.value) return '';
  if (!phone.value) return method.value === 'PESAPAL' ? '' : 'Enter the paying mobile number.';
  return /^(\+?256|0)?7\d{8}$/.test(phone.value.replace(/\s/g, '')) ? '' : 'Use a Ugandan number such as 0770 123 456.';
});

function priceLabel(plan) {
  if (!plan) return '—';
  const currency = plan.currency || 'UGX';
  if (!plan.price_cents) return 'Free';
  try {
    return `${new Intl.NumberFormat('en-UG', { style: 'currency', currency, maximumFractionDigits: 0 }).format(plan.price_cents)} / ${plan.billing_interval || 'month'}`;
  } catch {
    return `${currency} ${plan.price_cents} / ${plan.billing_interval || 'month'}`;
  }
}

async function pay() {
  if (phoneError.value) return;
  paying.value = true;
  payMessage.value = '';
  demoPaymentId.value = '';
  try {
    const result = await api('/api/v1/payments', {
      method: 'POST',
      body: { plan_id: selectedPlan.value, payment_method: method.value, phone: phone.value },
    });
    payMessage.value = result.message;
    if (result.demo) {
      demoPaymentId.value = result.id;
      await router.push({ path: '/billing/sandbox', query: { payment: result.id, method: method.value } });
      return;
    }
    if (result.checkout_url) {
      window.location.assign(result.checkout_url);
      return;
    }
    toast.success(result.message);
    if (!result.demo) pollStatus(result.id);
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to start this payment.'));
  } finally {
    paying.value = false;
  }
}

async function pollStatus(id) {
  for (let i = 0; i < 12; i += 1) {
    await new Promise((resolve) => setTimeout(resolve, 4000));
    const status = await api(`/api/v1/payments/${id}`);
    if (status.status === 'PAID') {
      toast.success('Payment received. The plan is active.');
      queryClient.invalidateQueries({ queryKey: ['organization-usage'] });
      return;
    }
    if (status.status === 'FAILED') {
      toast.error('The payment was not completed.');
      return;
    }
  }
}

async function confirmDemo() {
  paying.value = true;
  try {
    await api(`/api/v1/payments/${demoPaymentId.value}/demo-confirm`, { method: 'POST', body: {} });
    toast.success('Demo payment confirmed. The plan is active.');
    demoPaymentId.value = '';
    queryClient.invalidateQueries({ queryKey: ['organization-usage'] });
    queryClient.invalidateQueries({ queryKey: ['org-subscriptions'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to confirm the demo payment.'));
  } finally {
    paying.value = false;
  }
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
    assignOpen.value = false;
    queryClient.invalidateQueries({ queryKey: ['org-subscriptions'] });
    queryClient.invalidateQueries({ queryKey: ['organization-usage'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to assign this plan.'));
  } finally {
    saving.value = false;
  }
}
</script>
