<template>
  <div class="mx-auto max-w-xl px-4 py-16 text-center">
    <p class="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--accent)]">Received</p>
    <h1 class="mt-2 font-display text-4xl">Complaint submitted</h1>
    <p class="mt-3 text-muted">Save both values. The tracking code is required to check status and is not shown again.</p>
    <div class="surface mt-8 rounded-3xl p-6 text-left print:border-0 print:shadow-none">
      <p class="text-sm text-muted">Complaint reference</p>
      <p class="font-display text-3xl">{{ reference }}</p>
      <p class="mt-5 text-sm text-muted">Tracking code</p>
      <p class="font-display text-3xl tracking-wide">{{ code }}</p>
    </div>
    <div class="mt-6 flex justify-center gap-3">
      <AppButton variant="secondary" @click="print">Print confirmation</AppButton>
      <router-link to="/track-complaint"><AppButton>Track this complaint</AppButton></router-link>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import AppButton from '@/components/common/AppButton.vue';

const route = useRoute();
const reference = computed(() => route.query.ref || '—');
const code = computed(() => route.query.code || '—');
function print() {
  window.print();
}
</script>
