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
loadEnv(resolve(process.cwd(), 'backend/.env'));

const token = process.env.SUPABASE_ACCESS_TOKEN;
const url = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const ref = process.env.SUPABASE_PROJECT_REF || (url || '').match(/https:\/\/([a-z0-9]+)\.supabase\.co/i)?.[1];

if (!token || !ref) {
  console.error('Set SUPABASE_ACCESS_TOKEN and SUPABASE_URL.');
  process.exit(1);
}

const response = await fetch(`https://api.supabase.com/v1/projects/${ref}/database/backups`, {
  headers: { Authorization: `Bearer ${token}` },
});
const text = await response.text();
let data = null;
try {
  data = text ? JSON.parse(text) : null;
} catch {
  data = { raw: text };
}

if (!response.ok) {
  console.log('Backup API status:', response.status);
  console.log(text);
} else {
  const backups = Array.isArray(data) ? data : data?.backups || data?.data || [];
  console.log(`Backup listing for ${ref}: ${Array.isArray(backups) ? backups.length : 0} item(s).`);
  if (Array.isArray(backups)) {
    for (const item of backups.slice(0, 8)) {
      console.log('-', item.inserted_at || item.created_at || item.status || JSON.stringify(item));
    }
  } else {
    console.log(JSON.stringify(data, null, 2));
  }
}

console.log(`
Restore drill (do this on staging, never first on production):

1. Supabase Dashboard → ${ref} → Database → Backups → Restore to a new project
   or download a logical backup if PITR is on.
2. Point .env.staging at the restored project.
3. npm run supabase:migrate   (only if the restore is older than repo migrations)
4. npm run test:isolation
5. Sign in as admin@demo.org, open one complaint, submit one public complaint.
6. Record the date, who ran it, and how long restore + smoke took in docs/RUNBOOKS.md.

Pass criteria: users can sign in, RLS still isolates Demo vs Northwind, /ready is 200.
`);
