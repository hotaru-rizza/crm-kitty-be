# Backend Tech Debt & Quality Audit

> Зафиксировано: 2026-06-10 (обновлено 2026-06-11)  
> Репозиторий: `crm-kitty-be` (~365 Java files, 29 controllers)  
> Контекст: аудит после удаления мёртвых модулей (inventory, waiver, gift certificates, promotions)

Статусы: `[ ]` open · `[~]` in progress · `[x]` done · `[—]` deferred

---

## Summary

| Severity | Count | Focus |
|----------|-------|-------|
| Critical | 0 | P0 closed |
| High | 0 | P1 closed |
| Medium | 1 | Gemini Vision |
| Low | 2 | i18n properties unused, cost tracking |

**Tests today:** 20 tests — `contextLoads` + unit tests + integration tests (AppointmentService tenant isolation, RequestController auth/validation, public endpoint security)

**Largest services:** `AppointmentService` (~270), `GoogleCalendarSyncService` (~312), `MonobankService` (~280)

---

## Cross-cutting — Logging & Readability

> Стандарты зафиксированы в `.cursor/rules/java-backend.mdc` (секция «Readability & logging»).

### Logging

- [x] **LOG-1. Silent failures without logs** — `AppointmentService` email side-effects now log warnings
- [x] **LOG-2. No structured logging on mutations** — all CRM + public mutation controllers log entityId/action
- [x] **LOG-3. Consumer/AI endpoints under-logged** — generate/try-on/booking/onboarding log success; cost tracking — TODO

### Code style & readability

- [x] **STYLE-1. God services / delegation facades** — StaffService slim CRUD; SettingsService → RolePermissionService; AppointmentService → mapper + entity resolver
- [x] **STYLE-2. Section comments & controller javadoc** — removed across Payment, File, Monobank, Staff, Transaction, Monobank/Subscription services
- [x] **STYLE-3. DRY mappers** — SummaryMapper, AppointmentMapper, ProjectMapper, EmailTemplateMapper, EmailLogMapper, TransactionMapper, PhoneUtils
- [x] **STYLE-4. Method granularity & spacing** — EmailService → AppointmentEmailComposer + EmailTenantContextLoader; controllers with blank lines before return

---

## API format — DONE (Option A)

- [x] Единый `ApiResponse<T>` на всех REST endpoints (кроме OAuth redirect и payment webhook)
- Spec: `docs/API_FORMAT.md`
- Helpers: `ApiResponses.ok/created/page/empty/requireConsumer`
- Frontend: `client-tattoo-web` — `apiGetData` / `apiGetPage`; `crm-kitty` — email + audit переведены на `data` + `pagination`

---

## P0 — Critical (Security & Data Integrity)

- [x] **P0-1. Onboarding JWT not verified**  
  `OnboardingController` → `JwtTokenProvider.verifyToken()` (Supabase JWKS). Idempotent: returns existing tenant if staff with `authUserId` already exists.

- [x] **P0-2. Public admin endpoints on tattoo catalog**  
  `seed`/`retag` removed from `/public/catalog/tattoos`. Moved to `POST /catalog/admin/tattoos/seed|retag` with `@RequirePermission("settings.access")`.

- [x] **P0-3. Monobank webhook without verification**  
  Idempotent handler (skip if already `success` + transaction recorded). Amount cross-check. Remote status verify via `GET /api/merchant/invoice/status`. Sandbox mode skips remote verify.

- [x] **P0-4. Cross-tenant IDOR**  
  - Google Calendar → `StaffLookup` + signed OAuth state (`GoogleOAuthStateSigner`, HMAC, 10 min TTL)  
  - Staff FAQ → `StaffLookup.requireStaff()` (was fixed earlier)  
  - `POST /requests` → removed from `permitAll`; requires CRM JWT + `requests.create`; `tenantId` removed from body; always uses `SecurityUtils.getCurrentTenantId()`

