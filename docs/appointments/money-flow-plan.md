# Appointment money flow — client balance, payment lifecycle, reserve, basic earnings

> Spec for wiring the **money side** of appointments: a real client balance
> (debt / credit), a clear payment lifecycle decoupled from attendance status,
> a "reserve" slot mode, basic read-only staff earnings, and (later) Integrica‑style
> multi-line payments.
>
> **Source of truth. Every decision here is LOCKED.** Do not invent alternatives,
> extra tables, extra enums, extra abstractions, or "nice to have" extras.
> If something is genuinely undefined: pick the **simplest reversible** option that
> matches an existing project pattern, implement it, and note it in the PR description.
> Do NOT redesign, do NOT add scope, do NOT touch anti-goals.
>
> Repos:
> - Backend: `crm-kitty-be-new` (Spring, modular monolith — see `.cursor/rules/architecture.mdc`).
> - Frontend: `crm-kitty` (React + Mantine + i18n).
>
> **Owner-confirmed decisions (do not re-ask):**
> - Completing an appointment with an unpaid remainder **auto-creates debt** (negative balance). No confirmation prompt.
> - Balance is stored as an **append-only ledger table** (`client_balance_entries`) **plus** a cached `Client.balance`.
> - Implement **all four phases** in the locked order. The executor decides nothing about scope or order.

## Current state (verified in code — do not re-question)

| Thing | State | Evidence |
|-------|-------|----------|
| `Client.balance` | Column exists, `BigDecimal`, default `0`. Method `adjustBalance(amount)` exists. | `domain/entity/Client.java:98,143` |
| `adjustBalance` callers | **NONE.** Balance is never written. | grep: only defined, never called |
| Balance read | Returned in client DTO + filter `balanceMin/Max`. | `module/client/service/ClientService.java:240,309` |
| Balance UI | Shown only in clients **table** column (red if `< 0`) + filter. NOT in profile, NOT in appointment drawer. | `features/clients/.../ClientsTable.tsx:212` |
| Payments | Each payment = a `Transaction` (`type=INCOME`, linked `appointment`, `paymentType`, `paymentMethod`). | `module/payment/service/PaymentProcessingService.java` |
| Appointment "remaining" | `AppointmentPaymentSummaryCalculator`: `remaining = finalPrice − totalPaid` (appointment‑scoped, NOT client balance). | `module/payment/support/AppointmentPaymentSummaryCalculator.java` |
| Completion hook | `AppointmentCompletedEvent` published on `SCHEDULED → COMPLETED`. | `module/appointment/service/AppointmentSideEffectService.java:105` |
| Statuses | `SCHEDULED, COMPLETED, CANCELLED, NO_SHOW` only. No "confirmed"/"arrived". No reserve flag. | `domain/enums/AppointmentStatus.java` |
| Staff salary | `salaryType` (`none/fixed/percent`) + `salaryRate` per staff already captured. | `features/staff/EditStaffDrawer/types/editStaff.schema.ts:26` |
| Deposit | `DEPOSIT` payment increments `appointment.prepayment`. | `PaymentProcessingService.applyDepositIfNeeded` |

## Core mental model (LOCKED)

Two **independent** lifecycles. Never couple them:

1. **Attendance status** — `SCHEDULED → COMPLETED | NO_SHOW | CANCELLED`. About whether work happened. Money‑agnostic.
2. **Payment state** — `unpaid → partially paid → fully paid`. Derived from payments, never a stored status.

**Client balance is the ledger bridge between appointments:**

- `balance > 0` → **credit** (client prepaid / overpaid; can be spent on future work).
- `balance < 0` → **debt** (client owes the studio).
- A balance ledger is a running sum of signed entries. The stored `Client.balance` is the cached sum.

### Balance sign rules (LOCKED — implement exactly)

| Event | Effect on `Client.balance` | Notes |
|-------|----------------------------|-------|
| Payment received (real money in: cash/card/split/mono), `paymentType ∈ {SERVICE_PAYMENT, DEPOSIT}` | `+amount` | Money in raises the client's standing. |
| Appointment **COMPLETED** | `−finalPrice` | The charge lands when work is done. |
| Payment via method **BALANCE** (spend existing credit) | `−amount` | No external money; draws down credit. Still counts as an appointment payment for the summary. |
| Refund of a payment | `−refundedAmount` | Reverses money out. |
| COMPLETED → SCHEDULED (restore) | `+finalPrice` | Reverse the charge. |
| Appointment edited while COMPLETED and `finalPrice` changes | N/A — **editing a COMPLETED appointment's price/items is blocked** | Avoids reconciliation. `isAppointmentEditable` is already false for terminal statuses; enforce server-side too. |

