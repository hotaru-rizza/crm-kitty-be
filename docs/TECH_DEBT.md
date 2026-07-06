# Tech debt register

Tracked gaps that are **known** and **intentionally deferred**. Not forgotten.

---

## TD-001 — Artist can PATCH another artist's appointment (same tenant)

**Severity:** Medium (same-tenant privilege escalation)

**Current behaviour**

- Artist role has `calendar.edit`, `calendar.create`, `calendar.cancel` in `RolePermissionDefaults`.
- List/calendar views restrict artist to own rows via `resolveArtistIds` / `CALENDAR_VIEW_OWN`.
- **`PATCH /appointments/{id}`** does not verify `appointment.artist.id == currentUserId`.

**Expected**

- Artist may only mutate appointments where they are the assigned artist (or stricter product rule).
- Owner / admin: full access within tenant.

**Fix (when picked up)**

- Introduce `AppointmentAccessGuard` (or extend `AppointmentEntityResolver`):
  - `requireEditableByCurrentUser(Appointment appointment)`
  - Called at start of `updateAppointment`, `deleteAppointment`, photo mutations.
- Mirror pattern for `projects.edit` / `clients.edit` if similar gaps exist.

**Related:** permissions are role-based; location scope is a separate concern (see [LOCATION_SCOPE_PLAN.md](./LOCATION_SCOPE_PLAN.md)).

---

## TD-002 — Location filter not applied everywhere (in progress)

See [LOCATION_SCOPE_PLAN.md](./LOCATION_SCOPE_PLAN.md) — Phase 5 (remove redundant `?locationId=` from frontend once header is verified in prod).

**Status:** Core rollout done (Phases 1–4). Leaves module still uses query param only — wired via same `LocationScope` pattern when touched.

---

## TD-003 — `TenantContext.getCurrentLocation()` unused before LocationScope

Header infrastructure existed since `TenantContextFilter`; services ignored it until `LocationScope` rollout.

**Status:** Addressed by `LocationScope` (Phase 1).
