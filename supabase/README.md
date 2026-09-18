# Supabase

Apply migrations in order:

1. `migrations/00001_schema.sql`
2. `migrations/00002_functions.sql`
3. `migrations/00003_rls.sql`
4. `migrations/00004_permissions.sql`

Then set `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` and run `npm run seed` from the repository root.

`00003_rls.sql` includes Storage policies for the private `complaint-attachments` bucket. Run it in a Supabase project, not a bare Postgres instance.
