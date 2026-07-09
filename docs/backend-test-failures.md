# Backend test failures (2026-07-09)

Run: `./mvnw clean test -Dmaven.test.failure.ignore=true`

**Result:** 1087 tests — **6 failures, 11 errors** (17 issues in 6 classes)

**After fixes:** 1087 tests — **0 failures, 0 errors** ✅

## Summary

| Class | Fail | Err | Root cause |
|-------|------|-----|------------|
| `ClientServiceTest` | 1 | 0 | NPE — `clientStatsService` null in `getClientById` |
| `PaymentServiceTest` | 0 | 2 | NPE — `appointmentAccessGuard` null |
| `RefundProcessingServiceTest` | 0 | 1 | NPE — `appointmentAccessGuard` null |
| `ProjectServiceTest` | 3 | 8 | NPE — `projectAccessGuard` null |
| `DerivedBulkMutationSafetyTest` | 1 | 0 | `StaffInviteRepository.deleteByAcceptedAtIsNullAndExpiresAtBefore` — forbidden derived bulk delete |
| `TenantCrossTenantIsolationIntegrationTest` | 1 | 0 | `emailTemplateList_returnsOnlyCurrentTenantTemplates` — unscoped list query leaked foreign templates; test also expected count=1 but list seeds 9 builtins |

## Fixes applied

1. **Unit tests** — add `@Mock` for new dependencies (`ClientStatsService`, `ClientAccessGuard`, `AppointmentAccessGuard`, `ProjectAccessGuard`) + lenient `doNothing()` stubs.
2. **StaffInvite cleanup** — replace `deleteBy...` with `findBy...` → `deleteAll`.
3. **EmailTemplateService.list** — use `findAllByTenantIdOrderBy...` instead of unscoped `findAllByOrderBy...`.
4. **Integration test** — assert subject isolation (`A only` present, `B only` absent) instead of `size() == 1`.

## Per-test detail

### ClientServiceTest
- `getClientById_rejectsForeignTenant` — `clientStatsService.syncFromAppointments` called before repo lookup.

### PaymentServiceTest
- `getAppointmentPaymentSummary_delegatesToCalculator`
- `getAppointmentPayments_returnsPaymentList`

### RefundProcessingServiceTest
- `processRefund_reducesAppointmentPrepaymentForDeposit` — guard called when appointment linked.

### ProjectServiceTest (all methods touching get/update/delete/complete)
- `shouldReturnProjectWhenFoundById`
- `getProjectById_rejectsForeignTenant`
- `shouldPersistInProgressDefaultsWhenCreatingProject` (update/delete/complete variants)
- etc. — any path calling `projectAccessGuard.requireView/requireEdit`.

### DerivedBulkMutationSafetyTest
- Violation: `StaffInviteRepository.deleteByAcceptedAtIsNullAndExpiresAtBefore`

### TenantCrossTenantIsolationIntegrationTest
- `emailTemplateList_returnsOnlyCurrentTenantTemplates` — cross-tenant leak via global `findAllByOrderBy...`
