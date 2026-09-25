<template>
  <div class="min-h-screen bg-paper">
    <div v-if="mobileOpen" class="fixed inset-0 z-30 bg-[#161310]/40 lg:hidden" @click="mobileOpen = false" />
    <aside
      :class="[
        'fixed inset-y-0 left-0 z-40 flex h-svh w-72 flex-col transform bg-[var(--sidebar)] text-[#d8d0c3] transition lg:translate-x-0',
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
      ]"
    >
      <div class="shrink-0 border-b border-white/10 px-5 py-5">
        <BrandMark />
        <p class="mt-3 font-display text-lg leading-tight text-white">{{ auth.state.organization?.name || 'Complaint System' }}</p>
        <p class="mt-1 text-xs text-[#9c9488]">{{ displayName(auth.state.profile) }}</p>
      </div>
      <nav class="min-h-0 flex-1 space-y-6 overflow-y-auto overflow-x-hidden p-4 pb-8">
        <section v-for="group in nav" :key="group.label">
          <p class="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.16em] text-[#8a8276]">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm hover:bg-white/10 hover:text-white"
            active-class="bg-white/10 text-white"
            @click="mobileOpen = false"
          >
            <component :is="item.icon" class="h-4 w-4 opacity-80" />
            {{ item.label }}
          </router-link>
        </section>
      </nav>
      <div class="shrink-0 border-t border-white/10 p-4">
        <AppButton variant="ghost" type="button" class="w-full justify-start text-[#d8d0c3] hover:bg-white/10 hover:text-white" @click="logout">
          Sign out
        </AppButton>
      </div>
    </aside>

    <div class="lg:pl-72">
      <header class="sticky top-0 z-50 flex items-center justify-between gap-3 border-b border-[var(--line)] bg-[var(--surface)]/90 px-4 py-3 backdrop-blur">
        <button class="rounded-xl p-2 lg:hidden" type="button" @click="mobileOpen = true" aria-label="Open menu">
          <Menu class="h-5 w-5" />
        </button>
        <div class="min-w-0 flex-1">
          <p class="text-xs text-muted">{{ crumb }}</p>
          <form class="mt-1 hidden max-w-xl md:block" @submit.prevent="goSearch">
            <label class="sr-only" for="global-search">Search complaints</label>
            <div class="relative">
              <Search class="absolute left-3 top-2.5 h-4 w-4 text-muted" />
              <input
                id="global-search"
                v-model="search"
                class="field-input pl-9"
                placeholder="Search reference, title, category, or department"
              />
            </div>
          </form>
        </div>
        <div class="ml-auto flex items-center gap-1">
          <button class="hidden rounded-xl px-2 py-1 text-xs text-muted hover:bg-[var(--paper)] md:inline" type="button" aria-label="Open command palette" @click="openPalette">
            ⌘K
          </button>
          <button class="rounded-xl p-2 hover:bg-[var(--paper)]" type="button" :aria-label="isDark ? 'Switch to light appearance' : 'Switch to dark appearance'" @click="theme.toggleAppearance()">
            <Sun v-if="isDark" class="h-5 w-5" />
            <Moon v-else class="h-5 w-5" />
          </button>
          <button class="rounded-xl p-2 text-xs font-semibold hover:bg-[var(--paper)]" type="button" @click="theme.setDensity(theme.state.density === 'compact' ? 'comfortable' : 'compact')">
            {{ theme.state.density === 'compact' ? 'Cozy' : 'Compact' }}
          </button>
          <router-link to="/notifications" class="relative rounded-xl p-2 hover:bg-[var(--paper)]" aria-label="Notifications">
            <Bell class="h-5 w-5" />
            <span
              v-if="notifications.unreadCount.value"
              class="absolute right-1 top-1 inline-flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold text-white"
              style="background: var(--danger)"
            >
              {{ notifications.unreadCount.value }}
            </span>
          </router-link>
          <router-link to="/profile" class="rounded-xl px-3 py-2 text-sm font-medium hover:bg-[var(--paper)]">
            {{ displayName(auth.state.profile) }}
          </router-link>
        </div>
      </header>
      <main id="main" class="p-4 lg:p-8">
        <router-view />
      </main>
    </div>
    <CommandPalette :commands="commands" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell,
  Building2,
  ChartColumn,
  CreditCard,
  Globe,
  Inbox,
  LayoutDashboard,
  Mail,
  Menu,
  Moon,
  Plus,
  Sun,
  ScrollText,
  Search,
  Settings,
  Shield,
  SlidersHorizontal,
  Tags,
  Timer,
  UserRound,
  Users,
} from 'lucide-vue-next';
import AppButton from '@/components/common/AppButton.vue';
import BrandMark from '@/components/common/BrandMark.vue';
import CommandPalette from '@/components/common/CommandPalette.vue';
import { useAuth } from '@/composables/useAuth';
import { useNotifications } from '@/composables/useNotifications';
import { useTheme } from '@/composables/useTheme';
import { displayName } from '@/lib/utils';

