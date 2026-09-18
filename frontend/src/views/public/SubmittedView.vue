<template>
  <div class="mx-auto max-w-xl px-4 py-16 text-center">
    <h1 class="text-3xl font-semibold">Complaint submitted</h1>
    <p class="mt-3 text-slate-600">Save these details. The tracking code is required to check status and is not shown again.</p>
    <div class="mt-8 rounded-2xl border border-slate-200 bg-white p-6 print:border-0">
      <p class="text-sm text-slate-500">Complaint reference</p>
      <p class="text-2xl font-semibold">{{ reference }}</p>
      <p class="mt-4 text-sm text-slate-500">Tracking code</p>
      <p class="text-2xl font-semibold tracking-wide">{{ code }}</p>
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
