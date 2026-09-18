<template>
  <section class="space-y-6">
    <PageHeader title="Organization settings" />
    <form class="max-w-xl space-y-4 rounded-2xl border bg-white p-6" @submit.prevent="save">
      <FormField v-model="name" label="Organization name" />
      <FormField v-model="email" label="Contact email" type="email" />
      <FormField v-model="phone" label="Phone" />
      <FormField v-model="address" label="Address" type="textarea" />
      <AppButton type="submit" :disabled="!auth.can('settings:update')">Save settings</AppButton>
    </form>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useAuth } from '@/composables/useAuth';
import { useToast } from '@/composables/useToast';
import { getErrorMessage } from '@/lib/utils';

const auth = useAuth();
const toast = useToast();
const name = ref('');
const email = ref('');
const phone = ref('');
const address = ref('');

watch(
  () => auth.state.organization,
  (org) => {
    if (!org) return;
    name.value = org.name || '';
    email.value = org.email || '';
    phone.value = org.phone || '';
    address.value = org.address || '';
  },
  { immediate: true }
);

async function save() {
  const { error } = await supabase
    .from('organizations')
    .update({ name: name.value, email: email.value, phone: phone.value, address: address.value })
    .eq('id', auth.state.profile.organization_id);
  if (error) toast.error(getErrorMessage(error));
  else {
    toast.success('Settings saved');
    auth.refresh();
  }
}
</script>
