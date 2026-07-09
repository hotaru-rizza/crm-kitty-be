# Staff invite / onboarding — implementation plan

> **Status:** Complete (E2E email delivery — manual prod check)  
> **Owner:** backend + frontend  
> **Related:** [missing-features.md](./mailings/missing-features.md) (TEAM_INVITE), [bucket-a-active.md](./mailings/bucket-a-active.md)

Single source of truth for invite flow hardening. Mark phases `[x]` when shipped with tests.

---

## Problem statement

Invite token flow existed, but:
- No lifecycle email on invite create
- Accept endpoint trusted client `authUserId` without Supabase check
- Pending invite blocked re-send; expired rows never cleaned
- Misleading UI copy (“email will be sent” while only manual link worked)

Supabase email confirmation remains **out of scope** (handled by Supabase Auth).

---

## Phase 1 — Critical product & security `[x]`

| # | Item | Status |
|---|------|--------|
| 1.1 | Send `TEAM_INVITE` email on invite create | [x] `StaffInviteNotificationService` |
| 1.2 | Validate locations at invite time | [x] |
| 1.3 | Resend on duplicate email (new token + email) | [x] |
| 1.4 | Supabase verify on accept (authUserId + email) | [x] when service role configured |
| 1.5 | `syncUserTenantClaims` on accept | [x] |
| 1.6 | Duplicate staff / authUserId check on accept | [x] |
| 1.7 | Pessimistic lock on accept | [x] `findByTokenForUpdate` |
| 1.8 | Config: `inkflow.frontend-url`, `inkflow.invite.ttl-days` | [x] |
| 1.9 | Email normalization (lowercase) | [x] |
| 1.10 | UI copy: email + backup link | [x] |

---

## Phase 2 — API & UX polish `[x]`

| # | Item | Status |
|---|------|--------|
| 2.1 | Invite response: `{ token, resent, emailDispatched }` | [x] `InviteStaffResultDto` |
| 2.2 | Frontend toasts: sent vs resent; warn if email failed | [x] |
| 2.3 | `GET /staff/invites` — list pending invites | [x] |
| 2.4 | `DELETE /staff/invites/{id}` — revoke pending invite | [x] |
| 2.5 | `lockBodyScroll` on `InviteStaffDrawer` | [x] |

---

## Phase 3 — Ops & quality `[x]`

| # | Item | Status |
|---|------|--------|
| 3.1 | Scheduled cleanup of expired unaccepted invites | [x] `StaffInviteCleanupScheduler` |
| 3.2 | Integration tests: resend, invalid location, list/revoke | [x] |
| 3.3 | Unit tests stable (StaffInviteServiceTest) | [x] |
| 3.4 | Update `missing-features.md` (TEAM_INVITE) | [x] |

---

## Phase 4 — Cleanup `[x]`

| # | Item | Status |
|---|------|--------|
| 4.1 | Remove dead `InviteStaffModal` | [x] |
| 4.2 | Remove unused `settings/StaffList` stub | [x] |

---

## Explicitly out of scope

| Item | Reason |
|------|--------|
| CRM email confirmation on accept | Supabase confirm enabled |
| Cross-tenant same email | By design |
| WELCOME_ONBOARD / ROLE_CHANGED emails | Separate mailings bucket A |

---

## Verification checklist (manual)

- [ ] `POST /staff/invite` → email in inbox (Resend configured)
- [ ] Repeat invite same email → no error, new link, `resent: true`
- [ ] Accept flow → staff row + Supabase claims
- [ ] Expired invite → accept rejected; new invite works
