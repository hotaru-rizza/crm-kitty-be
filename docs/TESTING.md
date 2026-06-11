# Testing

> Quality-first: tests should catch **business regressions**, not chase JaCoCo %.
>
> **Current:** ~950 tests, ~90% lines / ~88% instructions (JaCoCo excludes dto/entity/mapper).  
> **Run:** `./mvnw test` → `target/site/jacoco/index.html`  
> **Rules:** `.cursor/rules/testing.mdc`

## DoD for a good test

- Arrange via `IntegrationTestData` / factories — no magic UUIDs
- One scenario per test; name: `should<Outcome>When<Condition>`
- HTTP: status + `$.success` + key fields
- Domain: at least one of — DB entity state, enum status, count, mock verify (external API)
- Controllers: 401 without auth; 403/400 where applicable

## Open backlog

### API negatives (partial)

| Area | Gap | Priority |
|------|-----|----------|
| `@RequirePermission` controllers | 403 matrix not complete | P1 |
| Create/Update DTOs | 400 validation on top POSTs | P1 |
| Tenant isolation | 404/403 for other tenant on more endpoints | P1 |
| Idempotency | duplicate invite, double payment | P2 |

### Mock-heavy integration (H2 limits)

| Controller test | Why mocked | Depth in |
|-----------------|------------|----------|
| Portfolio, Tattoo catalog | pgvector / H2 | unit `PortfolioService`, `TattooCatalogService` |
| Catalog admin seed/retag | embedding API | unit + smoke controller |

### H2 blockers (`wontfix` until Testcontainers PG)

| Scenario | Covered by |
|----------|------------|
| `GET/PATCH /settings/company` (JSONB) | `SettingsServiceTest` |
| `GET /transactions/stats` happy path (PG `::date`) | `TransactionServiceTest` |
| pgvector / embeddings | `@MockBean` + unit tests |

### Test infra

| Item | Status |
|------|--------|
| `IntegrationTestFixtures` (DRY repositories in integration tests) | open |
| `SecurityTestSupport` everywhere | open |
| JaCoCo gate in CI (min % on service/controller) | optional |
