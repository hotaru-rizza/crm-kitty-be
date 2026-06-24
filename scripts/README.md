# Scripts

| Path | Purpose |
|------|---------|
| `export_flyway_baseline.sh` | Export Supabase/PostgreSQL schema → `V1__baseline.sql` |
| `export_openapi.sh` | Generate `docs/openapi.yaml` (+ local Postman json, gitignored) |
| `check_openapi_up_to_date.sh` | CI/local: fail if committed spec is stale |
| `db/seed.sql` | Dev mock data (tenant, staff, appointments, transactions) |
| `db/dashboard-preview-dates.sql` | Shift existing appointment/client dates for dashboard preview |
| `supabase/custom_access_token_hook.sql` | Supabase Auth hook: inject `tenant_id`, `role`, `location_ids` into JWT |
| `postman/README.md` | Postman setup; output in `docs/postman/` (gitignored) |
