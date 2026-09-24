<template>
  <ol class="space-y-0">
    <li v-for="(event, index) in ordered" :key="event.id || `${event.event}-${event.at}-${index}`" class="relative border-l-2 border-slate-200 pb-6 pl-5 last:pb-0">
      <span class="absolute -left-[7px] top-1 h-3 w-3 rounded-full bg-blue-600" aria-hidden="true" />
      <p class="font-medium">{{ event.label || event.event }}</p>
      <p class="text-sm text-slate-500">{{ formatDate(event.at || event.created_at) }}</p>
      <p v-if="event.actor" class="text-sm text-slate-600">{{ event.actor }}</p>
      <p v-if="event.description" class="text-sm">{{ event.description }}</p>
    </li>
  </ol>
</template>

<script setup>
import { computed } from 'vue';
import { formatDate } from '@/lib/utils';

const props = defineProps({
  events: { type: Array, default: () => [] },
});

const ordered = computed(() =>
  [...(props.events || [])].sort((a, b) => new Date(a.at || a.created_at) - new Date(b.at || b.created_at))
);
</script>
