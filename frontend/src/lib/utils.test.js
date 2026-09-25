import { describe, expect, it } from 'vitest';
import { can } from '@/services/auth.service';
import { displayName, formatRecordSummary, getErrorMessage, isValidEmail, isValidPhone, remainingTime, toCsv } from './utils.js';

describe('displayName', () => {
  it('joins first and last name', () => {
    expect(displayName({ first_name: 'Amina', last_name: 'Otieno' })).toBe('Amina Otieno');
  });

  it('falls back to unassigned', () => {
    expect(displayName(null)).toBe('Unassigned');
  });
});

describe('validation', () => {
  it('accepts a normal email', () => {
    expect(isValidEmail('admin@demo.org')).toBe(true);
  });

  it('rejects a broken email', () => {
    expect(isValidEmail('admin@')).toBe(false);
  });

  it('allows an empty phone and rejects letters', () => {
    expect(isValidPhone('')).toBe(true);
    expect(isValidPhone('abc')).toBe(false);
    expect(isValidPhone('+254711223344')).toBe(true);
  });
});

describe('formatRecordSummary', () => {
  it('turns object fields into a short sentence', () => {
    expect(formatRecordSummary({ status: 'OPEN', reference_number: 'C-1' })).toBe('status: OPEN · reference number: C-1');
  });

  it('falls back for empty values', () => {
    expect(formatRecordSummary({})).toBe('—');
  });
});

describe('errors and exports', () => {
  it('hides permission errors', () => {
    expect(getErrorMessage({ message: 'row-level security violation' })).toBe(
      'You do not have permission to complete this action.'
    );
  });

  it('builds csv rows', () => {
    expect(toCsv([{ a: 1, b: 'x' }])).toContain('"1","x"');
  });

  it('marks overdue dates', () => {
    expect(remainingTime(new Date(Date.now() - 3600_000).toISOString()).overdue).toBe(true);
  });
});

describe('can', () => {
  it('matches a permission key', () => {
    expect(can(['complaints:view'], 'complaints:view')).toBe(true);
    expect(can(['complaints:view'], 'users:create')).toBe(false);
  });
});
