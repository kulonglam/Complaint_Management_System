<template>
  <section class="space-y-6">
    <PageHeader title="Notifications">
      <AppButton variant="secondary" @click="markAll">Mark all read</AppButton>
    </PageHeader>
    <EmptyState v-if="!items.length" title="No notifications" message="Assignment, SLA, and status events will appear here." />
    <ul v-else class="space-y-3">
      <li v-for="item in items" :key="item.id" class="surface rounded-2xl p-4" :class="item.read_at ? 'opacity-70' : ''">
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="font-medium">{{ item.title }}</p>
            <p class="text-sm text-muted">{{ item.message }}</p>
            <router-link v-if="item.related_complaint_id" class="text-sm font-semibold text-[var(--accent)]" :to="`/complaints/${item.related_complaint_id}`">Open complaint</router-link>
          </div>
          <AppButton v-if="!item.read_at" variant="secondary" @click="markRead(item.id)">Mark read</AppButton>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup>
import PageHeader from '@/components/common/PageHeader.vue';
import AppButton from '@/components/common/AppButton.vue';
import EmptyState from '@/components/common/EmptyState.vue';
import { useNotifications } from '@/composables/useNotifications';

const { items, markRead, markAll } = useNotifications();
</script>
