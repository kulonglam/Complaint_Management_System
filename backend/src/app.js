import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { createClient } from '@supabase/supabase-js';
import { sendEmail } from './services/email.js';

const app = express();
app.use(helmet());
app.use(cors({ origin: true, credentials: true }));
app.use(express.json({ limit: '1mb' }));
app.use(rateLimit({ windowMs: 15 * 60 * 1000, max: 200 }));

const supabaseAdmin = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

async function requireUser(req, res, next) {
  try {
    const token = req.headers.authorization?.replace('Bearer ', '');
    if (!token) return res.status(401).json({ message: 'Authentication required' });
    const { data, error } = await supabaseAdmin.auth.getUser(token);
    if (error || !data.user) return res.status(401).json({ message: 'Invalid session' });
    req.user = data.user;
    const { data: profile } = await supabaseAdmin.from('profiles').select('*').eq('id', data.user.id).single();
    req.profile = profile;
    next();
  } catch {
    res.status(401).json({ message: 'Authentication required' });
  }
}

app.get('/health', (_req, res) => res.json({ ok: true }));

app.post('/api/users/invite', requireUser, async (req, res) => {
  try {
    const { data: allowed } = await supabaseAdmin
      .from('user_roles')
      .select('role:roles(key, role_permissions(permission:permissions(key)))')
      .eq('user_id', req.user.id);
    const keys = (allowed || []).flatMap((row) => row.role?.role_permissions || []).map((item) => item.permission?.key);
    const isPlatform = (allowed || []).some((row) => row.role?.key === 'platform_administrator');
    if (!isPlatform && !keys.includes('users:create')) {
      return res.status(403).json({ message: 'You do not have permission to create users' });
    }
    if (!req.profile?.organization_id && !isPlatform) {
      return res.status(400).json({ message: 'You are not assigned to an organization' });
    }

    const organizationId = req.profile.organization_id;
    const { email, first_name, last_name, role_key, password } = req.body;
    const tempPassword = password || `Tmp-${Math.random().toString(36).slice(2, 10)}!9`;

    const { data: created, error } = await supabaseAdmin.auth.admin.createUser({
      email,
      password: tempPassword,
      email_confirm: true,
      user_metadata: { first_name, last_name },
    });
    if (error) throw error;

    const { error: profileError } = await supabaseAdmin.from('profiles').upsert({
      id: created.user.id,
      organization_id: organizationId,
      first_name,
      last_name,
      email,
      status: 'ACTIVE',
    });
    if (profileError) throw profileError;

    const { data: role } = await supabaseAdmin
      .from('roles')
      .select('id')
      .eq('organization_id', organizationId)
      .eq('key', role_key)
      .single();
    if (role) {
      await supabaseAdmin.from('user_roles').upsert({
        user_id: created.user.id,
        role_id: role.id,
        organization_id: organizationId,
      });
    }

    await sendEmail({
      template: 'user-invited',
      to: email,
      data: { first_name, organization: organizationId },
    });

    res.status(201).json({ id: created.user.id, email });
  } catch (error) {
    res.status(400).json({ message: 'Unable to invite this user.' });
  }
});

app.post('/api/jobs/sla', async (req, res) => {
  if (req.headers['x-job-key'] !== process.env.JOB_SECRET) {
    return res.status(401).json({ message: 'Unauthorized' });
  }
  const { data, error } = await supabaseAdmin.rpc('process_sla_jobs');
  if (error) return res.status(500).json({ message: 'Unable to process SLA jobs.' });
  res.json({ processed: data });
});

const port = Number(process.env.PORT) || 8000;
app.listen(port, () => {
  console.log(`API listening on ${port}`);
});
