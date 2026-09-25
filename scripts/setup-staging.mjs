import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

function loadEnv(file) {
  try {
    for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const idx = trimmed.indexOf('=');
      if (idx === -1) continue;
      const key = trimmed.slice(0, idx).trim();
      const value = trimmed.slice(idx + 1).trim();
      if (!process.env[key]) process.env[key] = value;
    }
  } catch {
    // optional
  }
}

loadEnv(resolve(process.cwd(), '.env'));
loadEnv(resolve(process.cwd(), '.env.staging'));

const token = process.env.SUPABASE_ACCESS_TOKEN;
const stagingUrl = process.env.STAGING_SUPABASE_URL || process.env.STAGING_URL;
const stagingRef = process.env.STAGING_PROJECT_REF
  || (stagingUrl || '').match(/https:\/\/([a-z0-9]+)\.supabase\.co/i)?.[1];

if (!token) {
  console.error('Set SUPABASE_ACCESS_TOKEN.');
  process.exit(1);
}

if (!stagingRef && process.env.CREATE_STAGING !== '1') {
  console.log('Staging is not configured yet.');
  console.log('Either set STAGING_SUPABASE_URL / STAGING_PROJECT_REF in .env.staging,');
  console.log('or run CREATE_STAGING=1 npm run supabase:staging to create a separate project.');
  process.exit(0);
}

const API = 'https://api.supabase.com/v1';

async function api(path, { method = 'GET', body } = {}) {
  const response = await fetch(`${API}${path}`, {
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
    throw new Error(data?.message || data?.error || `Supabase API ${response.status} on ${path}`);
  }
  return data;
}

let ref = stagingRef;
if (!ref && process.env.CREATE_STAGING === '1') {
  const name = process.env.STAGING_PROJECT_NAME || 'complaint-management-staging';
  const existing = await api('/projects');
  const found = (existing || []).find((project) => project.name === name);
  if (found) {
    ref = found.id || found.ref;
    console.log(`Reusing staging project ${ref}`);
  } else {
    console.log(`Create a staging project named ${name} in the Supabase dashboard, then set STAGING_PROJECT_REF.`);
    console.log('This script will not create a billed project unless you already have a ref.');
    process.exit(1);
  }
}

const dir = resolve(process.cwd(), 'supabase/migrations');
const files = readdirSync(dir).filter((name) => name.endsWith('.sql')).sort();
for (const file of files) {
  const query = readFileSync(resolve(dir, file), 'utf8');
  console.log(`Applying ${file} to staging ${ref}...`);
  await api(`/projects/${ref}/database/query`, { method: 'POST', body: { query } });
}

writeFileSync(
  resolve(process.cwd(), '.env.staging'),
  [
    `STAGING_PROJECT_REF=${ref}`,
    stagingUrl ? `STAGING_SUPABASE_URL=${stagingUrl}` : '',
    token ? `SUPABASE_ACCESS_TOKEN=${token}` : '',
    '',
  ].filter(Boolean).join('\n'),
  'utf8'
);

console.log('Staging migrations applied. Next: seed against the staging URL, then point a Render preview at it.');
