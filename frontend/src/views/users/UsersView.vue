<template>
  <section class="space-y-6">
    <PageHeader title="Users" description="People in your organization. New accounts are created through the API using your session organization, never a client-supplied tenant id.">
      <AppButton v-if="auth.can('users:create')" @click="startInvite">Invite user</AppButton>
    </PageHeader>
    <article v-if="invitePassword || inviteWarning" class="surface space-y-2 rounded-2xl p-4">
      <p v-if="inviteWarning" class="text-sm text-[var(--danger)]">{{ inviteWarning }}</p>
      <div v-if="invitePassword">
        <p class="text-xs text-muted">Share this temporary password. Copy it now — it is not shown again.</p>
        <p class="mt-1 font-mono text-sm break-all">{{ invitePassword }}</p>
      </div>
      <AppButton variant="secondary" type="button" @click="clearInviteResult">Dismiss</AppButton>
    </article>
    <LoadingSkeleton v-if="isLoading" />
    <EmptyState v-else-if="!(users || []).length" title="No users found" message="Invite a staff member to get started.">
      <AppButton v-if="auth.can('users:create')" @click="startInvite">Invite user</AppButton>
    </EmptyState>
    <DataTable
      v-else
      :columns="columns"
      :rows="users || []"
      clickable
      @row-click="$router.push(`/users/${$event.id}`)"
    >
      <template #name="{ row }">{{ displayName(row) }}</template>
      <template #department="{ row }">{{ row.department?.name || '—' }}</template>
      <template #role="{ row }">{{ row.user_roles?.[0]?.role?.name || '—' }}</template>
      <template #last_login_at="{ row }">{{ formatDate(row.last_login_at) }}</template>
      <template #actions="{ row }">
        <div class="flex gap-2" @click.stop>
          <AppButton variant="secondary" @click="$router.push(`/users/${row.id}`)">Edit</AppButton>
          <AppButton
            v-if="auth.can('users:disable') && row.status === 'ACTIVE'"
            variant="danger"
            @click="askDeactivate(row)"
          >
            Deactivate
          </AppButton>
        </div>
      </template>
      <template #card="{ row }">
        <p class="font-semibold">{{ displayName(row) }}</p>
        <p class="text-sm text-muted">{{ row.email }} · {{ row.user_roles?.[0]?.role?.name || 'No role' }}</p>
      </template>
    </DataTable>
    <Modal :open="open" title="Invite user" @close="open = false">
      <form class="grid gap-3" @submit.prevent="invite">
        <FormField v-model="form.email" label="Email" type="email" required :error="emailError" />
        <FormField v-model="form.first_name" label="First name" required />
        <FormField v-model="form.last_name" label="Last name" required />
        <FormField v-model="form.role_key" label="Role" type="select" :options="roleOptions" required />
        <p class="text-xs text-muted">The invited user joins your organization automatically.</p>
        <AppButton type="submit" :loading="saving">Send invite</AppButton>
      </form>
    </Modal>
    <ConfirmationDialog
      :open="Boolean(pendingUser)"
      title="Deactivate this account?"
      message="The user will immediately lose access to the organization workspace. You can reactivate them later from their profile."
      confirm-label="Deactivate"
      :loading="deactivating"
      @cancel="pendingUser = null"
      @confirm="deactivate"
    />
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import DataTable from '@/components/common/DataTable.vue';
import Modal from '@/components/common/Modal.vue';
import ConfirmationDialog from '@/components/common/ConfirmationDialog.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { api } from '@/lib/api';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { displayName, formatDate, getErrorMessage, isValidEmail } from '@/lib/utils';

const columns = [
  { key: 'name', label: 'Name' },
  { key: 'email', label: 'Email' },
  { key: 'department', label: 'Department' },
  { key: 'role', label: 'Role' },
  { key: 'status', label: 'Status' },
  { key: 'last_login_at', label: 'Last login' },
  { key: 'actions', label: 'Actions' },
];

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const saving = ref(false);
const deactivating = ref(false);
const pendingUser = ref(null);
const form = reactive({ email: '', first_name: '', last_name: '', role_key: 'investigator' });
const invitePassword = ref('');
const inviteWarning = ref('');
const emailError = computed(() => (form.email && !isValidEmail(form.email) ? 'Enter a valid email address.' : ''));

const { data: users, isLoading } = useQuery({
  queryKey: ['users'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('profiles')
      .select('*, department:departments!profiles_department_id_fkey(name), user_roles(role:roles(name, key))')
      .order('created_at', { ascending: false });
    if (error) throw error;
    return data;
  },
});

const { data: roles } = useQuery({
  queryKey: ['roles'],
  queryFn: async () => {
    const { data, error } = await supabase.from('roles').select('key, name').not('organization_id', 'is', null);
    if (error) throw error;
    return data;
  },
});
const roleOptions = computed(() => (roles.value || []).map((role) => ({ value: role.key, label: role.name })));

function clearInviteResult() {
  invitePassword.value = '';
  inviteWarning.value = '';
}

function startInvite() {
  clearInviteResult();
  form.email = '';
  form.first_name = '';
  form.last_name = '';
  form.role_key = 'investigator';
  open.value = true;
}

function askDeactivate(user) {
  pendingUser.value = user;
}

async function deactivate() {
  deactivating.value = true;
  try {
    const { error } = await supabase.from('profiles').update({ status: 'INACTIVE' }).eq('id', pendingUser.value.id);
    if (error) throw error;
    toast.success('User deactivated');
    pendingUser.value = null;
    queryClient.invalidateQueries({ queryKey: ['users'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to deactivate this user.'));
  } finally {
    deactivating.value = false;
  }
}

async function invite() {
  if (emailError.value) return;
  saving.value = true;
  invitePassword.value = '';
  inviteWarning.value = '';
  try {
    const result = await api('/api/v1/users', {
      method: 'POST',
      body: {
        email: form.email.trim(),
        first_name: form.first_name.trim(),
        last_name: form.last_name.trim(),
        role_key: form.role_key,
      },
    });
    const password = result.temporary_password || result.temporaryPassword || '';
    const warning = result.email_warning || result.emailWarning || '';
    invitePassword.value = password;
    inviteWarning.value = warning;
    toast.success(warning
      ? 'User created. Copy the temporary password — the email was not delivered.'
      : 'User invited');
    open.value = false;
    queryClient.invalidateQueries({ queryKey: ['users'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to invite this user.'));
  } finally {
    saving.value = false;
  }
}
</script>
