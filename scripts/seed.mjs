import { createClient } from '@supabase/supabase-js';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function loadEnv(file) {
  try {
    const text = readFileSync(file, 'utf8');
    for (const line of text.split(/\r?\n/)) {
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

const url = process.env.SUPABASE_URL || process.env.VITE_SUPABASE_URL;
const serviceKey = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!url || !serviceKey) {
  console.error('Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY before seeding.');
  process.exit(1);
}

const supabase = createClient(url, serviceKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const PASSWORD = 'DemoPass123!';

function trackingHash(code) {
  return createHash('sha256').update(code.toUpperCase().trim()).digest('hex');
}

async function ensureUser({ email, password, firstName, lastName, organizationId, departmentId, jobTitle }) {
  const { data: existing } = await supabase.from('profiles').select('id').eq('email', email).maybeSingle();
  if (existing?.id) return existing.id;

  const { data, error } = await supabase.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { first_name: firstName, last_name: lastName },
  });
  if (error) throw error;

  const { error: profileError } = await supabase.from('profiles').upsert({
    id: data.user.id,
    organization_id: organizationId,
    first_name: firstName,
    last_name: lastName,
    email,
    department_id: departmentId || null,
    job_title: jobTitle || null,
    status: 'ACTIVE',
  });
  if (profileError) throw profileError;
  return data.user.id;
}

async function assignRole(userId, organizationId, roleKey) {
  const { data: role, error } = await supabase
    .from('roles')
    .select('id')
    .eq('key', roleKey)
    .eq('organization_id', organizationId)
    .single();
  if (error) throw error;
  await supabase.from('user_roles').upsert({
    user_id: userId,
    role_id: role.id,
    organization_id: organizationId,
  });
}

async function main() {
  const { data: demoOrg, error: demoOrgError } = await supabase
    .from('organizations')
    .upsert(
      {
        name: 'Demo Organization',
        slug: 'demo',
        description: 'Demonstration tenant for the complaint management platform.',
        email: 'hello@demo.org',
        phone: '+1-555-0100',
        address: '100 Accountability Avenue',
        country: 'Kenya',
        timezone: 'Africa/Nairobi',
        status: 'ACTIVE',
        public_portal_enabled: true,
      },
      { onConflict: 'slug' }
    )
    .select()
    .single();
  if (demoOrgError) throw demoOrgError;

  const { data: acmeOrg, error: acmeError } = await supabase
    .from('organizations')
    .upsert(
      {
        name: 'Northwind University',
        slug: 'northwind',
        description: 'Second tenant used to verify isolation.',
        email: 'info@northwind.edu',
        country: 'Kenya',
        timezone: 'Africa/Nairobi',
        status: 'ACTIVE',
        public_portal_enabled: true,
      },
      { onConflict: 'slug' }
    )
    .select()
    .single();
  if (acmeError) throw acmeError;

  await supabase.rpc('provision_organization', { p_org: demoOrg.id });
  await supabase.rpc('provision_organization', { p_org: acmeOrg.id });

  const departments = [
    ['Administration', 'ADM'],
    ['Finance', 'FIN'],
    ['Human Resources', 'HR'],
    ['Information Technology', 'IT'],
    ['Operations', 'OPS'],
    ['Customer Service', 'CS'],
  ];

  const deptIds = {};
  for (const [name, code] of departments) {
    const { data, error } = await supabase
      .from('departments')
      .upsert(
        { organization_id: demoOrg.id, name, code, status: 'ACTIVE' },
        { onConflict: 'organization_id,name' }
      )
      .select()
      .single();
    if (error) throw error;
    deptIds[name] = data.id;
  }

  const categories = [
    ['Customer Service', ['Wait times', 'Unhelpful staff']],
    ['Financial', ['Billing error', 'Refund delay']],
    ['Employee Conduct', ['Harassment', 'Discrimination']],
    ['Procurement', ['Conflict of interest', 'Vendor issue']],
    ['Fraud', ['Misuse of funds', 'False reporting']],
    ['Product/Service Quality', ['Defective service', 'Policy failure']],
    ['Data Privacy', ['Unauthorized access', 'Data leak']],
    ['Other', ['General']],
  ];

  const categoryIds = {};
  for (const [name, subs] of categories) {
    const { data, error } = await supabase
      .from('complaint_categories')
      .upsert(
        { organization_id: demoOrg.id, name, status: 'ACTIVE' },
        { onConflict: 'organization_id,name' }
      )
      .select()
      .single();
    if (error) throw error;
    categoryIds[name] = data.id;
    for (const sub of subs) {
      await supabase.from('complaint_subcategories').upsert(
        {
          organization_id: demoOrg.id,
          category_id: data.id,
          name: sub,
          status: 'ACTIVE',
        },
        { onConflict: 'category_id,name' }
      );
    }
  }

  const platformId = await ensureUser({
    email: 'platform@cms.local',
    password: PASSWORD,
    firstName: 'Platform',
    lastName: 'Admin',
    organizationId: null,
    jobTitle: 'Platform Administrator',
  });
  const { data: platformRole } = await supabase
    .from('roles')
    .select('id')
    .eq('key', 'platform_administrator')
    .is('organization_id', null)
    .single();
  await supabase.from('user_roles').upsert({
    user_id: platformId,
    role_id: platformRole.id,
    organization_id: null,
  });

  const adminId = await ensureUser({
    email: 'admin@demo.org',
    password: PASSWORD,
    firstName: 'Amina',
    lastName: 'Otieno',
    organizationId: demoOrg.id,
    departmentId: deptIds.Administration,
    jobTitle: 'Organization Administrator',
  });
  const officerId = await ensureUser({
    email: 'officer@demo.org',
    password: PASSWORD,
    firstName: 'Daniel',
    lastName: 'Mwangi',
    organizationId: demoOrg.id,
    departmentId: deptIds['Customer Service'],
    jobTitle: 'Complaint Officer',
  });
  const investigatorId = await ensureUser({
    email: 'investigator@demo.org',
    password: PASSWORD,
    firstName: 'Grace',
    lastName: 'Wambui',
    organizationId: demoOrg.id,
    departmentId: deptIds['Human Resources'],
    jobTitle: 'Investigator',
  });
  const managerId = await ensureUser({
    email: 'manager@demo.org',
    password: PASSWORD,
    firstName: 'Samuel',
    lastName: 'Kiptoo',
    organizationId: demoOrg.id,
    departmentId: deptIds.Operations,
    jobTitle: 'Department Manager',
  });
  const supervisorId = await ensureUser({
    email: 'supervisor@demo.org',
    password: PASSWORD,
    firstName: 'Naomi',
    lastName: 'Cheruiyot',
    organizationId: demoOrg.id,
    departmentId: deptIds.Administration,
    jobTitle: 'Supervisor',
  });
  const auditorId = await ensureUser({
    email: 'auditor@demo.org',
    password: PASSWORD,
    firstName: 'Peter',
    lastName: 'Njoroge',
    organizationId: demoOrg.id,
    jobTitle: 'Auditor',
  });
  const northwindAdminId = await ensureUser({
    email: 'admin@northwind.org',
    password: PASSWORD,
    firstName: 'Lina',
    lastName: 'Hassan',
    organizationId: acmeOrg.id,
    jobTitle: 'Organization Administrator',
  });

  await assignRole(adminId, demoOrg.id, 'organization_administrator');
  await assignRole(officerId, demoOrg.id, 'complaint_officer');
  await assignRole(investigatorId, demoOrg.id, 'investigator');
  await assignRole(managerId, demoOrg.id, 'department_manager');
  await assignRole(supervisorId, demoOrg.id, 'supervisor');
  await assignRole(auditorId, demoOrg.id, 'auditor');
  await assignRole(northwindAdminId, acmeOrg.id, 'organization_administrator');

  const now = new Date();
  const daysAgo = (n) => new Date(now.getTime() - n * 86400000).toISOString();

  const sampleComplaints = [
    {
      ref: 'CMP-2026-000001',
      code: 'DEMO-AA01',
      title: 'Delayed response from customer service',
      description: 'A service request submitted two weeks ago has not received any acknowledgement.',
      category: 'Customer Service',
      department: 'Customer Service',
      priority: 'MEDIUM',
      status: 'UNDER_INVESTIGATION',
      assignedTo: investigatorId,
      created: daysAgo(12),
      due: daysAgo(-3),
    },
    {
      ref: 'CMP-2026-000002',
      code: 'DEMO-AA02',
      title: 'Incorrect invoice charged to department',
      description: 'Finance billed Operations twice for the same vendor invoice.',
      category: 'Financial',
      department: 'Finance',
      priority: 'HIGH',
      status: 'ASSIGNED',
      assignedTo: officerId,
      created: daysAgo(6),
      due: daysAgo(-1),
    },
    {
      ref: 'CMP-2026-000003',
      code: 'DEMO-AA03',
      title: 'Alleged conflict of interest in procurement',
      description: 'A procurement officer is reported to have a personal relationship with a shortlisted vendor.',
      category: 'Procurement',
      department: 'Administration',
      priority: 'CRITICAL',
      status: 'ESCALATED',
      assignedTo: supervisorId,
      created: daysAgo(4),
      due: daysAgo(1),
      breached: true,
    },
    {
      ref: 'CMP-2026-000004',
      code: 'DEMO-AA04',
      title: 'Workplace harassment complaint',
      description: 'An employee reported repeated inappropriate comments by a supervisor.',
      category: 'Employee Conduct',
      department: 'Human Resources',
      priority: 'HIGH',
      status: 'PENDING_ACTION',
      assignedTo: investigatorId,
      created: daysAgo(18),
      due: daysAgo(2),
    },
    {
      ref: 'CMP-2026-000005',
      code: 'DEMO-AA05',
      title: 'Unresolved system outage affecting records',
      description: 'The case management portal was unavailable during peak hours.',
      category: 'Product/Service Quality',
      department: 'Information Technology',
      priority: 'MEDIUM',
      status: 'RESOLVED',
      assignedTo: managerId,
      created: daysAgo(30),
      due: daysAgo(20),
      resolved: daysAgo(22),
    },
    {
      ref: 'CMP-2026-000006',
      code: 'DEMO-AA06',
      title: 'Unauthorized access to personnel files',
      description: 'Audit found a shared login used to open confidential HR files.',
      category: 'Data Privacy',
      department: 'Information Technology',
      priority: 'CRITICAL',
      status: 'CLOSED',
      assignedTo: investigatorId,
      created: daysAgo(40),
      due: daysAgo(38),
      resolved: daysAgo(35),
      closed: daysAgo(33),
    },
    {
      ref: 'CMP-2026-000007',
      code: 'DEMO-AA07',
      title: 'Long queue times at the service desk',
      description: 'Visitors waited more than two hours without being seen.',
      category: 'Customer Service',
      department: 'Customer Service',
      priority: 'LOW',
      status: 'RECEIVED',
      created: daysAgo(1),
      due: daysAgo(-12),
    },
    {
      ref: 'CMP-2026-000008',
      code: 'DEMO-AA08',
      title: 'Suspected misuse of petty cash',
      description: 'Receipts do not match the recorded petty cash disbursements.',
      category: 'Fraud',
      department: 'Finance',
      priority: 'HIGH',
      status: 'UNDER_REVIEW',
      assignedTo: officerId,
      created: daysAgo(3),
      due: daysAgo(-2),
    },
  ];

  await supabase.from('organization_counters').upsert({
    organization_id: demoOrg.id,
    year: 2026,
    last_number: sampleComplaints.length,
  });

  for (const item of sampleComplaints) {
    const payload = {
      organization_id: demoOrg.id,
      reference_number: item.ref,
      tracking_code_hash: trackingHash(item.code),
      is_anonymous: false,
      complainant_name: 'Public Submitter',
      complainant_email: 'submitter@example.org',
      title: item.title,
      description: item.description,
      category_id: categoryIds[item.category],
      department_id: deptIds[item.department],
      priority: item.priority,
      status: item.status,
      assigned_to: item.assignedTo || null,
      due_date: item.due,
      submitted_at: item.created,
      created_at: item.created,
      updated_at: item.created,
      resolved_at: item.resolved || null,
      closed_at: item.closed || null,
      sla_breached: Boolean(item.breached) || (item.due && new Date(item.due) < now && !['RESOLVED', 'CLOSED', 'REJECTED'].includes(item.status)),
    };

    const { data: complaint, error } = await supabase
      .from('complaints')
      .upsert(payload, { onConflict: 'organization_id,reference_number' })
      .select()
      .single();
    if (error) throw error;

    await supabase.from('complaint_status_history').delete().eq('complaint_id', complaint.id);
    const history = ['SUBMITTED', 'RECEIVED'];
    if (!['SUBMITTED', 'RECEIVED'].includes(item.status)) history.push(item.status === 'ESCALATED' ? 'UNDER_REVIEW' : item.status);
    if (item.status === 'ESCALATED') history.push('ESCALATED');
    let prev = null;
    for (const status of [...new Set(history)]) {
      await supabase.from('complaint_status_history').insert({
        complaint_id: complaint.id,
        organization_id: demoOrg.id,
        old_status: prev,
        new_status: status,
        changed_by: officerId,
        reason: status === 'SUBMITTED' ? 'Public submission' : 'Seeded workflow',
        created_at: item.created,
      });
      prev = status;
    }

    if (item.assignedTo) {
      await supabase.from('complaint_assignments').insert({
        complaint_id: complaint.id,
        organization_id: demoOrg.id,
        assigned_to: item.assignedTo,
        assigned_by: adminId,
        department_id: deptIds[item.department],
        assigned_at: item.created,
        due_date: item.due,
        status: 'IN_PROGRESS',
        notes: 'Seed assignment',
      });
    }

    if (item.status === 'RESOLVED' || item.status === 'CLOSED') {
      await supabase.from('complaint_resolutions').upsert({
        complaint_id: complaint.id,
        organization_id: demoOrg.id,
        summary: 'Corrective action completed and the complainant was informed.',
        corrective_action: 'Process updated and staff retrained.',
        notes: 'Seeded resolution',
        resolved_by: supervisorId,
        resolution_date: (item.resolved || item.created).slice(0, 10),
        approval_status: 'APPROVED',
        reviewed_by: supervisorId,
      });
    }
  }

  await supabase.from('complaints').upsert(
    {
      organization_id: acmeOrg.id,
      reference_number: 'CMP-2026-000001',
      tracking_code_hash: trackingHash('NWUN-0001'),
      is_anonymous: true,
      title: 'Northwind-only facilities complaint',
      description: 'This record must never be visible to Demo Organization users.',
      priority: 'LOW',
      status: 'SUBMITTED',
    },
    { onConflict: 'organization_id,reference_number' }
  );

  console.log('Seed complete.');
  console.log('Demo org slug: demo');
  console.log('Login accounts (password: DemoPass123!):');
  console.log('  platform@cms.local');
  console.log('  admin@demo.org');
  console.log('  officer@demo.org');
  console.log('  investigator@demo.org');
  console.log('  manager@demo.org');
  console.log('  supervisor@demo.org');
  console.log('  auditor@demo.org');
  console.log('  admin@northwind.org (isolated tenant)');
  console.log('Public tracking example: CMP-2026-000001 / DEMO-AA01');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
