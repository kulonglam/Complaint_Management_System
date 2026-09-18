export function classNames(...values) {
  return values.filter(Boolean).join(' ');
}

export function formatDate(value, withTime = true) {
  if (!value) return '—';
  const date = new Date(value);
  return withTime ? date.toLocaleString() : date.toLocaleDateString();
}

export function displayName(profile) {
  if (!profile) return 'Unassigned';
  const name = `${profile.first_name || ''} ${profile.last_name || ''}`.trim();
  return name || profile.email || 'Unknown';
}

export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const message = error?.message || error?.error_description || fallback;
  if (/row-level security|permission denied|jwt/i.test(message)) {
    return 'You do not have permission to complete this action.';
  }
  return message;
}

export function remainingTime(dueDate) {
  if (!dueDate) return { label: 'No due date', overdue: false };
  const diff = new Date(dueDate).getTime() - Date.now();
  const overdue = diff < 0;
  const abs = Math.abs(diff);
  const hours = Math.round(abs / 36e5);
  const days = Math.round(abs / 864e5);
  const label =
    days >= 1 ? `${days} day${days === 1 ? '' : 's'}` : `${hours} hour${hours === 1 ? '' : 's'}`;
  return {
    overdue,
    label: overdue ? `${label} overdue` : `${label} remaining`,
  };
}

export function toCsv(rows) {
  if (!rows.length) return '';
  const headers = Object.keys(rows[0]);
  const escape = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  return [headers.join(','), ...rows.map((row) => headers.map((key) => escape(row[key])).join(','))].join('\n');
}

export function downloadText(filename, text, type = 'text/csv') {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function dateRangePreset(preset) {
  const now = new Date();
  const start = new Date(now);
  if (preset === 'today') start.setHours(0, 0, 0, 0);
  if (preset === 'week') start.setDate(now.getDate() - 7);
  if (preset === 'month') start.setMonth(now.getMonth() - 1);
  if (preset === '30') start.setDate(now.getDate() - 30);
  if (preset === 'quarter') start.setMonth(now.getMonth() - 3);
  if (preset === 'year') start.setFullYear(now.getFullYear() - 1);
  if (preset === 'all') return { from: null, to: null };
  return { from: start.toISOString(), to: now.toISOString() };
}
