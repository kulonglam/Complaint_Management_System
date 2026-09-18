<template>
  <section class="space-y-6">
    <PageHeader title="Users" description="People in your organization. New accounts are created through the API using your session organization, never a client-supplied tenant id.">
      <AppButton v-if="auth.can('users:create')" @click="open = true">Invite user</AppButton>
    </PageHeader>
    <LoadingSkeleton v-if="isLoading" />
    <div v-else class="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-left text-xs uppercase text-slate-500">
          <tr>
            <th class="px-4 py-3">Name</th>
            <th class="px-4 py-3">Email</th>
            <th class="px-4 py-3">Department</th>
            <th class="px-4 py-3">Status</th>
            <th class="px-4 py-3">Last login</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id" class="cursor-pointer border-t hover:bg-slate-50" @click="$router.push(`/users/${user.id}`)">
            <td class="px-4 py-3">{{ displayName(user) }}</td>
            <td class="px-4 py-3">{{ user.email }}</td>
            <td class="px-4 py-3">{{ user.department?.name || '—' }}</td>
            <td class="px-4 py-3">{{ user.status }}</td>
            <td class="px-4 py-3">{{ formatDate(user.last_login_at) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <Modal :open="open" title="Invite user" @close="open = false">
      <form class="grid gap-3" @submit.prevent="invite">
        <FormField v-model="form.email" label="Email" type="email" required />
        <FormField v-model="form.first_name" label="First name" required />
        <FormField v-model="form.last_name" label="Last name" required />
        <FormField v-model="form.role_key" label="Role" type="select" :options="roleOptions" required />
        <p class="text-xs text-slate-500">The invited user joins your organization automatically.</p>
        <AppButton type="submit" :loading="saving">Send invite</AppButton>
      </form>
    </Modal>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { displayName, formatDate, getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const saving = ref(false);
const form = reactive({ email: '', first_name: '', last_name: '', role_key: 'investigator' });

const { data: users, isLoading } = useQuery({
  queryKey: ['users'],
  queryFn: async () => {
    const { data, error } = await supabase.from('profiles').select('*, department:departments(name)').order('created_at', { ascending: false });
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

async function invite() {
  saving.value = true;
  try {
    const response = await fetch(`${import.meta.env.VITE_API_URL || ''}/api/users/invite`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`,
      },
      body: JSON.stringify(form),
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.message || 'Invite failed');
    toast.success('User invited');
    open.value = false;
    queryClient.invalidateQueries({ queryKey: ['users'] });
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to invite this user.'));
  } finally {
    saving.value = false;
  }
}
</script>
