# Supabase

Apply migrations in order:

1. `migrations/00001_schema.sql`
2. `migrations/00002_functions.sql`
3. `migrations/00003_rls.sql`
4. `migrations/00004_permissions.sql`
5. `migrations/00005_gap_closures.sql`
6. `migrations/00006_partial_closures.sql`

## Live project setup

Docker is not required. Use a hosted Supabase project.

1. Create a project at [https://supabase.com/dashboard](https://supabase.com/dashboard).
2. Create a personal access token at [https://supabase.com/dashboard/account/tokens](https://supabase.com/dashboard/account/tokens).
3. Put the token in the repository root `.env`:

```
SUPABASE_ACCESS_TOKEN=sbp_your_token
SUPABASE_PROJECT_NAME=complaint-management
```

4. From the repository root run:

```
npm run supabase:setup
```

The script creates or reuses the project, applies every migration, creates the `complaint-attachments` and `organization-logos` buckets, writes `frontend/.env` and `backend/.env`, and seeds the demo organization.

If the project already exists, also set `SUPABASE_PROJECT_REF`.

Then set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` and run `npm run seed` from the repository root if you skipped the setup script.

`00003_rls.sql` includes Storage policies for the private `complaint-attachments` bucket. Run it in a Supabase project, not a bare Postgres instance.

Demo accounts after seed (password `DemoPass123!`):

- `platform@cms.local`
- `admin@demo.org`
