<template>
  <div class="min-h-screen bg-paper">
    <header class="sticky top-0 z-40 border-b border-[var(--line)] bg-[var(--surface)]/90 backdrop-blur">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-3.5">
        <router-link to="/" class="text-ink">
          <BrandMark label="Complaint Management" />
        </router-link>
        <nav class="hidden items-center gap-6 text-sm md:flex">
          <router-link to="/submit-complaint" class="text-muted hover:text-ink">Submit</router-link>
          <router-link to="/track-complaint" class="text-muted hover:text-ink">Track</router-link>
          <router-link to="/help" class="text-muted hover:text-ink">Help</router-link>
          <router-link to="/login" class="font-semibold text-[var(--accent)]">Sign in</router-link>
        </nav>
        <button class="rounded-xl p-2 md:hidden" type="button" aria-label="Open menu" @click="open = !open">
          <Menu class="h-5 w-5" />
        </button>
      </div>
      <div v-if="open" class="border-t border-[var(--line)] px-4 py-3 md:hidden">
        <nav class="grid gap-2 text-sm">
          <router-link to="/submit-complaint" @click="open = false">Submit a complaint</router-link>
          <router-link to="/track-complaint" @click="open = false">Track a complaint</router-link>
          <router-link to="/help" @click="open = false">Help</router-link>
          <router-link to="/login" class="font-semibold text-[var(--accent)]" @click="open = false">Sign in</router-link>
        </nav>
      </div>
    </header>
    <router-view />
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { Menu } from 'lucide-vue-next';
import BrandMark from '@/components/common/BrandMark.vue';

const open = ref(false);
</script>