Worked examples (final price = 500):
- Pay 0, complete → balance `−500` (debt). ✔ matches Integrica "−50".
- Pay 200 (deposit), complete → `+200 − 500 = −300` debt.
- Pay 500, complete → `0`.
- Pay 700, complete → `+200` credit (carried to next appt).

> **Why not "settle once on completion"?** Because deposits/partials happen *before*
> completion. A running ledger keeps every state correct without special cases.

## Anti-goals (do NOT do)

- ❌ Cash registers / multiple tills (Integrica "To cash register"). Defer.
- ❌ Staff payout records / "pay salary" actions / payroll ledger. Earnings are **read-only computed** only.
- ❌ Fiscal receipts (РРО/ПРРО), tax lines, currency conversion.
- ❌ Per-line discounts (stays appointment-level — see redesign-plan.md).
- ❌ Auto-charging completion if unpaid. Underpayment becomes debt, never blocks completion.

---

# Phase 1 — Client balance (START HERE)

Goal: balance becomes a live ledger; payments and completion move it; it is visible
where money decisions are made; partial payment works because the remainder becomes debt.

## 1A. Backend — balance ledger

**Decision:** introduce an append-only `client_balance_entries` table (audit + reconstructable),
and keep `Client.balance` as the cached running total updated via `adjustBalance`.

Tasks:
1. **Migration** `V25__client_balance_entries.sql`:
   - `id`, `tenant_id`, `client_id`, `amount` (signed, 12,2), `reason` (enum string:
     `PAYMENT`, `CHARGE`, `BALANCE_SPEND`, `REFUND`, `CHARGE_REVERSAL`, `MANUAL_ADJUSTMENT`),
     `appointment_id` (nullable), `transaction_id` (nullable), `note` (nullable),
     `created_by` (nullable), `created_at`.
   - Index `(tenant_id, client_id, created_at)`.
2. **Entity** `domain/entity/ClientBalanceEntry.java` + enum `domain/enums/ClientBalanceReason.java`.
   - DTO-only: no manual JSON parsing anywhere (user rule).
3. **Service** `module/client/service/ClientBalanceService.java`:
   - `record(client, amount, reason, appointmentId?, transactionId?, note?)`:
     saves entry **and** calls `client.adjustBalance(amount)` + `clientRepository.save`.
   - All balance mutations go through this one method. No other code calls `adjustBalance` directly.
4. **Wire into payment flow** (`PaymentProcessingService.processPayment`):
   - After a `SERVICE_PAYMENT`/`DEPOSIT` with a real method → `clientBalanceService.record(+amount, PAYMENT, ...)`.
   - If `paymentMethod == BALANCE` → record `−amount` with reason `BALANCE_SPEND` (and **do not** also add `+amount`).
     Requires adding `BALANCE` to `PaymentMethod` enum (front + back). Validate `amount ≤ currentCredit`.
   - In refund flow (`RefundProcessingService`) → record `−refundedAmount`, reason `REFUND`.
5. **Wire into completion** — listener on `AppointmentCompletedEvent`:
   - New `module/client/listener/AppointmentBalanceListener.java`: on completion,
     `clientBalanceService.record(−finalPrice, CHARGE, appointmentId)`.
   - On restore (COMPLETED → SCHEDULED) record `+finalPrice`, reason `CHARGE_REVERSAL`.
   - **Idempotency mechanism (locked):** add nullable `balance_charged_at` (Instant) to `Appointment`
     (same migration as the entries table). Charge only when `balanceChargedAt == null`; set it after charging.
     Restore (reversal) only when `balanceChargedAt != null`; clear it after reversing.
     This is the ONLY idempotency mechanism — do not add markers elsewhere.
   - Skip entirely when `appointment.getClient() == null` (e.g. reservations).
6. **Expose** balance history endpoint: `GET /clients/{id}/balance` →
   `{ balance, entries: [...] }` via a `ClientBalanceDto`. Controller thin, logic in service.

Acceptance (Phase 1A):
- Creating a payment changes the client's balance via a ledger entry.
- Completing an unpaid 500 appt sets balance to `−500` and writes one `CHARGE` entry.
- Refund and restore correctly reverse entries.
- `GET /clients/{id}/balance` returns the running total == sum of entries.

## 1B. Frontend — surface the balance

1. **Service**: `services/clients` — add `useClientBalance(clientId)` hook + types
   (`ClientBalanceEntry`, `ClientBalanceReason`). No manual parsing.
