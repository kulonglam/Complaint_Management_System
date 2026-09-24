import { readdirSync, readFileSync } from 'node:fs';
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
  console.error('Set SUPABASE_ACCESS_TOKEN and SUPABASE_URL or SUPABASE_PROJECT_REF.');
  process.exit(1);
}

const dir = resolve(process.cwd(), 'supabase/migrations');
const requested = process.argv[2];
const files = readdirSync(dir)
  .filter((name) => name.endsWith('.sql'))
  .sort()
  .filter((name) => !requested || name === requested || name.startsWith(requested));
for (const file of files) {
  const query = readFileSync(resolve(dir, file), 'utf8');
  console.log(`Applying ${file}...`);
  const response = await fetch(`https://api.supabase.com/v1/projects/${ref}/database/query`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${file} failed: ${text}`);
  }
}
console.log('Migrations applied.');
