# Backend

Spring Boot 3 API for privileged operations that must not run in the browser.

## Endpoints

- `GET /health`
- `POST /api/users/invite` — requires a Supabase Bearer token and `users:create`
- `POST /api/jobs/sla` — requires `x-job-key`

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

The service listens on port `8000` and loads `backend/.env`. `JAVA_HOME` must point at a JDK, not the Oracle `javapath` shim.
