# API

Most staff UI reads still go through Supabase with RLS. Spring Boot is the privileged and versioned HTTP API.

## Versioned REST (`/api/v1`)

Auth: `Authorization: Bearer <Supabase access token>`.

Spring validates the token as an OIDC resource server (JWKS at `{SUPABASE_URL}/auth/v1/.well-known/jwks.json`, optional `SUPABASE_JWT_SECRET` for HS256, then Auth introspection).

| Method | Path | Access |
| --- | --- | --- |
| `GET` | `/api/v1` | Public catalog |
| `GET` | `/api/v1/me` | Authenticated |
| `GET` | `/api/v1/users` | `users:view` |
| `GET` | `/api/v1/users/{id}` | `users:view` |
| `POST` | `/api/v1/users` | `users:create` (invite) |
| `POST` | `/api/v1/users/{id}/reset-access` | `users:update` |
| `GET` | `/api/v1/complaints` | `complaints:view` / `view_all` / `view_assigned` |
| `GET` | `/api/v1/complaints/{id}` | same |
| `POST` | `/api/v1/complaints` | `complaints:create` |
| `PATCH` | `/api/v1/complaints/{id}` | status transition |
| `GET` | `/api/v1/organizations` | platform admin |
| `GET` | `/api/v1/emails` | `settings:view` |
| `POST` | `/api/v1/jobs/sla` | `x-job-key` |

Collection responses use `{ "data": [...], "page": { "offset", "limit", "total" } }`.
Errors use `{ "message", "code", "status" }`.

OpenAPI: `/swagger-ui.html` and `/v3/api-docs`.

## Compatibility aliases

- `POST /api/users/invite`
- `POST /api/users/reset-access`
- `POST /api/jobs/sla`

## Health

- `GET /health`
- `GET /ready` (503 if Supabase is not configured)

## Database RPCs

Still used by the Vue app and some Spring writes:

- `submit_public_complaint`
- `track_public_complaint`
- `transition_complaint_status`
- `assign_complaint`
- `escalate_complaint`
- `dashboard_metrics`
- `process_sla_jobs`
- `search_complaints`
- `complaint_timeline`
- `organization_usage`

`/api/**` is rate limited. Email is stored in `email_outbox` and delivered when Resend or SMTP is configured.
