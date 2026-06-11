# InkFlow CRM — Backend

Spring Boot API for tattoo studio CRM and B2C consumer apps.

**Stack:** Java 21 · Spring Boot 3.4 · PostgreSQL · Supabase JWT · Flyway (prod)

**Base path:** `/api` (e.g. `http://localhost:8080/api`)

## Quick start

```bash
# configure .env (see Environment below)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

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
