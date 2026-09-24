import { randomBytes } from 'node:crypto';
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';

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
loadEnv(resolve(process.cwd(), 'backend/.env'));
loadEnv(resolve(process.cwd(), 'frontend/.env'));

const API = 'https://api.supabase.com/v1';
const token = process.env.SUPABASE_ACCESS_TOKEN;
const region = process.env.SUPABASE_REGION || 'eu-west-1';
const projectName = process.env.SUPABASE_PROJECT_NAME || 'complaint-management';
const root = process.cwd();

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

function writeEnvFiles({ url, anonKey, serviceKey, dbPassword }) {
  const frontend = [
    `VITE_SUPABASE_URL=${url}`,
    `VITE_SUPABASE_ANON_KEY=${anonKey}`,
    'VITE_API_URL=http://localhost:8000',
    '',
  ].join('\n');
  const backend = [
    'PORT=8000',
    `SUPABASE_URL=${url}`,
    `SUPABASE_SERVICE_ROLE_KEY=${serviceKey}`,
    `JOB_SECRET=${process.env.JOB_SECRET || randomBytes(18).toString('hex')}`,
    'CORS_ORIGINS=http://localhost:3000',
    'SLA_JOB_ENABLED=true',
    'SLA_CRON=0 */15 * * * *',
    'MAIL_ENABLED=false',
    'MAIL_FROM=noreply@localhost',
    'RATE_LIMIT_CAPACITY=60',
    'RATE_LIMIT_WINDOW_SECONDS=60',
    '',
  ].join('\n');
  const rootEnv = [
    `SUPABASE_URL=${url}`,
    `SUPABASE_SERVICE_ROLE_KEY=${serviceKey}`,
    dbPassword ? `SUPABASE_DB_PASSWORD=${dbPassword}` : '',
    token ? `SUPABASE_ACCESS_TOKEN=${token}` : '',
    '',
  ].filter((line) => line !== undefined).join('\n');

  writeFileSync(resolve(root, 'frontend/.env'), frontend);
  writeFileSync(resolve(root, 'backend/.env'), backend);
  writeFileSync(resolve(root, '.env'), rootEnv);
  console.log('Wrote frontend/.env, backend/.env, and .env');
}

async function applySql(ref) {
  const dir = resolve(root, 'supabase/migrations');
  const files = readdirSync(dir).filter((name) => name.endsWith('.sql')).sort();
  for (const file of files) {
    const query = readFileSync(resolve(dir, file), 'utf8');
    console.log(`Applying ${file}...`);
    await api(`/projects/${ref}/database/query`, { method: 'POST', body: { query } });
  }
}

async function ensureBuckets(ref, serviceKey, url) {
  const buckets = [
    { id: 'complaint-attachments', name: 'complaint-attachments', public: false },
    { id: 'organization-logos', name: 'organization-logos', public: true },
  ];
  for (const bucket of buckets) {
    const response = await fetch(`${url}/storage/v1/bucket`, {
      method: 'POST',
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(bucket),
    });
    if (!response.ok && response.status !== 409) {
      const text = await response.text();
      if (!/already exists|duplicate/i.test(text)) {
        console.warn(`Bucket ${bucket.id}: ${text}`);
      }
    }
  }
}

function runSeed() {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(process.execPath, [resolve(root, 'scripts/seed.mjs')], {
      cwd: root,
      stdio: 'inherit',
      env: process.env,
    });
    child.on('exit', (code) => (code === 0 ? resolvePromise() : reject(new Error(`seed exited ${code}`))));
  });
}

async function waitUntilActive(ref) {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const project = await api(`/projects/${ref}`);
    if (project.status === 'ACTIVE_HEALTHY') return project;
    console.log(`Project status: ${project.status}. Waiting...`);
    await new Promise((resolveWait) => setTimeout(resolveWait, 15000));
  }
  throw new Error('Timed out waiting for the Supabase project to become healthy.');
}

function printManualSteps() {
  console.log(`
Live Supabase is not configured yet. Do this once:

1. Open https://supabase.com/dashboard and create a project named "${projectName}".
2. Create a personal access token at https://supabase.com/dashboard/account/tokens
3. Put it in a root .env file:

   SUPABASE_ACCESS_TOKEN=sbp_your_token
   SUPABASE_PROJECT_REF=your_project_ref   (optional if the project already exists)

4. Re-run:

   npm run supabase:setup

The script will apply migrations 00001-00006, create storage buckets, write env files, and seed demo data.
`);
}

async function main() {
  if (!token) {
    printManualSteps();
    process.exit(1);
  }

  const orgs = await api('/organizations');
  const organization = orgs[0];
  if (!organization) {
    throw new Error('No Supabase organization found for this access token.');
  }

  let project = (await api('/projects')).find((item) =>
    item.ref === process.env.SUPABASE_PROJECT_REF || item.name === projectName
  );
  const dbPassword = process.env.SUPABASE_DB_PASSWORD || `Cms-${randomBytes(12).toString('base64url')}!9`;

  if (!project) {
    console.log(`Creating Supabase project "${projectName}" in ${organization.name}...`);
    project = await api('/projects', {
      method: 'POST',
      body: {
        name: projectName,
        organization_id: organization.id,
        region,
        db_pass: dbPassword,
      },
    });
  } else {
    console.log(`Using existing project ${project.name} (${project.ref})`);
  }

  await waitUntilActive(project.ref);
  const keys = await api(`/projects/${project.ref}/api-keys`);
  const anon = keys.find((item) => item.name === 'anon' || item.id === 'anon');
  const service = keys.find((item) => item.name === 'service_role' || item.id === 'service_role');
  const url = `https://${project.ref}.supabase.co`;
  if (!anon?.api_key || !service?.api_key) {
    throw new Error('Could not read Supabase API keys.');
  }

  writeEnvFiles({
    url,
    anonKey: anon.api_key,
    serviceKey: service.api_key,
    dbPassword,
  });
  process.env.SUPABASE_URL = url;
  process.env.SUPABASE_SERVICE_ROLE_KEY = service.api_key;
  process.env.VITE_SUPABASE_URL = url;
  process.env.VITE_SUPABASE_ANON_KEY = anon.api_key;

  await applySql(project.ref);
  await ensureBuckets(project.ref, service.api_key, url);
  await runSeed();
  console.log(`\nSupabase is live at ${url}`);
  console.log('Demo login: admin@demo.org / DemoPass123!');
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
