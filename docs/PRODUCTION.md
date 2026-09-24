# Production checklist

Do not call the system production-ready until every item below is true.

## Required

- [ ] Hosted Supabase project with migrations `00001`–`00007` applied
- [ ] `frontend/.env` has a real `VITE_SUPABASE_URL` and anon key
- [ ] `backend/.env` has the same project URL and the service role key
- [ ] Seed or first organization exists
- [ ] `GET /ready` returns `{ "ok": true, "supabase": true }`
- [ ] Tenant isolation script passes: `npm run test:isolation`
- [ ] CI is green
- [ ] Auth redirect URLs include the production origin and `/auth/callback`
- [ ] Storage buckets `complaint-attachments` (private) and `organization-logos` (public) exist

## Strongly recommended

- [ ] `MAIL_ENABLED=true` with SMTP, or `RESEND_API_KEY`
- [ ] Confirm a queued row in **Email log** after a public submission
- [ ] Enable Google or GitHub under Supabase Auth providers if you use SSO
- [ ] Ask at least one admin to enroll 2FA on `/profile`
- [ ] Change `JOB_SECRET` from `change-me`
- [ ] Restrict CORS to the real frontend origin
- [ ] Database backups enabled in the Supabase project
- [ ] Custom SMTP for Supabase Auth emails (invites / password reset)

## Optional later

- [ ] `STRIPE_SECRET_KEY` if you want card checkout (plans and usage limits already work without Stripe)
- [ ] Custom domain
- [ ] Sentry or another error reporter
