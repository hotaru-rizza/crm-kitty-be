# Test quality backlog

> **Цель:** не гнаться за % JaCoCo ради цифры, а иметь тесты, которые ловят регрессии в бизнес-логике и API-контрактах.
>
> **JaCoCo (~2025-06, после волны 6):** ~650 тестов, **~70% instructions / ~72% lines** (excludes dto/entity/mapper).
> **Запуск:** `./mvnw test` → отчёт `target/site/jacoco/index.html`

---

## Как работать с этим документом

**Не делать:** полный аудит всех тест-классов → потом массовый рефакторинг (устареет, отложит пользу).

**Делать:**

1. **Новый тест** — чеклист из `.cursor/rules/testing.mdc` (минимум: auth + happy path + business assertion).
2. **Трогаешь модуль** — подтяни 1–2 пункта из backlog для этого модуля (boy scout).
3. **Quality sprint** — выбери одну категорию ниже и закрой 3–5 пунктов за итерацию.
4. **Новый smoke-тест** — если осознанно только coverage, пометь `[SMOKE]` в имени метода или добавь строку в таблицу «Known smoke-only».

Статусы: `open` | `in-progress` | `done` | `wontfix` (с причиной)

---

## Категории долга

### A. Smoke-only integration (200 + exists, без проверки outcome)

| Модуль / класс | Что не проверяется | Приоритет | Статус |
|----------------|-------------------|-----------|--------|
| Многие `*ControllerIntegrationTest` | состояние БД после POST/PATCH/DELETE | P1 | open |
| `AppointmentControllerIntegrationTest` | PATCH, смена статуса, side effects | P1 | done (PATCH cancel + DB, 400, create count) |
| `LeaveControllerIntegrationTest` | reject/cancel → статус в БД | P2 | done |
| `PaymentControllerIntegrationTest` | суммы, receipt, связь с appointment | P1 | done (process + DB, artist 403) |
| `StaffControllerIntegrationTest` | invite flow end-to-end | P2 | open |

**Улучшение:** после мутации — `repository.findById` / `assertThat(entity.getStatus())`, не только `jsonPath("$.success")`.

---

### B. Нет негативных API-кейсов

| Область | Нужно добавить | Приоритет | Статус |
|---------|----------------|-----------|--------|
| Controllers с `@RequiresPermission` | 403 без права | P1 | частично (Payment process, Settings permissions) |
| Create/Update DTO | 400 validation (пустые поля, неверный range) | P1 | частично (Appointment create {}) |
| Tenant isolation | 404/403 чужой tenant | P1 | частично (Client, Appointment) |
| Idempotency / conflict | duplicate invite, double payment | P2 | open |

---

### C. Mock-heavy controllers (слабая e2e-уверенность)

| Класс | Mock | Почему | Альтернатива | Статус |
|-------|------|--------|--------------|--------|
| `PortfolioControllerIntegrationTest` | `PortfolioService` | pgvector / H2 | unit `PortfolioService` + contract test | open |
| `TattooControllerIntegrationTest` | сервис каталога | pgvector / H2 | то же | open |
| `CatalogAdminControllerIntegrationTest` | embedding | внешний API | unit + smoke controller | open |

**Правило:** mock на controller OK для coverage; **глубина** — в unit service tests.

---

### D. H2 / infra blockers (integration невозможен или flaky)

| Сценарий | Покрытие сейчас | Статус |
|----------|-----------------|--------|
| `GET /settings/company` (JSONB) | `SettingsServiceTest` unit | wontfix H2 |
| `OnboardingService` full flow + company_settings | unit + controller smoke | wontfix H2 |
| `POST /consumer/me/generations` persist | integration + unit | done — TestConsumerAuthFilter loads managed user |
| pgvector / embeddings | `@MockBean` + unit | wontfix until Testcontainers PG |

---

### E. Service unit — мало branch coverage

| Сервис | Пробелы | Приоритет | Статус |
|--------|---------|-----------|--------|
| `PaymentProcessingService` | refund paths, partial pay | P0 | done (unit: deposit, tip, split, zero) |
| `RequestService` | status transitions, edge cases | P1 | done (convert, spam, repliedAt) |
| `StaffService` / lifecycle | deactivate, permissions | P1 | done (delete soft, owner-only, dup email) |
| `TransactionService` | create + rollback paths | P1 | done (delete soft, finance stats, missing location) |
| `GoogleCalendarSyncService` | sync beyond early-return | P2 | open |
| `AppointmentService` | reschedule, conflict rules | P1 | done (unit: reschedule, cancel, done, pricing) |
| `RefundProcessingService` | partial refund, deposit adjust | P0 | done |

**Приоритет для 80%:** unit на `module.*.service` даёт больше пользы на строку, чем ещё один smoke controller.

---

### F. Инфраструктура тестов (DRY, поддерживаемость)

| Проблема | Идея | Статус |
|----------|------|--------|
| 5× `@Autowired` repositories в каждом integration test | `IntegrationTestFixtures` / builder поверх `IntegrationTestData` | open |
| Дублирование auth headers | уже есть `SecurityTestSupport` — использовать везде | open |
| `IntegrationTestData.seedArtist()` | helper для artist + permission tests | done |
| Нет JaCoCo gate | optional: `check` только на `**/service/**` + `**/controller/**` min 70% | open |

---

### G. Модули без тестов (файлов нет на диске)

- inventory / warehouse / giftcertificate — **skip** до появления кода.

---

## Definition of Done для «хорошего» теста

- [ ] **Arrange** через `IntegrationTestData` / явные фабрики, без magic ids
- [ ] **Act** один сценарий на тест
- [ ] **Assert HTTP** — status + `$.success` + ключевые поля
- [ ] **Assert domain** — минимум одно: entity в БД, enum status, count, side effect (mock verify для внешних API)
- [ ] **Негатив** (для controller): 401 без auth и 403/400 где уместно
- [ ] Имя: `should<Outcome>When<Condition>`

---

## Рекомендуемый порядок quality sprints

1. **P0 services:** PaymentProcessing, Appointment mutations
2. **P1 controller depth:** Appointment PATCH, Payment flow, 403 matrix для Staff/Settings
3. **P1 negatives:** validation 400 на top 5 POST endpoints
4. **P2 infra:** fixtures refactor, consumer transient user fix
5. **P3 optional:** Testcontainers PostgreSQL для JSONB + pgvector integration

---

## Changelog

| Дата | Изменение |
|------|-----------|
| 2025-06-10 | Parallel waves 2–3: consumer, analytics, email, auth, catalog, appointment/payment, subscription (+~130 tests → 505) |
| 2025-06-10 | Initial backlog after coverage wave (~310 tests) |
