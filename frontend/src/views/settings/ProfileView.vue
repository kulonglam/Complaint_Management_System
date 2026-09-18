<template>
  <section class="space-y-6">
    <PageHeader title="Profile" />
    <form class="max-w-xl space-y-4 rounded-2xl border bg-white p-6" @submit.prevent="save">
      <FormField v-model="firstName" label="First name" />
      <FormField v-model="lastName" label="Last name" />
      <FormField v-model="phone" label="Phone" />
      <FormField v-model="jobTitle" label="Job title" />
      <p class="text-sm text-slate-500">Email and organization membership cannot be changed here.</p>
      <AppButton type="submit">Save profile</AppButton>
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
const firstName = ref('');
const lastName = ref('');
const phone = ref('');
const jobTitle = ref('');

watch(
  () => auth.state.profile,
  (profile) => {
    if (!profile) return;
    firstName.value = profile.first_name || '';
    lastName.value = profile.last_name || '';
    phone.value = profile.phone || '';
    jobTitle.value = profile.job_title || '';
  },
  { immediate: true }
);

async function save() {
  const { error } = await supabase
    .from('profiles')
    .update({
      first_name: firstName.value,
      last_name: lastName.value,
      phone: phone.value,
      job_title: jobTitle.value,
    })
    .eq('id', auth.state.profile.id);
  if (error) toast.error(getErrorMessage(error));
  else {
    toast.success('Profile updated');
    auth.refresh();
  }
}
</script>
