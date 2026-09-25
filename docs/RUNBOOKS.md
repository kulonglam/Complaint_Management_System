# Runbooks

## Public submit is failing

1. Open `GET https://<cms-api>/ready`. `ok` must be true.
2. In Supabase → Logs → Postgres, look for `submit_public_complaint` errors.
3. Confirm migrations `00008` (rate-limit `actor`) and `00009` (`digest`) and `00010` are applied.
4. Confirm the public portal is enabled on the organization.

## Mail is not leaving the outbox

1. Email log should show `PENDING`, then `SENT`, or `FAILED` after 8 attempts.
2. `MAIL_ENABLED=true` and either `RESEND_API_KEY` or SMTP must be set on **cms-api**, then redeploy.
3. Resend `onboarding@resend.dev` only delivers to the account owner until a domain is verified.
4. For Auth invites and password reset, set Custom SMTP under Supabase → Auth → SMTP to the same verified domain.

## Administrator locked out of 2FA

1. Another organization administrator can invite a replacement user.
2. A platform administrator can reset access from Users → Reset access.
3. Do not disable `require_admin_mfa` in production except as a break-glass, and turn it back on.

## Restore drill

```bash
npm run ops:restore-drill
```

Restore the backup into a **new** project (or staging). Point `.env.staging` at it. Run `npm run test:isolation` and sign in. Record the date here:

| Date | Operator | Restore minutes | Isolation | Notes |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## Rotate a leaked secret

Rotate immediately if a key was pasted into chat, committed, or shown in a screenshot:

- Supabase service role and anon key (dashboard → API)
- `JOB_SECRET` (Render + local `backend/.env`)
- Resend / SMTP password
- GitHub OAuth client secret
- `SUPABASE_ACCESS_TOKEN`

Then redeploy cms-api and cms-web.

## Uptime

GitHub Action `.github/workflows/uptime.yml` curls `/ready` and `/submit-complaint` when you set repository secrets or variables `READY_URL` and `SITE_URL`.
