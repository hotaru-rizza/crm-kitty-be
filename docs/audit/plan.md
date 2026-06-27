# Audit & Observability — Implementation Plan

> Spec for expanding the existing (placeholder) audit system into a real,
> tenant-scoped activity log with a controlled action taxonomy, plus the
> observability baseline (Sentry + correlation IDs).
>
> **This document is the source of truth. Do not invent alternative
> approaches.** Locked decisions are in the box below. If a step seems to
> need a different design, stop and ask — do not improvise.

## Locked decisions (do NOT change without asking)

| Decision | Choice |
|----------|--------|
| How audit is recorded | **Domain events** → `AuditEvent` published by services, written by a single `@Async @EventListener`. Mirrors existing `NotificationEventListener`. |
| "Client" filter | **Add `subject_client_id` column** to `audit_log`. Filter shows everything about a client, not only `entityType=CLIENT`. |
| Action taxonomy | **Curated enum** for real crm-kitty features. Do NOT copy Integrica 1:1 (no `Sold`, `Debited`, `Calculated` — those features don't exist). |
| Action/entity storage | `varchar` columns backed by Java enums. **No DB CHECK constraint** (future actions must not break migrations). Validate at app layer. |
| Magic strings | **Forbidden.** Always use `AuditAction` / `AuditEntityType` enums, never string literals like `"CREATE"`. |

## Current state (baseline — what already exists)

**Backend (`crm-kitty-be-new`)**
- `domain/entity/AuditLogEntry.java` — `tenant_id, actor_id, actor_name, action, entity_type, entity_id, entity_label, details, ip_address (unused), created_at`.
- `module/audit/service/AuditLogService.java` — `log(...)` / `logCurrent(...)`, both `@Async`, swallow exceptions (never break the request).
- `module/audit/controller/AuditLogController.java` — `GET /audit-log`, filters: `actorId, entityType, from, to, page, size`. Gated by `CALENDAR_VIEW_ALL` (wrong — to be replaced).
- `module/audit/dto/AuditLogDto.java`.
- `domain/repository/AuditLogRepository.java` — `JpaSpecificationExecutor`.
- **Coverage = ~5%**: only appointment CREATE/DELETE via `AppointmentSideEffectService.auditCreated/auditDeleted` using magic strings.
- Latest Flyway migration: **V21** → next is **V22**.

**Frontend (`crm-kitty`)**
- `features/settings/tabs/AuditLog/` — page, table, constants, icons, utils.
- Action metadata split across `constants/auditLog.icons.tsx` + `utils/auditLog.utils.ts`. Only 5 actions known (CREATE/UPDATE/DELETE/PAYMENT/LOGIN).
- Entity filter hardcoded to 3 (APPOINTMENT/CLIENT/TRANSACTION). **No action filter.**
- `services/audit/` — `api/audit.api.ts`, `hooks/useAuditLog.ts`, `types/audit.types.ts`.
- `components/ui/ClientPicker/ClientFilterPicker.tsx` — reuse for the "Client" filter.
- `components/ui/StaffFilterPicker` / `ArtistPickerDropdown` — reuse for "Employee".

---

## Phase 0 — Type backbone ("прошивка типами")

Goal: replace free-form strings with controlled enums on both ends. No behavior change yet.

### Backend
1. `domain/enums/AuditAction.java` — enum with `value`, `label` (uk), `category`.
   Curated set:

   | Enum | Integrica analog | Category |
   |------|------------------|----------|
   | `CREATE` | Created / Added | GENERIC |
   | `UPDATE` | Modified | GENERIC |
   | `DELETE` | Deleted | GENERIC |
   | `CANCEL` | Canceled | APPOINTMENT |
   | `CONFIRM` | Confirmed | APPOINTMENT |
   | `RESCHEDULE` | Transferred / Moved | APPOINTMENT |
   | `STATUS_CHANGE` | — | REQUEST/APPOINTMENT |
   | `CONVERT` | — | REQUEST |
   | `PAYMENT` | Paid | FINANCE |
   | `TXN_INCOME` | Replenished | FINANCE |
   | `TXN_EXPENSE` | Debited | FINANCE |
   | `BLACKLIST_ADD` | Added to blacklist | CLIENT |
   | `BLACKLIST_REMOVE` | Removed from blacklist | CLIENT |
   | `SCHEDULE_SET` | Set work schedule | STAFF |
   | `STAFF_INVITE` | — | STAFF |
   | `STAFF_DEACTIVATE` | — | STAFF |
   | `PERMISSIONS_CHANGE` | Changed access rights | ROLE |
   | `LOGIN` | — | AUTH |

   Deferred (no feature yet, do NOT add): `Sold`, `Debited`(inventory), `Calculated`(payroll).
   Add `fromValue(String)` like the existing `Permission` / `RequestSource` enums.

2. `domain/enums/AuditEntityType.java` — `APPOINTMENT, CLIENT, TRANSACTION, REQUEST, STAFF, ROLE, SCHEDULE, LOCATION` (+ `fromValue`).

3. Refactor `AuditLogService` signatures to accept `AuditAction` / `AuditEntityType` instead of `String`. Persist `enum.getValue()`.

4. Replace the 2 existing appointment audit calls (`AppointmentSideEffectService`) with enum-based calls (kept working through Phase 1; moved onto events in Phase 2).

### Frontend
5. Single source of truth: `features/settings/tabs/AuditLog/constants/auditActions.ts`
   - `AUDIT_ACTION` value map + per-action `{ color, icon, category }`.
   - Merge `auditLog.icons.tsx` action map into it (keep entity icons separate is fine).
   - `getActionMeta` reads from this single map; unknown action → graceful fallback (already present).

**DO NOT** add a DB CHECK constraint on `action`/`entity_type`. **DO NOT** keep any `"CREATE"`-style literals after this phase.

---

## Phase 1 — Filters (Integrica parity: Employee / Client / Type)

Goal: the three dropdowns from the Integrica screenshot + existing date range.

### Backend
1. **Migration `V22__audit_subject_client.sql`**
   - `ALTER TABLE audit_log ADD COLUMN subject_client_id uuid NULL;`
   - `CREATE INDEX idx_audit_subject_client ON audit_log (tenant_id, subject_client_id);`
2. `AuditLogEntry` — add `subjectClientId` field (nullable). `AuditLogService.log(...)` — accept optional `subjectClientId`.
3. `AuditLogController GET /audit-log` — add params:
   - `actions` → `List<String>` (multi-select Type filter)
   - `clientId` → `UUID`
   - keep `actorId, from, to, page, size`. (`entityType` may stay for back-compat.)
4. `AuditLogService.getLog(...)` — extend the `Specification`:
   - `action IN (:actions)` when present
   - `subjectClientId = :clientId` when present
   - keep existing tenant + actor + date predicates.

### Frontend
5. `services/audit/` — extend `AuditLogQueryParams` + `fetchAuditLog` with `actions?: string[]`, `clientId?: string`. Update `useAuditLog` query key.
6. `features/settings/tabs/AuditLog/AuditLog.tsx` — replace the "from-scratch" filter row with **Employee / Client / Type / Period**:
   - **Employee** — reuse existing staff/artist picker (multi).
   - **Client** — reuse `components/ui/ClientPicker/ClientFilterPicker.tsx`.
   - **Type** — multi-select of `AuditAction`, options grouped by `category`.
   - **Period** — keep current quick-period + custom range.
   - Wire active-filter chips (pattern already in `auditLogActiveFilters.utils.ts`).
7. Table: action column already renders via `getActionMeta`; just ensure all new actions have icon/color in the Phase 0 map.

**Result after Phase 1:** working, visible feature — typed actions + Employee/Client/Type/Period filters. This is the agreed stopping point for the current batch.

---

## Phase 2 — Coverage via domain events (later)

Goal: actually record the important actions across modules. Do this through events, not scattered service calls.

1. Event type: `module/audit/event/AuditEvent.java` (record): `tenantId, actorId, actorName, action (AuditAction), entityType (AuditEntityType), entityId, entityLabel, subjectClientId, details, ipAddress`.
2. `module/audit/event/AuditEventListener.java` — `@Async @EventListener` → `auditLogService.log(...)`. Same shape as `NotificationEventListener`.
3. Services publish via `ApplicationEventPublisher` (thin, single responsibility). Provide a small `AuditRecorder` helper that builds the event from `SecurityUtils.getCurrentUser()` so call sites stay one-liners.
4. Migrate appointment CREATE/DELETE off direct `AuditLogService` calls onto events.
5. Wire critical actions (priority order):
   - Client: create / update / delete / blacklist add+remove → set `subjectClientId`.
   - Appointment: cancel / reschedule / status change → `subjectClientId` = appointment's client.
   - Request: status change / convert.
   - Finance: payment / income / expense → `subjectClientId` when the txn is tied to a client.
   - Staff: invite / deactivate / schedule set.
   - Roles: permissions change.

**Guardrail:** every `subjectClientId` must be set wherever a client is involved, or the "Client" filter stays empty for that action.

---

## Phase 3 — Permissions & hygiene (later)

1. `Permission.AUDIT_VIEW` ("audit.view") — add to enum + assign to owner/admin roles. Replace the `CALENDAR_VIEW_ALL` gate on `AuditLogController`.
2. Populate `ip_address` (column exists, currently null) — pull from request in the event/recorder.
3. **Retention**: scheduled cleanup of `audit_log` older than N (config-driven, e.g. `inkflow.audit.retention-days`, default ~730). Reuse `SchedulerRun` infra. GDPR + storage cost.

---

## Phase 4 — Observability (separate from audit, later)

Audit answers "who did what" (for the studio). This answers "why did it break" (for us).

1. **Sentry** (priority #1): backend Spring integration + frontend `@sentry/react`. Tags: `tenantId`, `userId`, `environment`. This is the fastest ROI for "a client says it's broken".
2. **Correlation ID + MDC**: servlet filter sets `X-Request-Id`; put `tenantId`, `userId`, `requestId` into MDC so every log line is traceable end-to-end.
3. Centralized logs (Loki/Datadog) + JSON logging: **defer** until traffic/tenant count grows. stdout + Sentry covers the early stage.

---

## Effort (rough)

| Phase | Scope | Estimate |
|-------|-------|----------|
| 0 | Enums + FE single-source map | 0.5 day |
| 1 | Filters (BE params + migration + FE selects) | 1 day |
| 2 | Event infra + wire ~10 actions | 1.5 days |
| 3 | Permission + ip + retention | 0.5 day |
| 4 | Sentry + MDC | 1 day |

## Anti-goals (do NOT do)

- No `Sold` / `Debited` / `Calculated` actions (features don't exist).
- No DB CHECK constraint on action/entity columns.
- No magic action strings — enums only.
- No per-tenant separate log files / databases — `tenant_id` column + filtering only.
- No full field-diff audit ("changed X from A to B") in this batch — `details` free text is enough for now.
- No log aggregator / JSON logging until Phase 4 and only when justified by scale.

---

## Phase 2.5 — Enforcement (implemented)

Goal: stop relying on manual one-off `auditRecorder.record()` calls as the only guard.

1. **`@Audited` annotation + `AuditedAspect`** — declarative audit on service methods via SpEL (`#result`, params, `@bean` refs). See `StaffFaqService.upsertFaq` as reference.
2. **`AuditRecorder.recordSystem(tenantId, …)`** — webhooks/schedulers without user principal (Monobank payment webhook).
3. **`docs/audit/coverage-matrix.md`** — living checklist of every mutation; update on every PR that adds/changes endpoints.
4. **`AuditedAspectTest` + `AuditRecorderSystemTest`** — unit regression for aspect and system actor.
5. **Rollout rule:** new mutation endpoints MUST either use `@Audited` or call `AuditRecorder` + update coverage matrix.

Remaining manual wiring is acceptable for complex side-effects (appointments via `AppointmentSideEffectService`) until migrated incrementally.

