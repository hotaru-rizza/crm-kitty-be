# Tech debt register

Tracked gaps that are **known** and **intentionally deferred**. Not forgotten.

---

## TD-001 — Artist can PATCH another artist's appointment (same tenant)

**Status:** ✅ Resolved (Phase 1 — `AppointmentAccessGuard`)

**Was**

- Artist role has `calendar.edit`, `calendar.create`, `calendar.cancel` in `RolePermissionDefaults`.
- List/calendar views restrict artist to own rows via `resolveArtistIds` / `CALENDAR_VIEW_OWN`.
- **`PATCH /appointments/{id}`** did not verify `appointment.artist.id == currentUserId`.

**Fix shipped**

- `AppointmentAccessGuard` in `module/appointment/support/` — `requireView` / `requireEdit` / `requireCancel` / `requireAssignableArtist`.
- Wired into `AppointmentService` (get, create, update, delete, photos, artist reassignment).
- Unit: `AppointmentAccessGuardTest`; integration: `AppointmentControllerIntegrationTest` + `AppointmentServiceIntegrationTest`.

See **[PERMISSIONS_ACCESS_PLAN.md](./PERMISSIONS_ACCESS_PLAN.md)** Phase 1.

---

## TD-002 — Location filter not applied everywhere (in progress)

See [LOCATION_SCOPE_PLAN.md](./LOCATION_SCOPE_PLAN.md) — Phase 5 (remove redundant `?locationId=` from frontend once header is verified in prod).

**Status:** Core rollout done (Phases 1–4). Leaves module still uses query param only — wired via same `LocationScope` pattern when touched.

---

## TD-003 — `TenantContext.getCurrentLocation()` unused before LocationScope

Header infrastructure existed since `TenantContextFilter`; services ignored it until `LocationScope` rollout.

**Status:** Addressed by `LocationScope` (Phase 1).

---

## TD-004 — Delete endpoints owner-only policy

**Status:** ✅ Documented and aligned (Phase 4)

Staff, location, service, and transaction **delete** operations require `owner` role (`SecurityUtils.requireOwner()` in service). Controllers no longer advertise misleading edit permissions on delete routes. Admin integration tests assert 403.

See **[PERMISSIONS_ACCESS_PLAN.md](./PERMISSIONS_ACCESS_PLAN.md)** Phase 4.