- [x] **P0-5. Schema management (Flyway)**  
  Prod: `ddl-auto: none`, Flyway enabled with `baseline-on-migrate`.  
  `V1__baseline.sql` exported from Supabase (`public` schema, ~1900 lines). Script: `./scripts/export_flyway_baseline.sh` (reads `.env`).

---

## P1 — High (Architecture & RBAC) — DONE

All P1 items closed. See Done section at bottom.

---

## P2 — Medium (Consistency & API Design)

- [x] **P2-1. Three API response formats** — unified `ApiResponse<T>` (see `docs/API_FORMAT.md`)

- [~] **P2-2. Map/JsonNode instead of DTOs** — Gemini image + text clients unified; VisionService on `GeminiTextClient`

- [~] **P2-3. Inconsistent error handling**  
  `IllegalArgumentException` → 400 via `GlobalExceptionHandler`. Remaining: `RuntimeException` in email settings path replaced with `ResourceNotFoundException`.

- [x] **P2-4. Inconsistent module layout**  
  Good: `module/{name}/controller|service|dto|mapper` (appointment, client, staff, email, analytics, audit, finance, google).  
  Remaining split entity ownership: `domain/entity` vs `module/catalog/entity` vs `module/consumer/entity`.

- [x] **P2-5. Duplicate parallel flows**  
  Intentional separation by design:  
  - `POST /public/consumer/requests` — B2C client app (consumer JWT, tenant from artist)  
  - `POST /requests` — CRM internal leads (CRM JWT, tenant from auth context)  
  Subdomain public booking removed.

- [x] **P2-6. Frontend ↔ backend mismatch**  
  CRM calls `GET/PATCH /api/settings/user` — implemented via `UserSettingsService` (`Staff.uiLanguage`, `Staff.startPage`)

- [x] **P2-7. CORS misconfiguration**  
  Prod `SecurityConfig` → explicit origins from `inkflow.cors.allowed-origins` in `application.yml`. Dev profile keeps permissive CORS.

- [x] **P2-8. File delete without ownership check**  
  New uploads: `{tenantId}/{folder}/...`. Delete requires tenant prefix match; legacy keys without prefix rejected.

- [x] **P2-9. Public AI cost abuse**  
  `POST /public/consumer/generate` and `/try-on` require consumer JWT (`ApiResponses.requireConsumer`). Booking submit also requires auth.

---

## P3 — Low (Style, Maintainability, Tests)

### Magic strings & config drift

- [x] **P3-1. Status/permission literals in JPQL and code**  
  JPQL enum literals in Appointment/Staff/Leave repositories.  
  `@RequirePermission(Permission.*)` on all controllers.  
  Native SQL in `TattooRepository` → `:status` param with `TattooStatus.READY.name()`.

- [~] **P3-2. Gemini config drift** — `GeminiProperties` covers image + text endpoints; VisionService migrated

- [x] **P3-3. Timezone not centralized**  
  `InkflowProperties` + `application.yml`; all runtime code migrated. Entity default on `Tenant` remains as schema default.

### i18n

- [~] **P3-4. Dead i18n infrastructure**  
  Removed unused `MessageUtil`. `I18nConfig` + `messages_uk/en.properties` kept for future; messages still hardcoded in services.

### N+1 queries — DONE

- [x] **P3-5. PublicArtistService** — batch FAQ + `PortfolioShowcaseResolver`
- [x] **P3-6. StaffService.getStaffServices** — `@EntityGraph(service)`
- [x] **P3-7. ConsumerBookingController.getMyRequests** — `@EntityGraph(assignedStaff)`

### Validation & silent failures

- [x] **P3-8. Missing `@Valid`** on consumer endpoints + portfolio bulk upload

- [x] **P3-9. Appointment time validation** — `@ValidAppointmentTimeRange` on create/update DTOs

- [x] **P3-10. Silent exception swallowing**  
  Google sync: structured warn logs with `tenantId` + `appointmentId`; `syncCalendarSafely` in side-effect service; OAuth callback → `BusinessRuleException`.

