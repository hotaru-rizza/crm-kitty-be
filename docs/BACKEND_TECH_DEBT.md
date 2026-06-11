# Backend Tech Debt & Quality Audit

> Зафиксировано: 2026-06-10 (обновлено 2026-06-10)  
> Репозиторий: `crm-kitty-be` (~300 Java files, 29 controllers)  
> Контекст: аудит после удаления мёртвых модулей (inventory, waiver, gift certificates, promotions)

**Решение по приоритетам:** P0 (security) — отложено на потом. Старт рефакторинга с P1+ после фиксации формата API.

Статусы: `[ ]` open · `[~]` in progress · `[x]` done · `[—]` deferred

---

## Summary

| Severity | Count | Focus |
|----------|-------|-------|
| Critical | 5 | Security, schema management |
| High | 8 | Architecture, RBAC, layer violations |
| Medium | 9 | API consistency, duplication, frontend sync |
| Low | 7 | Style, i18n, tests, validation |

**Tests today:** 1 smoke test (`InkFlowCrmApplicationTests.contextLoads`)

**Largest services:** `PaymentService` (~368 ln), `AppointmentService` (~390), `StaffService` (~115 CRUD only)

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

## P0 — Critical (Security & Data Integrity) `[—]` DEFERRED

> Намеренно отложено. Вернуться перед prod / публичным трафиком.

- [—] **P0-1. Onboarding JWT not verified**  
  `OnboardingController` uses `JWT.decode(token)` without signature verification. Endpoint is `permitAll`. Attacker can forge `sub`/`email` and create tenants.  
  → Fix: verify via `JwtTokenProvider` / Supabase JWKS (same as consumer auth).

- [—] **P0-2. Public admin endpoints on tattoo catalog**  
  `POST /public/catalog/tattoos/seed` and `/retag` — no auth. Anyone can trigger Unsplash/Gemini jobs and load DB/API.  
  → Fix: move behind CRM admin auth or disable in prod.

- [—] **P0-3. Monobank webhook without verification**  
  `POST /payments/monobank/webhook` is public; payload trusted as-is. Fake `success` can activate subscriptions or record payments.  
  → Fix: signature/IP validation + idempotent handler.

- [—] **P0-4. Cross-tenant IDOR**  
  - `GoogleCalendarController` / `GoogleCalendarSyncService` — `staffRepository.findById(id)` without `tenantId`  
  - `StaffService.upsertFaq` / `getFaq` — `getCurrentTenantId()` result not used to verify staff ownership  
  - Public `POST /requests` — `tenantId` accepted from body; leads can be injected into arbitrary tenants  
  → Fix: always `findByIdAndTenantId`; validate tenant on public endpoints.

- [—] **P0-5. Schema management (ddl-auto, no Flyway)**  
  Default/dev: `ddl-auto: update`, Flyway disabled, no files in `db/migration/`. Schema drift managed via manual SQL scripts.  
  → Fix: enable Flyway, baseline migration, `ddl-auto: validate` (or `none`) on non-dev profiles.

---

## P1 — High (Architecture & RBAC)

### Layer violations (fat controllers)

- [x] **P1-1. EmailController** — templates/settings/bulk send → `EmailManagementService` + `EmailSettingsMapper`  
  File: `module/email/controller/EmailController.java`

- [x] **P1-2. ConsumerUserController** — → `ConsumerUserService` + `ConsumerUserMapper`; DTOs in `dto/`  
  File: `module/consumer/controller/ConsumerUserController.java`

- [x] **P1-3. ConsumerBookingController** — → `ConsumerBookingService`  
  File: `module/consumer/controller/ConsumerBookingController.java`

- [x] **P1-4. AIGeneratorController** — → `AIGeneratorService`, `AIGeneratorPromptBuilder`, shared `GeminiImageClient`  
  File: `module/consumer/controller/AIGeneratorController.java`

- [x] **P1-5. TattooController** — → `TattooCatalogService` + `TattooMapper`  
  File: `module/catalog/controller/TattooController.java`

- [x] **P1-6. Service depends on controller DTO** — `AppointmentFilterRequest` moved to `module/appointment/dto/`  
  File: `module/appointment/service/AppointmentService.java`

### God services (split by bounded context)

- [x] **P1-7. StaffService (530 ln)** — split: Invite, Schedule, Pricing, Lifecycle, FAQ, Detail + `StaffLookup`  
  `StaffService` → CRUD + thin delegation (~175 ln)

- [x] **P1-8. AnalyticsService (540 ln)** — split into facade + 5 query services + support (CommissionCalculator, StaffUtilizationCalculator, AnalyticsTimeSeriesBuilder)

- [x] **P1-9. AppointmentService** — side effects → `AppointmentSideEffectService`; CRUD → `AppointmentMapper` + `AppointmentEntityResolver`

- [ ] **P1-10. PaymentService (368 ln)** — payments + refunds + receipt numbering  
  → Consider split if grows further.

### RBAC gaps

- [x] **P1-11. Missing `@RequirePermission`** — added to Leave, Service, Location, Email, Payment, File, Google Calendar, Monobank invoice

- [x] **P1-12. Permission enum incomplete** — added leaves, services, locations, emails, payments, files permissions

---

## P2 — Medium (Consistency & API Design)

- [x] **P2-1. Three API response formats** — unified `ApiResponse<T>` (see `docs/API_FORMAT.md`)

- [~] **P2-2. Map/JsonNode instead of DTOs** — Gemini image + text clients unified; VisionService on `GeminiTextClient`

- [~] **P2-3. Inconsistent error handling**  
  `IllegalArgumentException` → 400 via `GlobalExceptionHandler`. Remaining: `RuntimeException` in email settings path replaced with `ResourceNotFoundException`.

