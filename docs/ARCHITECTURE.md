# Architecture

The system is a multi-tenant SaaS. PostgreSQL (Supabase) is the system of record. The Vue client talks to Supabase for authenticated CRUD. Privileged auth-admin operations go through a thin Express API.

```
Public user  →  submit_public_complaint / track_public_complaint (RPC, anon)
Staff        →  Supabase Auth + RLS + permission checks
Admin invite →  Express API + service role
SLA job      →  process_sla_jobs()
```

Tenant isolation is enforced in the database:

- `current_organization_id()` reads `profiles.organization_id` for `auth.uid()`
- Policies require `organization_id = current_organization_id()`
- Platform admins bypass via `is_platform_admin()`
- The frontend never decides which tenant a write belongs to

Reserved tables `subscription_plans` and `organization_subscriptions` exist for later billing.
