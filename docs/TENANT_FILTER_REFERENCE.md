# Hibernate `@Filter` — где работает, где нет

Краткий справочник для команды. `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")` включается через `TenantFilterAspect` на `com.inkflow.crm.module..service..*` когда `TenantContext` задан.

---

## ✅ Filter **работает**

| API | Пример |
|-----|--------|
| Derived query (Spring Data) | `findByDeletedAtIsNull()`, `findByIdAndTenantId()` |
| JPQL SELECT | `@Query("SELECT c FROM Client c WHERE ...")` |
| Criteria API | `Specification`, `entityManager.createQuery(...)` SELECT |
| Entity load по derived query | `findByIdAndDeletedAtIsNull(id)` |

Filter добавляет `AND tenant_id = :tenantId` к SQL/JPQL.

---

## ❌ Filter **НЕ работает**

| API | Почему | Что делать |
|-----|--------|------------|
| `JpaRepository.findById(id)` | `EntityManager.find()` по PK, мимо Query API | `findByIdAndDeletedAtIsNull(id)` |
| `JpaRepository.findAllById(ids)` | то же | `findByIdInAndDeletedAtIsNull(ids)` |
| `@Modifying` JPQL UPDATE/DELETE | Bulk operation, filter не применяется | Явный `AND c.tenantId = :tenantId` в `@Query` |
| Derived `deleteByRole()`, `deleteByX()` без tenant | Spring генерирует bulk DELETE | `findBy...()` → `deleteAll(list)` **или** explicit tenant в query |
| Native SQL (`nativeQuery = true`) | Filter не участвует | `WHERE tenant_id = :tenantId` в SQL |
| `JdbcTemplate` / `createNativeQuery` | вне Hibernate session filter | Явный `tenant_id` в SQL |
| `@BypassTenantFilter` | Aspect не включает filter | Явный `tenant_id` или ручная проверка |

---

## ⚠️ Особые случаи

| Случай | Безопасно? | Условие |
|--------|------------|---------|
| Bulk DELETE по `staff.id = :staffId` | ✅ | `staffId` предварительно проверен через `staffLookup` / filter-aware find |
| Bulk UPDATE по `recipientId = :userId` | ✅ | UUID пользователя глобально уникален |
| Scheduler с `@BypassTenantFilter` | ✅ | Loop по tenants + `TenantContext.setCurrentTenant()` **и** explicit `tenant_id` в bulk SQL |
| Cross-tenant scheduler (email outbox) | ✅ by design | Allowlist в `NativeSqlTenantSafetyTest` |

---

## Правила для нового кода

1. **Read one by id** → `findByIdAndDeletedAtIsNull` / `findByIdAndTenantId`
2. **Bulk delete/update на tenant entity** → `tenantId = :tenantId` в `@Query` **или** load-then-mutate
3. **Native SQL на tenant table** → `tenant_id` в WHERE
4. **Никогда** не полагаться на filter в `@Modifying`, derived `deleteBy*` / `updateBy*`, `deleteById`, `JdbcTemplate`
5. CI: `TenantSecurityArchitectureTest`, `DerivedBulkMutationSafetyTest`, `BulkJpqlTenantSafetyTest`, `NativeSqlTenantSafetyTest`, `JdbcTemplateTenantSafetyTest`

---

## Как включается filter (runtime)

```
HTTP request
  → JwtAuthenticationFilter → TenantContext.setCurrentTenant(tenantId)
  → Service method (module..service..)
  → TenantFilterAspect → session.enableFilter("tenantFilter").setParameter("tenantId", ...)
  → Repository call (derived/JPQL SELECT) → filter в SQL
```

`@BypassTenantFilter` на классе/методе → aspect **пропускает** → filter **выключен**.

---

## История инцидентов (fixed)

| Баг | Причина | Фикс |
|-----|---------|------|
| `RolePermissionService.updateRolePermissions` | `deleteByRole()` bulk без tenant | `findByRole` → `deleteAll` |
| `ClientRepository.markDormantClients` | `@Modifying` UPDATE без tenant | `AND c.tenantId = :tenantId` |

Полный аудит: `docs/TENANT_SECURITY_AUDIT.md`
