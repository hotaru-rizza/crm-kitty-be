# API Response Format (Option A)

All REST endpoints return `ApiResponse<T>` unless noted (OAuth redirects, payment webhooks).

## Success

```json
{
  "success": true,
  "data": { ... }
}
```

## Paginated list

```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

## Error

HTTP status reflects the error. Body:

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Resource not found",
    "timestamp": "2026-06-10T12:00:00Z"
  }
}
```

## Backend helpers

- `ApiResponses.ok(data)`
- `ApiResponses.created(data)`
- `ApiResponses.page(springPage)`
- `ApiResponses.empty()`
- `ApiResponses.requireConsumer(user)` — throws `UNAUTHORIZED`

## Exceptions (webhooks / redirects)

| Endpoint | Response |
|----------|----------|
| `GET /public/google/callback` | HTTP 302 redirect |
| `POST /payments/monobank/webhook` | HTTP 200 empty body |

## Frontend

- **crm-kitty:** `authFetcher` returns full JSON; use `.data` / `.pagination`
- **client-tattoo-web:** `apiGetData<T>()`, `apiGetPage<T>()` in `lib/api.ts`
