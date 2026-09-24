# Backend

Spring Boot 3.4 API for privileged operations that must not run in the browser: user invite, SLA processing, and email dispatch.

## Layout

```
src/main/java/com/cms/backend
  config/      application, security, RestClient, .env loading
  security/    Supabase JWT filter and JSON auth handlers
  client/      Supabase Admin / PostgREST adapter
  controller/  HTTP endpoints
  service/     invite, SLA, email
  dto/         request and response records
  exception/   API errors
```

## Endpoints

- `GET /` — service info
- `GET /health` — liveness
- `GET /actuator/health` — actuator health
- `POST /api/users/invite` — Bearer token and `users:create`
- `POST /api/jobs/sla` — `x-job-key` header

SLA jobs also run on a cron (`SLA_CRON`, default every 15 minutes) when `SLA_JOB_ENABLED=true` and Supabase is configured.

## Run

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

From the repo root: `npm run dev:backend`.

The service listens on port `8000` and loads `backend/.env`. `JAVA_HOME` must point at a JDK 21+.
