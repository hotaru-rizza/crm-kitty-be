# Permissions & entity access — implementation plan

> **Status:** Ready for implementation  
> **Owner:** backend  
> **Related:** [TECH_DEBT.md](./TECH_DEBT.md) TD-001, [LOCATION_SCOPE_PLAN.md](./LOCATION_SCOPE_PLAN.md)

This document is the **source of truth** for the next security hardening sprint.  
Follow phases in order unless noted. Each phase must ship with tests before moving on.

---

## Problem statement

Permissions today have **three working layers** and **one missing layer**:

| Layer | Mechanism | Status |
|-------|-----------|--------|
| Role | `owner` / `admin` / `artist` | ✅ |
| Permission strings | `@RequirePermission` + `role_permissions` | ✅ |
| List data scope | `view_all` vs `view_own` in list queries | ✅ partial |
| Location scope | `LocationScope` + `X-Location-Id` | ✅ partial (see Phase L) |
| **Entity access by ID** | Guard on `GET/PATCH/DELETE /{id}` | ❌ **TD-001** |

**Rule going forward:** list scope ≠ entity access. Every `/{id}` read/mutation must call an access guard when the user lacks `*_view_all`.

Reference implementation: `GoogleCalendarAccessGuard` (self OR elevated permission).

---

## Target model (who can do what)

### Roles (fixed)

| Role | Intent |
|------|--------|
| **owner** | Full tenant control; bypasses `@RequirePermission`; only role that edits permission matrix |
| **admin** | Studio manager; all permissions except `settings.roles` by default; configurable |
| **artist** | Own calendar + own clients/projects (read); no finance/settings/staff admin |

### Permission pairs: `*_view_all` vs `*_view_own`

| Module | `view_all` | `view_own` means |
|--------|------------|------------------|
| Calendar | all appointments in tenant (within location scope) | `appointment.artist.id == currentUserId` |
| Clients | all clients | client has appointment/project with current artist |
| Projects | all projects | `project.artist.id == currentUserId` OR session artist at location (list rule) |

### Entity guard decision tree

```
if user has *_view_all (or owner/admin with equivalent) → allow (tenant + location rules still apply)
else if user has *_view_own only → require ownership relationship on entity
else → 403
```

### Mutations without `view_all`

| Action | Artist rule |
|--------|-------------|
| `POST /appointments` | force `artistId = currentUserId` unless `calendar.view_all` |
| `PATCH /appointments/{id}` | guard: assigned artist only |
| `DELETE /appointments/{id}` | guard: assigned artist only |
| `GET /appointments/{id}` | guard: assigned artist only |
| Photo upload/delete on appointment | same as PATCH |
| `GET /clients/{id}` | guard: worked-with artist |
| `PATCH /clients/{id}` | artist has no `clients.edit` by default → blocked at permission layer |
| `GET /projects/{id}` | guard: lead artist OR visibility rule |
| `PATCH /projects/{id}` | artist has no `projects.edit` by default → blocked at permission layer |

---

## Phase L — Location scope test coverage (do first)

**Goal:** Cross-location behaviour is fully regression-tested at HTTP + service level before permission guards add more complexity.

**Why first:** Recent location work (header scope, project multi-location visibility, entity scope) must not break when we touch appointment/client guards.

### L.1 — Already covered ✅

| Area | Test file | What it proves |
|------|-----------|----------------|
| `LocationScope` resolution | `LocationScopeTest` | header > query param; empty = all; artist denied foreign query param; entity scope ignores workspace header |
| `LocationContextFilter` | `LocationContextFilterTest` | `X-Location-Id` sets tenant context; artist denied foreign header; entity scope flag |
| `UserPrincipal.hasAccessToLocation` | `UserPrincipalTest`, `SecurityUtilsTest` | admin all locations; artist limited list |
| Project multi-location visibility | `ProjectServiceIntegrationTest` (3 tests) | orphan excluded; lead at A; cross-location via session artist |
| Location CRUD + cross-tenant | `LocationControllerIntegrationTest`, `TenantCrossTenantIsolationIntegrationTest` | tenant isolation on location ops |
| Default location rules | `LocationServiceTest` | cannot delete default / last location |

### L.2 — Gaps to implement ✅

Add **`LocationScopeIntegrationTest`** (new class, `@IntegrationTest`) — HTTP-level, two locations in one tenant:

