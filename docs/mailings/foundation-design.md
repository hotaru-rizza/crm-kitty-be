# Foundation Design — Notification Engine

Спека для реализации **фундамента** системы писем. Не фича-письма, а движок, на котором
они потом включаются дёшево. Реализатор (AI) пишет код строго по этому скелету; решения
уже приняты — не переизобретать.

Связанные доки: [`architecture.md`](./architecture.md) (концепция), [`README.md`](./README.md)
(конвенции, реестр переменных), [`bucket-a-active.md`](./bucket-a-active.md) (тексты писем).

Принцип: **дефолты в коде, оверрайды в БД (только дельта). Дефолты на 2 языка (uk/en).**

---

## 1. Что уже есть (переиспользовать, не дублировать)

| Компонент | Роль | Действие |
|-----------|------|----------|
| `ResendEmailClient.send(to, subject, html)` | низкоуровневая отправка | оставить как есть |
| `EmailLog` + `EmailLogRepository` | лог отправок, идемпотентность | расширить под новые типы |
| `EmailType` (enum) | тип для лога/статистики | **заменить/связать** с `TemplateKey` |
| `EmailTemplates` (статик HTML) | дефолтные тексты + layout | реорганизовать (см. §4) |
| `EmailTemplateMapper`, `EmailManagementService` | текущий CRUD по jsonb (3 типа) | **заменить** на реестр + таблицу |
| `CompanySettings.email_templates` (jsonb) | старое хранилище оверрайдов | мигрировать в новую таблицу, поле убрать |
| `AppointmentEmailComposer`, `AppointmentSideEffectService` | триггеры appointment-писем | перевести на новый `NotificationSender` |

---

## 2. Реестр шаблонов (ядро)

`TemplateKey` — enum, единый источник правды о письме.

```
enum TemplateKey {
  TEAM_INVITE(LIFECYCLE, CONFIGURABLE?, vars...),
  BOOKING_CONFIRMED(CLIENT_OP, CONFIGURABLE, vars...),
  ... // все ключи из bucket-a-active.md
  ;
  Category category;     // SECURITY | LIFECYCLE | BILLING | CLIENT_OP | MARKETING
  Ownership ownership;   // SYSTEM | CONFIGURABLE
  Set<TemplateVar> requiredVars;
}
```

- **Ownership** определяет видимость на фронте: `CONFIGURABLE` редактируется студией,
  `SYSTEM` — только нами (на странице не показывается или read-only).
- `TemplateVar` — enum переменных из реестра в [`README.md`](./README.md) (`APP_NAME`,
  `CLIENT_NAME`, ...). Нужен для валидации оверрайдов и чипов в UI.
- В реестр включаем письма ведра A. Ведро B/C/D — НЕ добавляем (фич нет).

---

## 3. Хранение оверрайдов (новая таблица)

Flyway-миграция `V3__email_template_overrides.sql` (номер — следующий свободный).

```sql
CREATE TABLE email_template_override (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL,
    template_key varchar(64) NOT NULL,
    locale       varchar(8)  NOT NULL,   -- 'uk' | 'en'
    subject      text NOT NULL,
    body         text NOT NULL,
    updated_by   uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_override UNIQUE (tenant_id, template_key, locale)
);
CREATE INDEX idx_override_tenant ON email_template_override (tenant_id);
```

- Только **configurable**-письма попадают сюда (system не переопределяются).
- Хранится только то, что студия реально изменила. Нет строки → берём дефолт.
- Миграция данных из старого `CompanySettings.email_templates` jsonb (3 типа) — разовый
  бэкофис-скрипт/SQL в той же миграции; после — колонку jsonb удалить.

---

## 4. Дефолты + layout (в коде)

- `TemplateDefaults` — резолв дефолтного `{subject, body}` по `(TemplateKey, locale)`.
  Источник: i18n-ресурсы (`messages_uk/en` или отдельные `mail_*.properties`) ИЛИ статические
  методы по образцу текущего `EmailTemplates`. Тексты — из `bucket-a-active.md`.
