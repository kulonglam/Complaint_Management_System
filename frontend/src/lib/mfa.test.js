import { describe, expect, it } from 'vitest';
import { adminMfaRequired, isPrivilegedAdmin, mfaRedirect } from './mfa.js';
import { assertSafeUpload } from './uploads.js';

describe('admin MFA policy', () => {
  it('treats organization admins as privileged', () => {
    expect(isPrivilegedAdmin([{ key: 'organization_administrator' }])).toBe(true);
    expect(isPrivilegedAdmin([{ key: 'complaint_officer' }])).toBe(false);
  });

  it('is on by default and can be disabled per organization', () => {
    expect(adminMfaRequired(null, [{ key: 'platform_administrator' }])).toBe(true);
    expect(adminMfaRequired({ require_admin_mfa: false }, [{ key: 'organization_administrator' }])).toBe(false);
  });

  it('sends unenrolled admins to profile setup', () => {
    expect(mfaRedirect({ enrolled: false, verified: false }, 'dashboard')).toEqual({
      path: '/profile',
      query: { enrollMfa: '1' },
    });
    expect(mfaRedirect({ enrolled: true, verified: false }, 'dashboard')).toEqual({
      path: '/profile',
      query: { verifyMfa: '1' },
    });
    expect(mfaRedirect({ enrolled: true, verified: true }, 'dashboard')).toBeNull();
  });
});

describe('upload safety', () => {
  it('blocks executable names even if the MIME type looks safe', () => {
    expect(() => assertSafeUpload({ name: 'payload.exe', type: 'application/pdf', size: 10 })).toThrow(/blocked/i);
  });

  it('accepts a normal PDF', () => {
    expect(assertSafeUpload({ name: 'letter.pdf', type: 'application/pdf', size: 1024 })).toBe(true);
  });
});