const auth = useAuth();
const theme = useTheme();
const notifications = useNotifications();
const router = useRouter();
const route = useRoute();
const mobileOpen = ref(false);
const search = ref('');

const crumbs = {
  dashboard: 'Workspace / Dashboard',
  complaints: 'Workspace / All complaints',
  'complaint-create': 'Workspace / New complaint',
  'complaint-detail': 'Workspace / Case',
  investigation: 'Workspace / Investigation',
  resolution: 'Workspace / Resolution',
  'my-complaints': 'Workspace / My complaints',
  notifications: 'Workspace / Notifications',
  reports: 'Workspace / Reports',
  users: 'Administration / Users',
  'user-detail': 'Administration / Users',
  departments: 'Administration / Departments',
  categories: 'Administration / Categories',
  sla: 'Administration / SLA',
  roles: 'Administration / Roles',
  audit: 'Administration / Audit',
  settings: 'Administration / Settings',
  billing: 'Administration / Billing',
  'email-log': 'Administration / Email log',
  'system-settings': 'Platform / System',
  organizations: 'Platform / Organizations',
  profile: 'Account / Profile',
};

const crumb = computed(() => crumbs[route.name] || 'Workspace');
const isDark = theme.isDark;

function openPalette() {
  window.dispatchEvent(new Event('cms-palette'));
}

const nav = computed(() => {
  const can = auth.can;
  const groups = [
    {
      label: 'Workspace',
      items: [
        { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, show: true },
        { to: '/complaints', label: 'All complaints', icon: Inbox, show: can('complaints:view') || can('complaints:view_all') || can('complaints:view_assigned') },
        { to: '/my-complaints', label: 'My complaints', icon: UserRound, show: true },
        { to: '/complaints/new', label: 'Create complaint', icon: Plus, show: can('complaints:create') },
        { to: '/reports', label: 'Reports', icon: ChartColumn, show: can('reports:view') },
      ],
    },
    {
      label: 'Administration',
      items: [
        { to: '/users', label: 'Users', icon: Users, show: can('users:view') },
        { to: '/departments', label: 'Departments', icon: Building2, show: can('departments:view') },
        { to: '/categories', label: 'Categories', icon: Tags, show: can('categories:view') },
        { to: '/sla-policies', label: 'SLA policies', icon: Timer, show: can('sla:view') },
        { to: '/roles', label: 'Roles & permissions', icon: Shield, show: can('roles:view') },
        { to: '/audit-logs', label: 'Audit logs', icon: ScrollText, show: can('audit_logs:view') },
        { to: '/settings', label: 'Settings', icon: Settings, show: can('settings:view') },
        { to: '/billing', label: 'Billing', icon: CreditCard, show: can('settings:view') || auth.isPlatformAdmin.value },
        { to: '/email-log', label: 'Email log', icon: Mail, show: can('settings:view') || auth.isPlatformAdmin.value },
        { to: '/system-settings', label: 'System settings', icon: SlidersHorizontal, show: auth.isPlatformAdmin.value },
        { to: '/organizations', label: 'Organizations', icon: Globe, show: auth.isPlatformAdmin.value },
      ],
    },
  ];
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.show) }))
    .filter((group) => group.items.length);
});

const commands = computed(() => [
  { id: 'search', label: 'Search complaints', hint: 'Cases', to: { path: '/complaints' } },
  { id: 'overdue', label: 'Review overdue cases', hint: 'Cases', to: { path: '/complaints', query: { overdue: '1' } } },
  ...nav.value.flatMap((group) => group.items.map((item, index) => ({
    id: `nav:${group.label}:${index}:${typeof item.to === 'string' ? item.to : item.to?.path || item.to?.name || 'item'}`,
    label: item.label,
    hint: group.label,
    to: item.to,
  }))),
  { id: 'theme', label: 'Toggle dark mode', hint: 'Display', run: () => theme.toggleAppearance() },
  { id: 'density', label: 'Toggle compact density', hint: 'Display', run: () => theme.setDensity(theme.state.density === 'compact' ? 'comfortable' : 'compact') },
]);

function goSearch() {
  router.push({ path: '/complaints', query: { q: search.value } });
}

async function logout() {
  mobileOpen.value = false;
  await auth.logout();
  await router.replace({ path: '/login' });
}
</script>
