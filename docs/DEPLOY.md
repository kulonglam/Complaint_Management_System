# Deploy

Supabase is already the hosted database and auth. You deploy two more services:

1. **cms-api** — Spring Boot (`backend/`)
2. **cms-web** — Vue static build (`frontend/`)

Use [Render](https://dashboard.render.com) for both. One GitHub repo, two services.

## 1. Push the repo

The Render blueprint reads `origin`. Commit and push the deploy files first if they are not on GitHub yet.

## 2. Backend on Render

1. [New Blueprint](https://dashboard.render.com/blueprints) → connect `Complaint_Management_System`.
2. Or **New Web Service** → this repo → Docker.
   - Dockerfile path: `backend/Dockerfile`
   - Docker context: `backend`
   - Health check: `/health`
3. Set env vars (copy values from local `backend/.env`, never commit them):

| Key | Value |
| --- | --- |
| `PORT` | `8000` |
| `SUPABASE_URL` | `https://ssdmcjtyatocjnoskkad.supabase.co` |
| `SUPABASE_SERVICE_ROLE_KEY` | service role key |
| `JOB_SECRET` | same as local |
| `CORS_ORIGINS` | leave `http://localhost:3000` until the frontend URL exists, then add it |
| `MAIL_ENABLED` | `true` |
| `MAIL_FROM` | `onboarding@resend.dev` |
| `RESEND_API_KEY` or `SMTP_*` | your mail settings |

4. Deploy. Open `https://<cms-api>.onrender.com/ready`. You want `{ "ok": true, "supabase": true }`.

Free Render services sleep after idle. The first request can take ~30s. For anything you call production, use a paid always-on instance and a custom domain.

Optional: set `SENTRY_DSN` on cms-api and `VITE_SENTRY_DSN` on cms-web (rebuild the static site after adding it). Set GitHub secrets `READY_URL` and `SITE_URL` so `.github/workflows/uptime.yml` can ping `/ready` and `/submit-complaint`.

Supabase Auth invites still use Auth SMTP. Point Auth → SMTP at the same verified domain you use for Resend.

## 3. Frontend on Render

1. **New Static Site** → same repo.
2. Root directory: `frontend`
3. Build: `npm ci && npm run build`
4. Publish: `dist`
5. Rewrite: `/*` → `/index.html`
6. Build env vars:

| Key | Value |
| --- | --- |
| `VITE_SUPABASE_URL` | same project URL |
| `VITE_SUPABASE_ANON_KEY` | anon/public key from Supabase → Settings → API |
| `VITE_API_URL` | `https://<cms-api>.onrender.com` with no trailing slash |

7. Deploy. You get `https://<cms-web>.onrender.com`.

`VITE_*` is baked in at build time. Change the API URL → rebuild the static site.

## 4. Point Auth and CORS at the live site

From the repo root:

```bash
node scripts/set-site-origin.mjs https://<cms-web>.onrender.com
```

Then on the **cms-api** service set:

```
CORS_ORIGINS=https://<cms-web>.onrender.com,http://localhost:3000
```

and redeploy the API.

GitHub OAuth app homepage/callback stay:

`https://ssdmcjtyatocjnoskkad.supabase.co/auth/v1/callback`

## 5. Smoke test

- Public: `/`, `/submit-complaint`, `/track-complaint`
- Staff: `/login` as `admin@demo.org` (2FA code)
- `GET https://<cms-api>.onrender.com/api/v1`
- `GET https://<cms-api>.onrender.com/ready`

## Alternative: Vercel frontend

`frontend/vercel.json` already rewrites the SPA. Set the same three `VITE_*` vars. Backend still needs Render (or any Docker host).

## Alternative: Docker on a VPS

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
