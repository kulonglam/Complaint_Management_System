<template>
  <label class="grid gap-1.5 text-sm font-medium text-ink">
    <span>
      {{ label }}
      <span v-if="required" class="text-[var(--danger)]" aria-hidden="true">*</span>
    </span>
    <input
      v-if="type !== 'textarea' && type !== 'select'"
      :id="fieldId"
      :type="type"
      :value="modelValue"
      :required="required"
      :placeholder="placeholder"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? `${fieldId}-error` : undefined"
      class="field-input font-normal"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <textarea
      v-else-if="type === 'textarea'"
      :id="fieldId"
      :value="modelValue"
      :required="required"
      :placeholder="placeholder"
      :aria-invalid="Boolean(error)"
      rows="4"
      class="field-input font-normal"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <select
      v-else
      :id="fieldId"
      :value="modelValue"
      :required="required"
      :aria-invalid="Boolean(error)"
      class="field-input font-normal"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option value="">{{ placeholder || 'Select' }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <span v-if="error" :id="`${fieldId}-error`" class="font-normal text-[var(--danger)]">{{ error }}</span>
    <span v-else-if="hint" class="font-normal text-muted">{{ hint }}</span>
  </label>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
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

const fieldId = computed(() => `field-${(props.label || 'input').toLowerCase().replace(/[^a-z0-9]+/g, '-')}`);
</script>
