# API

Most data access is through Supabase with RLS.

RPC:

- `submit_public_complaint`
- `track_public_complaint`
- `list_public_catalog`
- `transition_complaint_status`
- `assign_complaint`
- `escalate_complaint`
- `dashboard_metrics`
- `process_sla_jobs`
- `provision_organization`
- `create_organization`
- `submit_public_feedback`
- `link_public_attachments`
- `review_resolution`
- `report_metrics`
- `mark_login`
- `mark_logout`
- `search_complaints`
- `complaint_timeline`
- `add_complaint_comment`
- `submit_resolution`
- `organization_usage`
- `assert_plan_capacity`
- `assert_named_rate_limit`

Spring Boot (`backend/`):

- `GET /health`
- `GET /ready` (503 if Supabase is not configured)
- `POST /api/users/invite` (Bearer token, `users:create`)
- `POST /api/users/reset-access` (Bearer token, `users:update`)
- `POST /api/jobs/sla` (`x-job-key`)

`/api/**` is rate limited in the database when Supabase is configured, otherwise in memory. Email is stored in `email_outbox` and delivered when `MAIL_ENABLED=true` or `RESEND_API_KEY` is set.
