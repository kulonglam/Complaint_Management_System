import { supabase } from '@/lib/supabase';

export async function fetchSessionContext() {
  const {
    data: { session },
  } = await supabase.auth.getSession();
  if (!session?.user) return { session: null, profile: null, permissions: [], roles: [], organization: null };

  const alreadyMarked = sessionStorage.getItem('cms-login-audited');
  if (!alreadyMarked) {
    await supabase.rpc('mark_login');
    sessionStorage.setItem('cms-login-audited', '1');
  }

  const { data: profile, error } = await supabase
    .from('profiles')
    .select('*, organization:organizations(*), department:departments!profiles_department_id_fkey(id, name)')
    .eq('id', session.user.id)
    .maybeSingle();
  if (error) throw error;

  const { data: userRoles } = await supabase
    .from('user_roles')
    .select('role:roles(id, key, name, role_permissions(permission:permissions(key)))')
    .eq('user_id', session.user.id);

  const roles = (userRoles || []).map((row) => row.role).filter(Boolean);
  const permissions = [...new Set(roles.flatMap((role) => (role.role_permissions || []).map((item) => item.permission?.key).filter(Boolean)))];

  return {
    session,
    profile,
    organization: profile?.organization || null,
    roles,
    permissions,
  };
}

export function can(permissions, key) {
  return permissions.includes(key) || permissions.length > 0 && permissions.includes('*');
}
