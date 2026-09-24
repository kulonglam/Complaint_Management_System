<template>
  <div class="min-h-screen bg-slate-50">
    <div v-if="mobileOpen" class="fixed inset-0 z-30 bg-slate-900/40 lg:hidden" @click="mobileOpen = false" />
    <aside
      :class="[
        'fixed inset-y-0 left-0 z-40 flex h-svh w-72 flex-col transform bg-slate-950 text-slate-300 transition lg:translate-x-0',
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
      ]"
    >
      <div class="shrink-0 border-b border-white/10 px-5 py-5">
        <p class="text-xs uppercase tracking-[0.2em] text-slate-500">CMS</p>
        <p class="mt-1 font-semibold text-white">{{ auth.state.organization?.name || 'Complaint System' }}</p>
      </div>
      <nav class="min-h-0 flex-1 space-y-6 overflow-y-auto overflow-x-hidden p-4 pb-8">
        <section v-for="group in nav" :key="group.label">
          <p class="px-3 pb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="flex items-center gap-3 rounded-lg px-3 py-2 text-sm hover:bg-white/10 hover:text-white"
            active-class="bg-white/10 text-white"
            @click="mobileOpen = false"
          >
            <component :is="item.icon" class="h-4 w-4" />
            {{ item.label }}
          </router-link>
        </section>
      </nav>
      <div class="shrink-0 border-t border-white/10 p-4">
        <AppButton variant="ghost" type="button" class="w-full justify-start text-slate-300 hover:bg-white/10 hover:text-white" @click="logout">
          Sign out
        </AppButton>
      </div>
    </aside>

    <div class="lg:pl-72">
      <header class="sticky top-0 z-50 flex items-center justify-between gap-3 border-b border-slate-200 bg-white/90 px-4 py-3 backdrop-blur">
        <button class="rounded-lg p-2 lg:hidden" type="button" @click="mobileOpen = true" aria-label="Open menu">
          <Menu class="h-5 w-5" />
        </button>
        <form class="hidden flex-1 md:block" @submit.prevent="goSearch">
          <label class="sr-only" for="global-search">Search complaints</label>
          <div class="relative max-w-xl">
            <Search class="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input
              id="global-search"
              v-model="search"
              class="w-full rounded-lg border border-slate-300 py-2 pl-9 pr-3 text-sm"
              placeholder="Search reference, title, category, status, or department"
            />
          </div>
        </form>
        <div class="ml-auto flex items-center gap-2">
          <router-link to="/notifications" class="relative rounded-lg p-2 hover:bg-slate-100" aria-label="Notifications">
            <Bell class="h-5 w-5" />
            <span
              v-if="notifications.unreadCount.value"
              class="absolute right-1 top-1 inline-flex min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-semibold text-white"
            >
              {{ notifications.unreadCount.value }}
            </span>
          </router-link>
          <router-link to="/profile" class="rounded-lg px-3 py-2 text-sm font-medium hover:bg-slate-100">
            {{ displayName(auth.state.profile) }}
          </router-link>
          <AppButton variant="ghost" type="button" @click="logout">Sign out</AppButton>
        </div>
      </header>
      <main class="p-4 lg:p-8">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  Bell,
  Building2,
  ClipboardList,
  FileBarChart,
  LayoutDashboard,
  Menu,
  Search,
  Settings,
  Shield,
  SlidersHorizontal,
  Tags,
  Users,
} from 'lucide-vue-next';
import AppButton from '@/components/common/AppButton.vue';
import { useAuth } from '@/composables/useAuth';
import { useNotifications } from '@/composables/useNotifications';
import { displayName } from '@/lib/utils';

const auth = useAuth();
const notifications = useNotifications();
const router = useRouter();
const mobileOpen = ref(false);
const search = ref('');

const nav = computed(() => {
  const can = auth.can;
  const groups = [
    {
      label: 'Workspace',
      items: [
        { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, show: true },
        { to: '/complaints', label: 'All complaints', icon: ClipboardList, show: can('complaints:view') || can('complaints:view_all') || can('complaints:view_assigned') },
        { to: '/my-complaints', label: 'My complaints', icon: ClipboardList, show: true },
        { to: '/complaints/new', label: 'Create complaint', icon: ClipboardList, show: can('complaints:create') },
        { to: '/reports', label: 'Reports', icon: FileBarChart, show: can('reports:view') },
      ],
    },
    {
      label: 'Administration',
      items: [
        { to: '/users', label: 'Users', icon: Users, show: can('users:view') },
        { to: '/departments', label: 'Departments', icon: Building2, show: can('departments:view') },
        { to: '/categories', label: 'Categories', icon: Tags, show: can('categories:view') },
        { to: '/sla-policies', label: 'SLA policies', icon: SlidersHorizontal, show: can('sla:view') },
        { to: '/roles', label: 'Roles & permissions', icon: Shield, show: can('roles:view') },
        { to: '/audit-logs', label: 'Audit logs', icon: Shield, show: can('audit_logs:view') },
        { to: '/settings', label: 'Settings', icon: Settings, show: can('settings:view') },
        { to: '/billing', label: 'Billing', icon: FileBarChart, show: can('settings:view') || auth.isPlatformAdmin.value },
        { to: '/email-log', label: 'Email log', icon: Settings, show: can('settings:view') || auth.isPlatformAdmin.value },
        { to: '/system-settings', label: 'System settings', icon: SlidersHorizontal, show: auth.isPlatformAdmin.value },
        { to: '/organizations', label: 'Organizations', icon: Building2, show: auth.isPlatformAdmin.value },
      ],
    },
  ];
  return groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.show) }))
    .filter((group) => group.items.length);
});

function goSearch() {
  router.push({ path: '/complaints', query: { q: search.value } });
}

async function logout() {
  mobileOpen.value = false;
  await auth.logout();
  await router.replace({ path: '/login' });
}
</script>
