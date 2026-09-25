# Staging

Do not apply new SQL to production first. Use a second Supabase project.

## Create the project

1. In the Supabase dashboard, create `complaint-management-staging` in the same org as production.
2. Copy the URL, anon key, and service role into `.env.staging` from `.env.staging.example`.
3. Apply every migration in order:

```bash
# uses STAGING_PROJECT_REF from .env.staging
npm run supabase:staging
```

4. Seed against the staging URL (temporarily point `SUPABASE_URL` / `VITE_SUPABASE_URL` at staging, or export those vars for one command):

```bash
npm run seed
npm run test:isolation
```

## Hosted staging

Use a Render preview environment or a second pair of services (`cms-api-staging`, `cms-web-staging`) with the staging Supabase keys. Keep `CORS_ORIGINS` and Auth redirect URLs on the staging origin only.

Paid always-on instances and a custom domain belong on production after staging is green.

## Promotion

1. Merge to main only after CI is green.
2. Apply any new `supabase/migrations/000xx_*.sql` file to **staging**, smoke-test, then production.
3. Redeploy API then web. `VITE_*` values are build-time.
