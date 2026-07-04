# Tenant Security Audit

## Почему DevAdmin показал чужих staff

**Не потому что фильтр "не работает".** Две дыры наложились:

1. **`@BypassTenantFilter` на `DevAdminService`** — Hibernate filter не включается
2. **После рефакторинга** `findByTenantIdAndDeletedAtIsNull()` → `findByDeletedAtIsNull()` без явного `tenant_id` в SQL

Для bypass-сервисов фильтр **никогда** не включится. Там нужен **явный** `WHERE tenant_id = ?`.

---

## Две дыры в архитектуре (важно понять)

### Дыра 1: `JpaRepository.findById()` / `findAllById()` обходят `@Filter`

Hibernate `@Filter` работает только для **Query API** (JPQL, Criteria, derived queries).

`EntityManager.find(Entity.class, id)` — **напрямую по PK**, фильтр не применяется.

| Метод | Filter работает? |
|-------|------------------|
| `findByIdAndDeletedAtIsNull(id)` | ✅ (derived query) |
| `findById(id)` | ❌ |
| `findAllById(ids)` | ❌ |
| `findByIdAndTenantId(id, tenantId)` | ✅ (derived query) |
| `findByIdAndRecipientId(id, userId)` | ✅ (derived query) |
| `findVisibleById(id)` (JPQL) | ✅ |

### Дыра 2: `@BypassTenantFilter` отключает aspect полностью

Классы/методы с этой аннотацией **не получают** Hibernate filter.
Любой query без явного `tenant_id` = cross-tenant leak.

---

## Исправлено

| Файл | Фикс |
|------|------|
| `DevAdminService.getTenantDetail` | SQL `WHERE tenant_id = ?` |
| `DevAdminService.deleteStaffMember` | `findByIdAndDeletedAtIsNull` |
| `EmailTemplateService.requireTemplate` | `findByIdAndTenantId` |
| `AppointmentService.deletePhoto` | `findByIdAndAppointmentId` |
| `ProjectService.deletePhoto` | `findByIdAndProjectId` |
| `LocationService.assignStaff` | `findByIdInAndDeletedAtIsNull` |
| `StaffService.resolveLocations` | `findByIdInAndDeletedAtIsNull` |
| `StaffInviteService.acceptInvite` | явная проверка `tenantId` на locations |
| `RolePermissionService.updateRolePermissions` | `deleteAll(findByRole)` вместо bulk `deleteByRole` (filter bypass) |
| `StaffFaqService.upsertFaq` | `deleteAll(findByStaffId...)` вместо derived `deleteByStaffId` |
| `StaffPricingService.updateStaffServices` | `deleteAll(findByStaffId)` вместо `@Modifying deleteByStaffId` |
| `StaffScheduleRepository.deleteByStaffId` | удалён (dead code; schedule через `staff.getSchedules().clear()`) |
| `DeviceTokenRepository.deleteByToken` | удалён (unused; при необходимости `findByToken` → `delete`) |
| `RolePermissionService.initializeDefaultPermissionsIfNeeded` | `existsByTenantId` вместо global `count()` |
| `ClientRepository.markDormantClients` / `reactivateDormantClients` | явный `c.tenantId = :tenantId` в `@Modifying` JPQL |
| `LeaveService.requireLeave` | `findByIdAndDeletedAtIsNull` |
| `CategoryConfigService.delete` | `findByIdAndDeletedAtIsNull` |
| `NotificationService.markAsRead` | `findByIdAndRecipientId` + `@Filter` на entity |
| `GoogleCalendarSyncService` | `findByIdAndDeletedAtIsNull` |
| `ProjectProgressSyncService` | `findByIdAndDeletedAtIsNull` |
| `ProjectProgressSyncListener` | `findByIdAndDeletedAtIsNull` |
| `AppointmentEmailListener` | `findByIdAndDeletedAtIsNull` |

---

## Безопасно by design (не tenant-scoped)

| Файл | Почему ок |
|------|-----------|
| `TenantRepository.findById` | Tenant — не tenant-scoped |
| `SchedulerRunRepository` | системная таблица |
| `TattooRepository` | глобальный каталог |
| `ConsumerUserRepository` | consumer-таблицы, отдельный auth |
| `MonobankService.handleWebhook` | tenantId из invoice |
| `TriggerScheduler` | `TenantContext` per template |

---

## Правила для нового кода

1. **Никогда** `repository.findById()` / `findAllById()` на tenant-scoped entity
2. Один ID → `findByIdAndDeletedAtIsNull(id)`
3. Список ID → `findByIdInAndDeletedAtIsNull(ids)`
4. Без soft delete → `findByIdAndTenantId` или JPQL `findVisibleById`
5. **`@BypassTenantFilter`** → явный `WHERE tenant_id = ?` или ручная проверка
6. Правило записано в `.cursor/rules/java-backend.mdc` → секция **Tenant Safety**
7. Справочник filter: `docs/TENANT_FILTER_REFERENCE.md`

---

## CI guardrails (добавлено 2026-07-04, расширено)

- `TenantSecurityArchitectureTest` — ArchUnit: `findById`, `findAllById`, `deleteById`, derived `deleteBy*`/`updateBy*` на tenant repos
- `DerivedBulkMutationSafetyTest` — derived `deleteBy*` / `updateBy*` без `@Query` = fail CI (auto-scan all repos)
- `BulkJpqlTenantSafetyTest` — bulk `@Modifying` JPQL без `tenantId` = fail CI (auto-scan all repos)
- `NativeSqlTenantSafetyTest` — native SQL на tenant-таблицах без `tenant_id` = fail CI (auto-scan all repos)
- `JdbcTemplateTenantSafetyTest` — `JdbcTemplate` mutations вне bypass allowlist = fail CI
- `TenantCrossTenantIsolationIntegrationTest` — cross-tenant regression suite (email, location, project, appointment, staff, service, leave, request, category, notification, staff FAQ, staff pricing)
- `TenantFinanceSettingsIsolationIntegrationTest` — transactions, payments/refunds, finance categories, company settings, role permissions

---

## Native SQL (инвентаризация)

| Репозиторий | Метод | tenant_id? | Статус |
|-------------|-------|------------|--------|
| `TransactionRepository` | `sumIncomeByDayAndDateRange` | ✅ `:tenantId` | OK |
| `TransactionRepository` | `sumIncomeByDayAndDateRangeForStaffs` | ✅ `:tenantId` | OK |
| `EmailMessageRepository` | `findPendingForProcessing` | ❌ (cross-tenant scheduler) | Allowlist — outbox worker |
| `TattooRepository` | все native queries | N/A | Глобальный каталог, не tenant-scoped |

JdbcTemplate: только `DevAdminService` — с явным `WHERE tenant_id = ?`.

---

## TODO (следующий проход)

- [x] ArchUnit: запрет findById на tenant-scoped repos в service layer
- [x] Integration tests: cross-tenant EmailTemplate, GalleryPhoto delete, Location assignStaff
- [x] Cross-tenant tests: finance categories, transactions, manual payments/refunds, settings
- [ ] Рассмотреть `TenantScopedLookup.requireById()` helper для единого паттерна
- [ ] Monobank webhook cross-tenant — когда webhook flow будет в prod
