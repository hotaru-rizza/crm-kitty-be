# Email History Filters — Implementation Handoff

Goal: расширить вкладку **History** («Журнал») на странице «Оповіщення» полезными
фильтрами: **поиск**, **тип листа (trigger)**, **статус**, **диапазон дат**.

This doc is a self-contained handoff. Follow it top to bottom. Two repos:
- Backend: `crm-kitty-be`
- Frontend: `crm-kitty`

---

## STATUS — what is already done vs remaining

### DONE (do NOT redo)

Backend (`crm-kitty-be`) — production code already updated:
- `EmailMessageRepository.findFiltered(...)` — now also filters by `status` and `search`.
  New signature: `(UUID tenantId, TriggerType triggerType, EmailMessageStatus status, Instant from, Instant to, String search, Pageable pageable)`.
- `EmailMessageQueryService.getMessages(...)` — new signature with `status` + `search`;
  blank search is normalized to `null` via `StringUtils.hasText`.
- `EmailController.getMessages` — new query params `status`, `from`, `to`, `search`
  (in addition to existing `page`, `size`, `triggerType`).

Frontend (`crm-kitty`) — already updated:
- `src/utils/date.ts` — added `buildQuickPeriodRange(period, custom)` returning `{ from?, to? }`
  (ISO strings) and exported type `DateRangeIso`. Uses `AUDIT_QUICK_PERIOD` constant.
- `src/features/settings/tabs/AuditLog/auditLog.utils.ts` — `buildRange` now delegates
  to `buildQuickPeriodRange` (de-duplicated). No behavior change.

### REMAINING (this is the work to do)

1. Backend tests — fix call sites for new signatures (Section A).
2. Frontend API — `fetchEmailMessages` params (Section B).
3. Frontend hook — `useEmailMessages` params + queryKey (Section C).
4. Frontend UI — rebuild `HistoryTab.tsx` with `FilterIsland` (Section D).
5. Locales — add uk/en strings (Section E).
6. Verify/build (Section F).

---

## Reference: enums / types

- Backend `EmailMessageStatus` (`com.inkflow.crm.domain.enums.EmailMessageStatus`): `PENDING`, `SENT`, `FAILED`.
- Frontend `EmailMessageStatus` (`src/services/email/types/email.types.ts`): `'PENDING' | 'SENT' | 'FAILED'`.
- Frontend `TriggerType` union lives in the same file.
- Quick-period constant `AUDIT_QUICK_PERIOD` (`src/constants/index.ts`): `{ WEEK: '7d', MONTH: '30d', CUSTOM: 'custom' }`.

---

## A. Backend tests (only edit test files, not production code)

### A1 — `src/test/java/com/inkflow/crm/module/email/service/sending/EmailMessageQueryServiceTest.java`

In `getMessages_mapsFilteredResults()` replace:
```java
        when(emailMessageRepository.findFiltered(tenantId, TriggerType.BEFORE_BOOKING, from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(emailMessageMapper.toDto(message)).thenReturn(dto);

        Page<EmailMessageDto> result = emailMessageQueryService.getMessages(
                tenantId, TriggerType.BEFORE_BOOKING, from, to, pageable);
```
with:
```java
        when(emailMessageRepository.findFiltered(
                tenantId, TriggerType.BEFORE_BOOKING, EmailMessageStatus.SENT, from, to, "client", pageable))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(emailMessageMapper.toDto(message)).thenReturn(dto);

        Page<EmailMessageDto> result = emailMessageQueryService.getMessages(
                tenantId, TriggerType.BEFORE_BOOKING, EmailMessageStatus.SENT, from, to, "client", pageable);
```
`EmailMessageStatus` is already imported in this file.

### A2 — `src/test/java/com/inkflow/crm/module/email/controller/EmailControllerIntegrationTest.java`

Two call sites. Argument order: `tenantId, triggerType, status, from, to, search, pageable`.

