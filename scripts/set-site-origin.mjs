import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function loadEnv(file, into = {}) {
  try {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const idx = trimmed.indexOf('=');
      if (idx === -1) continue;
      const key = trimmed.slice(0, idx).trim();
      const value = trimmed.slice(idx + 1).trim();
      if (!into[key]) into[key] = value;
    }
  } catch {
    // optional
  }
  return into;
}

const origin = (process.argv[2] || process.env.SITE_URL || '').replace(/\/+$/, '');
if (!origin.startsWith('http')) {
  console.error('Usage: node scripts/set-site-origin.mjs https://your-frontend.onrender.com');
  process.exit(1);
}

const env = loadEnv(resolve(process.cwd(), '.env'));
loadEnv(resolve(process.cwd(), 'backend/.env'), env);
const token = env.SUPABASE_ACCESS_TOKEN;
const url = env.SUPABASE_URL || env.VITE_SUPABASE_URL;
const ref = env.SUPABASE_PROJECT_REF || (url || '').match(/https:\/\/([a-z0-9]+)\.supabase\.co/i)?.[1];
if (!token || !ref) {
  console.error('Need SUPABASE_ACCESS_TOKEN and SUPABASE_URL in .env');
  process.exit(1);
}

const redirects = [
  origin,
  `${origin}/auth/callback`,
  `${origin}/reset-password`,
  `${origin}/login`,
  'http://localhost:3000',
  'http://localhost:3000/auth/callback',
  'http://localhost:3000/reset-password',
  'http://localhost:3000/login',
].join(',');

const response = await fetch(`https://api.supabase.com/v1/projects/${ref}/config/auth`, {
  method: 'PATCH',
  headers: {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    site_url: origin,
    uri_allow_list: redirects,
  }),
});
const text = await response.text();
if (!response.ok) {
  console.error(`Auth update failed (${response.status}): ${text}`);
  process.exit(1);
}

console.log(`Auth site URL: ${origin}`);
console.log('Also allowed: http://localhost:3000');
console.log(`Set backend CORS_ORIGINS=${origin},http://localhost:3000 and redeploy the API.`);
