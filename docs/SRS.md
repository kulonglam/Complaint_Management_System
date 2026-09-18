# Software Requirements Specification

The Complaint Management System is a multi-tenant SaaS application for intake, investigation, resolution, SLA monitoring, reporting, and audit of complaints.

Functional scope is implemented in the Vue client, Supabase PostgreSQL/Auth/Storage, and a thin privileged API. Authorization uses permission keys. Tenant isolation is enforced with Row Level Security.
