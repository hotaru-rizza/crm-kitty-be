# MVP Pre-Release Security Audit

**Дата:** 2026-07-10  
**Охват:** `crm-kitty-be-new` (бекенд), `crm-kitty` (CRM фронт), `client-tattoo-web` (клиентское приложение)  
**Вердикт:** готовы к MVP при соблюдении deploy checklist ниже.

---

## Статус исправлений

| # | Severity | Finding | Repo | Статус |
|---|----------|---------|------|--------|
| 1 | 🔴 Critical | Аналитика сливает финансы всей студии роли ARTIST | backend | ✅ fixed |
| 2 | 🔴 Critical | `/demo` в проде с захардкоженными кредами | crm-kitty | ✅ fixed |
| 3 | 🔴 Critical | Monobank webhook без верификации (fail-open) | backend | ✅ fixed |
| 4 | 🟠 High | Нет rate-limiting на публичных эндпоинтах | backend | ✅ fixed |
| 5 | 🟠 High | Swagger без auth на staging | backend | ✅ fixed |
| 6 | 🟠 High | `.env` не в `.gitignore` (CRM фронт) | crm-kitty | ✅ fixed |
| 7 | 🟠 High | `SPRING_PROFILES_ACTIVE=dev` в проде = полный bypass | backend | 📋 deploy checklist |
| 8 | 🟡 Medium | PII в логах + email в Sentry | backend + crm-kitty | ✅ fixed |
| 9 | 🟡 Medium | XSS в ContactModal | client-tattoo-web | ✅ fixed |
| 10 | 🟡 Medium | R2-файлы публично адресуемы | backend + crm-kitty | ⚠️ partial — signed-url API готов |
| 11 | 🟡 Medium | accept-invite + authUserId без Supabase admin | backend | 📋 ensure admin in prod |
| 12 | 🟡 Medium | Sentry Session Replay без маскирования | crm-kitty | ✅ fixed |
| 13 | 🟢 Low | JWT без проверки `aud` | backend | ✅ fixed |
| 14 | 🟢 Low | Токены в localStorage (стандартный SPA tradeoff) | оба фронта | accepted risk |
| 15 | 🟢 Low | Нет CSP в фронтах | оба фронта | ⏳ backlog (hosting layer) |

---

## 🔴 Critical

### 1. Аналитика — утечка финансовых данных ARTIST → OWNER data

**Где:** `StaffPerformanceAnalyticsService`, `AppointmentAnalyticsQueryService`, `ClientAnalyticsQueryService`, `ServicePopularityAnalyticsService`  
**Проблема:** эндпоинты `/analytics/*` принимают `*_VIEW_OWN`, но возвращают данные по **всем** мастерам студии — зарплаты (`calculatedSalary`), выручку, KPI.  
**Исправление:** `AnalyticsAppointmentScope` — при отсутствии `VIEW_ALL` фильтрует appointments по `SecurityUtils.getCurrentUserId()`.

### 2. `/demo` доступен в production

**Где:** `crm-kitty/src/routes.tsx`, `src/hooks/useDemo.ts`  
**Проблема:** роут не закрыт под `import.meta.env.DEV`; пароль `demo-inkat-2026` в бандле; реальная сессия Supabase к демо-тенанту.  
**Исправление:** роут только в DEV (как `/dev/admin`).

### 3. Monobank webhook — fail-open

**Где:** `MonobankService.verifySuccessPayload()`  
**Проблема:** если `MONOBANK_TOKEN` не задан или `REPLACE_*`, верификация пропускается (`return true`). Подделанный webhook активирует подписку.  
**Исправление:** fail-closed вне `dev` профиля.

---

## 🟠 High

### 4. Rate-limiting на публичных эндпоинтах

**Где:** `PublicEndpointRateLimitFilter`, `InMemoryRateLimiter`  
**Лимиты по умолчанию:**
- `GET /staff/invite/info/**` — 30/мин на IP
- `POST /staff/accept-invite` — 10/мин на IP
- `POST /onboarding` — 5/час на IP
- `POST /payments/monobank/webhook` — 120/мин на IP

Отключение: `INKFLOW_RATE_LIMIT_ENABLED=false`  
**Note:** in-memory лимитер — per pod; для multi-instance нужен edge rate limit.

### 5. Swagger на staging

