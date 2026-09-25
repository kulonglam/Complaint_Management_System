# Production checklist

Deploy steps live in [DEPLOY.md](DEPLOY.md). Staging is in [STAGING.md](STAGING.md). Incidents are in [RUNBOOKS.md](RUNBOOKS.md).

Do not call the system enterprise-production until every **required** item is true. Items that need your accounts (paid host, DNS, Sentry org, verified domain) are marked **you**.

## Required

- [ ] Staging Supabase project with migrations `00001`–`00010` applied first ([STAGING.md](STAGING.md))
- [ ] Same migrations applied to production after staging smoke
- [ ] `GET /ready` returns `{ "ok": true, "supabase": true }`
- [ ] Tenant isolation script passes: `npm run test:isolation`
- [ ] CI is green, including `npm audit --audit-level=high`
- [ ] Auth redirect URLs include the production origin and `/auth/callback`
- [ ] Storage buckets `complaint-attachments` (private) and `organization-logos` (public) exist
- [ ] `JOB_SECRET` is not `change-me`
- [ ] CORS is the real frontend origin
- [ ] Organization setting **Require 2FA for administrators** is on
- [ ] At least one restore drill recorded in [RUNBOOKS.md](RUNBOOKS.md)

## Strongly recommended (you)

- [ ] Paid always-on host (Render Standard or equivalent) so `/ready` does not sleep
- [ ] Custom domain on the web app and API
- [ ] `SENTRY_DSN` on cms-api and `VITE_SENTRY_DSN` on cms-web
- [ ] GitHub `READY_URL` / `SITE_URL` secrets for the uptime workflow
- [ ] Verified sending domain in Resend (or SMTP) and the same host as Supabase Auth SMTP
- [ ] Database backups confirmed; PITR if the Supabase plan allows it
- [ ] WAF / bot protection in front of `/submit-complaint` (Cloudflare or the host)

## Optional later

- [ ] Uganda payments: `PESAPAL_CONSUMER_KEY` / `PESAPAL_CONSUMER_SECRET`, `MTN_*`, and `AIRTEL_*` (Stripe is not used)
- [ ] ClamAV / vendor malware scan on `complaint-attachments` (`scan_status` is already on the row)
- [ ] External IdP (Entra / Okta) beyond GitHub/Google
- [ ] Independent pentest and a written DPIA
