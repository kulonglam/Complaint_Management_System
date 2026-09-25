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

function fail(message) {
  console.error(message);
  process.exit(1);
}

const demo = await signIn('admin@demo.org');
const northwind = await signIn('admin@northwind.org');
const officer = await signIn('officer@demo.org');

const { data: demoRows, error: demoError } = await demo.client.from('complaints').select('id, organization_id');
const { data: northwindRows, error: northError } = await northwind.client.from('complaints').select('id, organization_id');
if (demoError) throw demoError;
if (northError) throw northError;

const demoOrg = new Set((demoRows || []).map((row) => row.organization_id));
const northOrg = new Set((northwindRows || []).map((row) => row.organization_id));
const overlap = [...demoOrg].filter((id) => northOrg.has(id));
if (overlap.length) fail('Tenant isolation failed: both users saw the same organization_id.');

const { data: leaked } = await demo.client
  .from('complaints')
  .select('id')
  .in('id', (northwindRows || []).map((row) => row.id).concat('00000000-0000-0000-0000-000000000000'));
if ((leaked || []).length) fail('Tenant isolation failed: demo admin could read Northwind complaint ids.');

const { data: demoUsers } = await demo.client.from('profiles').select('id, organization_id');
const { data: northUsers } = await northwind.client.from('profiles').select('id, organization_id');
const profileOverlap = [...new Set((demoUsers || []).map((row) => row.organization_id))]
  .filter((id) => (northUsers || []).some((row) => row.organization_id === id));
if (profileOverlap.length) fail('Tenant isolation failed: profile rows leaked across organizations.');

const { data: demoDepts } = await demo.client.from('departments').select('id, organization_id');
const { data: northDepts } = await northwind.client.from('departments').select('id, organization_id');
if ((demoDepts || []).some((row) => (northDepts || []).some((other) => other.id === row.id))) {
  fail('Tenant isolation failed: department ids leaked.');
}

const { data: demoAudit } = await demo.client.from('audit_logs').select('id, organization_id').limit(50);
const { data: northAudit } = await northwind.client.from('audit_logs').select('id, organization_id').limit(50);
if ((demoAudit || []).some((row) => (northAudit || []).some((other) => other.id === row.id))) {
  fail('Tenant isolation failed: audit log ids leaked.');
}

const foreignId = (northwindRows || [])[0]?.id;
if (foreignId) {
  const { data: wrote, error: updateError } = await demo.client
    .from('complaints')
    .update({ title: 'cross-tenant write' })
    .eq('id', foreignId)
    .select('id');
  if (updateError && !/row-level security|permission denied/i.test(updateError.message || '')) {
    throw updateError;
  }
  if ((wrote || []).length) fail('Tenant isolation failed: demo admin updated a Northwind complaint.');
}

const { data: tracked, error: trackError } = await officer.client.rpc('track_public_complaint', {
  p_reference: 'CMP-NOT-REAL',
  p_tracking_code: 'WRONG-CODE',
});
if (tracked && tracked.id) fail('Public track returned a complaint for a bogus code.');
if (trackError && !/invalid|not found|denied|no complaint matched/i.test(trackError.message || '')) {
  throw trackError;
}

console.log(`Tenant isolation passed. Demo saw ${demoRows.length} complaint(s); Northwind saw ${northwindRows.length}.`);