**Где:** `application-staging.yml` — `inkflow.openapi.enabled: true`  
**Риск:** полная карта API без auth.  
**Исправление:** `enabled: false` на staging.

### 6. `.env` в git (CRM фронт)

**Где:** `crm-kitty/.env` tracked, `.gitignore` не игнорирует `.env`  
**Исправление:** добавить `.env` в `.gitignore`, создать `.env.example`.

### 7. Deploy checklist — профиль Spring

**Риск:** `SPRING_PROFILES_ACTIVE=dev` → `DevSecurityConfig.anyRequest().permitAll()` + `DevAdminController` с удалением тенантов.  
**Действие:** в CI/CD и runbook явно проверять `prod` или `staging`, никогда `dev`.

---

## 🟡 Medium (backlog)

### 8. PII в логах и Sentry
- `JwtAuthenticationFilter` — `log.info` на каждый запрос с user/tenant/authUserId → переведено на `debug`, email убран из Sentry.
- CRM фронт: `setSentryUser({ email })` → только `id` + `tenantId`.

### 9. XSS ContactModal (client app)
- `dangerouslySetInnerHTML` + i18n `escapeValue: false` + `artistName` из API.
- Исправлено: plain-text рендер с `<strong>{artistName}</strong>`.

### 10. R2 публичные URL (частично)

**Где:** `GET /files/signed-url?key=...`, CRM `resolveSignedFileUrl()`  
**Статус:** API готов. Для полного закрытия — сделать bucket private и мигрировать `<img src>` на signed URLs.

### 12. Sentry Replay

**Исправление:** `maskAllText: true`, `blockAllMedia: true` в `replayIntegration()`.

---

## 🟢 Low / Accepted

- JWT `aud` validation — `SUPABASE_JWT_AUDIENCE=authenticated` (дефолт).
- localStorage для JWT — стандартный SPA tradeoff; митигируется XSS-фиксами.
- CSP — на уровне хостинга (nginx/Cloudflare).

## Осталось (post-MVP)

> Детальніше: [`crm-kitty/docs/important/security-post-mvp.md`](../../../crm-kitty/docs/important/security-post-mvp.md)

- Миграция всех `<img>` на `resolveSignedFileUrl()` + private R2 bucket
- Edge rate-limit для multi-instance деплоя
- CSP headers на nginx/Cloudflare

---

## Что сделано хорошо (не трогать)

| Область | Детали |
|---------|--------|
| JWT | Подпись через JWKS (`JwtTokenProvider`), issuer check |
| Tenant isolation | Hibernate filter на `BaseEntity`; `tenantId` не принимается от клиента |
| Dev backdoors | `@Profile("dev")` на `DevSecurityConfig`, `DevAdminController`, `DevStartupListener` |
| Invite tokens | `UUID.randomUUID()`, TTL 7 дней |
| Onboarding | JWT verified в контроллере несмотря на `permitAll` |
| Permissions | `@RequirePermission` + `PermissionAspect` на CRM API |
| Own-vs-all | `AppointmentService`, `ClientService`, access guards |
| File uploads | Tenant-prefixed keys, folder whitelist, presigned PUT 15 min |
| Secrets | Из env, не в source |
| Errors | `GlobalExceptionHandler` — generic message, без stack trace |
| Prod actuator | Только `health`, `show-details: never` |
| SQL | Native queries с `@Param` binding |
| Onboarding wizard | Пароль вырезается перед localStorage |
| Legal docs | Чекбоксы на регистрации/онбординге, страницы `/legal/*` |

---

## Deploy checklist (перед релизом)

- [ ] `SPRING_PROFILES_ACTIVE=prod` (или `staging`, **не** `dev`)
- [ ] `MONOBANK_TOKEN` задан и не `REPLACE_*` (или Monobank отключён)
- [ ] `supabase.admin.enabled=true` в prod
- [ ] CORS origins — только prod/staging домены
- [ ] `.env` не в git (CRM фронт)
- [ ] `/demo` недоступен в prod build
- [ ] Swagger выключен на staging/prod
- [ ] `SUPABASE_JWT_AUDIENCE=authenticated` (или ваш audience)
- [ ] Rate limit: `INKFLOW_RATE_LIMIT_ENABLED=true` (дефолт)

---

*Последнее обновление: 2026-07-10*
