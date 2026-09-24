<template>
  <div class="mx-auto max-w-2xl px-4 py-12">
    <h1 class="text-3xl font-semibold">Track a complaint</h1>
    <p class="mt-2 text-slate-600">Enter the reference number and tracking code issued when the complaint was submitted.</p>
    <form class="mt-8 grid gap-4 rounded-2xl border border-slate-200 bg-white p-6" @submit.prevent="onSubmit">
      <FormField v-model="reference" label="Complaint reference" placeholder="CMP-2026-000001" required />
      <FormField v-model="code" label="Tracking code" placeholder="8F7K-92LM" required />
      <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
      <AppButton type="submit" :loading="loading">Track</AppButton>
    </form>

    <div v-if="result" class="mt-8 space-y-6 rounded-2xl border border-slate-200 bg-white p-6">
      <div>
        <h2 class="text-lg font-semibold">{{ result.reference_number }}</h2>
        <p class="text-sm text-slate-500">{{ result.title }}</p>
      </div>
      <dl class="grid gap-2 text-sm">
        <div class="flex justify-between"><dt>Status</dt><dd><StatusBadge :value="result.status" /></dd></div>
        <div class="flex justify-between"><dt>Priority</dt><dd><PriorityBadge :value="result.priority" /></dd></div>
        <div class="flex justify-between"><dt>Category</dt><dd>{{ result.category || '—' }}{{ result.subcategory ? ` / ${result.subcategory}` : '' }}</dd></div>
        <div class="flex justify-between"><dt>Submitted</dt><dd>{{ formatDate(result.submitted_at) }}</dd></div>
        <div class="flex justify-between"><dt>Last updated</dt><dd>{{ formatDate(result.updated_at) }}</dd></div>
      </dl>
      <p v-if="result.resolution_summary" class="text-sm text-slate-600">{{ result.resolution_summary }}</p>

      <div v-if="(result.timeline || []).length">
        <h3 class="font-semibold">Timeline</h3>
        <ol class="mt-2 space-y-2 border-l pl-4 text-sm">
          <li v-for="(item, index) in result.timeline" :key="index">
            <p class="font-medium">{{ STATUS_LABELS[item.status] || item.status }}</p>
            <p class="text-slate-500">{{ formatDate(item.created_at) }}</p>
          </li>
        </ol>
      </div>

      <div v-if="(result.comments || []).length">
        <h3 class="font-semibold">Updates</h3>
        <ul class="mt-2 space-y-2 text-sm">
          <li v-for="(item, index) in result.comments" :key="index" class="rounded-lg border p-3">
            <p>{{ item.content }}</p>
            <p class="mt-1 text-xs text-slate-500">{{ formatDate(item.created_at) }}</p>
          </li>
        </ul>
      </div>

      <div v-if="(result.attachments || []).length">
        <h3 class="font-semibold">Files</h3>
        <ul class="mt-2 space-y-1 text-sm">
          <li v-for="file in result.attachments" :key="file.id">
            <button class="text-blue-700" type="button" @click="openFile(file.file_path)">{{ file.file_name }}</button>
          </li>
        </ul>
      </div>

      <form v-if="result.can_feedback" class="grid gap-3 rounded-xl bg-slate-50 p-4" @submit.prevent="sendFeedback">
        <h3 class="font-semibold">How was the resolution?</h3>
        <FormField v-model="rating" label="Rating" type="select" :options="ratingOptions" required />
        <FormField v-model="comment" label="Comment" type="textarea" />
        <AppButton type="submit" :loading="savingFeedback">Submit feedback</AppButton>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import AppButton from '@/components/common/AppButton.vue';
import FormField from '@/components/forms/FormField.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import { STATUS_LABELS } from '@/lib/constants';
import { attachmentUrl, submitPublicFeedback, trackPublicComplaint } from '@/services/complaint.service';
import { formatDate, getErrorMessage } from '@/lib/utils';

const reference = ref('');
const code = ref('');
const result = ref(null);
const error = ref('');
const loading = ref(false);
const rating = ref('5');
const comment = ref('');
const savingFeedback = ref(false);
const ratingOptions = [1, 2, 3, 4, 5].map((value) => ({ value: String(value), label: `${value} / 5` }));

async function onSubmit() {
  error.value = '';
  loading.value = true;
  result.value = null;
  try {
    result.value = await trackPublicComplaint(reference.value, code.value);
  } catch (err) {
    error.value = getErrorMessage(err, 'No complaint matched those details.');
  } finally {
    loading.value = false;
  }
}

async function openFile(path) {
  const url = await attachmentUrl(path);
  window.open(url, '_blank');
}

async function sendFeedback() {
  savingFeedback.value = true;
  try {
    await submitPublicFeedback(reference.value, code.value, Number(rating.value), comment.value);
    result.value = { ...result.value, can_feedback: false };
  } catch (err) {
    error.value = getErrorMessage(err, 'Unable to submit feedback.');
  } finally {
    savingFeedback.value = false;
  }
}
</script>
