# crm-kitty-be — documentation

## Reference

| Doc | What |
|-----|------|
| [API_FORMAT.md](API_FORMAT.md) | `ApiResponse<T>` contract, helpers, exceptions |
| [ENTITY_OWNERSHIP.md](ENTITY_OWNERSHIP.md) | Where JPA entities live (`domain/` vs `module/*/entity/`) |
| [MOBILE_API.md](MOBILE_API.md) | Mobile dev onboarding (auth, flows, staging) |
| [openapi.yaml](openapi.yaml) | Full OpenAPI 3 spec (auto-generated) |
| [bruno/](bruno/) | Bruno starter requests + environment |
| [../scripts/postman/](../scripts/postman/) | Postman env template (collection generated locally) |
| [TESTING.md](TESTING.md) | Test backlog (open items), DoD checklist |
| [../scripts/README.md](../scripts/README.md) | Flyway export, OpenAPI export, dev seed |
| [../src/main/resources/db/migration/README.md](../src/main/resources/db/migration/README.md) | Flyway migrations |

## API overview

**Response format:** all REST → `ApiResponse<T>` (see API_FORMAT). Exceptions: OAuth redirect, Monobank webhook.

**Removed modules (do not re-add):** waivers, inventory, gift certificates, promotions, public subdomain booking.

### Public (B2C)

| Method | Path | Auth |
|--------|------|------|
| GET | `/public/catalog/tattoos/**`, `/public/artists/**` | None |
| POST | `/public/consumer/requests`, `/generate`, `/try-on` | Consumer JWT |
| GET/PATCH | `/public/consumer/users/me/**`, `/public/consumer/requests/my` | Consumer JWT |

### CRM (Supabase JWT + RBAC)

| Module | Base path |
|--------|-----------|
| Appointments | `/appointments` |
| Clients | `/clients` |
| Staff | `/staff` |
| Requests | `/requests` |
| Settings | `/settings/**` |
| Files | `/files/**` |
| Catalog admin | `/catalog/admin/tattoos/seed`, `/retag` |
| Onboarding | `POST /onboarding` |
| Payments | `POST /payments/monobank/webhook` (public callback) |

Full route list: [openapi.yaml](openapi.yaml) or Swagger UI (`/api/swagger-ui.html` on dev/staging).

## Mobile / external developers

See **[MOBILE_API.md](MOBILE_API.md)** — auth, Postman/Bruno, staging setup. No backend repo access required.

## API docs workflow (OpenAPI / Postman)

**Source of truth:** Java controllers + DTOs in `src/main/`.  
**Not source of truth:** `docs/openapi.yaml` and Postman JSON — they are **generated snapshots**.

When you change an endpoint (path, body, response):

```bash
./scripts/export_openapi.sh          # regenerate yaml (+ optional local postman)
git add docs/openapi.yaml
```

Before PR / in CI:

```bash
./scripts/check_openapi_up_to_date.sh   # fails if you forgot export
```

**You never read 7000 lines manually.** Use:

- Swagger UI on staging/dev — groups `consumer` / `crm` / `all`
- PR diff on `docs/openapi.yaml` — only when API files changed (GitHub shows the delta)
- Tell mobile dev: re-import `openapi.yaml` in Postman/Bruno after deploy, or use live Swagger

**Prod:** Swagger off. Staging stays in sync with deployed branch + exported spec in git.

## Open work

- **i18n:** `messages_uk/en.properties` exist; most user-facing strings still hardcoded in services.
- **AI cost tracking:** consumer generate/try-on — logging OK, usage/cost metrics TODO.
- **Tests:** see [TESTING.md](TESTING.md) for remaining gaps (403 matrix, fixtures DRY, optional Testcontainers PG).

## Architecture notes (stable)

- CRM JWT vs consumer JWT — separate filter chains in `SecurityConfig`.
- Tenant scope — `findByIdAndTenantId` on repositories; `@RequirePermission` + OWNER bypass.
- Prod schema — Flyway (`ddl-auto: none`); dev may use `ddl-auto: update`.
