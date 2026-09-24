import { supabase } from '@/lib/supabase';

export async function api(path, { method = 'GET', body } = {}) {
  const token = (await supabase.auth.getSession()).data.session?.access_token;
  const response = await fetch(`${import.meta.env.VITE_API_URL || ''}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || 'Request failed');
  }
  return payload;
}
