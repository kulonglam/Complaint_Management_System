<template>
  <section class="mx-auto max-w-lg space-y-4">
    <PageHeader :title="title" description="Local test only. No money is sent to MTN, Airtel, or Pesapal." />
    <article class="surface space-y-4 rounded-3xl p-6">
      <p class="text-sm text-muted">{{ copy }}</p>
      <p v-if="amountLabel" class="font-display text-2xl">{{ amountLabel }}</p>
      <p v-if="error" class="text-sm text-[var(--danger)]">{{ error }}</p>
      <div class="flex flex-wrap gap-2">
        <AppButton :loading="saving" @click="approve">Approve payment</AppButton>
        <AppButton variant="secondary" :loading="saving" @click="decline">Decline</AppButton>
      </div>
    </article>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import { api } from '@/lib/api';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const titles = {
  MTN: 'MTN Mobile Money',
  AIRTEL: 'Airtel Money',
  PESAPAL: 'Pesapal checkout',
};

const route = useRoute();
const router = useRouter();
const toast = useToast();
const saving = ref(false);
const error = ref('');
const amount = ref(0);
const currency = ref('UGX');
const method = computed(() => String(route.query.method || 'PESAPAL').toUpperCase());
const title = computed(() => titles[method.value] || 'Local payment');
const amountLabel = computed(() => {
  if (!amount.value) return '';
  try {
    return new Intl.NumberFormat('en-UG', { style: 'currency', currency: currency.value, maximumFractionDigits: 0 }).format(amount.value);
  } catch {
    return `${currency.value} ${amount.value}`;
  }
});
const copy = computed(() => {
  if (method.value === 'MTN') return 'This stands in for the MTN USSD prompt. Approve to activate the plan.';
  if (method.value === 'AIRTEL') return 'This stands in for the Airtel Money PIN prompt. Approve to activate the plan.';
  return 'This stands in for the Pesapal hosted page. Approve as if the customer paid with mobile money or a card.';
});

onMounted(async () => {
  if (!route.query.payment) {
    error.value = 'Missing payment reference. Start again from Billing.';
    return;
  }
  try {
    const status = await api(`/api/v1/payments/${route.query.payment}`);
    amount.value = status.amount || 0;
    currency.value = status.currency || 'UGX';
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to load this payment.');
  }
});

async function approve() {
  saving.value = true;
  try {
    await api(`/api/v1/payments/${route.query.payment}/demo-confirm`, { method: 'POST', body: {} });
    toast.success(`${title.value} approved. The plan is active.`);
    await router.push('/billing');
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to approve this local payment.');
  } finally {
    saving.value = false;
  }
}

async function decline() {
  toast.info('Payment declined. No plan change was made.');
  await router.push('/billing');
}
</script>
