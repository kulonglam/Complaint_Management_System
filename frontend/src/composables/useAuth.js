import { computed, reactive } from 'vue';
import { fetchSessionContext } from '@/services/auth.service';
import { supabase } from '@/lib/supabase';

let auth;
let refreshGeneration = 0;

export function createAuthState() {
  if (auth) return auth;

  const state = reactive({
    loading: true,
    session: null,
    profile: null,
    organization: null,
    settings: null,
    roles: [],
    permissions: [],
  });

  async function refresh() {
    const generation = ++refreshGeneration;
    state.loading = true;
    try {
      const context = await fetchSessionContext();
      if (generation !== refreshGeneration) return state;
      Object.assign(state, { ...context, loading: false });
    } catch {
      const {
        data: { session },
      } = await supabase.auth.getSession();
      if (generation !== refreshGeneration) return state;
      Object.assign(state, {
        loading: false,
        session,
        profile: session ? state.profile : null,
        organization: session ? state.organization : null,
        settings: session ? state.settings : null,
        roles: session ? state.roles : [],
        permissions: session ? state.permissions : [],
      });
    }
    return state;
  }

  function can(permission) {
    if (state.roles.some((role) => role.key === 'platform_administrator')) return true;
    return state.permissions.includes(permission);
  }

  function clearSession() {
    refreshGeneration += 1;
    sessionStorage.removeItem('cms-login-audited');
    Object.assign(state, {
      loading: false,
      session: null,
      profile: null,
      organization: null,
      settings: null,
      roles: [],
      permissions: [],
    });
  }

  async function logout() {
    try {
      await Promise.race([
        supabase.rpc('mark_logout'),
        new Promise((resolve) => setTimeout(resolve, 1500)),
      ]);
    } catch {
      // Audit write must not block sign-out.
    }
    const { error } = await supabase.auth.signOut({ scope: 'local' });
    if (error) {
      await supabase.auth.signOut({ scope: 'global' }).catch(() => {});
    }
    clearSession();
  }

  supabase.auth.onAuthStateChange((_event, session) => {
    state.session = session;
    if (session) refresh();
    else {
      clearSession();
    }
  });

  refresh();

  auth = {
    state,
    refresh,
    logout,
    can,
    isAuthenticated: computed(() => Boolean(state.session)),
    isPlatformAdmin: computed(() => state.roles.some((role) => role.key === 'platform_administrator')),
  };

  return auth;
}

export function useAuth() {
  return createAuthState();
}
