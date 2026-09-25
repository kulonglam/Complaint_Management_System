import { supabase } from './supabase';

export const ADMIN_ROLE_KEYS = ['platform_administrator', 'organization_administrator'];

export function isPrivilegedAdmin(roles = []) {
  return roles.some((role) => ADMIN_ROLE_KEYS.includes(role?.key || role));
}

export function adminMfaRequired(settings, roles) {
  return settings?.require_admin_mfa !== false && isPrivilegedAdmin(roles);
}

export async function readMfaGate() {
  const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  const { data: factors } = await supabase.auth.mfa.listFactors();
  const totp = factors?.totp?.find((item) => item.status === 'verified');
  return {
    enrolled: Boolean(totp),
    verified: aal?.currentLevel === 'aal2',
    factorId: totp?.id || factors?.totp?.[0]?.id || '',
  };
}

export function mfaRedirect(gate, currentName) {
  if (currentName === 'profile') return null;
  if (!gate.enrolled) return { path: '/profile', query: { enrollMfa: '1' } };
  if (!gate.verified) return { path: '/profile', query: { verifyMfa: '1' } };
  return null;
}
