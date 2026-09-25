<template>
  <section class="space-y-6">
    <PageHeader title="Complaints" description="Search, filter, and open cases. Results are limited to your organization and permissions.">
      <AppButton v-if="auth.can('complaints:create')" @click="$router.push('/complaints/new')">Create complaint</AppButton>
    </PageHeader>
    <ComplaintFilters
      v-model="filters"
      :departments="departments || []"
      :categories="categories || []"
      :staff="staff || []"
      :show-officer="route.name !== 'my-complaints'"
    />
    <LoadingSkeleton v-if="isLoading" />
    <ErrorState v-else-if="isError" :message="getErrorMessage(error)" />
    <EmptyState v-else-if="!items.length" title="No complaints found" message="Try changing your filters or submit a new complaint.">
      <AppButton v-if="auth.can('complaints:create')" @click="$router.push('/complaints/new')">Submit a complaint</AppButton>
    </EmptyState>
    <DataTable
      v-else
      :columns="columns"
      :rows="items"
      :sort="filters.sort"
      :ascending="filters.ascending"
      :total="total"
      clickable
      @sort="toggleSort"
      @row-click="$router.push(`/complaints/${$event.id}`)"
    >
      <template #priority="{ row }"><PriorityBadge :value="row.priority" /></template>
      <template #status="{ row }"><StatusBadge :value="row.status" /></template>
      <template #assignee="{ row }">{{ displayName(row.assignee) }}</template>
      <template #due_date="{ row }">{{ formatDate(row.due_date, false) }}</template>
      <template #created_at="{ row }">{{ formatDate(row.created_at, false) }}</template>
      <template #category="{ row }">{{ row.category?.name || '—' }}</template>
      <template #department="{ row }">{{ row.department?.name || '—' }}</template>
      <template #card="{ row }">
        <div class="flex items-start justify-between gap-2">
          <div>
            <p class="text-xs text-slate-500">{{ row.reference_number }}</p>
            <h3 class="font-semibold">{{ row.title }}</h3>
          </div>
          <StatusBadge :value="row.status" />
        </div>
        <p class="mt-2 text-sm text-slate-500">{{ row.department?.name || 'Unassigned' }} · {{ formatDate(row.created_at, false) }}</p>
      </template>
      <template #pagination>
        <AppButton variant="secondary" :disabled="page === 1" @click="page -= 1">Previous</AppButton>
        <AppButton variant="secondary" :disabled="page * Number(filters.pageSize) >= total" @click="page += 1">Next</AppButton>
      </template>
    </DataTable>
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
import DataTable from '@/components/common/DataTable.vue';
import StatusBadge from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import ComplaintFilters from '@/components/complaints/ComplaintFilters.vue';
import { fetchComplaints } from '@/services/complaint.service';
import { displayName, formatDate, getErrorMessage } from '@/lib/utils';
import { useAuth } from '@/composables/useAuth';
import { supabase } from '@/lib/supabase';

const columns = [
  { key: 'reference_number', label: 'Reference', sortable: true },
  { key: 'title', label: 'Title', sortable: true },
  { key: 'category', label: 'Category' },
  { key: 'priority', label: 'Priority', sortable: true },
  { key: 'department', label: 'Department' },
  { key: 'assignee', label: 'Assigned officer' },
  { key: 'status', label: 'Status', sortable: true },
  { key: 'due_date', label: 'Due date', sortable: true },
  { key: 'created_at', label: 'Created date', sortable: true },
];

const auth = useAuth();
const route = useRoute();
const page = ref(1);
const filters = reactive({
  search: route.query.q?.toString() || '',
  status: '',
  priority: '',
  departmentId: '',
  categoryId: '',
  assignedTo: '',
  from: '',
  to: '',
  overdue: route.query.overdue === '1',
  pageSize: 12,
  sort: 'created_at',
  ascending: false,
});

watch(() => route.query.q, (value) => {
  if (value != null) filters.search = String(value);
});
watch(() => route.query.overdue, (value) => {
  filters.overdue = value === '1';
});
watch(() => [filters.search, filters.status, filters.priority, filters.departmentId, filters.categoryId, filters.assignedTo, filters.from, filters.to, filters.overdue, filters.pageSize], () => {
  page.value = 1;
});

function toggleSort(key) {
  if (filters.sort === key) filters.ascending = !filters.ascending;
  else {
    filters.sort = key;
    filters.ascending = false;
  }
}

const { data: departments } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error } = await supabase.from('departments').select('id, name').eq('status', 'ACTIVE');
    if (error) throw error;
    return data;
  },
});
const { data: categories } = useQuery({
  queryKey: ['categories-lite'],
  queryFn: async () => {
    const { data, error } = await supabase.from('complaint_categories').select('id, name').eq('status', 'ACTIVE');
    if (error) throw error;
    return data;
  },
});
const { data: staff } = useQuery({
  queryKey: ['assignable-users'],
  queryFn: async () => {
    const { data, error } = await supabase.from('profiles').select('id, first_name, last_name, email').eq('status', 'ACTIVE');
    if (error) throw error;
    return data;
  },
});

const queryKey = computed(() => ['complaints', { ...filters, page: page.value, mine: route.name === 'my-complaints' }]);
const { data, isLoading, isError, error } = useQuery({
  queryKey,
  queryFn: () => fetchComplaints({
    page: page.value,
    pageSize: Number(filters.pageSize),
    search: filters.search,
    status: filters.status,
    priority: filters.priority,
    departmentId: filters.departmentId || undefined,
    categoryId: filters.categoryId || undefined,
    assignedTo: route.name === 'my-complaints' ? auth.state.profile?.id : (filters.assignedTo || undefined),
    from: filters.from ? new Date(filters.from).toISOString() : undefined,
    to: filters.to ? new Date(`${filters.to}T23:59:59`).toISOString() : undefined,
    overdue: filters.overdue || undefined,
    sort: filters.sort,
    ascending: filters.ascending,
  }),
});
const items = computed(() => data.value?.items || []);
const total = computed(() => data.value?.total || 0);
</script>
