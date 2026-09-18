<template>
  <label class="grid gap-1.5 text-sm font-medium text-slate-700">
    {{ label }}
    <input
      v-if="type !== 'textarea' && type !== 'select'"
      :type="type"
      :value="modelValue"
      :required="required"
      :placeholder="placeholder"
      class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal text-slate-900"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <textarea
      v-else-if="type === 'textarea'"
      :value="modelValue"
      :required="required"
      :placeholder="placeholder"
      rows="4"
      class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <select
      v-else
      :value="modelValue"
      :required="required"
      class="rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option value="">{{ placeholder || 'Select' }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <span v-if="error" class="font-normal text-red-600">{{ error }}</span>
    <span v-else-if="hint" class="font-normal text-slate-500">{{ hint }}</span>
  </label>
</template>

<script setup>
defineProps({
  label: String,
  modelValue: [String, Number],
  type: { type: String, default: 'text' },
  required: Boolean,
  placeholder: String,
  options: { type: Array, default: () => [] },
  error: String,
  hint: String,
});
defineEmits(['update:modelValue']);
</script>