### Tests & docs

- [x] **P3-11. Test coverage**  
  Unit: `AppointmentTimeRangeValidatorTest`, `GoogleOAuthStateSignerTest`, `PortfolioShowcaseResolverTest`, `StaffLookupTest`, `RequestServiceTest`.  
  Integration: `AppointmentServiceIntegrationTest`, `RequestControllerIntegrationTest`, `PublicEndpointSecurityIntegrationTest` (test profile + `TestSecurityConfig`).

- [x] **P3-12. Stale documentation**  
  Added `docs/API_SPECIFICATION_SUMMARY.md`; deprecation notice on root `API_SPECIFICATION.md`.

---

## What's OK (keep as reference patterns)

- Multi-chain security: CRM JWT vs consumer Supabase JWT in `SecurityConfig`
- `SubscriptionFilter`, `DemoTenantFilter` for SaaS constraints
- Core services use tenant-scoped repository methods (`findByIdAndTenantId`)
- `open-in-view: false`
- `PermissionAspect` + OWNER bypass
- `AppointmentRepository` `@EntityGraph` for N+1 prevention
- External integrations (R2, Resend, HuggingFace, Unsplash, Monobank) mostly in `application.yml`

---

## Recommended refactor phases

| Phase | Scope | Status |
|-------|-------|--------|
| **P0** | Security hotfixes | Done |
| **P1** | RBAC + layer extraction | Done |
| **P2** | API/error consistency | ~95% — entity ownership split open |
| **P3** | Tests, i18n, magic strings | ~95% |
| **P4** | i18n, Gemini Vision, cost tracking | Next |

---

## Related cleanup (done 2026-06-10)

Removed modules & DB tables:
- Waiver (code was already gone; tables dropped)
- Inventory cluster (products, warehouses, stock, invoices, counts) — backend controllers/services removed
- Gift certificates — backend removed
- Promotions — fully removed (backend + frontend + DB script)
- Public subdomain booking (`module/booking/`, `/public/book/**`) — removed; client app uses `POST /public/consumer/requests`
- `NotificationController` REST (internal `NotificationService` kept)

Script: `drop_removed_modules.sql`

---

## Notes

- Add item IDs (`P0-1`, etc.) when starting work; mark `[x]` in this file.
- Do not delete items — move resolved ones to a "Done" section at the bottom if the list grows.

### Done

- 2026-06-10: Option A API format (`docs/API_FORMAT.md`)
- 2026-06-10: P1 layer extraction + StaffService split + RBAC permissions
- 2026-06-10: EmailService refactor; AnalyticsService split; dead module cleanup
- 2026-06-11: P1-10 PaymentService split; P2-6 `/settings/user`
- 2026-06-11: Removed public subdomain booking; consumer app booking via `/public/consumer/requests`
- 2026-06-11: Portfolio tenant checks; Google Calendar IDOR partial; analytics timezone
- 2026-06-11: File storage tenant-prefixed keys; consumer `@Valid`; N+1 fixes; timezone cleanup
- 2026-06-11: **Security wave** — onboarding JWT verify + idempotency; catalog admin auth; Monobank webhook hardening; requests tenant injection fix; OAuth state signing; CORS config; AI/booking auth required; appointment time validation; unit tests; docs summary
- 2026-06-11: **Quality wave** — `@RequirePermission(Permission.*)`; JPQL enum literals; Google sync logging; removed MessageUtil; StaffLookupTest; Flyway export script
- 2026-06-11: **Layout wave** — audit/finance/google → `controller|service` subpackages; TattooRepository status param; RequestServiceTest
- 2026-06-11: **Integration tests** — TestSecurityConfig (test profile), AppointmentService/RequestController/PublicEndpoint security tests (20 total)
- 2026-06-11: **Flyway baseline** — `V1__baseline.sql` from Supabase public schema; script reads `.env`
