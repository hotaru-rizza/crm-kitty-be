# Flyway migrations

Production uses `spring.jpa.hibernate.ddl-auto: none` and Flyway for schema management.

## Current state

Baseline exported from production Supabase (`public` schema only) as `V1__baseline.sql`.

Regenerate:

```bash
./scripts/export_flyway_baseline.sh
```

Reads `.env` (`SPRING_DATASOURCE_URL`, `DB_USERNAME`, `DB_PASSWORD`) or accepts `DATABASE_URL` directly.

## First deploy with Flyway

## Profiles

| Profile | ddl-auto | Flyway |
|---------|----------|--------|
| dev     | update   | disabled |
| prod    | none     | enabled (configure in deployment) |

Do not re-enable `ddl-auto: update` in production.