- `EmailLayout` — единый HTML-каркас (шапка с лого, кнопка, футер). Один base + маркетинговый
  футер (unsubscribe+адрес) для `category=MARKETING`. `{app_name}` подставляется как переменная.
- Никакого хардкода «INKAT» — только `{app_name}` (значение из конфигурации приложения).

---

## 5. Резолв + отправка (сервисы)

```
TemplateResolver.resolve(tenantId, key, locale) -> RenderedContent(subject, body)
   content = override(tenant, key, locale)
           ?? override(tenant, key, defaultLocale)
           ?? TemplateDefaults(key, locale)

NotificationSender.send(tenantId, recipient, key, vars, locale)
   1. content = TemplateResolver.resolve(...)
   2. subject = substitute(content.subject, vars)     // {app_name}, {client_name}, ...
   3. html    = EmailLayout.wrap(substitute(content.body, vars), key.category)
   4. ResendEmailClient.send(recipient.email, subject, html)
   5. EmailLog.save(...)                               // тип = key, для идемпотентности
```

- `substitute` — единая подстановка переменных (одна реализация, без ручных парсеров).
- Идемпотентность для cron-писем (reminder/aftercare/birthday): проверка через `EmailLog`
  как сейчас (`wasAlreadySent`), обобщить под `TemplateKey` + опциональный entityId.
- Все триггеры (события / status-change / cron / ручное) зовут **один** `NotificationSender`.

---

## 6. API для фронта (configurable only)

Контроллер `EmailTemplateController` (заменяет текущие endpoint'ы шаблонов).

```
GET    /email/templates?locale=uk
       -> [{ key, category, subject, body, availableVars, isOverridden }]   // только CONFIGURABLE
PUT    /email/templates/{key}?locale=uk   body: { subject, body }
       -> upsert override; валидация: body содержит все requiredVars(key)
DELETE /email/templates/{key}?locale=uk
       -> сброс к дефолту (удалить строку)
GET    /email/templates/{key}/preview?locale=uk
       -> rendered HTML с примерными данными (sample vars)
```

- `availableVars` берётся из `TemplateKey.requiredVars` (+ опциональные) → фронт рисует чипы.
- `isOverridden` = есть ли строка в таблице (для бейджа «изменено / дефолт»).
- System-письма через этот API не отдаются и не редактируются.

---

## 7. Порядок реализации (для AI, по шагам)

1. `TemplateVar`, `Category`, `Ownership` enums + `TemplateKey` реестр (только ключи ведра A).
2. `TemplateDefaults` + перенос текстов из `bucket-a-active.md` (uk/en), `{app_name}` как var.
3. `EmailLayout` (base + marketing footer).
4. Flyway `V3` + entity `EmailTemplateOverride` + repository + миграция старого jsonb.
5. `TemplateResolver` + `substitute` + unit-тесты (override>fallback>default, валидация vars).
6. `NotificationSender` + интеграция с `EmailLog` (идемпотентность) + тесты.
7. `EmailTemplateController` (4 endpoint'а) + DTO + тесты; удалить старый шаблон-CRUD.
8. Перевести `AppointmentEmailComposer`/`SideEffectService` на `NotificationSender`; убрать
   старый `EmailService` appointment-API и хардкод «INKAT». Тесты обновить.

После foundation письма ведра A добавляются так: ключ в `TemplateKey` + дефолт-текст +
вызов `NotificationSender.send(...)` в нужном триггере. Фич-страница работает без изменений.

---

## 8. Definition of Done

- [ ] Ни одного «INKAT» в коде; везде `{app_name}`.
- [ ] Configurable-письмо можно переопределить и сбросить через API; system — нельзя.
- [ ] Дефолты резолвятся на uk/en; fallback на defaultLocale работает.
- [ ] Старый jsonb-CRUD и `CompanySettings.email_templates` удалены, данные мигрированы.
- [ ] Appointment-письма работают через новый `NotificationSender` (регресс не сломан).
- [ ] Тесты зелёные (unit на resolver/validation/sender, контроллерные на 4 endpoint'а).
