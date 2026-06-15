# Missing / Incomplete Features (revealed by mailings)

Письма — хороший детектор пробелов в продукте. Здесь — фичи, которых не хватает, чтобы
шаблоны можно было реально отправлять. Разбито по тому, **насколько это большой кусок работы**.

Легенда статуса бэка: ✅ готово · 🟡 частично · ❌ нет.

---

## Категория 1 — Фича отсутствует целиком (большой кусок)

Письма из ведра B заблокированы, пока этого нет.

### 1.1 Recurring billing / подписки ❌
Сейчас: разовая оплата через Monobank (`SubscriptionService.createCheckout`), trial на 14 дней,
gate по истечению (`SubscriptionFilter`). На фронте прямо написано «no auto-renewal».
Нет: сохранённых карт, автосписаний, dunning-цикла, счетов (PDF), смены тарифа, отмены
автопродления.
**Разблокирует:** PAYMENT_RECEIPT, RENEWAL_REMINDER, PAYMENT_FAILED, PAYMENT_RETRY,
SUBSCRIPTION_SUSPENDED, CARD_EXPIRING, PLAN_UPDATED, SUBSCRIPTION_CANCELED.

### 1.2 Депозит-gate + возвраты ❌ (данные частично есть)
Сейчас: поле `prepayment` у записи, Monobank invoice типа `deposit`, учёт депозита в финансах.
Нет: логики «запись подтверждается только после предоплаты», автоматических статусов и
возврата депозита клиенту.
**Разблокирует:** DEPOSIT_REQUEST, DEPOSIT_RECEIVED, DEPOSIT_REFUND.

### 1.3 Система отзывов ❌
Сейчас: `NotificationType.REVIEW_RECEIVED` существует, но отзывов в CRM нет — только mock в
public artist API.
**Разблокирует:** REVIEW_RECEIVED (внутренний отзыв мастеру/владельцу).
*Прим.:* REVIEW_REQUEST (клиенту) можно сделать и без этого — если CTA ведёт на внешний
ресурс (Google/Instagram). Тогда это ведро A.

### 1.4 Онлайн-бронирование со слотами + client self-service ❌
Сейчас: consumer booking — это **лид с идеей** (`/public/consumer/requests`), не запись на
конкретный слот. Нет публичного управления записью (отмена/перенос по ссылке).
**Разблокирует:** корректный BOOKING_DECLINED, CTA «скасувати/перенести» в клиентских письмах.

### 1.5 Digest / аналитические рассылки ❌
Сейчас: аналитика в CRM есть, но нет cron-джоб, которые собирают и шлют сводку.
**Разблокирует:** DAILY_SCHEDULE (мастеру), WEEKLY_SUMMARY (владельцу).

### 1.6 Marketing consent + unsubscribe ❌ (предусловие, маленькая фича)
Нет: флага `marketingConsent` у клиента, endpoint'а `/unsubscribe`, маркетингового футера.
**Разблокирует (легально):** BIRTHDAY, WINBACK, маркетинговый BULK, PRODUCT_UPDATE.
Это не «целая фича», но без неё нельзя слать маркетинг — выделено отдельно.

---

## Категория 2 — Фича есть, но недоделана (докрутка в коде)

Это ведро A. Маленькие правки, новой фичи строить не нужно.

| Что | Состояние | Что докрутить | Письмо |
|-----|-----------|---------------|--------|
| Приглашение сотрудника | 🟡 invite-токен, `/invite/:token`, accept-invite | сейчас админ копирует ссылку руками → слать email | TEAM_INVITE |
| Деактивация/реактивация | 🟡 `StaffLifecycleService` | добавить вызов письма в методы | STAFF_DEACTIVATED / REACTIVATED |
| Онбординг | 🟡 flow есть | письмо после верификации | WELCOME_ONBOARD |
| Смена роли | 🟡 роли/права редактируются | добавить событие смены роли | ROLE_CHANGED |
| Consumer booking | 🟡 заявка сохраняется, мастеру идёт push | добавить email мастеру + ack клиенту (если есть email) | NEW_REQUEST_TO_APPROVE, BOOKING_REQUEST_RECEIVED |
| Статусы записи | 🟡 enum new/confirmed/in_progress/done/cancelled | добавить `NO_SHOW` | (NO_SHOW → ведро B) |
| Статусы заявки | 🟡 enum new/replied/converted/spam | добавить `DECLINED` | (BOOKING_DECLINED → ведро B) |
| День рождения | 🟡 `birthDate`/`birthday` есть | cron + consent | BIRTHDAY |
| Winback | 🟡 история визитов есть | запрос «давно не был» + cron + consent | WINBACK |
| Trial-письма | 🟡 trial создаётся, gate работает | письмо при старте + cron «истекает через 3 дня» | TRIAL_STARTED, TRIAL_EXPIRING |

> NO_SHOW и BOOKING_DECLINED стоят в ведре B, потому что письмо требует **нового статуса**
> в домене, хоть правка и небольшая. Сам enum-патч — из категории 2; письмо включится после.

---

## Категория 3 — Чужая зона / вне scope MVP

Письма есть в архиве, но фичу мы не строим (или ей владеет Supabase).

| Что | Почему | Письмо |
|-----|--------|--------|
| Подтверждение почты, сброс пароля | Supabase Auth | VERIFY_EMAIL, PASSWORD_RESET |
| Уведомления безопасности | отложено | PASSWORD_CHANGED, NEW_LOGIN_DETECTED |
| 2FA | нет фичи | TWO_FACTOR |
| Смена email | нет фичи | EMAIL_CHANGE_VERIFY / EMAIL_CHANGED |
| GDPR-экспорт/удаление данных | нет фичи | DATA_EXPORT_READY |
| Полное удаление аккаунта студии | не делаем в MVP | ACCOUNT_DELETION |
| SMS / Telegram каналы | не в MVP (email-only) | — (кросс-канально) |

---

## Сводка приоритетов

1. **Сначала маленькое (категория 2)** — даёт быстрые рабочие письма ведра A.
2. **Предусловие маркетинга (1.6)** — разблокирует birthday/winback/bulk.
3. **Большие фичи (1.1–1.5)** — по продуктовым приоритетам; письма ждут в ведре B.
