<template>
  <section class="space-y-6">
    <PageHeader title="System settings" description="Platform-wide configuration. Organization users cannot see this page." />
    <form class="surface max-w-xl space-y-4 rounded-3xl p-6" @submit.prevent="save">
      <label class="flex items-center gap-2 text-sm">
        <input v-model="maintenance" type="checkbox" /> Maintenance mode
      </label>
      <FormField v-model="supportEmail" label="Support email" type="email" :error="emailError" />
      <FormField v-model="publicMessage" label="Public banner message" type="textarea" />
      <AppButton type="submit" :loading="saving">Save system settings</AppButton>
    </form>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import PageHeader from '@/components/common/PageHeader.vue';
import FormField from '@/components/forms/FormField.vue';
import AppButton from '@/components/common/AppButton.vue';
import { supabase } from '@/lib/supabase';
import { useToast } from '@/composables/useToast';
import { getErrorMessage, isValidEmail } from '@/lib/utils';

const toast = useToast();
const maintenance = ref(false);
const supportEmail = ref('');
const publicMessage = ref('');
const saving = ref(false);
const emailError = computed(() => (supportEmail.value && !isValidEmail(supportEmail.value) ? 'Enter a valid email address.' : ''));

const { data } = useQuery({
  queryKey: ['system-settings'],
  queryFn: async () => {
    const { data, error } = await supabase.from('system_settings').select('*').eq('key', 'app').maybeSingle();
    if (error) throw error;
    return data;
  },
});

watch(data, (row) => {
  const value = row?.value || {};
  maintenance.value = Boolean(value.maintenance);
  supportEmail.value = value.support_email || '';
  publicMessage.value = value.public_message || '';
}, { immediate: true });

async function save() {
  if (emailError.value) return;
  saving.value = true;
  try {
    const { error } = await supabase.from('system_settings').upsert({
      key: 'app',
      value: {
        maintenance: maintenance.value,
        support_email: supportEmail.value,
        public_message: publicMessage.value,
      },
    });
    if (error) throw error;
    toast.success('System settings saved');
  } catch (err) {
    toast.error(getErrorMessage(err, 'Unable to save system settings.'));
  } finally {
    saving.value = false;
  }
}
</script>
