# Appointment redesign — multi-service, tattoo pricing, drawer tabs

> Spec for evolving the appointment "костяк" from a single fixed-price service
> into a tattoo-appropriate model: multiple line items, pricing derived from a
> per-service pricing type (hourly / fixed / project) but always adjustable, plus
> two read-only drawer tabs (Notifications, History).
>
> **Source of truth. Locked decisions below — do not improvise alternatives.**
> If a step seems to need a different design, stop and flag it.

## Locked decisions (do NOT change without flagging)

| Decision | Choice | Why |
|----------|--------|-----|
| Multi-service | Add `appointment_items` (line items). `appointments` keeps `service_id` as **primary service** (synced = first SERVICE item) + cached `price/discount/final_price`. | Source of truth for billing/duration moves to items, but calendar/filters/analytics/payment that read primary `service_id` + `final_price` keep working → low blast radius. |
| Line item kinds | `SERVICE` (linked `service_id`) or `CUSTOM` (free-text title + price, e.g. aftercare cream). **No product catalog / stock** in this batch. | Enables selling goods cheaply without an inventory subsystem (deferred). |
| Pricing source | Per **line** `unit_price` is *suggested* from the service's `PricingType`, then **always editable** at creation/edit. | Tattoo price isn't fixed; artist adjusts per piece. We already have a `customPrice` override — generalize it per line. |
| `HOURLY` semantics | When `service.pricingType = HOURLY`, `service.price` is the **hourly rate**. Suggested line price = `rate × (durationMinutes / 60)`. Recompute suggestion when the slot duration changes, **unless** the user manually overrode the price. | Matches "час работы + возможность регулировать". |
| `FIXED` semantics | Suggested price = `service.price`. | Flash / small pieces / piercing. |
| `PROJECT` semantics | Suggested price = `0` (manual). Real cost lives on the linked `Project.estimatedCost`. | Multi-session work is priced at project level, not per appointment. |
| Total math | `final_price = Σ(item.unit_price × quantity) − appointment.discount`. Discount stays **appointment-level** (no per-line discount in this batch). | Keeps `PaymentSummaryCalculator` / `toPay` logic untouched (it reads `final_price`). |
| Duration | `end_time` suggested = `start_time + Σ(item.durationMinutes)`; still manually overridable. | Multi-service slot length. |
| Single artist | Stays one artist per appointment. **No** multi-employee / depending-on-services. | Salon feature; overkill for tattoo. Our Projects cover the complex case. |
| Notifications tab | Read-only view of `EmailMessage WHERE entity_id = appointmentId`, showing `triggerType` + `status` + `sentAt`. No new sending logic. | Data already exists; pure surface. |
| History tab | Read-only per-appointment audit (`entityType=APPOINTMENT`, `entityId=appointmentId`). Requires finishing **audit Phase 2** (event coverage) to have content. | Ties into the audit work already in flight. |

## Anti-goals (do NOT do in this batch)

- No product catalog with stock/inventory (CUSTOM free-text line is enough).
- No per-line discounts, no tax/VAT lines, no fiscal receipt (РРО/ПРРО).
- No cash register / till entity.
- No multi-employee per appointment.
- No payroll/commission payout workflow here (separate doc; only noted below).

---

## Current state (baseline)

**Backend**
- `Appointment`: exactly one `service_id NOT NULL`, `price → discount → final_price`, `prepayment`, `amountToPay = final_price − prepayment`, `notes`, `sketchImage`, photos, transactions.
- Pricing: `Service.pricingType` (`FIXED/HOURLY/PROJECT`) + `Service.price` + `duration` **already exist** but the appointment ignores pricing type — it just takes a flat `price` from the request.
- Payment: `PaymentProcessingService` / `AppointmentPaymentSummaryCalculator` read `final_price`. Tips + deposits + refunds + split + Monobank already handled.
- `EmailMessage.entityId` = appointment id for appointment-triggered emails; `triggerType` enum distinguishes confirmation/reminder/review.
- Audit: Phase 0–1 done (typed actions + filters). Phase 2 (event-based coverage) **not** done → only appointment CREATE/DELETE recorded.

**Frontend**
- Create drawer: `ClientSection / ArtistSection / ServiceSection (single) / ScheduleSection / PricingSection (one price + discount) / SketchSection / ProjectSection / NotesSection`.
- Details drawer: 3 tabs — Info / Work (sketch + before/after) / Payment.

