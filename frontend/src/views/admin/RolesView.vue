<template>
  <section class="space-y-6">
    <PageHeader title="Roles and permissions" description="Authorization is based on permission keys, not hardcoded role names in the UI.">
      <AppButton v-if="auth.can('roles:create')" @click="open = true">New custom role</AppButton>
    </PageHeader>
    <div class="grid gap-4 lg:grid-cols-2">
      <article v-for="role in roles || []" :key="role.id" class="rounded-2xl border bg-white p-4">
        <div class="flex items-start justify-between gap-3">
          <div>
            <h3 class="font-semibold">{{ role.name }}</h3>
            <p class="text-xs text-slate-500">{{ role.key }}{{ role.is_system ? ' · system' : '' }}</p>
          </div>
          <AppButton v-if="auth.can('roles:update') && !role.is_system" variant="secondary" @click="edit(role)">Edit</AppButton>
        </div>
        <div class="mt-3 flex flex-wrap gap-2">
          <span v-for="item in role.role_permissions" :key="item.permission.key" class="rounded-full bg-slate-100 px-2 py-1 text-xs">
            {{ item.permission.key }}
          </span>
        </div>
      </article>
    </div>
    <Modal :open="open" :title="editing ? 'Edit role' : 'New role'" @close="open = false">
      <form class="grid gap-3" @submit.prevent="save">
        <FormField v-model="form.name" label="Name" required />
        <FormField v-if="!editing" v-model="form.key" label="Key" required />
        <div class="max-h-64 space-y-2 overflow-y-auto text-sm">
          <label v-for="permission in permissions || []" :key="permission.id" class="flex items-center gap-2">
            <input v-model="form.permissionIds" type="checkbox" :value="permission.id" />
            {{ permission.key }}
          </label>
        </div>
        <AppButton type="submit">Save</AppButton>
      </form>
    </Modal>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import Modal from '@/components/common/Modal.vue';
import FormField from '@/components/forms/FormField.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const queryClient = useQueryClient();
const open = ref(false);
const editing = ref(null);
const form = reactive({ name: '', key: '', permissionIds: [] });

const { data: roles } = useQuery({
  queryKey: ['roles-detail'],
  queryFn: async () => {
    const { data, error } = await supabase
      .from('roles')
      .select('id, key, name, is_system, organization_id, role_permissions(permission:permissions(id, key))')
      .order('name');
    if (error) throw error;
    return data;
  },
});
const { data: permissions } = useQuery({
  queryKey: ['permissions'],
  queryFn: async () => {
    const { data, error } = await supabase.from('permissions').select('id, key').order('key');
    if (error) throw error;
    return data;
  },
});

function edit(role) {
  editing.value = role;
  form.name = role.name;
  form.key = role.key;
  form.permissionIds = (role.role_permissions || []).map((item) => item.permission.id);
  open.value = true;
}

async function save() {
  try {
    let roleId = editing.value?.id;
    if (!roleId) {
      const { data, error } = await supabase.from('roles').insert({
        organization_id: auth.state.profile.organization_id,
        key: form.key,
        name: form.name,
        is_system: false,
      }).select('id').single();
      if (error) throw error;
      roleId = data.id;
    } else {
      const { error } = await supabase.from('roles').update({ name: form.name }).eq('id', roleId);
      if (error) throw error;
    }
    await supabase.from('role_permissions').delete().eq('role_id', roleId);
    if (form.permissionIds.length) {
      const { error } = await supabase.from('role_permissions').insert(
        form.permissionIds.map((permission_id) => ({ role_id: roleId, permission_id }))
      );
      if (error) throw error;
    }
    open.value = false;
    editing.value = null;
    queryClient.invalidateQueries({ queryKey: ['roles-detail'] });
    toast.success('Role saved');
  } catch (err) {
    toast.error(getErrorMessage(err));
  }
}
</script>
