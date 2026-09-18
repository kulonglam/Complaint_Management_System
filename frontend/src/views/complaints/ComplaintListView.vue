<template>
  <section class="space-y-6">
    <PageHeader title="Complaints" description="Search, filter, and open cases. Results are limited to your organization and permissions.">
      <AppButton v-if="auth.can('complaints:create')" @click="$router.push('/complaints/new')">Create complaint</AppButton>
    </PageHeader>
    <div class="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 md:grid-cols-5">
      <input v-model="filters.search" class="rounded-lg border border-slate-300 px-3 py-2 text-sm md:col-span-2" placeholder="Search reference, title, description" />
      <select v-model="filters.status" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="">All statuses</option>
        <option v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <select v-model="filters.priority" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="">All priorities</option>
        <option v-for="option in PRIORITY_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <select v-model="filters.pageSize" class="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option :value="12">12 / page</option>
        <option :value="25">25 / page</option>
        <option :value="50">50 / page</option>
      </select>
    </div>
    <LoadingSkeleton v-if="isLoading" />
    <ErrorState v-else-if="isError" :message="getErrorMessage(error)" />
    <EmptyState v-else-if="!items.length" title="No complaints found" message="Try changing your filters or submit a new complaint.">
      <AppButton v-if="auth.can('complaints:create')" @click="$router.push('/complaints/new')">Submit a complaint</AppButton>
    </EmptyState>
    <div v-else class="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <div class="hidden overflow-x-auto md:block">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th class="px-4 py-3">Reference</th>
              <th class="px-4 py-3">Title</th>
              <th class="px-4 py-3">Category</th>
              <th class="px-4 py-3">Priority</th>
              <th class="px-4 py-3">Department</th>
              <th class="px-4 py-3">Assigned</th>
              <th class="px-4 py-3">Status</th>
              <th class="px-4 py-3">Due</th>
              <th class="px-4 py-3">Created</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id" class="cursor-pointer border-t hover:bg-slate-50" @click="$router.push(`/complaints/${item.id}`)">
              <td class="px-4 py-3 font-medium">{{ item.reference_number }}</td>
              <td class="px-4 py-3">{{ item.title }}</td>
              <td class="px-4 py-3">{{ item.category?.name || '—' }}</td>
              <td class="px-4 py-3"><PriorityBadge :value="item.priority" /></td>
              <td class="px-4 py-3">{{ item.department?.name || '—' }}</td>
              <td class="px-4 py-3">{{ displayName(item.assignee) }}</td>
              <td class="px-4 py-3"><StatusBadge :value="item.status" /></td>
              <td class="px-4 py-3">{{ formatDate(item.due_date, false) }}</td>
              <td class="px-4 py-3">{{ formatDate(item.created_at, false) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="space-y-3 p-4 md:hidden">
        <article v-for="item in items" :key="item.id" class="rounded-xl border border-slate-200 p-4" @click="$router.push(`/complaints/${item.id}`)">
          <div class="flex items-start justify-between gap-2">
            <div>
              <p class="text-xs text-slate-500">{{ item.reference_number }}</p>
              <h3 class="font-semibold">{{ item.title }}</h3>
            </div>
            <StatusBadge :value="item.status" />
          </div>
          <p class="mt-2 text-sm text-slate-500">{{ item.department?.name || 'Unassigned' }} · {{ formatDate(item.created_at, false) }}</p>
        </article>
      </div>
      <div class="flex items-center justify-between border-t px-4 py-3 text-sm">
        <p>{{ total }} records</p>
        <div class="flex gap-2">
          <AppButton variant="secondary" :disabled="page === 1" @click="page -= 1">Previous</AppButton>
          <AppButton variant="secondary" :disabled="page * Number(filters.pageSize) >= total" @click="page += 1">Next</AppButton>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import { fetchComplaints } from '@/services/complaint.service';
import { STATUS_OPTIONS, PRIORITY_OPTIONS } from '@/lib/constants';
import { displayName, formatDate, getErrorMessage } from '@/lib/utils';
import { useAuth } from '@/composables/useAuth';

const auth = useAuth();
const route = useRoute();
const page = ref(1);
const filters = reactive({
  search: route.query.q?.toString() || '',
  status: '',
  priority: '',
  pageSize: 12,
});

watch(() => filters.search, () => { page.value = 1; });

const queryKey = computed(() => ['complaints', { ...filters, page: page.value, assignedTo: route.meta.mine ? auth.state.profile?.id : null }]);
const { data, isLoading, isError, error } = useQuery({
  queryKey,
  queryFn: () => fetchComplaints({
    page: page.value,
    pageSize: Number(filters.pageSize),
    search: filters.search,
    status: filters.status,
    priority: filters.priority,
    assignedTo: route.name === 'my-complaints' ? auth.state.profile?.id : undefined,
  }),
});
const items = computed(() => data.value?.items || []);
const total = computed(() => data.value?.total || 0);
</script>
