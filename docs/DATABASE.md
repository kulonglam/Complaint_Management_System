# Database

See `supabase/migrations/` for the live schema.

Core entities: organizations, profiles, roles, permissions, departments, categories, complaints and child tables (assignments, comments, attachments, investigations, resolutions, feedback, escalations), SLA policies, notifications, audit logs.

Complaint references are unique per organization (`CMP-YYYY-000001`). Tracking codes are stored as SHA-256 hashes.

Important indexes include organization_id, reference_number, status, priority, department_id, assigned_to, category_id, created_at, and due_date.
