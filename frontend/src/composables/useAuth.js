import { computed, reactive } from 'vue';
import { fetchSessionContext } from '@/services/auth.service';
import { supabase } from '@/lib/supabase';

let auth;

export function createAuthState() {
  if (auth) return auth;

  const state = reactive({
    loading: true,
    session: null,
    profile: null,
    organization: null,
    roles: [],
    permissions: [],
  });

  async function refresh() {
    state.loading = true;
    try {
      const context = await fetchSessionContext();
      Object.assign(state, { ...context, loading: false });
    } catch {
      const {
        data: { session },
      } = await supabase.auth.getSession();
      Object.assign(state, {
        loading: false,
        session,
        profile: session ? state.profile : null,
        organization: session ? state.organization : null,
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

  supabase.auth.onAuthStateChange((_event, session) => {
    state.session = session;
    if (session) refresh();
    else {
      state.profile = null;
      state.organization = null;
      state.roles = [];
      state.permissions = [];
      state.loading = false;
    }
  });

  refresh();

  auth = {
    state,
    refresh,
    can,
    isAuthenticated: computed(() => Boolean(state.session)),
    isPlatformAdmin: computed(() => state.roles.some((role) => role.key === 'platform_administrator')),
  };

  return auth;
}

export function useAuth() {
  return createAuthState();
}
