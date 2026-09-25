import { createRouter, createWebHistory } from 'vue-router';
import { createAuthState } from '@/composables/useAuth';
import { adminMfaRequired, mfaRedirect, readMfaGate } from '@/lib/mfa';
import PublicLayout from '@/layouts/PublicLayout.vue';
import AppShell from '@/layouts/AppShell.vue';

const routes = [
  {
    path: '/',
    component: PublicLayout,
    children: [
      { path: '', name: 'landing', component: () => import('@/views/public/LandingView.vue') },
      { path: 'submit-complaint', name: 'submit', component: () => import('@/views/public/SubmitComplaintView.vue') },
      { path: 'track-complaint', name: 'track', component: () => import('@/views/public/TrackComplaintView.vue') },
      { path: 'complaint-submitted', name: 'submitted', component: () => import('@/views/public/SubmittedView.vue') },
      { path: 'help', name: 'help', component: () => import('@/views/public/HelpView.vue') },
      { path: 'login', name: 'login', component: () => import('@/views/auth/LoginView.vue'), meta: { guest: true } },
      { path: 'auth/callback', name: 'auth-callback', component: () => import('@/views/auth/AuthCallbackView.vue') },
      { path: 'forgot-password', name: 'forgot', component: () => import('@/views/auth/ForgotPasswordView.vue'), meta: { guest: true } },
      { path: 'reset-password', name: 'reset', component: () => import('@/views/auth/ResetPasswordView.vue') },
    ],
  },
  {
    path: '/',
    component: AppShell,
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
      { path: 'complaints', name: 'complaints', component: () => import('@/views/complaints/ComplaintListView.vue') },
      { path: 'complaints/new', name: 'complaint-create', meta: { permission: 'complaints:create' }, component: () => import('@/views/complaints/CreateComplaintView.vue') },
      { path: 'complaints/:id', name: 'complaint-detail', component: () => import('@/views/complaints/ComplaintDetailView.vue') },
      { path: 'complaints/:id/investigation', name: 'investigation', meta: { permission: 'complaints:investigate' }, component: () => import('@/views/complaints/InvestigationView.vue') },
      { path: 'complaints/:id/resolution', name: 'resolution', meta: { permission: 'complaints:resolve' }, component: () => import('@/views/complaints/ResolutionView.vue') },
      { path: 'my-complaints', name: 'my-complaints', component: () => import('@/views/complaints/MyComplaintsView.vue') },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/notifications/NotificationsView.vue') },
      { path: 'reports', name: 'reports', meta: { permission: 'reports:view' }, component: () => import('@/views/reports/ReportsView.vue') },
      { path: 'users', name: 'users', meta: { permission: 'users:view' }, component: () => import('@/views/users/UsersView.vue') },
      { path: 'users/:id', name: 'user-detail', meta: { permission: 'users:view' }, component: () => import('@/views/users/UserDetailView.vue') },
      { path: 'departments', name: 'departments', meta: { permission: 'departments:view' }, component: () => import('@/views/admin/DepartmentsView.vue') },
      { path: 'categories', name: 'categories', meta: { permission: 'categories:view' }, component: () => import('@/views/admin/CategoriesView.vue') },
      { path: 'sla-policies', name: 'sla', meta: { permission: 'sla:view' }, component: () => import('@/views/admin/SlaPoliciesView.vue') },
      { path: 'roles', name: 'roles', meta: { permission: 'roles:view' }, component: () => import('@/views/admin/RolesView.vue') },
      { path: 'audit-logs', name: 'audit', meta: { permission: 'audit_logs:view' }, component: () => import('@/views/admin/AuditLogsView.vue') },
      { path: 'settings', name: 'settings', meta: { permission: 'settings:view' }, component: () => import('@/views/admin/SettingsView.vue') },
      { path: 'system-settings', name: 'system-settings', meta: { platform: true }, component: () => import('@/views/admin/SystemSettingsView.vue') },
      { path: 'organizations', name: 'organizations', meta: { platform: true }, component: () => import('@/views/admin/OrganizationsView.vue') },
      { path: 'billing', name: 'billing', meta: { permission: 'settings:view' }, component: () => import('@/views/admin/BillingView.vue') },
      { path: 'billing/return', name: 'billing-return', meta: { permission: 'settings:view' }, component: () => import('@/views/admin/PaymentReturnView.vue') },
      { path: 'billing/sandbox', name: 'billing-sandbox', meta: { permission: 'settings:view' }, component: () => import('@/views/admin/PaymentSandboxView.vue') },
      { path: 'email-log', name: 'email-log', meta: { permission: 'settings:view' }, component: () => import('@/views/admin/EmailLogView.vue') },
      { path: 'profile', name: 'profile', component: () => import('@/views/settings/ProfileView.vue') },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

router.beforeEach(async (to) => {
  const auth = createAuthState();
  if (auth.state.loading) {
    await auth.refresh();
  }

  if (to.meta.requiresAuth && !auth.state.session) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.guest && auth.state.session) {
    return '/dashboard';
  }
  if (to.meta.platform && !auth.isPlatformAdmin.value) {
    return '/dashboard';
  }
  if (to.meta.permission && !auth.can(to.meta.permission)) {
    return '/dashboard';
  }
  if (to.meta.requiresAuth && adminMfaRequired(auth.state.settings, auth.state.roles)) {
    const redirect = mfaRedirect(await readMfaGate(), to.name);
    if (redirect) return redirect;
  }
  return true;
});

export default router;
