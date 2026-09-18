<template>
  <div class="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
    {{ message }}
  </div>
</template>

<script setup>
defineProps({
  message: { type: String, default: 'Unable to load this page. Please try again.' },
});
</script>
