import { createClient } from '@supabase/supabase-js';
import { readFileSync } from 'node:fs';
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
loadEnv(resolve(process.cwd(), 'frontend/.env'));
loadEnv(resolve(process.cwd(), 'backend/.env'));

const url = process.env.VITE_SUPABASE_URL || process.env.SUPABASE_URL;
const anon = process.env.VITE_SUPABASE_ANON_KEY;
if (!url || !anon || url.includes('YOUR_PROJECT')) {
  console.log('Skipping tenant isolation test: Supabase env is not configured.');
  process.exit(0);
}

async function signIn(email) {
  const client = createClient(url, anon, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data, error } = await client.auth.signInWithPassword({ email, password: 'DemoPass123!' });
  if (error) throw error;
  return { client, user: data.user };
}

const demo = await signIn('admin@demo.org');
const northwind = await signIn('admin@northwind.org');

const { data: demoRows, error: demoError } = await demo.client.from('complaints').select('id, organization_id');
const { data: northwindRows, error: northError } = await northwind.client.from('complaints').select('id, organization_id');
if (demoError) throw demoError;
if (northError) throw northError;

const demoOrg = new Set((demoRows || []).map((row) => row.organization_id));
const northOrg = new Set((northwindRows || []).map((row) => row.organization_id));
const overlap = [...demoOrg].filter((id) => northOrg.has(id));

if (overlap.length) {
  console.error('Tenant isolation failed: both users saw the same organization_id.');
  process.exit(1);
}

const { data: leaked } = await demo.client
  .from('complaints')
  .select('id')
  .in('id', (northwindRows || []).map((row) => row.id));

if ((leaked || []).length) {
  console.error('Tenant isolation failed: demo admin could read Northwind complaint ids.');
  process.exit(1);
}

console.log(`Tenant isolation passed. Demo saw ${demoRows.length} complaint(s); Northwind saw ${northwindRows.length}.`);
