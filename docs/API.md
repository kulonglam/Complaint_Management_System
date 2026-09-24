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
- `mark_login`

Spring Boot (`backend/`):

- `GET /health`
- `POST /api/users/invite` (Bearer token, `users:create`)
- `POST /api/jobs/sla` (`x-job-key`)