| # | Test | Endpoint | Assert | Status |
|---|------|----------|--------|--------|
| L2.1 | Appointments list scoped by header | `GET /appointments?from=&to=` + `X-Location-Id: A` | returns only location A appointments | ✅ |
| L2.2 | Appointments list at B | same with header B | excludes A-only appointment | ✅ |
| L2.3 | Appointments list all locations | no header | returns A + B | ✅ |
| L2.4 | Artist denied foreign header | artist auth + header for location not in JWT `location_ids` | 403 | ✅ |
| L2.5 | Calendar query scoped | `GET /appointments/calendar` + header A | same as L2.1 | ✅ |
| L2.6 | Transactions list scoped | `GET /transactions` + header A | only location A txs | ✅ |
| L2.7 | Projects list scoped | `GET /projects` + header A | uses existing visibility rules + location filter | ✅ |
| L2.8 | Entity scope ignores header on profile data | `GET /appointments` + `X-Entity-Scope: true` | workspace header does not hide cross-location profile data | ✅ |

**`AppointmentServiceIntegrationTest`** location cases:

| # | Test | Assert | Status |
|---|------|--------|--------|
| L2.9 | Appointment at location B not returned when filtering A | service-level via `TenantContext.setCurrentLocation` | ✅ |

**Done criteria for Phase L:** all L2.* tests green ✅

---

## Phase 1 — `AppointmentAccessGuard` (TD-001 core)

### 1.1 Implementation

**New file:** `module/appointment/support/AppointmentAccessGuard.java`

```java
void requireView(Appointment appointment);
void requireEdit(Appointment appointment);
void requireCancel(Appointment appointment);
```

Logic:

- `owner` → pass
- `calendar.view_all` → pass
- `calendar.view_own` / `calendar.edit` / `calendar.cancel` → require `appointment.getArtist().getId().equals(currentUserId)`
- else → `AccessDeniedException`

**Wire into:**

| Method | Guard |
|--------|-------|
| `AppointmentService.getAppointmentById` | `requireView` |
| `AppointmentService.updateAppointment` | `requireEdit` |
| `AppointmentService.deleteAppointment` | `requireCancel` |
| `AppointmentItemService.*` (if separate entry) | `requireEdit` |
| Photo add/delete handlers | `requireEdit` |
| `AppointmentService.createAppointment` | if !`view_all` → reject or override `artistId` to self |

Do **not** put guards in controller — service layer only (matches `GoogleCalendarAccessGuard` usage pattern).

### 1.2 Unit tests — `AppointmentAccessGuardTest`

| # | Scenario | Expected |
|---|----------|----------|
| P1.U1 | owner views any appointment | pass |
| P1.U2 | admin with `view_all` views any | pass |
| P1.U3 | artist views own appointment | pass |
| P1.U4 | artist views another artist's appointment | `AccessDeniedException` |
| P1.U5 | artist edits own | pass |
| P1.U6 | artist edits other's | denied |
| P1.U7 | artist cancels own | pass |
| P1.U8 | artist cancels other's | denied |
| P1.U9 | user with no calendar permissions | denied |

### 1.3 Integration tests — extend `AppointmentControllerIntegrationTest`

Seed: tenant with **two artists** (A, B), one appointment each.

| # | Scenario | Auth | Expected |
|---|----------|------|----------|
| P1.I1 | Artist A PATCH own appointment | artist A | 200 |
| P1.I2 | Artist A PATCH artist B appointment | artist A | **403** |
| P1.I3 | Artist A GET artist B appointment | artist A | **403** |
| P1.I4 | Admin PATCH any | admin | 200 |
| P1.I5 | Artist A POST with `artistId=B` | artist A | **403** or forced to A (document chosen behaviour in test name) |
| P1.I6 | Cross-tenant PATCH | tenant A auth, tenant B id | 404 |

**Done criteria:** P1.U* + P1.I* green ✅; TD-001 marked resolved in TECH_DEBT.md ✅

---

## Phase 2 — `ClientAccessGuard` & `ProjectAccessGuard`

### 2.1 `ClientAccessGuard`

**New file:** `module/client/support/ClientAccessGuard.java`

- `requireView(Client client)` — `clients.view_all` OR (`clients.view_own` AND client worked with current artist)
- Reuse existing spec/helper: `ClientSpecifications.workedWithArtist(currentUserId)` or equivalent EXISTS query

**Wire into:** `getClientById`, `getClientBalance` (view), any nested client reads that bypass list filter.

### 2.2 `ProjectAccessGuard`

**New file:** `module/project/support/ProjectAccessGuard.java`

