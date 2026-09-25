<template>
  <section class="mx-auto max-w-xl space-y-4">
    <PageHeader title="Payment status" description="Confirming the Pesapal, MTN, or Airtel payment against the provider." />
    <article class="surface rounded-3xl p-6">
      <p class="text-sm text-muted">{{ message }}</p>
      <AppButton class="mt-4" variant="secondary" @click="$router.push('/billing')">Back to billing</AppButton>
    </article>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import { api } from '@/lib/api';
import { useToast } from '@/composables/useToast';

const route = useRoute();
const toast = useToast();
const message = ref('Checking payment…');

onMounted(async () => {
  const tracking = route.query.OrderTrackingId || route.query.orderTrackingId;
  if (!tracking) {
    message.value = 'No payment reference was returned. Open Billing and check the plan status.';
    return;
  }
  try {
    await api('/api/v1/payments/ipn/pesapal?' + new URLSearchParams({ OrderTrackingId: String(tracking) }));
    message.value = 'If the payment succeeded, your plan is now active. You can return to billing.';
    toast.success('Payment status refreshed.');
  } catch {
    message.value = 'The payment could not be confirmed automatically. Return to billing and try again.';
  }
});
</script>