2. **Client profile**: show a balance card (amount, red if `< 0`, green if `> 0`) +
   collapsible history list (date · reason · signed amount · linked appointment).
3. **Appointment drawer → Payment tab** (`AppointmentPaymentLedger`): add a settlement
   row "Баланс клієнта" showing current credit/debt, and after completion show the
   resulting balance impact.
4. **Payment form** (`PaymentFormContent`):
   - If client has credit (`balance > 0`): show method "З балансу" (BALANCE) capped at credit.
   - Keep existing partial-payment toggle. Copy: when paying < remaining on a completed
     appt, show hint "Залишок {x} ₴ піде в борг клієнта".
5. **i18n**: add keys under `clients.*` and `calendar.appointmentDrawer.payment.*`
   (uk + en). No hardcoded strings.

Acceptance (Phase 1B):
- Balance visible in: clients table (exists), client profile, payment ledger.
- Paying from balance reduces credit and the appointment's remaining.
- Partial payment leaves a visible, explained debt.

---

# Phase 2 — Reserve slot mode

Goal: a "Резерв" appointment = blocked time, no client attendance lifecycle.

## Backend
1. Add `boolean reservation` (default `false`) to `Appointment` + migration `V26__appointment_reservation.sql`.
2. `CreateAppointmentRequest`/`UpdateAppointmentRequest` accept `reservation`.
3. Validation: when `reservation = true`, `clientId` optional; `service`/items optional;
   status forced to `SCHEDULED`; no payment allowed (reject in `PaymentProcessingService`).

## Frontend (`CreateAppointmentDrawer`)
1. "Резерв" checkbox at top.
2. When checked: hide the status stepper, make client optional, neutral slot color,
   hide services/payment. (Mirror Integrica screen 1.)
3. Calendar renders reservation slots in a muted/striped style.

Acceptance: a reserve slot blocks time, has no status toggler, no client required, no money.

---

# Phase 3 — Basic staff earnings (read-only)

Goal: owner/admin sees "нарахування за період" per master. NO payouts.

1. Backend `module/staff` (or analytics): `GET /staff/{id}/earnings?from&to` →
   for COMPLETED appointments in range, compute per master:
   - `percent`: `Σ(appointment.finalPrice) × salaryRate/100`.
   - `fixed`: `salaryRate` is a **monthly** rate; prorate by days in the selected range
     (`salaryRate × selectedDays / daysInMonth`). Locked — do not invent other period semantics.
   - `none`: `0`.
   - Return `{ revenue, rate, salaryType, earnings, appointmentsCount }` via DTO.
2. Frontend: a read-only block on `StaffAnalyticsPage` (or staff profile finance):
   period picker + per-master earnings. Reuse existing analytics period filters.

Acceptance: a number per master, derived from existing `salaryType/rate` + completed work. No write actions.

---

# Phase 4 — Multi-line payment (Integrica last screen) — LAST

Goal: one payment composed of multiple lines (method + category + amount), accumulating
against "До сплати", building a Payments list.

1. Backend: accept a batch `ProcessPaymentRequest { lines: [{ method, category, amount, paymentType }] }`;
   each line → one `Transaction` (reuse current builder); run all in one `@Transactional`.
2. Frontend `PaymentFormContent`: list of payment lines with add/remove, running total vs remaining,
   disable submit until `Σ lines == intended amount`.
3. Categories reuse existing `useCategoryConfigs()` / `TransactionCategory`.

> Cash registers ("To cash register") remain **out of scope** until/if a till feature is greenlit.

---

# Cross-cutting guardrails (for any executor)

- **DTO-only.** Never hand-roll parsing; map via DTOs/mappers (user rule + project convention).
- **Reuse, don't recreate:** `Transaction`, `Client.adjustBalance`, `AppointmentCompletedEvent`,
  `AppointmentPaymentSummaryCalculator`, `salaryType/rate`, `GradientSwitch`, `useCategoryConfigs`.
- **Thin controllers, logic in services.** No god methods.
- **No magic values/strings:** enums + i18n keys + config.
- **Block editing of COMPLETED appointments' price/items** server-side. No reconciliation path exists by design.
- **Every balance mutation goes through `ClientBalanceService.record`** — single source of truth.
- After each phase: run backend tests + `npx tsc --noEmit` (frontend) and lint touched files.

# Execution order

1. **Phase 1A → 1B** (balance) — START HERE.
2. Phase 2 (reserve).
3. Phase 3 (earnings).
4. Phase 4 (multi-line payment).

Do not start a later phase before the previous one's acceptance criteria pass.
