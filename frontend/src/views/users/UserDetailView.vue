<template>
  <section class="space-y-6">
    <PageHeader :title="displayName(user)" :description="user?.email" />
    <form v-if="user" class="max-w-xl space-y-4 rounded-2xl border border-slate-200 bg-white p-6 text-sm" @submit.prevent="save">
      <p>Email: {{ user.email }}</p>
      <p>Last login: {{ formatDate(user.last_login_at) }}</p>
      <FormField v-model="jobTitle" label="Job title" />
      <FormField v-model="departmentId" label="Department" type="select" :options="departmentOptions" />
      <FormField v-model="status" label="Status" type="select" :options="statusOptions" />
      <FormField v-model="roleId" label="Role" type="select" :options="roleOptions" />
      <div class="flex flex-wrap gap-2">
        <AppButton type="submit" :disabled="!auth.can('users:update') && !auth.can('users:disable')">Save user</AppButton>
        <AppButton
          v-if="auth.can('users:update')"
          variant="secondary"
          type="button"
          :loading="resetting"
          @click="resetAccess"
        >
          Reset access
        </AppButton>
        <AppButton
          v-if="auth.can('users:disable') && status === 'ACTIVE'"
          variant="danger"
          type="button"
          @click="confirmDeactivate = true"
        >
          Deactivate
        </AppButton>
      </div>
    </form>
    <ConfirmationDialog
      :open="confirmDeactivate"
      title="Deactivate this account?"
      message="The user will lose access immediately. You can set the status back to Active later."
      confirm-label="Deactivate"
      @cancel="confirmDeactivate = false"
      @confirm="deactivate"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import ConfirmationDialog from '@/components/common/ConfirmationDialog.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { displayName, formatDate, getErrorMessage } from '@/lib/utils';

const route = useRoute();
const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const jobTitle = ref('');
const departmentId = ref('');
const status = ref('ACTIVE');
const roleId = ref('');
const confirmDeactivate = ref(false);
const resetting = ref(false);
const statusOptions = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'INVITED', label: 'Invited' },
];

const { data: user } = useQuery({
  queryKey: ['user', route.params.id],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('profiles')
      .select('*, department:departments!profiles_department_id_fkey(name), user_roles(role_id, role:roles(id, name))')
      .eq('id', route.params.id)
      .single();
    if (error) throw error;
    return data;
  },
});
const { data: departments } = useQuery({
  queryKey: ['departments'],
  queryFn: async () => {
    const { data, error } = await supabase.from('departments').select('id, name');
    if (error) throw error;
    return data;
  },
});
const { data: roles } = useQuery({
  queryKey: ['roles'],
  queryFn: async () => {
    const { data, error } = await supabase.from('roles').select('id, name').not('organization_id', 'is', null);
    if (error) throw error;
    return data;
  },
});

const departmentOptions = computed(() => [{ value: '', label: 'None' }, ...(departments.value || []).map((item) => ({ value: item.id, label: item.name }))]);
const roleOptions = computed(() => (roles.value || []).map((item) => ({ value: item.id, label: item.name })));

watch(user, (value) => {
  if (!value) return;
  jobTitle.value = value.job_title || '';
  departmentId.value = value.department_id || '';
  status.value = value.status;
  roleId.value = value.user_roles?.[0]?.role_id || '';
}, { immediate: true });

async function save() {
  const { error } = await supabase.from('profiles').update({
    job_title: jobTitle.value,
    department_id: departmentId.value || null,
    status: status.value,
  }).eq('id', user.value.id);
  if (error) return toast.error(getErrorMessage(error));
  if (roleId.value && auth.can('users:update')) {
    await supabase.from('user_roles').delete().eq('user_id', user.value.id);
    const { error: roleError } = await supabase.from('user_roles').insert({
      user_id: user.value.id,
      role_id: roleId.value,
      organization_id: user.value.organization_id,
    });
    if (roleError) return toast.error(getErrorMessage(roleError));
  }
  toast.success('User updated');
  queryClient.invalidateQueries({ queryKey: ['user', route.params.id] });
  queryClient.invalidateQueries({ queryKey: ['users'] });
}

async function deactivate() {
  status.value = 'INACTIVE';
  confirmDeactivate.value = false;
  await save();
}

async function resetAccess() {
  resetting.value = true;
  try {
    const response = await fetch(`${import.meta.env.VITE_API_URL || ''}/api/users/reset-access`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
      },
      body: JSON.stringify({ user_id: user.value.id }),
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.message || 'Reset failed');
    toast.success(payload.temporary_password
      ? `Access reset. Temporary password: ${payload.temporary_password}`
      : 'Access reset and emailed if mail is configured.');
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to reset access for this user.'));
  } finally {
    resetting.value = false;
  }
}
</script>
