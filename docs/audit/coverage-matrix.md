# Audit coverage matrix

> Living inventory of mutation endpoints vs audit logging.
> Update this file whenever a service method gains or loses audit coverage.
> Source of truth for Phase 2 completion — not `plan.md` checkboxes alone.

## Root cause (why random actions were missing)

1. **Opt-in manual wiring** — each mutation needs an explicit `auditRecorder.record()` or `@Audited`. No repository hook, no CI gate yet.
2. **Phase 2 marked "done" while ~80% of mutations were never on the checklist** — plan listed ~10 priority actions; codebase has 55+ tenant-facing mutation methods.
3. **Split code paths** — same user action can hit different services (e.g. staff deactivate: `StaffService.deleteStaff` vs `StaffLifecycleService.deactivateStaff`; payments: manual vs Monobank webhook).
4. **Silent no-op** — `AuditRecorder.record()` returns when `SecurityUtils.getCurrentUser()` is null; use `recordSystem()` for webhooks / public endpoints.
5. **No contract tests** — endpoints ship without asserting an audit row exists (integration test pending async listener strategy).

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Audited |
| ⚠️ | Partially audited (some paths missing) |
| ❌ | Not audited |
| — | Intentionally out of scope (derived/system/read-only) |

## Coverage by module

### Appointments

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `AppointmentSideEffectService` | create/update/delete/cancel/reschedule/status | ✅ | CREATE/UPDATE/DELETE/CANCEL/RESCHEDULE / APPOINTMENT |
| `AppointmentService` | addPhoto / deletePhoto | ✅ | UPDATE / APPOINTMENT |
| `StaffLifecycleService` | cancelFutureAppointments | ⚠️ | Bulk cancel summarized on deactivate, not per appointment |

### Clients

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `ClientService` | create / update / delete | ✅ | CREATE/UPDATE/DELETE / CLIENT |
| `ClientService` | blacklist toggle | ✅ | BLACKLIST_ADD/REMOVE / CLIENT |

### Requests

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `RequestService` | createRequest | ✅ | CREATE / REQUEST |
| `RequestService` | updateRequestStatus | ✅ | STATUS_CHANGE / REQUEST |
| `RequestService` | convertToClient | ✅ | CONVERT / REQUEST + CREATE / CLIENT |
| `RequestService` | deleteRequest | ✅ | DELETE / REQUEST |

### Staff

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `StaffService` | createStaff | ✅ | CREATE / STAFF |
| `StaffService` | updateStaff | ✅ | UPDATE / STAFF |
| `StaffService` | deleteStaff | ✅ | STAFF_DEACTIVATE / STAFF |
| `StaffLifecycleService` | deactivateStaff | ✅ | STAFF_DEACTIVATE / STAFF |
| `StaffLifecycleService` | reactivateStaff | ✅ | UPDATE / STAFF |
| `StaffPricingService` | all mutations | ✅ | UPDATE / STAFF |
| `StaffScheduleService` | updateSchedule | ✅ | SCHEDULE_SET / SCHEDULE |
| `StaffInviteService` | inviteStaff | ✅ | STAFF_INVITE / STAFF |
| `StaffInviteService` | acceptInvite | ✅ | CREATE / STAFF via `recordSystem()` |
| `StaffFaqService` | upsertFaq | ✅ | UPDATE / STAFF via `@Audited` |
| `UserSettingsService` | updateCurrentUserSettings | ✅ | UPDATE / STAFF |

### Leave

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `LeaveService` | create / status / cancel / delete | ✅ | CREATE/STATUS_CHANGE/CANCEL/DELETE / LEAVE |

### Projects

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `ProjectService` | create / update / delete | ✅ | CREATE/UPDATE/DELETE / PROJECT |
| `ProjectService` | addPhoto / deletePhoto | ✅ | UPDATE / PROJECT |
| `ProjectProgressSyncService` | syncProject | — | derived totals, not user action |

### Locations

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `LocationService` | create / update / delete | ✅ | CREATE/UPDATE/DELETE / LOCATION |
| `LocationService` | assignStaff | ✅ | UPDATE / LOCATION |

### Catalog (studio services)

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `ServiceService` | create / update / delete | ✅ | CREATE/UPDATE/DELETE / SERVICE |
| `PortfolioService` | upload / update / showcase / delete | ✅ | CREATE/UPDATE/DELETE / STAFF |

### Finance

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `TransactionService` | createTransaction | ✅ | TXN_INCOME/TXN_EXPENSE / TRANSACTION |
| `TransactionService` | deleteTransaction | ✅ | DELETE / TRANSACTION |
| `PaymentProcessingService` | processPayment | ✅ | PAYMENT / APPOINTMENT |
| `PaymentProcessingService` | tips / deposit side-effects | ✅ | TXN_INCOME / UPDATE APPOINTMENT |
| `RefundProcessingService` | processRefund | ✅ | TXN_EXPENSE / TRANSACTION |
| `MonobankService` | handleWebhook → payment | ✅ | PAYMENT via `recordSystem()` |
| `CategoryConfigService` | upsert / create / delete | ✅ | CREATE/UPDATE/DELETE / TRANSACTION |

### Settings

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `SettingsService` | updateCompanySettings | ✅ | UPDATE / TENANT |
| `SettingsService` | updateClientDormancySettings | ✅ | UPDATE / TENANT |
| `RolePermissionService` | updateRolePermissions | ✅ | PERMISSIONS_CHANGE / ROLE |

### Email / portfolio / integrations

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `EmailTemplateService` | create / update / delete | ✅ | CREATE/UPDATE/DELETE / EMAIL_TEMPLATE |
| `BulkEmailService` | sendBulk | ✅ | UPDATE / TENANT |
| `GoogleCalendarSyncService` | connect / disconnect | ✅ | UPDATE / STAFF |

### Auth

| Service | Method | Audit | Action / Entity |
|---------|--------|-------|-----------------|
| `AuthLoginAuditService` | recordLoginIfNeeded | ✅ | LOGIN / STAFF |

## Remaining gaps

| Item | Status | Notes |
|------|--------|-------|
| `StaffLifecycleService.cancelFutureAppointments` per-appointment rows | ⚠️ | Summary on deactivate is enough for MVP |
| Integration / contract tests | ❌ | Need async-aware test profile |
| CI gate on matrix drift | ❌ | Future: grep vs matrix in CI |

## Enforcement (Phase 2.5)

| Mechanism | Purpose |
|-----------|---------|
| `@Audited` + `AuditedAspect` | Declarative audit on new service methods |
| `AuditRecorder.recordSystem()` | Webhooks / no auth context |
| `coverage-matrix.md` | PR checklist — no silent gaps |
| `AuditedAspectTest` / `AuditRecorderSystemTest` | Regression gate for aspect + system actor |

## How to mark done

When wiring a method:
1. Add `auditRecorder.record(...)` (or `@Audited`)
2. Use `AuditLabelFormatter` for human-readable labels
3. Set `subjectClientId` when a client is involved
4. Use `recordSystem(tenantId, ...)` when no auth context
5. Update this matrix: ❌ → ✅
6. Add integration test asserting audit row (target state)
