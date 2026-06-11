# Flyway migrations

Production uses `spring.jpa.hibernate.ddl-auto: none` and Flyway for schema management.

## Current state

Baseline exported from production Supabase (`public` schema only) as `V1__baseline.sql`.

Regenerate baseline:

```bash
./scripts/export_flyway_baseline.sh
```

Reads `.env` (`SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`) or accepts `DATABASE_URL` directly.

## Migrations

| Version | File | Purpose |
|---------|------|---------|
| V1 | `V1__baseline.sql` | Schema snapshot (public schema) |
| V2 | `V2__drop_removed_modules.sql` | Drop waiver/inventory/gift certs/promotions tables |

## First deploy with Flyway

## Profiles

| Profile | ddl-auto | Flyway |
|---------|----------|--------|
| dev     | update   | disabled |
| prod    | none     | enabled (configure in deployment) |

Do not re-enable `ddl-auto: update` in production.
