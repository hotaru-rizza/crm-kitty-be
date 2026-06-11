# InkFlow CRM — API Specification (summary)

> **Updated**: 2026-06-11  
> **Canonical response format**: see [docs/API_FORMAT.md](docs/API_FORMAT.md)  
> **Tech debt tracker**: see [docs/BACKEND_TECH_DEBT.md](docs/BACKEND_TECH_DEBT.md)

This file is a high-level index. The full legacy specification below is partially outdated.

## Removed modules (do not implement)

- Waivers / consent templates
- Inventory (products, warehouses, stock)
- Gift certificates
- Promotions
- Public subdomain booking (`/public/book/{subdomain}`)

## Active public (B2C) endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/public/catalog/tattoos/**` | None |
| GET | `/public/artists/**` | None |
| POST | `/public/consumer/requests` | Consumer JWT |
| GET | `/public/consumer/requests/my` | Consumer JWT |
| POST | `/public/consumer/generate` | Consumer JWT |
| POST | `/public/consumer/try-on` | Consumer JWT |
| GET/PATCH | `/public/consumer/users/me/**` | Consumer JWT |

## Active CRM endpoints (require Supabase JWT)

| Module | Base path | Notes |
|--------|-----------|-------|
| Appointments | `/appointments` | RBAC via `@RequirePermission` |
| Clients | `/clients` | Tenant-scoped |
| Staff | `/staff` | Includes portfolio, schedule, pricing |
| Requests | `/requests` | CRM leads; auth + `requests.create` |
| Settings | `/settings/**` | Includes `GET/PATCH /settings/user` |
| Files | `/files/**` | Tenant-prefixed storage keys |
| Catalog admin | `/catalog/admin/tattoos/seed`, `/retag` | `settings.access` permission |

## Onboarding

`POST /onboarding` — requires verified Supabase JWT (not `JWT.decode`). Idempotent: returns existing tenant if staff already exists.

## Payments

`POST /payments/monobank/webhook` — public callback; verifies amount + remote invoice status; idempotent processing.

---

<!-- Legacy document preserved below for reference; sections on removed modules may be inaccurate. -->
