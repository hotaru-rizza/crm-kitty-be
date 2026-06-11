# Mobile API guide

Onboarding doc for mobile developers (consumer app + CRM admin). **No backend repo access required.**

## Quick links

| Resource | Location |
|----------|----------|
| Interactive docs (staging/dev) | `{baseUrl}/api/swagger-ui.html` |
| OpenAPI spec (YAML) | [openapi.yaml](openapi.yaml) |
| Response format | [API_FORMAT.md](API_FORMAT.md) |
| Postman | Import [openapi.yaml](openapi.yaml), or `./scripts/export_openapi.sh` → `docs/postman/` (gitignored) |
| Postman environment template | [scripts/postman/staging.postman_environment.example.json](../scripts/postman/staging.postman_environment.example.json) |
| Bruno collection | [bruno/](bruno/) (git-friendly alternative) |

Regenerate spec after API changes:

```bash
./scripts/export_openapi.sh
```

## Base URL

All paths below are relative to **`/api`** (Spring `context-path`).

| Environment | Example |
|-------------|---------|
| Local dev | `http://localhost:8080/api` |
| Staging | `https://api-staging.your-domain.com/api` (set by DevOps) |

Swagger UI groups:

- **consumer** — B2C mobile (`/public/**`)
- **crm** — admin mobile (`/appointments`, `/clients`, …)
- **all** — full surface

## Authentication

Both apps use **Supabase Auth**. After login, send:

```http
Authorization: Bearer <access_token>
```

### CRM (admin mobile)

- Same Supabase project as web CRM (`crm-kitty`).
- JWT must include custom claims (via Supabase hook): `tenant_id`, `role`, `location_ids`.
- Hook SQL: [scripts/supabase/custom_access_token_hook.sql](../scripts/supabase/custom_access_token_hook.sql)
- Optional header for location-scoped operations: `X-Location-Id: <uuid>`

Roles: `OWNER`, `ADMIN`, `ARTIST` — permissions enforced server-side (`@RequirePermission`).

### Consumer (B2C mobile)

- Supabase consumer project / anon key (same as `client-tattoo-web`).
- Required for `/public/consumer/**` (except none — all consumer routes need JWT except public catalog reads).

### No auth required

| Method | Path |
|--------|------|
| GET | `/public/catalog/tattoos/**` |
| GET | `/public/artists/**` |

### Special cases

| Endpoint | Auth |
|----------|------|
| `POST /onboarding` | Supabase JWT (verified, not decoded) |
| `POST /payments/monobank/webhook` | Monobank signature (server-to-server) |
| Google OAuth callback | Browser redirect, not JSON API |

## Response envelope

Every JSON endpoint returns `ApiResponse<T>`:

```json
{ "success": true, "data": { ... } }
```

Paginated lists add `pagination`. Errors: `{ "success": false, "error": { "code", "message", "timestamp" } }`.

See [API_FORMAT.md](API_FORMAT.md).

## Typical flows

### Consumer app (mirror `client-tattoo-web`)

1. Supabase sign-in → store `access_token`
2. Feed: `GET /public/catalog/tattoos?page=0&size=20`
3. Artist: `GET /public/artists`, `GET /public/artists/{id}`
4. Profile: `GET /public/consumer/users/me`
5. Save tattoo: `POST /public/consumer/users/me/saved-tattoos/{tattooId}`
6. AI generate: `POST /public/consumer/generate` (JSON body — see Swagger)
7. Try-on: `POST /public/consumer/try-on`
8. Booking request: `POST /public/consumer/requests`

### CRM admin mobile

1. Supabase sign-in (staff user) → JWT with tenant claims
2. If new tenant: `POST /onboarding` once
3. Calendar: `GET /appointments?from=…&to=…`
4. Client card: `GET /clients/{id}`
5. Create appointment: `POST /appointments`
6. Files: `POST /files/presigned-upload` → upload to R2 → confirm

## File uploads

1. `POST /files/presigned-upload` with `{ folder, contentType, fileName }`
2. PUT file to returned `uploadUrl`
3. Use `publicUrl` / key in subsequent API calls

## Staging setup (DevOps)

1. Deploy backend with profile **`staging`** (enables Swagger: `OPENAPI_ENABLED=true` or `spring.profiles.active=staging`)
2. Seed data: `psql "$DATABASE_URL" -f scripts/db/seed.sql`
3. Create Supabase test users (CRM owner + consumer)
4. Share staging URL + test credentials + this doc

**Production:** Swagger disabled by default. Do not enable publicly without VPN/basic auth.

## Code generation (optional)

From `docs/openapi.yaml`:

```bash
# Kotlin (Retrofit)
openapi-generator-cli generate -i docs/openapi.yaml -g kotlin -o mobile-api-client

# Swift
openapi-generator-cli generate -i docs/openapi.yaml -g swift5 -o ios-api-client
```

## Getting help

- Broken contract → compare mobile request with Swagger **Try it out**
- 401 → token expired or wrong Supabase project
- 403 → CRM permission missing for role
- 402 → subscription inactive (`SubscriptionFilter`)
