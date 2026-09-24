import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
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

function upsertEnv(file, updates) {
  let text = '';
  try {
    text = readFileSync(file, 'utf8');
  } catch {
    text = '';
  }
  const keys = new Set();
  const lines = text.split(/\r?\n/).map((line) => {
    const idx = line.indexOf('=');
    if (idx === -1) return line;
    const key = line.slice(0, idx).trim();
    if (updates[key] !== undefined) {
      keys.add(key);
      return `${key}=${updates[key]}`;
    }
    return line;
  });
  for (const [key, value] of Object.entries(updates)) {
    if (!keys.has(key)) lines.push(`${key}=${value}`);
  }
  writeFileSync(file, `${lines.filter((line, index, all) => !(line === '' && all[index - 1] === '')).join('\n').trim()}\n`);
}

const root = process.cwd();
const env = loadEnv(resolve(root, '.env'));
loadEnv(resolve(root, 'backend/.env'), env);
loadEnv(resolve(root, 'frontend/.env'), env);

const token = env.SUPABASE_ACCESS_TOKEN;
const url = env.SUPABASE_URL || env.VITE_SUPABASE_URL;
const service = env.SUPABASE_SERVICE_ROLE_KEY;
const ref = env.SUPABASE_PROJECT_REF || (url || '').match(/https:\/\/([a-z0-9]+)\.supabase\.co/i)?.[1];
const origin = env.VITE_APP_ORIGIN || 'http://localhost:3000';

if (!token || !ref || !url || !service) {
  console.error('Need SUPABASE_ACCESS_TOKEN, SUPABASE_URL, and SUPABASE_SERVICE_ROLE_KEY.');
  process.exit(1);
}

async function api(path, { method = 'GET', body } = {}) {
  const response = await fetch(`https://api.supabase.com/v1${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { message: text };
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} failed (${response.status}): ${data?.message || text}`);
  }
  return data;
}

const done = [];
const next = [];

const buckets = [
  { id: 'complaint-attachments', name: 'complaint-attachments', public: false },
  { id: 'organization-logos', name: 'organization-logos', public: true },
];
for (const bucket of buckets) {
  const response = await fetch(`${url}/storage/v1/bucket`, {
    method: 'POST',
    headers: {
      apikey: service,
      Authorization: `Bearer ${service}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(bucket),
  });
  if (response.ok || response.status === 409) {
    done.push(`Storage bucket ${bucket.id}`);
  } else {
    const text = await response.text();
    if (/already exists|duplicate/i.test(text)) done.push(`Storage bucket ${bucket.id}`);
    else next.push(`Create storage bucket ${bucket.id}: ${text}`);
  }
}

const redirects = [
  origin,
  `${origin}/auth/callback`,
  `${origin}/reset-password`,
  `${origin}/login`,
].join(',');

try {
  await api(`/projects/${ref}/config/auth`, {
    method: 'PATCH',
    body: {
      site_url: origin,
      uri_allow_list: redirects,
    },
  });
  done.push(`Auth site URL and redirects set to ${origin}`);
} catch (error) {
  next.push(`Set Auth URL config: ${error.message}`);
}

let jobSecret = env.JOB_SECRET;
if (!jobSecret || jobSecret === 'change-me') {
  jobSecret = randomBytes(24).toString('hex');
  done.push('Generated a new JOB_SECRET');
} else {
  done.push('JOB_SECRET already set');
}

upsertEnv(resolve(root, 'backend/.env'), {
  JOB_SECRET: jobSecret,
  CORS_ORIGINS: origin,
  MAIL_ENABLED: env.MAIL_ENABLED || 'false',
  MAIL_FROM: env.MAIL_FROM || 'noreply@localhost',
  SMTP_HOST: env.SMTP_HOST || '',
  SMTP_PORT: env.SMTP_PORT || '587',
  SMTP_USER: env.SMTP_USER || '',
  SMTP_PASSWORD: env.SMTP_PASSWORD || '',
  RESEND_API_KEY: env.RESEND_API_KEY || '',
  STRIPE_SECRET_KEY: env.STRIPE_SECRET_KEY || '',
});

if (!env.MAIL_ENABLED || env.MAIL_ENABLED === 'false') {
  next.push('Step 2: add RESEND_API_KEY or SMTP so emails leave the outbox');
}
next.push('Step 3: enable Google and/or GitHub in Supabase Auth (needs provider client IDs)');
next.push('Step 4: enroll 2FA at /profile while signed in as admin@demo.org');
next.push('Step 5: confirm daily backups in the Supabase project settings');

console.log(`Project: ${url}`);
console.log('\nDone now:');
for (const item of done) console.log(`- ${item}`);
console.log('\nYour next steps:');
for (const item of next) console.log(`- ${item}`);
