<template>
  <div class="rounded-2xl border border-[var(--danger)]/20 bg-[#fdecea] p-4 text-sm text-[var(--danger)]">
    {{ message }}
  </div>
</template>

<script setup>
defineProps({
  message: { type: String, default: 'Unable to load this page. Please try again.' },
});
</script>