Call site A (~line 148):
```java
        var messages = emailMessageRepository.findFiltered(
                bundle.tenant().getId(), TriggerType.MANUAL, null, null, PageRequest.of(0, 10)).getContent();
```
→
```java
        var messages = emailMessageRepository.findFiltered(
                bundle.tenant().getId(), TriggerType.MANUAL, null, null, null, null, PageRequest.of(0, 10)).getContent();
```

Call site B (~line 257):
```java
        assertEquals(0, emailMessageRepository.findFiltered(
                tenantA.tenant().getId(), null, null, null, PageRequest.of(0, 10)).getTotalElements());
```
→
```java
        assertEquals(0, emailMessageRepository.findFiltered(
                tenantA.tenant().getId(), null, null, null, null, null, PageRequest.of(0, 10)).getTotalElements());
```

Verify: `cd crm-kitty-be && ./mvnw -q -Dtest=EmailMessageQueryServiceTest,EmailControllerIntegrationTest test`

---

## B. Frontend API — `src/services/email/api/email.api.ts`

Add an optional filters object. Replace the current `fetchEmailMessages`:
```ts
export function fetchEmailMessages(page = 0, size = 20, triggerType?: TriggerType) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (triggerType) params.set('triggerType', triggerType);
  return authFetcher<{ data: EmailMessage[]; pagination: PaginationMeta }>(
    `${API_ENDPOINTS.EMAILS}/messages?${params}`,
  );
}
```
with:
```ts
export interface EmailMessagesFilters {
  triggerType?: TriggerType;
  status?: EmailMessageStatus;
  from?: string;
  to?: string;
  search?: string;
}

export function fetchEmailMessages(page = 0, size = 20, filters: EmailMessagesFilters = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.triggerType) params.set('triggerType', filters.triggerType);
  if (filters.status) params.set('status', filters.status);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.search?.trim()) params.set('search', filters.search.trim());
  return authFetcher<{ data: EmailMessage[]; pagination: PaginationMeta }>(
    `${API_ENDPOINTS.EMAILS}/messages?${params}`,
  );
}
```
Add `EmailMessageStatus` to the type import at the top of the file (it imports from `../types/email.types`).
Export `EmailMessagesFilters` from `src/services/email/index.ts` if you want it reusable (optional).

---

## C. Frontend hook — `src/services/email/hooks/useEmail.ts`

Replace `useEmailMessages`:
```ts
export const useEmailMessages = (page = 0, size = 20, triggerType?: TriggerType) => {
  return useQuery({
    queryKey: ['email-messages', page, size, triggerType],
    queryFn: () => fetchEmailMessages(page, size, triggerType),
  });
};
```
with:
```ts
export const useEmailMessages = (page = 0, size = 20, filters: EmailMessagesFilters = {}) => {
  return useQuery({
    queryKey: ['email-messages', page, size, filters],
    queryFn: () => fetchEmailMessages(page, size, filters),
  });
};
```
Update the import from `../api/email.api` to also import the `EmailMessagesFilters` type
(use `import type { EmailMessagesFilters } from '../api/email.api';` or merge into existing import).

---

## D. Frontend UI — `src/features/mailings/MailingsPage/tabs/HistoryTab.tsx`

Pattern to mirror: `src/features/settings/tabs/AuditLog/AuditLog.tsx` (FilterIsland + quick
chips + custom-range Popover). Reuse `FilterIsland` from `@/components/ui`.

State to add:
```ts
const [page, setPage] = useState(0);
const [size, setSize] = useState(MAILINGS_DEFAULT_PAGE_SIZE);
const [triggerType, setTriggerType] = useState<TriggerType | null>(null);
const [status, setStatus] = useState<EmailMessageStatus | null>(null);
const [search, setSearch] = useState('');
const [debouncedSearch] = useDebouncedValue(search, 300); // from '@mantine/hooks'
const [period, setPeriod] = useState<string>(''); // '' = all time; or AUDIT_QUICK_PERIOD.*
const [customRange, setCustomRange] = useState<[Date | null, Date | null]>([null, null]);
const [datePickerOpen, setDatePickerOpen] = useState(false);
const [tempRange, setTempRange] = useState<[Date | null, Date | null]>([null, null]);
```