---

## Phase 1 — Backend: line items + pricing engine

1. **Entity** `domain/entity/AppointmentItem.java` (extends `BaseEntity`):
   `appointment` (ManyToOne), `service` (ManyToOne, nullable), `source` (`AppointmentItemSource`: `SERVICE|CUSTOM`), `title`, `quantity` (int, default 1), `unitPrice` (decimal), `durationMinutes` (int), `lineTotal` (decimal), `sortOrder`.
   `Appointment` gets `@OneToMany List<AppointmentItem> items` (cascade ALL, orphanRemoval).
2. **Enum** `domain/enums/AppointmentItemSource.java` (`SERVICE/CUSTOM`, `fromValue`).
3. **Migration** `V23__appointment_items.sql`:
   - create `appointment_items` (with `tenant_id` for future RLS, FK to appointments, index on `appointment_id`).
   - backfill: one `SERVICE` item per existing non-deleted appointment from `service_id / price / duration`, `quantity=1`, `unit_price=price`, `line_total=price`, `sort_order=0`.
4. **Pricing service** `module/appointment/service/AppointmentPricingService.java` (single responsibility):
   - `suggestUnitPrice(Service, durationMinutes)` → applies `PricingType` rules above.
   - `recompute(Appointment)` → sets each `lineTotal`, the cached `price` (= Σ lineTotal), `finalPrice` (= price − discount), syncs primary `service_id` (first SERVICE item), and `endTime` if duration-derived.
5. **Service layer**: `createAppointment` / `updateAppointment` accept `List<ItemRequest>`; build/replace items; call `recompute`. Keep the single-service request shape working (1 item) for back-compat.
6. **DTOs/mapper**: `AppointmentItemDto`, add `items` to `AppointmentDto`/`AppointmentDetailDto`. `CreateAppointmentRequest`/`UpdateAppointmentRequest` get `items` (validated: ≥1 item, SERVICE items need `serviceId`, CUSTOM need `title`+`unitPrice`).
7. **Payment**: no change to `PaymentProcessingService` (reads `final_price`). Add a test that multi-item `final_price` flows through payment summary correctly.

**Guardrail:** `final_price` and primary `service_id` must always be recomputed server-side from items — never trust client totals.

## Phase 2 — Frontend: create & details drawer

1. Replace `ServiceSection` + `PricingSection` with a **line-items editor**:
   - rows: service picker (or "custom line") · qty · suggested price (editable) · duration · line total · remove.
   - "Add service" / "Add custom line" buttons.
   - live appointment total, appointment-level discount, grand total.
   - price suggestion reacts to service choice + slot duration (HOURLY); manual edit sticks.
2. Details drawer Info tab: render the items table (read-only when terminal), reflect totals.
3. Make it visually consistent with existing `DrawerFormSection` styling (no new design language).

## Phase 3 — Notifications tab (cheap)

1. BE: endpoint `GET /appointments/{id}/notifications` → `EmailMessage` for `entityId=id`, mapped to `{ triggerType, triggerLabel, status, sentAt }`. Reuse `EmailMessageQueryService`.
2. FE: new `AppointmentNotificationsTab` — table Type / Status / Sent, with status pills. Mirrors Integrica's notifications view but read-only.

## Phase 4 — Audit Phase 2 + History tab

1. Finish audit **Phase 2** for appointments (events: CREATE/UPDATE/CANCEL/RESCHEDULE/STATUS_CHANGE/DELETE + payment) per `docs/audit/plan.md`.
2. FE: `AppointmentHistoryTab` — reuse audit-log list filtered by `entityType=APPOINTMENT & entityId`.

## Deferred (separate docs / later)

- **Payroll/commission payout**: today `CommissionCalculator` is analytics-only — artist payout is never recorded as an EXPENSE transaction. Needs its own flow (per-appointment payout vs period payroll). Flagged, not built here.
- Product catalog + stock; cash register; review-link landing; appointment tags; "confirmed by client" status.

## Effort (rough)

| Phase | Scope | Estimate |
|-------|-------|----------|
| 1 | Items entity + migration + pricing engine + DTOs + payment test | 1.5 day |
| 2 | Drawer line-items editor (create + details) | 1.5 day |
| 3 | Notifications tab | 0.5 day |
| 4 | Audit Phase 2 + History tab | 1 day |