- [ ] **P2-4. Inconsistent module layout**  
  Good: `module/{name}/controller|service|dto|mapper` (appointment, client, staff, email, analytics).  
  Flat: audit, finance, google.  
  Split entity ownership: `domain/entity` vs `module/catalog/entity` vs `module/consumer/entity`.

- [ ] **P2-5. Duplicate parallel flows**  
  - `POST /public/consumer/requests` vs `POST /requests` (two public booking paths)  
  - Gemini client duplicated: ~~`AIGeneratorController` + `GeminiTattooService`~~ → shared `GeminiImageClient`; VisionService still separate  
  - Timezone: `Europe/Kiev` vs `Europe/Kyiv`

- [ ] **P2-6. Frontend ↔ backend mismatch**  
  CRM calls `GET/PATCH /api/settings/user` — **endpoint does not exist on backend**.  
  Frontend: `crm-kitty` → `SystemSettings.tsx` / `services/settings/api/settings.api.ts`

- [ ] **P2-7. CORS misconfiguration**  
  `SecurityConfig`: `allowedOriginPatterns("*")` + `allowCredentials(true)` — known anti-pattern.

- [ ] **P2-8. File delete without ownership check**  
  `FileController.deleteFile(key)` — authenticated but no tenant/path validation; possible cross-tenant deletion if key guessed.

- [ ] **P2-9. Public AI cost abuse**  
  `POST /public/consumer/generate` — no auth; burns Gemini API key.

---

## P3 — Low (Style, Maintainability, Tests)

### Magic strings & config drift

- [ ] **P3-1. Status/permission literals in JPQL and code**  
  `'DONE'`, `'CANCELLED'`, `'WORKING'` in queries; `"calendar.view_all"` inline instead of `Permission` enum.

- [~] **P3-2. Gemini config drift** — `GeminiProperties` covers image + text endpoints; VisionService migrated

- [ ] **P3-3. Timezone not centralized**  
  `Europe/Kiev` / `Europe/Kyiv` scattered; not in `application.yml`.

### i18n

- [ ] **P3-4. Dead i18n infrastructure**  
  `MessageUtil` + `messages_uk/en.properties` exist but unused. Messages hardcoded in Ukrainian/English in services and filters.

### N+1 queries

- [ ] **P3-5. PublicArtistService** — FAQ + showcase fetched per artist in loop (critical for B2C)  
  File: `module/catalog/service/PublicArtistService.java`

- [ ] **P3-6. StaffService.getStaffServices** — lazy `Service` per pricing row  
- [ ] **P3-7. ConsumerBookingController.getMyRequests** — lazy `Staff` per request  

  Note: `AppointmentRepository` uses `@EntityGraph` correctly — good pattern to replicate.

### Validation & silent failures

- [ ] **P3-8. Missing `@Valid`** on consumer endpoints: `AIGeneratorController`, `TryOnController`, `ConsumerUserController`; portfolio `BulkUploadRequest` without `@NotEmpty`.

- [ ] **P3-9. Appointment time validation** — no check that `endTime > startTime` on create/update.

- [ ] **P3-10. Silent exception swallowing**  
  `AppointmentService` — email failures now logged (LOG-1). Google sync errors still unchecked.

### Tests & docs

- [ ] **P3-11. No meaningful test coverage**  
  Only `contextLoads`. Priority targets: `AppointmentService`, `StaffService`, tenant isolation, public endpoints.

- [ ] **P3-12. Stale documentation**  
  `API_SPECIFICATION.md` still describes removed modules (waivers, inventory). `docs/FEATURES.md` largely obsolete.

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

| Phase | Scope | Goal |
|-------|-------|------|
| **P0** | Security hotfixes | Close holes before prod traffic |
| **P1** | RBAC + layer extraction | Safe multi-user CRM |
| **P2** | API/error consistency, Gemini client, `/settings/user` | Predictable contracts for frontends |
| **P3** | God service splits, Flyway, module layout | Long-term maintainability |
| **P4** | Tests, i18n, N+1 fixes | Confidence when changing code |

---

## Related cleanup (done 2026-06-10)

Removed modules & DB tables:
- Waiver (code was already gone; tables dropped)
- Inventory cluster (products, warehouses, stock, invoices, counts) — backend controllers/services removed
- Gift certificates — backend removed
- Promotions — fully removed (backend + frontend + DB script)
- `NotificationController` REST (internal `NotificationService` kept)

Still active: `module/booking/` (public subdomain booking)

Script: `drop_removed_modules.sql`

---

## Notes

- Add item IDs (`P0-1`, etc.) when starting work; mark `[x]` in this file.
- Do not delete items — move resolved ones to a "Done" section at the bottom if the list grows.

### Done

- 2026-06-10: Option A API format (`docs/API_FORMAT.md`)
- 2026-06-10: P1-1/2/3/6 layer extraction — EmailManagementService, ConsumerUserService, ConsumerBookingService, AppointmentFilterRequest dto
- 2026-06-10: P1-7/9/11/12 — StaffService split, AppointmentSideEffectService, RBAC permissions
- 2026-06-10: EmailService refactor — EmailLogMapper, AppointmentEmailComposer, EmailTenantContextLoader
- 2026-06-10: AnalyticsService split — 5 query services + support calculators; controller unchanged
- 2026-06-10: Dead module cleanup — warehouse/inventory/giftcertificate controllers removed; promotion fully dropped
- 2026-06-10: Controller logging pass — Payment, Transaction, Settings, Email, File, Subscription, Finance, Portfolio, Tattoo, Booking, Consumer
