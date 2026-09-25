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
          <button class="rounded-xl p-2 hover:bg-[var(--paper)]" type="button" :aria-label="isDark ? 'Switch to light appearance' : 'Switch to dark appearance'" @click="theme.toggleAppearance()">
            <Sun v-if="isDark" class="h-4 w-4" />
            <Moon v-else class="h-4 w-4" />
          </button>
          <router-link to="/login" class="font-semibold text-[var(--accent)]">Sign in</router-link>
        </nav>
        <div class="flex items-center gap-1">
          <button class="rounded-xl p-2 md:hidden" type="button" :aria-label="isDark ? 'Switch to light appearance' : 'Switch to dark appearance'" @click="theme.toggleAppearance()">
            <Sun v-if="isDark" class="h-5 w-5" />
            <Moon v-else class="h-5 w-5" />
          </button>
          <button class="rounded-xl p-2 md:hidden" type="button" aria-label="Open menu" @click="open = !open">
            <Menu class="h-5 w-5" />
          </button>
        </div>
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
    <main id="main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { Menu, Moon, Sun } from 'lucide-vue-next';
import BrandMark from '@/components/common/BrandMark.vue';
import { useTheme } from '@/composables/useTheme';

const open = ref(false);
const theme = useTheme();
const isDark = computed(() => theme.state.appearance === 'dark' || (theme.state.appearance === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches));
</script>
