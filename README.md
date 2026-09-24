# Complaint Management System

Production-oriented multi-tenant SaaS for receiving, investigating, resolving, and monitoring complaints.

The app uses **Vue 3**, **Supabase PostgreSQL + Auth + Storage**, and **Row Level Security** for tenant isolation. Organization membership is taken from the authenticated profile, never from a client-supplied `organization_id`.

## Architecture

- `frontend/` — Vue 3, Vite, Tailwind CSS, Vue Router, TanStack Query
- `supabase/migrations/` — schema, RPCs, RLS, permissions
- `backend/` — Spring Boot API for privileged operations (user invite, SLA job, email dispatch)
- `scripts/seed.mjs` — demo tenant data

Public complaint submit/track runs through `SECURITY DEFINER` database functions so anonymous users cannot enumerate or read internal records.

## Setup

1. Create a Supabase project.
2. Run every SQL file in `supabase/migrations/` in order in the SQL editor (or `supabase db push` if you use the CLI).
3. Copy environment files:

```bash
cp frontend/.env.example frontend/.env
cp backend/.env.example backend/.env
```

4. Set `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` in `frontend/.env`.
5. Set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` in `backend/.env` (and at the repo root for seeding).
6. Install and run:

```bash
npm install
cd frontend && npm install && cd ..
npm run seed
cd frontend && npm run dev
```

In another terminal:

```bash
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend: http://localhost:3000  
API: http://localhost:8000

## Demo accounts

Password for all seed users: `DemoPass123!`

| Email | Role |
| --- | --- |
| platform@cms.local | Platform administrator |
| admin@demo.org | Organization administrator |
| officer@demo.org | Complaint officer |
| investigator@demo.org | Investigator |
| manager@demo.org | Department manager |
| supervisor@demo.org | Supervisor |
| auditor@demo.org | Auditor |
| admin@northwind.org | Admin of a second tenant |

Public tracking example: reference `CMP-2026-000001`, tracking code `DEMO-AA01`.

`admin@northwind.org` must not see Demo Organization complaints. That isolation is enforced by RLS.

## Security model

- RLS on every tenant table
- Permission keys (`complaints:assign`, `audit_logs:view`, …) rather than hardcoded role names
- Platform administrator is separate from organization administrator
- Public tracking requires reference **and** tracking code (hash stored, not the raw code)
- Internal comments and private attachments are excluded from public track results
- Profiles cannot change `organization_id` unless the actor is a platform admin
- Service role key is backend-only

## Email

`backend/src/main/java/com/cms/backend/service/EmailService.java` is the integration point for SMTP/Resend/Postmark. Templates exist; sending is logged until a provider is connected.

## SLA job

```bash
curl -X POST http://localhost:8000/api/jobs/sla -H "x-job-key: $JOB_SECRET"
```
