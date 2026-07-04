# План: Hibernate `@Filter` + рефакторинг tenant isolation (Plan A)

## Статус реализации

- [x] Шаг 1: Entity annotations (`BaseEntity` + 9 standalone + `package-info.java`)
- [x] Шаг 2: `TenantFilterAspect`
- [x] Шаг 3: `@BypassTenantFilter` на cross-tenant сервисах
- [x] Шаг 4: Рефакторинг репозиториев + specifications
- [x] Шаг 5: Рефакторинг сервисов
- [x] Шаг 7: Обновление тестов + `TenantIsolationIntegrationTest`
- [x] `mvn test` — 943+ tests pass

---

## Контекст

- Сервисы получают `tenantId` через `SecurityUtils.getCurrentTenantId()` → `UserPrincipal`.
- `TenantContextFilter` уже пишет `app.current_tenant` в PostgreSQL (RLS-подготовка).
- Фильтр Hibernate опирается на `TenantContext.getCurrentTenant()`.
- Цель: глобальная SQL-level изоляция + убрать избыточный `tenantId` из репозиториев/сервисов.

---

## Шаг 1: Аннотации на сущностях

### `BaseEntity` — добавить `@FilterDef` + `@Filter`

Все 11 наследников получают фильтр автоматически:
`Staff`, `Appointment`, `AppointmentItem`, `Project`, `Transaction`, `TransactionCategoryConfig`, `Client`, `Service`, `ClientBalanceEntry`, `LeaveRequest`, `Location`.

### Standalone (9 файлов) — те же аннотации

- `Request.java`
- `EmailTemplate.java`
- `EmailMessage.java`
- `AuditLogEntry.java`
- `RolePermission.java`
- `StaffInvite.java`
- `GalleryPhoto.java`
- `MonobankInvoice.java`
- `Subscription.java`

### НЕ трогать (нет `tenantId`)

`Tenant`, `SchedulerRun`, `ArtistServicePricing`, `StaffFaq`, `StaffSchedule`.

---

## Шаг 2: `TenantFilterAspect`

**Новый файл:** `config/TenantFilterAspect.java`

- AOP `@Around` на `com.inkflow.crm.module..service..*(..)`
- Исключения: `@BypassTenantFilter` на классе/методе
- Если `TenantContext.getCurrentTenant() != null` → `session.enableFilter("tenantFilter").setParameter("tenantId", tenantId)`

---

## Шаг 3: `@BypassTenantFilter`

**Новый файл:** `config/BypassTenantFilter.java`

### На класс

| Сервис | Причина |
|--------|---------|
| `OnboardingService` | Создаёт тенанта, нет контекста |
| `ConsumerBookingService` | Публичное API |
| `ConsumerUserService` | Consumer-таблицы |
| `DevAdminService` | Dev-only, все тенанты |
| `PublicArtistService` | Публичный каталог |
| `TattooCatalogService` | Глобальный каталог |
| `OutboxPoller` | Scheduler |
| `TriggerScheduler` | Scheduler |
| `BuiltInTemplateSeeder` | Bootstrap |
| `EmailModuleBootstrap` | Bootstrap |
| `AuditRetentionScheduler` | Scheduler |
| `ClientDormancyJob` | Scheduler |
| `AppointmentReminderScheduler` | Scheduler |

### На метод

| Сервис | Метод |
|--------|-------|
| `StaffInviteService` | `acceptInvite()`, `getInviteInfo()` |
| `MonobankService` | `handleWebhook()` |
| `SubscriptionService` | `createTrialForTenant()`, `isSubscriptionActive()` |

---

## Шаг 4: Рефакторинг репозиториев

Убрать `tenantId` из derived query names и JPQL `@Query`.

**Исключения (не рефакторить):**
- `TransactionRepository`: 2 native SQL (`sumIncomeByDayAndDateRange*`) — `@Filter` не работает с native
- `SubscriptionRepository.findByTenantId` — cross-tenant callers
- Методы без tenant scope (public/cross-tenant by design)

### Репозитории с tenant-scoped методами (16)

`AppointmentRepository`, `StaffRepository`, `ClientRepository`, `TransactionRepository`, `LocationRepository`, `ServiceRepository`, `ProjectRepository`, `RequestRepository`, `LeaveRequestRepository`, `EmailTemplateRepository`, `EmailMessageRepository`, `RolePermissionRepository`, `StaffInviteRepository`, `GalleryPhotoRepository`, `AuditLogRepository`, `ClientBalanceEntryRepository`, `TransactionCategoryConfigRepository`

### Specifications (5) — удалить `belongsToTenant()`

`AppointmentSpecifications`, `RequestSpecifications`, `ProjectSpecifications`, `ClientSpecifications`, `TransactionSpecifications`

---

## Шаг 5: Рефакторинг сервисов (~29 файлов)

- Убрать `tenantId` из вызовов repository, если использовался только для фильтрации
- Оставить `tenantId` для создания entity, audit, и т.д.

---

## Шаг 6: Schedulers (опционально)

Cross-tenant schedulers с `@BypassTenantFilter` — фильтр не включается.
При желании: `TenantContext.setCurrentTenant()` per-tenant в циклах.

---

## Шаг 7: Тесты

1. Unit-тесты — обновить mock-сигнатуры repository
2. Integration-тесты — JWT уже ставит TenantContext
3. **Новый:** `TenantIsolationIntegrationTest` — tenant A не видит данные tenant B

---

## Оценка объёма

| Категория | Файлов |
|-----------|--------|
| Entity аннотации | 10 |
| Новые классы | 2 |
| `@BypassTenantFilter` | ~15 |
| Репозитории | ~16 |
| Specifications | 5 |
| Сервисы | ~29 |
| Тесты | ~26 |
| Integration test | 1 |
| **Итого** | **~104** |

---

## Важные edge cases

- `JwtTokenProvider` → `findByAuthUserIdAndDeletedAtIsNull` — cross-tenant, без TenantContext (до auth)
- Native queries — оставить `tenant_id` в SQL вручную
- `MonobankInvoiceRepository` — без tenant methods, не трогать
