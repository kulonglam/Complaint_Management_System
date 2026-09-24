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

Spring Boot (`backend/`):

- `GET /health`
- `POST /api/users/invite` (Bearer token, `users:create`)
- `POST /api/users/reset-access` (Bearer token, `users:update`)
- `POST /api/jobs/sla` (`x-job-key`)

`/api/**` is rate limited. Email is sent from `email_outbox` when `MAIL_ENABLED=true`.
