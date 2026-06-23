# InkFlow CRM — Backend

Spring Boot API for tattoo studio CRM and B2C consumer apps.

**Stack:** Java 21 · Spring Boot 3.4 · PostgreSQL · Supabase JWT · Flyway (prod)

**Base path:** `/api` (e.g. `http://localhost:8080/api`)

## Quick start

```bash
# .env in repo root is loaded automatically (see LocalEnvLoader).
# Or set the same vars in IntelliJ Run → Environment variables.
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

**Dev profile (`dev`) always uses remote Supabase PostgreSQL** — there is no localhost fallback.

Set in **IntelliJ → Run → Edit Configurations → Environment variables** (or copy `.env.example`):

- `SPRING_DATASOURCE_URL` — Supabase JDBC URL (`sslmode=require`, add `connectTimeout=10&socketTimeout=30`)
- `DB_USERNAME`, `DB_PASSWORD`
- `SUPABASE_JWT_ISSUER`, `SUPABASE_JWKS_URI`

If `SPRING_DATASOURCE_URL` is missing, startup fails immediately with a clear error (instead of hanging on `127.0.0.1:5432`).

**Still hangs after env is set?** Check the JDBC host in logs (`Dev startup: connecting to Supabase…`). Wrong password, pooler vs direct port, or missing `sslmode=require` — usual causes.

Swagger UI (dev/staging): [http://localhost:8080/api/swagger-ui.html](http://localhost:8080/api/swagger-ui.html)

## Environment

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials |
| `SUPABASE_JWT_ISSUER` / `SUPABASE_JWKS_URI` | CRM JWT validation |
| `FRONTEND_URL` | CRM web origin (CORS) |
| `CONSUMER_FRONTEND_URL` | Consumer app origin (CORS) |
| `R2_*` | Cloudflare R2 file storage |
| `RESEND_*` | Transactional email |
| `GEMINI_API_KEY` | AI generate / try-on |

See `src/main/resources/application.yml` for the full list.

## Profiles

| Profile | Use |
|---------|-----|
| `dev` | Local dev, `ddl-auto: update`, Swagger on, auto CRM user |
| `staging` | Shared staging, Swagger on |
| `prod` | Flyway migrations, Swagger off |

## Tests

```bash
./mvnw test
```

JaCoCo report: `target/site/jacoco/index.html`

## API docs

| Resource | Path |
|----------|------|
| OpenAPI spec | `docs/openapi.yaml` |
| Mobile onboarding | `docs/MOBILE_API.md` |
| Response format | `docs/API_FORMAT.md` |
| Regenerate spec | `./scripts/export_openapi.sh` |
| Check spec is current | `./scripts/check_openapi_up_to_date.sh` |

## Docker

```bash
docker build -t inkflow-crm-be .
docker run -p 8080:8080 --env-file .env inkflow-crm-be
```

## Project layout

```
src/main/java/com/inkflow/crm/
  module/          # Feature modules (controller, service, dto)
  domain/          # Core entities & repositories
  config/          # Security, OpenAPI, properties
  security/        # JWT filters, RBAC
docs/              # API spec & guides
scripts/           # Flyway export, OpenAPI export, seed SQL
```
