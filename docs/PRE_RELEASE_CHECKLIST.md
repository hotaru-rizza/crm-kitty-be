ок# Pre-Release Checklist — Observability & Reliability

## 1. Sentry (ошибки + tracing)

Уже интегрирован. Нужно поправить одну вещь.

### Что сделать

На Render **prod** добавить / обновить env vars:

```
SENTRY_DSN=https://your-key@sentry.io/project-id
SENTRY_ENVIRONMENT=production
SENTRY_TRACES_SAMPLE_RATE=0.1
```

На Render **staging**:

```
SENTRY_ENVIRONMENT=staging
SENTRY_TRACES_SAMPLE_RATE=0.0
```

### Почему

- `traces-sample-rate: 0.0` (текущий дефолт) = нет performance tracing
- `0.1` = 10% запросов трекается, хватит для debugging
- Sentry бесплатно: 5K errors/мес, 10K transactions/мес

### Как искать проблему конкретного клиента

```
Sentry Dashboard → Issues → фильтр:
  tag: tenantId = "<tenant-uuid>"
```

Работает потому что `JwtAuthenticationFilter` уже пишет `tenantId` и `userId` в Sentry scope.

---

## 2. Log Stream (хранение логов)

### Проблема

Render хранит логи **7 дней**. После — пропали навсегда.

### Что сделать

Render Dashboard → Settings → **Log Streams** → добавить endpoint.

Рекомендация — **Axiom** (бесплатно: 500MB/мес, 30 дней retention):

1. Зарегистрироваться на [axiom.co](https://axiom.co)
2. Создать Dataset → получить API token
3. Render Log Stream: `https://api.axiom.co/v1/datasets/<dataset>/ingest`
4. Header: `Authorization: Bearer <api-token>`

### Как искать логи конкретного клиента

В Axiom:

```
tenantId="<uuid>" | level="ERROR" | _time > now()-7d
```

Или по requestId (если клиент скопировал из ответа `X-Request-Id` header):

```
requestId="<uuid>"
```

### Что уже логируется в каждой строке

Формат из `application.yml`:

```
2026-07-03 17:23:45.123 [thread] ERROR [requestId] [tenantId] [userId] ServiceName - сообщение
```

Всё что нужно для отладки — уже там.

---

## 3. UptimeRobot (мониторинг доступности)

### Что сделать

1. [uptimerobot.com](https://uptimerobot.com) → бесплатный аккаунт
2. New Monitor → HTTP(S)
3. URL: `https://api.inkat.app/api/actuator/health`
4. Interval: 5 минут
5. Alert contact: email или Telegram

### Почему именно `/actuator/health`

- Всегда отвечает `{"status":"UP"}` если сервис живой
- На prod ограничен только этим endpoint (настроено в `application-prod.yml`)
- Не требует авторизации

---

## 4. Supabase Backups (защита данных)

### Что проверить сейчас (до релиза)

Supabase Dashboard → Database → **Backups** → убедиться что видишь свежий автоматический backup.

### Что включено на Pro плане

| Защита | Детали |
|--------|--------|
| Daily backups | Автоматически, хранятся 7 дней |
| Manual backups | Можно скачать в любой момент |
| Point-in-Time Recovery | Addon (+$100/мес) — для будущего, не сейчас |

### Сценарий "что-то удалилось"

```
Supabase Dashboard → Database → Backups → Restore → выбрать точку
```

Это откатит всю БД. Занимает ~5-15 минут в зависимости от размера.

### Правило: staging first

**Любая новая Flyway-миграция** сначала деплоится на staging, проверяется, и только потом идёт на prod.

Никогда не пушить нетестированную миграцию напрямую в `main`.

---

## 5. Пул коннекшинов

Текущая конфигурация (`application.yml`):

```yaml
hikari:
  maximum-pool-size: 5   # оптимально для 0.5 CPU (формула: cores × 2 + 1)
  minimum-idle: 2
  connection-timeout: 5000
  max-lifetime: 600000
```

### Когда увеличивать

Только если в логах появится:

```
HikariPool-1 - Connection is not available, request timed out after 5000ms
```

Тогда: `HIKARI_MAX_POOL_SIZE=8` на Render → наблюдать. Не прыгать сразу на 15.

### Supabase Pro лимиты

- `max_connections = 200`
- Тебе доступно ~180 (20 резервирует Supabase)
- При pool=5 и 1 инстансе — занято **5 из 180**. Запас огромный.

---

## 6. Scheduler thread pool (мелкая, но важная правка)

Spring `@Scheduled` по умолчанию использует **1 thread** для всех scheduled задач.
Это значит `OutboxPoller` и `TriggerScheduler` могут блокировать друг друга.

### Что добавить

Новый файл `src/main/java/com/inkflow/crm/config/SchedulerConfig.java`:

```java
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    private static final int SCHEDULER_POOL_SIZE = 3;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(Executors.newScheduledThreadPool(SCHEDULER_POOL_SIZE));
    }
}
```

---

## 7. Итоговый чеклист

| # | Задача | Сложность | Цена | Статус |
|---|--------|-----------|------|--------|
| 1 | Sentry `traces-sample-rate: 0.1` на prod | 2 мин | $0 | [ ] |
| 2 | Подключить Axiom Log Stream на Render | 10 мин | $0 | [ ] |
| 3 | UptimeRobot на `/actuator/health` | 5 мин | $0 | [ ] |
| 4 | Проверить Supabase backups работают | 2 мин | $0 | [ ] |
| 5 | Добавить `SchedulerConfig` с 3 threads | 5 мин | $0 | [ ] |
| 6 | Правило: staging-first для миграций | договорённость | $0 | [ ] |

**Итого: ~25 минут работы, $0 дополнительных затрат.**

---

## Справка: как читать логи когда клиент жалуется

```
1. Клиент говорит: "не работает создание записи"
   → Спросить: примерное время когда было

2. Sentry → Issues → tag tenantId = "<их uuid>"
   → Видишь exception, stack trace, что именно упало

3. Если нужно больше деталей:
   Axiom → запрос: tenantId="<uuid>" | _time > "<время>"
   → Видишь полную цепочку логов за этот период

4. Если есть requestId (из заголовка X-Request-Id ответа):
   Axiom → requestId="<uuid>"
   → Точная цепочка одного конкретного запроса от начала до конца
```
