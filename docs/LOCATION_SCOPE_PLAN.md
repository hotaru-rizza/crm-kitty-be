# Location scope — plan & conventions

## Status

| Phase | Scope | Status |
|-------|--------|--------|
| 0 | Docs + tech debt register | ✅ |
| 1 | `LocationScope` + `X-Location-Id` header (web fetcher) | ✅ |
| 2 | Calendar, finance list/stats, analytics | ✅ |
| 3 | Default location (`is_default`, non-deletable, switchable) | ✅ |
| 4 | Mailings macro context from active/default location | ✅ |
| 5 | Unify existing `?locationId=` callers onto header + fallback | Planned |

---

## Two isolation layers (do not mix)

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| **Tenant** | Hibernate `@Filter` on `tenant_id` | Hard boundary between studios |
| **Location** | `LocationScope` + optional `X-Location-Id` | Workspace filter **inside** one tenant |

There is **no** Hibernate location filter. «All locations» mode must stay possible for owner/admin.

---

## Tenant-wide (no location filter)

| Area | Reason |
|------|--------|
| **Clients** | One person belongs to the studio, not a branch |
| **Services** | Shared catalog / pricing |
| **Settings, roles, categories** | Tenant configuration |
| **Mailings audience** | Campaigns go to all clients/staff; **not** scoped by location |
| **Email templates, audit (tenant)** | Shared |

---

## Location-scoped (filter when switcher ≠ «All locations»)

| Area | Field / join |
|------|----------------|
| Calendar / appointments | `appointment.location_id` |
| Finance / transactions | `transaction.location_id` |
| Projects | `project.location_id` |
| Staff list | `staff_locations` |
| Leaves | staff → locations |
| Requests | `request.location_id` |
| Dashboard & analytics | appointments + transactions in range |
| Location detail stats | already per location |

---

## Mailings (special case)

- **Recipients**: always tenant-wide (all clients / selected / staff).
- **Template macros** (`{{address}}`, future location fields): resolve from **active location context**:
  1. `X-Location-Id` / switcher selection (if set)
  2. else **default location** for tenant (`locations.is_default = true`)
- Default location: created at onboarding, **cannot be deleted**, owner can **change which location is default**.

---

## Single strategy: `LocationScope`

All location-scoped reads use one entry point:

```java
Optional<UUID> filter = LocationScope.resolveFilter(queryParamLocationId);
// empty = all locations (owner/admin "Всі локації")
// present = filter queries by location.id
```

**Resolution order**

1. `TenantContext.getCurrentLocation()` — set from `X-Location-Id` after access check (`TenantContextFilter`)
2. Explicit `?locationId=` on the request (same access check) — fallback for mobile/API
3. Empty → no location filter

**Mutations**: `SecurityUtils.requireLocationAccess(locationId)` when assigning a location to a new entity (existing rules).

**Do not** add a second parallel pattern (no ad-hoc `locationId` in every service without `LocationScope`).

---

## Entity detail pages (exception)

**Rule:** workspace filter applies to **lists and aggregates**. Already-open **entity cards** show full data for that person/project.

| Page | Header | Behaviour |
|------|--------|-----------|
| Staff list, calendar, finance, analytics | `X-Location-Id` | Workspace filter |
| **Staff profile**, **client profile** | `X-Entity-Scope: true` | Ignore workspace location; all locations |
| Appointments list (records page) with artist filter | `X-Location-Id` only | Workspace + artist filter |

Frontend: `authFetcher(url, { entityScope: true })` sends `X-Entity-Scope: true` and **not** `X-Location-Id`.

Backend: `LocationContextFilter` sets `TenantContext.entityScope`; `LocationScope.resolveFilter()` skips header in that mode.

Direct entity reads (`GET /staff/{id}`, `GET /clients/{id}`, `GET /appointments/client/{id}`) were already tenant-wide; entity scope fixes **nested lists** (activities, projects on profile).

---

## Frontend

- `locationStore.currentLocationId` — persisted switcher state
- `null` = «All locations» → **do not** send header
- UUID set → `fetcher` adds `X-Location-Id` on all `/api/` calls
- Pages that already pass `?locationId=` keep working during migration; header is primary

---

## Default location rules

- Column: `locations.is_default` (one per tenant, enforced in service)
- Onboarding: first location → `is_default = true`
- Delete: blocked for default location and last location
- API: `PATCH /locations/{id}/default` (owner/admin) — clears previous default, sets new

---

## Out of scope (documented in tech debt)

- Artist `calendar.edit` without ownership check on PATCH by UUID
- PostgreSQL RLS for `location_id` (Java enforcement sufficient for now)

See [TECH_DEBT.md](./TECH_DEBT.md).