- `requireView(Project project)` — `projects.view_all` OR (`projects.view_own` AND artist is lead OR matches list visibility rule)
- `requireEdit(Project project)` — `projects.edit` + same ownership if no `view_all`

**Wire into:** `getProjectById`, `updateProject`, `deleteProject`, photo mutations.

### 2.3 Tests

**Unit:** `ClientAccessGuardTest`, `ProjectAccessGuardTest` — mirror P1.U* matrix.

**Integration:** extend `ClientControllerIntegrationTest`, `ProjectControllerIntegrationTest`

| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| P2.I1 | Artist with `view_own` GET client they worked with | 200 | ✅ |
| P2.I2 | Artist GET client never worked with | 403 | ✅ |
| P2.I3 | Admin GET any client | 200 | ✅ |
| P2.I4 | Artist with `view_own` GET own lead project | 200 | ✅ |
| P2.I5 | Artist GET project of another lead (no session link) | 403 | ✅ |
| P2.I6 | Cross-tenant GET client/project | 404 | ✅ (pre-existing) |

**Done criteria for Phase 2:** unit + integration tests green ✅

---

## Phase 3 — Create-path hardening

| # | Change | Test | Status |
|---|--------|------|--------|
| P3.1 | `createAppointment`: artist without `view_all` cannot assign other artist | P1.I5 | ✅ |
| P3.2 | `createProject`: artist without `projects.create` stays blocked (permission layer) | existing | ✅ |
| P3.3 | Payment process: verify `payments.process` + appointment ownership | `PaymentControllerIntegrationTest` | ✅ |

---

## Phase 4 — Legacy `requireOwner()` alignment

**Policy (documented):** destructive deletes are **owner-only**. Edit/create stays on permission strings (`locations.edit`, `services.edit`, `staff.edit`, `finance.create`).

| Operation | Controller | Service | Status |
|-----------|------------|---------|--------|
| Delete staff | no `@RequirePermission` | `requireOwner()` | ✅ owner-only |
| Delete location | no `@RequirePermission` (was `locations.edit`) | `requireOwner()` | ✅ aligned |
| Delete service | no `@RequirePermission` (was `services.edit`) | `requireOwner()` | ✅ aligned |
| Delete transaction | no `@RequirePermission` | `requireOwner()` | ✅ owner-only |

**Tests:** admin gets 403 on all four delete endpoints — integration tests added.

**Done criteria for Phase 4:** ✅

---

## Phase 5 — Documentation & cleanup

- [ ] Update [TECH_DEBT.md](./TECH_DEBT.md): close TD-001, add TD-004 if Phase 4 deferred
- [ ] Add permission matrix to this doc or link from README
- [ ] Frontend: no change required for guards (API returns 403); optional: hide edit buttons when 403 on prefetch — out of scope unless requested
- [ ] Remove stale `?locationId=` callers (LOCATION_SCOPE_PLAN Phase 5) — separate PR

---

## Test conventions (mandatory)

Follow [.cursor/rules/testing.mdc](../.cursor/rules/testing.mdc):

1. **Unit tests** for every guard class — all branches, no Spring context.
2. **Integration tests** for every wired endpoint — happy + denied + cross-tenant.
3. Name: `should<Expected>When<Condition>`.
4. Never stop at `status().isOk()` — assert body, DB state, or count.
5. Cross-tenant negative case for every new `/{id}` guard.

### CI commands

```bash
# Fast guard unit tests
./mvnw test -Dtest=AppointmentAccessGuardTest,ClientAccessGuardTest,ProjectAccessGuardTest -Djacoco.skip=true

# Location scope integration
./mvnw test -Dtest=LocationScopeIntegrationTest,ProjectServiceIntegrationTest,LocationScopeTest -Djacoco.skip=true

# Full appointment security
./mvnw test -Dtest=AppointmentControllerIntegrationTest,AppointmentAccessGuardTest -Djacoco.skip=true
```

---

## Implementation order (checklist)

```
Phase L  — Location cross-scope HTTP tests     [x]
Phase 1  — AppointmentAccessGuard + tests      [x]
Phase 2  — Client/Project guards + tests         [x]
Phase 3  — Create-path hardening                 [x]
Phase 4  — requireOwner alignment                [x]
Phase 5  — Docs & TECH_DEBT close                [x]
```

**Sprint complete.** Optional follow-ups: frontend 403 UX, LOCATION_SCOPE_PLAN Phase 5 (remove stale `?locationId=`).