Build range + query:
```ts
const { from, to } = period ? buildQuickPeriodRange(period, customRange) : { from: undefined, to: undefined };

const { data, isLoading, isError, error, refetch } = useEmailMessages(page, size, {
  triggerType: triggerType ?? undefined,
  status: status ?? undefined,
  from,
  to,
  search: debouncedSearch,
});
```
Reset page to 0 in a `useEffect` on `[triggerType, status, debouncedSearch, from, to]`.

Filters UI (inside `<FilterIsland hasActiveFilters={...}>`):
- `TextInput` search — placeholder `mailings.history.searchPlaceholder`, leftSection `IconSearch`.
- `Select` trigger type — options from `useTriggerTypes()` mapped to
  `{ value: item.type, label: t('mailings.triggerTypes.'+item.type) }`. Keep this — it is the
  legit backend-driven list (`/emails/trigger-types`), NOT hardcoded enums. clearable.
- `Select` status — options `['SENT','FAILED','PENDING']` →
  `{ value, label: t('mailings.history.status.'+value) }`. clearable.
- Quick chips for period: WEEK / MONTH (use `AUDIT_QUICK_PERIOD`), plus custom-range `Popover`
  with `DatePicker type="range"` (copy from AuditLog.tsx). Make period clearable (toggle off → '').
- "Clear all" link when any filter active; resets all state + page 0.

Keep the existing table (`DataTable`) and `TablePagination` + `PageSizeControl` exactly as now.
Status column already renders a colored `Badge`; reuse `STATUS_COLORS`.

`hasActiveFilters = !!triggerType || !!status || !!debouncedSearch.trim() || !!period`.

Imports to add: `buildQuickPeriodRange` from `@/utils/date`, `AUDIT_QUICK_PERIOD` from `@/constants`,
`FilterIsland` from `@/components/ui`, `useDebouncedValue` from `@mantine/hooks`,
`TextInput`, `Chip`, `Popover`, `Button`, `Group` from `@mantine/core`,
`DatePicker` from `@mantine/dates`, icons `IconSearch`, `IconCalendar` from `@tabler/icons-react`,
and `EmailMessageStatus` type from `@/services/email`.

---

## E. Locales — add strings under `history` (and confirm `triggerTypes` exists)

`src/locales/uk/mailings.json` → inside `"history"` object add:
```json
"searchPlaceholder": "Пошук за email, ім'ям або темою",
"filterStatus": "Статус",
"allStatuses": "Усі статуси",
"period7d": "7 днів",
"period30d": "30 днів",
"customPeriod": "Період",
"selectRange": "Оберіть діапазон",
"status": {
  "SENT": "Надіслано",
  "FAILED": "Помилка",
  "PENDING": "В черзі"
}
```
`src/locales/en/mailings.json` → inside `"history"`:
```json
"searchPlaceholder": "Search by email, name or subject",
"filterStatus": "Status",
"allStatuses": "All statuses",
"period7d": "7 days",
"period30d": "30 days",
"customPeriod": "Period",
"selectRange": "Select range",
"status": {
  "SENT": "Sent",
  "FAILED": "Failed",
  "PENDING": "Queued"
}
```
Note: the table status `Badge` currently shows the raw enum (`row.status`). Optionally switch it
to `t('mailings.history.status.'+row.status)` for localized labels.

---

## F. Verify

Frontend: `cd crm-kitty && npm run build` (must pass, no TS errors).
Backend: `cd crm-kitty-be && ./mvnw -q test` (after Section A).

Manual QA:
- Search filters by recipient email / name / subject.
- Status filter shows only matching rows.
- Trigger-type filter works.
- Quick chips 7d/30d and custom range filter by date.
- "Clear all" resets everything and pagination returns to page 1.
