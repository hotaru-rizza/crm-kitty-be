# InkFlow CRM — API Specification

> **⚠️ Partially outdated (Jan 2026).** See [docs/API_SPECIFICATION_SUMMARY.md](docs/API_SPECIFICATION_SUMMARY.md) and [docs/BACKEND_TECH_DEBT.md](docs/BACKEND_TECH_DEBT.md) for current modules and endpoints. Removed: waivers, inventory, gift certificates, promotions, public subdomain booking.

> **Версія**: 1.0  
> **Дата**: 25 січня 2026  
> **Backend**: Spring Boot + Hibernate + PostgreSQL  
> **Auth**: Supabase Auth (JWT)  
> **Multi-tenancy**: Row Level Security (RLS)

---

## Зміст

1. [Архітектура та безпека](#1-архітектура-та-безпека)
2. [Загальні правила API](#2-загальні-правила-api)
3. [Auth Module](#3-auth-module)
4. [Users & Staff Module](#4-users--staff-module)
5. [Clients Module](#5-clients-module)
6. [Services Module](#6-services-module)
7. [Appointments Module](#7-appointments-module)
8. [Projects Module](#8-projects-module)
9. [Requests (Leads) Module](#9-requests-leads-module)
10. [Transactions Module](#10-transactions-module)
11. [Waivers Module](#11-waivers-module)
12. [Locations Module](#12-locations-module)
13. [Settings Module](#13-settings-module)
14. [RLS Policies](#14-rls-policies)
15. [Enums & Constants](#15-enums--constants)

---

## 1. Архітектура та безпека

### 1.1. Multi-tenancy Model

```
┌─────────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Client Request                                          │
│     ┌──────────────────────────────────────────────────┐   │
│     │ GET /api/appointments                            │   │
│     │ Authorization: Bearer eyJhbGciOiJS...            │   │
│     │ X-Location-Id: loc-456 (optional)                │   │
│     └──────────────────────────────────────────────────┘   │
│                          │                                  │
│                          ▼                                  │
│  2. JwtAuthFilter                                           │
│     - Валідація JWT через Supabase                          │
│     - Витягування: user_id, tenant_id, role, location_ids  │
│                          │                                  │
│                          ▼                                  │
│  3. TenantContextFilter                                     │
│     - SET app.current_tenant = 'tenant-123'                │
│     - SET app.current_user = 'user-456'                    │
│     - Валідація X-Location-Id ∈ user.location_ids          │
│                          │                                  │
│                          ▼                                  │
│  4. PostgreSQL + RLS                                        │
│     - Автоматична фільтрація по tenant_id                  │
│     - Неможливо побачити чужі дані                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2. JWT Token Structure

```json
{
  "sub": "user-uuid-123",
  "email": "artist@salon.com",
  "tenant_id": "tenant-uuid-456",
  "role": "artist",
  "location_ids": ["loc-1", "loc-2"],
  "iat": 1706180000,
  "exp": 1706266400
}
```

### 1.3. Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | ✅ | `Bearer {jwt_token}` |
| `X-Location-Id` | ❌ | UUID локації для фільтрації. Якщо не передано — всі локації (для owner/admin) |
| `Content-Type` | ✅ (POST/PUT/PATCH) | `application/json` |

### 1.4. Права доступу (RBAC)

| Операція | Owner | Admin | Artist |
|----------|-------|-------|--------|
| **Appointments** |
| Переглядати всі записи | ✅ | ✅ | ❌ Тільки свої |
| Створювати записи | ✅ | ✅ | ✅ |
| Редагувати чужі записи | ✅ | ✅ | ❌ |
| Видаляти записи | ✅ | ✅ | ❌ |
| **Clients** |
| Переглядати всіх | ✅ | ✅ | ✅ |
| Створювати | ✅ | ✅ | ✅ |
| Редагувати | ✅ | ✅ | ✅ |
| Видаляти | ✅ | ✅ | ❌ |
| **Staff** |
| Переглядати всіх | ✅ | ✅ | ✅ |
| Запрошувати | ✅ | ✅ | ❌ |
| Редагувати профіль | ✅ | ✅ | ❌ Тільки свій |
| Видаляти | ✅ | ❌ | ❌ |
| **Finance** |
| Переглядати всі транзакції | ✅ | ✅ | ❌ Тільки свої |
| Створювати витрати | ✅ | ✅ | ❌ |
| Бачити загальну статистику | ✅ | ✅ | ❌ |
| **Projects** |
| Переглядати всі | ✅ | ✅ | ❌ Тільки свої |
| Створювати | ✅ | ✅ | ✅ |
| Редагувати | ✅ | ✅ | ❌ Тільки свої |
| **Settings** |
| Налаштування компанії | ✅ | ❌ | ❌ |
| Керувати локаціями | ✅ | ❌ | ❌ |
| Керувати послугами | ✅ | ✅ | ❌ |

---

## 2. Загальні правила API

### 2.1. Base URL

```
Production: https://api.inkflow.app/v1
Development: http://localhost:8080/api
```

### 2.2. Response Format

**Success Response:**
```json
{
  "data": { ... },
  "message": "Operation successful"
}
```

**List Response (with pagination):**
```json
{
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 150,
    "totalPages": 8
  }
}
```

**Error Response:**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      { "field": "phone", "message": "Invalid phone format" }
    ]
  },
  "timestamp": "2026-01-25T12:00:00Z"
}
```

### 2.3. Error Codes

| HTTP Code | Error Code | Description |
|-----------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Невалідні дані |
| 401 | `UNAUTHORIZED` | Немає або невалідний токен |
| 403 | `FORBIDDEN` | Немає прав на операцію |
| 403 | `LOCATION_ACCESS_DENIED` | Немає доступу до локації |
| 404 | `NOT_FOUND` | Ресурс не знайдено |
| 409 | `CONFLICT` | Конфлікт (наприклад, час зайнятий) |
| 422 | `BUSINESS_RULE_VIOLATION` | Порушення бізнес-правил |
| 500 | `INTERNAL_ERROR` | Внутрішня помилка |

### 2.4. Audit Fields

Всі сутності містять:

```typescript
{
  id: UUID,
  tenantId: UUID,           // Автоматично з JWT
  createdAt: Timestamp,     // Автоматично
  updatedAt: Timestamp,     // Автоматично
  createdBy: UUID,          // User ID з JWT
  updatedBy: UUID,          // User ID з JWT
  deletedAt: Timestamp | null  // Soft delete
}
```

### 2.5. Pagination & Filtering

**Query Parameters:**
```
GET /api/clients?page=1&pageSize=20&sort=lastName:asc&search=John&status=active
```

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 1 | Номер сторінки |
| `pageSize` | int | 20 | Розмір сторінки (max: 100) |
| `sort` | string | `createdAt:desc` | Сортування `field:asc\|desc` |
| `search` | string | - | Повнотекстовий пошук |

---

## 3. Auth Module

### 3.1. Login (через Supabase)

> **Note**: Авторизація через Supabase Auth SDK на фронтенді. Backend тільки валідує JWT.

**Frontend flow:**
```typescript
const { data, error } = await supabase.auth.signInWithPassword({
  email: 'artist@salon.com',
  password: 'password123'
});
// data.session.access_token → використовуємо для API
```

### 3.2. Get Current User

```
GET /api/auth/me
```

**Response DTO: `CurrentUserResponse`**
```typescript
{
  id: UUID,
  email: string,
  firstName: string,
  lastName: string,
  avatar: string | null,
  role: "owner" | "admin" | "artist",
  tenantId: UUID,
  tenantName: string,
  locationIds: UUID[],
  permissions: string[]
}
```

### 3.3. Refresh Token

Handled by Supabase SDK automatically.

---

## 4. Users & Staff Module

### 4.1. List Staff

```
GET /api/staff
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `role` | string | Filter by role: `owner`, `admin`, `artist` |
| `status` | string | Filter by status: `working`, `vacation`, `sick` |
| `locationId` | UUID | Filter by assigned location |

**Response DTO: `StaffListResponse`**
```typescript
{
  data: StaffMemberDto[],
  pagination: PaginationDto
}
```

**DTO: `StaffMemberDto`**
```typescript
{
  id: UUID,
  firstName: string,
  lastName: string,
  email: string,
  phone: string,
  avatar: string | null,
  role: "owner" | "admin" | "artist",
  calendarColor: string,          // HEX color for calendar
  specialization: string[],       // ["Realism", "Japanese"]
  bio: string | null,
  status: "working" | "vacation" | "sick",
  locationIds: UUID[],            // Assigned locations
  createdAt: Timestamp
}
```

---

### 4.2. Get Staff Member

```
GET /api/staff/{id}
```

**Response: `StaffMemberDetailDto`**
```typescript
{
  id: UUID,
  firstName: string,
  lastName: string,
  email: string,
  phone: string,
  avatar: string | null,
  role: "owner" | "admin" | "artist",
  calendarColor: string,
  specialization: string[],
  bio: string | null,
  status: "working" | "vacation" | "sick",
  locationIds: UUID[],
  schedule: WeeklyScheduleDto,    // Робочий графік
  servicePricing: ArtistServicePricingDto[],  // Персональні ціни
  stats: {
    projectsCount: int,
    appointmentsThisMonth: int,
    totalRevenue: decimal
  },
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

**DTO: `WeeklyScheduleDto`**
```typescript
{
  monday: { isWorking: boolean, startTime: "10:00", endTime: "19:00" } | null,
  tuesday: { isWorking: boolean, startTime: "10:00", endTime: "19:00" } | null,
  // ... і так для всіх днів
}
```

**DTO: `ArtistServicePricingDto`**
```typescript
{
  serviceId: UUID,
  serviceName: string,
  basePrice: decimal,        // Базова ціна послуги
  artistPrice: decimal,      // Ціна цього майстра (override)
  artistDuration: int | null // Персональна тривалість (хв)
}
```

---

### 4.3. Invite Staff Member

```
POST /api/staff/invite
```

**Permission**: `owner`, `admin`

**Request DTO: `InviteStaffRequest`**
```typescript
{
  email: string,              // Required, unique per tenant
  role: "admin" | "artist",   // Required
  calendarColor: string,      // Required, HEX
  locationIds: UUID[]         // Required, at least one
}
```

**Response: `InviteStaffResponse`**
```typescript
{
  id: UUID,
  email: string,
  inviteToken: string,        // Для посилання-запрошення
  inviteUrl: string,          // https://app.inkflow.com/invite/{token}
  expiresAt: Timestamp        // 7 днів
}
```

**Business Logic:**
1. Перевірити, що email не існує в tenant
2. Створити запис у `staff_invites` таблиці
3. Надіслати email з посиланням
4. Користувач переходить по посиланню → реєструється в Supabase → прив'язується до tenant

---

### 4.4. Accept Invite

```
POST /api/staff/invite/accept
```

**Request DTO: `AcceptInviteRequest`**
```typescript
{
  token: string,
  firstName: string,
  lastName: string,
  phone: string,
  password: string        // Для Supabase Auth
}
```

**Response: `StaffMemberDto`**

---

### 4.5. Update Staff Member

```
PATCH /api/staff/{id}
```

**Permission**: 
- `owner`, `admin` — будь-якого
- `artist` — тільки себе (обмежені поля)

**Request DTO: `UpdateStaffRequest`**
```typescript
{
  firstName?: string,
  lastName?: string,
  phone?: string,
  avatar?: string,
  bio?: string,
  specialization?: string[],
  status?: "working" | "vacation" | "sick",
  calendarColor?: string,           // owner/admin only
  role?: "admin" | "artist",        // owner only
  locationIds?: UUID[]              // owner/admin only
}
```

---

### 4.6. Update Staff Schedule

```
PUT /api/staff/{id}/schedule
```

**Permission**: `owner`, `admin`, або сам майстер

**Request DTO: `UpdateScheduleRequest`**
```typescript
{
  schedule: {
    monday: { isWorking: true, startTime: "10:00", endTime: "19:00" } | null,
    tuesday: { isWorking: true, startTime: "10:00", endTime: "19:00" } | null,
    wednesday: { isWorking: true, startTime: "10:00", endTime: "19:00" } | null,
    thursday: { isWorking: true, startTime: "10:00", endTime: "19:00" } | null,
    friday: { isWorking: true, startTime: "10:00", endTime: "19:00" } | null,
    saturday: { isWorking: true, startTime: "11:00", endTime: "17:00" } | null,
    sunday: null  // Вихідний
  }
}
```

---

### 4.7. Set Artist Service Pricing

```
PUT /api/staff/{id}/pricing
```

**Permission**: `owner`, `admin`

**Request DTO: `SetArtistPricingRequest`**
```typescript
{
  pricing: [
    {
      serviceId: UUID,
      price: decimal,           // Override price
      duration: int | null      // Override duration (optional)
    }
  ]
}
```

**Business Logic:**
- Якщо price/duration = null → використовується базова ціна послуги
- Якщо є override → використовується персональна ціна майстра

---

### 4.8. Delete Staff Member

```
DELETE /api/staff/{id}
```

**Permission**: `owner` only

**Business Logic:**
1. Soft delete (встановити `deletedAt`)
2. Перевірити, що немає майбутніх appointments
3. Якщо є — повернути помилку `BUSINESS_RULE_VIOLATION`
4. Деактивувати Supabase user

---

## 5. Clients Module

### 5.1. List Clients

```
GET /api/clients
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `search` | string | Пошук по імені, телефону, email |
| `status` | string | `active`, `lost`, `banned` |
| `tags` | string[] | Filter by tags: `VIP`, `Problematic` |
| `artistId` | UUID | Клієнти конкретного майстра |
| `hasActiveProject` | boolean | Тільки з активними проєктами |

**Response DTO: `ClientListResponse`**
```typescript
{
  data: ClientDto[],
  pagination: PaginationDto
}
```

**DTO: `ClientDto`**
```typescript
{
  id: UUID,
  firstName: string,
  lastName: string,
  phone: string,
  email: string | null,
  avatar: string | null,
  instagram: string | null,
  telegram: string | null,
  tags: string[],                // ["VIP", "Pain: High"]
  status: "active" | "lost" | "banned",
  lastVisit: Timestamp | null,
  totalVisits: int,
  cancelledVisits: int,
  ltv: decimal,                  // Lifetime Value
  createdAt: Timestamp
}
```

---

### 5.2. Get Client

```
GET /api/clients/{id}
```

**Response DTO: `ClientDetailDto`**
```typescript
{
  id: UUID,
  firstName: string,
  lastName: string,
  phone: string,
  email: string | null,
  avatar: string | null,
  birthDate: Date | null,
  instagram: string | null,
  telegram: string | null,
  tags: string[],
  medicalConditions: string[],   // ["Allergy", "Diabetes"]
  source: "instagram" | "web" | "phone" | "referral" | "walk_in",
  status: "active" | "lost" | "banned",
  notes: string | null,
  
  // Statistics
  lastVisit: Timestamp | null,
  totalVisits: int,
  cancelledVisits: int,
  ltv: decimal,
  
  // Related data
  activeProjects: ProjectSummaryDto[],
  recentAppointments: AppointmentSummaryDto[],
  signedWaivers: SignedWaiverSummaryDto[],
  
  // Audit
  createdAt: Timestamp,
  updatedAt: Timestamp,
  createdBy: UUID
}
```

---

### 5.3. Create Client

```
POST /api/clients
```

**Request DTO: `CreateClientRequest`**
```typescript
{
  firstName: string,              // Required, min 2 chars
  lastName: string,               // Required, min 2 chars
  phone: string,                  // Required, unique per tenant
  email: string | null,           // Optional, valid email
  avatar: string | null,          // Optional, URL or base64
  birthDate: Date | null,
  instagram: string | null,       // @username
  telegram: string | null,        // @username
  tags: string[],
  medicalConditions: string[],
  source: "instagram" | "web" | "phone" | "referral" | "walk_in",
  notes: string | null
}
```

**Response: `ClientDto`**

**Business Logic:**
1. Validate phone uniqueness within tenant
2. Normalize phone format to +380...
3. Set status = "active"
4. Set ltv = 0, totalVisits = 0

---

### 5.4. Update Client

```
PATCH /api/clients/{id}
```

**Request DTO: `UpdateClientRequest`**
```typescript
{
  firstName?: string,
  lastName?: string,
  phone?: string,
  email?: string | null,
  avatar?: string | null,
  birthDate?: Date | null,
  instagram?: string | null,
  telegram?: string | null,
  tags?: string[],
  medicalConditions?: string[],
  status?: "active" | "lost" | "banned",
  notes?: string | null
}
```

---

### 5.5. Delete Client

```
DELETE /api/clients/{id}
```

**Permission**: `owner`, `admin`

**Business Logic:**
1. Soft delete
2. Зберігаємо для історії та LTV статистики

---

### 5.6. Get Client Projects

```
GET /api/clients/{id}/projects
```

**Response: `ProjectSummaryDto[]`**

---

### 5.7. Get Client Appointments

```
GET /api/clients/{id}/appointments
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Filter by status |
| `from` | Date | From date |
| `to` | Date | To date |

**Response: `AppointmentSummaryDto[]`**

---

## 6. Services Module

### 6.1. List Services

```
GET /api/services
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `isActive` | boolean | Filter active/inactive |
| `pricingType` | string | `fixed` or `hourly` |

**Response DTO: `ServiceListResponse`**
```typescript
{
  data: ServiceDto[],
  pagination: PaginationDto
}
```

**DTO: `ServiceDto`**
```typescript
{
  id: UUID,
  title: string,
  description: string | null,
  pricingType: "fixed" | "hourly",
  price: decimal,                 // Базова ціна
  duration: int,                  // Хвилини
  color: string,                  // HEX для календаря
  isActive: boolean,
  createdAt: Timestamp
}
```

---

### 6.2. Get Service

```
GET /api/services/{id}
```

**Response DTO: `ServiceDetailDto`**
```typescript
{
  id: UUID,
  title: string,
  description: string | null,
  pricingType: "fixed" | "hourly",
  price: decimal,
  duration: int,
  color: string,
  isActive: boolean,
  
  // Artist pricing overrides
  artistPricing: [
    {
      artistId: UUID,
      artistName: string,
      price: decimal,
      duration: int | null
    }
  ],
  
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

---

### 6.3. Create Service

```
POST /api/services
```

**Permission**: `owner`, `admin`

**Request DTO: `CreateServiceRequest`**
```typescript
{
  title: string,                  // Required
  description: string | null,
  pricingType: "fixed" | "hourly", // Required
  price: decimal,                 // Required, > 0
  duration: int,                  // Required, minutes
  color: string                   // Required, HEX
}
```

---

### 6.4. Update Service

```
PATCH /api/services/{id}
```

**Permission**: `owner`, `admin`

**Request DTO: `UpdateServiceRequest`**
```typescript
{
  title?: string,
  description?: string | null,
  pricingType?: "fixed" | "hourly",
  price?: decimal,
  duration?: int,
  color?: string,
  isActive?: boolean
}
```

---

### 6.5. Delete Service

```
DELETE /api/services/{id}
```

**Permission**: `owner`, `admin`

**Business Logic:**
1. Soft delete (isActive = false, deletedAt = now)
2. Не видаляємо фізично — є в історії appointments

---

### 6.6. Get Service Price for Artist

```
GET /api/services/{serviceId}/price?artistId={artistId}
```

**Response DTO: `ServicePriceDto`**
```typescript
{
  serviceId: UUID,
  serviceName: string,
  artistId: UUID,
  artistName: string,
  price: decimal,          // Ефективна ціна (override або базова)
  duration: int,           // Ефективна тривалість
  isOverride: boolean      // true якщо персональна ціна
}
```

---

## 7. Appointments Module

### 7.1. List Appointments

```
GET /api/appointments
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `from` | DateTime | Start date (ISO 8601) |
| `to` | DateTime | End date (ISO 8601) |
| `artistId` | UUID | Filter by artist |
| `clientId` | UUID | Filter by client |
| `status` | string | `new`, `confirmed`, `in_progress`, `done`, `cancelled` |
| `projectId` | UUID | Filter by project |

**Headers:**
- `X-Location-Id` — фільтрація по локації (опціонально)

**Permission Logic:**
- `owner`, `admin` — всі appointments (з фільтром локації якщо є)
- `artist` — тільки свої appointments

**Response DTO: `AppointmentListResponse`**
```typescript
{
  data: AppointmentDto[],
  pagination: PaginationDto
}
```

**DTO: `AppointmentDto`**
```typescript
{
  id: UUID,
  clientId: UUID,
  client: {
    id: UUID,
    firstName: string,
    lastName: string,
    phone: string,
    avatar: string | null,
    hasMedicalConditions: boolean
  },
  artistId: UUID,
  artist: {
    id: UUID,
    firstName: string,
    lastName: string,
    avatar: string | null,
    calendarColor: string
  },
  serviceId: UUID,
  service: {
    id: UUID,
    title: string,
    color: string
  },
  locationId: UUID,
  location: {
    id: UUID,
    name: string
  },
  projectId: UUID | null,
  project: {
    id: UUID,
    title: string
  } | null,
  
  startTime: DateTime,
  endTime: DateTime,
  status: "new" | "confirmed" | "in_progress" | "done" | "cancelled",
  
  price: decimal,
  prepayment: decimal,
  discount: decimal,
  finalPrice: decimal,
  
  notes: string | null,
  sketchImage: string | null,
  waiverSigned: boolean,
  
  createdAt: Timestamp
}
```

---

### 7.2. Get Appointment

```
GET /api/appointments/{id}
```

**Response DTO: `AppointmentDetailDto`**
```typescript
{
  // ... all fields from AppointmentDto
  
  // Additional details
  photos: GalleryPhotoDto[],
  transactions: TransactionSummaryDto[],
  signedWaiver: SignedWaiverDto | null,
  
  // Audit
  createdBy: UUID,
  updatedBy: UUID,
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

---

### 7.3. Create Appointment

```
POST /api/appointments
```

**Request DTO: `CreateAppointmentRequest`**
```typescript
{
  clientId: UUID,               // Required
  artistId: UUID,               // Required
  serviceId: UUID,              // Required
  locationId: UUID,             // Required
  projectId: UUID | null,       // Optional - прив'язка до проєкту
  
  startTime: DateTime,          // Required, ISO 8601
  endTime: DateTime | null,     // Optional, auto-calculated from service duration
  
  prepayment: decimal,          // Optional, default 0
  discount: decimal,            // Optional, default 0
  notes: string | null          // Optional
}
```

**Response: `AppointmentDto`**

**Business Logic:**

1. **Валідація клієнта**: Перевірити що client належить tenant
2. **Валідація майстра**: Перевірити що artist належить tenant і має доступ до location
3. **Розрахунок ціни**:
   ```typescript
   const price = await getServicePriceForArtist(serviceId, artistId);
   const finalPrice = price - discount;
   ```
4. **Розрахунок endTime** (якщо не передано):
   ```typescript
   const duration = await getServiceDurationForArtist(serviceId, artistId);
   const endTime = startTime + duration;
   ```
5. **Перевірка конфліктів**:
   ```sql
   SELECT * FROM appointments 
   WHERE artist_id = :artistId 
   AND status NOT IN ('cancelled', 'done')
   AND (
     (start_time <= :startTime AND end_time > :startTime) OR
     (start_time < :endTime AND end_time >= :endTime) OR
     (start_time >= :startTime AND end_time <= :endTime)
   )
   ```
   Якщо є конфлікт → `409 CONFLICT`
6. **Перевірка робочого графіку**: Чи працює майстер в цей час
7. **Встановлення status**: `new`
8. **Якщо є projectId**: Перевірити що проєкт належить clientId

---

### 7.4. Update Appointment

```
PATCH /api/appointments/{id}
```

**Permission**: 
- `owner`, `admin` — будь-який
- `artist` — тільки свої

**Request DTO: `UpdateAppointmentRequest`**
```typescript
{
  clientId?: UUID,
  artistId?: UUID,
  serviceId?: UUID,
  locationId?: UUID,
  projectId?: UUID | null,
  
  startTime?: DateTime,
  endTime?: DateTime,
  
  status?: "new" | "confirmed" | "in_progress" | "done" | "cancelled",
  
  prepayment?: decimal,
  discount?: decimal,
  notes?: string,
  sketchImage?: string | null
}
```

**Business Logic:**
- Якщо змінюється час — перевірити конфлікти
- Якщо status → `done` — тригерити оплату (якщо не оплачено)

---

### 7.5. Cancel Appointment

```
POST /api/appointments/{id}/cancel
```

**Request DTO: `CancelAppointmentRequest`**
```typescript
{
  reason: string | null,
  refundPrepayment: boolean    // Чи повертати передоплату
}
```

**Business Logic:**
1. Встановити status = `cancelled`
2. Якщо refundPrepayment = true → створити expense transaction
3. Оновити client.cancelledVisits += 1

---

### 7.6. Checkout Appointment (Оплата)

```
POST /api/appointments/{id}/checkout
```

**Request DTO: `CheckoutAppointmentRequest`**
```typescript
{
  paymentMethod: "cash" | "card" | "split",
  splitDetails: {              // Тільки якщо split
    cash: decimal,
    card: decimal
  } | null,
  discount: decimal | null,    // Додаткова знижка при оплаті
  tip: decimal | null          // Чайові (опціонально)
}
```

**Response DTO: `CheckoutResultDto`**
```typescript
{
  appointmentId: UUID,
  transactionId: UUID,
  finalAmount: decimal,
  paymentMethod: string,
  paidAt: Timestamp,
  receiptUrl: string | null    // URL для друку чека
}
```

**Business Logic:**

1. **Розрахунок суми**:
   ```typescript
   const toPay = appointment.finalPrice - appointment.prepayment - (discount || 0);
   ```
2. **Створення транзакції**:
   ```typescript
   await createTransaction({
     type: 'income',
     category: 'service',
     amount: toPay + (tip || 0),
     paymentMethod,
     appointmentId,
     staffId: appointment.artistId,
     description: `Payment for ${service.title}`
   });
   ```
3. **Оновлення appointment**:
   - status = `done`
   - finalPrice = updated with discount
4. **Оновлення client**:
   - ltv += finalAmount
   - totalVisits += 1
   - lastVisit = now
5. **Якщо є projectId**:
   - project.completedSessions += 1
   - project.totalPaid += finalAmount
6. **Генерація чека** (PDF)

---

### 7.7. Reschedule Appointment

```
POST /api/appointments/{id}/reschedule
```

**Request DTO: `RescheduleAppointmentRequest`**
```typescript
{
  newStartTime: DateTime,
  newEndTime: DateTime | null,   // Auto-calculated if null
  newArtistId: UUID | null,      // Якщо змінюється майстер
  reason: string | null
}
```

**Business Logic:**
1. Перевірити конфлікти для нового часу
2. Якщо newArtistId — перевірити доступність
3. Зберегти історію змін в audit log
4. Надіслати нотифікацію клієнту (TODO)

---

### 7.8. Upload Sketch/Photo

```
POST /api/appointments/{id}/photos
```

**Request**: `multipart/form-data`
```
file: File (image/png, image/jpeg, max 10MB)
stage: "sketch" | "stencil" | "fresh" | "healed"
bodyPart: string | null
```

**Response: `GalleryPhotoDto`**
```typescript
{
  id: UUID,
  url: string,
  stage: string,
  bodyPart: string | null,
  uploadedAt: Timestamp
}
```

---

### 7.9. Get Appointments for Calendar View

```
GET /api/appointments/calendar
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `from` | DateTime | Start of period |
| `to` | DateTime | End of period |
| `artistIds` | UUID[] | Filter by multiple artists |

**Response DTO: `CalendarViewResponse`**
```typescript
{
  appointments: CalendarEventDto[],
  artists: ArtistSummaryDto[]     // Для легенди
}
```

**DTO: `CalendarEventDto`**
```typescript
{
  id: UUID,
  title: string,                  // Client name
  start: DateTime,
  end: DateTime,
  artistId: UUID,
  artistColor: string,
  status: string,
  statusColor: string,
  clientHasMedicalConditions: boolean,
  serviceName: string
}
```

---

## 8. Projects Module

### 8.1. List Projects

```
GET /api/projects
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `status` | string | `in_progress`, `finished`, `cancelled` |
| `artistId` | UUID | Filter by artist |
| `clientId` | UUID | Filter by client |

**Permission Logic:**
- `owner`, `admin` — всі проєкти
- `artist` — тільки свої проєкти

**Response DTO: `ProjectListResponse`**
```typescript
{
  data: ProjectDto[],
  pagination: PaginationDto
}
```

**DTO: `ProjectDto`**
```typescript
{
  id: UUID,
  title: string,
  description: string | null,
  
  clientId: UUID,
  client: {
    id: UUID,
    firstName: string,
    lastName: string,
    avatar: string | null
  },
  
  artistId: UUID,
  artist: {
    id: UUID,
    firstName: string,
    lastName: string,
    avatar: string | null,
    calendarColor: string
  },
  
  status: "in_progress" | "finished" | "cancelled",
  
  estimatedCost: decimal,
  totalPaid: decimal,
  
  totalSessions: int,
  completedSessions: int,
  
  photosCount: int,
  
  createdAt: Timestamp
}
```

---

### 8.2. Get Project

```
GET /api/projects/{id}
```

**Response DTO: `ProjectDetailDto`**
```typescript
{
  id: UUID,
  title: string,
  description: string | null,
  
  clientId: UUID,
  client: ClientSummaryDto,
  
  artistId: UUID,
  artist: ArtistSummaryDto,
  
  status: "in_progress" | "finished" | "cancelled",
  
  estimatedCost: decimal,
  totalPaid: decimal,
  remainingCost: decimal,        // estimatedCost - totalPaid
  
  totalSessions: int,
  completedSessions: int,
  remainingSessions: int,
  progressPercent: int,          // (completedSessions / totalSessions) * 100
  
  appointments: ProjectAppointmentDto[],  // Всі сеанси проєкту
  photos: GalleryPhotoDto[],              // Згруповані по stage
  
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

**DTO: `ProjectAppointmentDto`**
```typescript
{
  id: UUID,
  sessionNumber: int,            // 1, 2, 3...
  date: DateTime,
  duration: int,                 // minutes
  status: string,
  price: decimal,
  isPaid: boolean,
  photos: GalleryPhotoDto[]
}
```

---

### 8.3. Create Project

```
POST /api/projects
```

**Request DTO: `CreateProjectRequest`**
```typescript
{
  title: string,                 // Required
  description: string | null,
  clientId: UUID,                // Required
  artistId: UUID,                // Required
  estimatedCost: decimal,        // Required
  totalSessions: int             // Required, estimated sessions count
}
```

**Business Logic:**
1. Перевірити що client і artist належать tenant
2. Встановити status = `in_progress`
3. Встановити completedSessions = 0, totalPaid = 0

---

### 8.4. Update Project

```
PATCH /api/projects/{id}
```

**Permission**: 
- `owner`, `admin` — будь-який
- `artist` — тільки свої

**Request DTO: `UpdateProjectRequest`**
```typescript
{
  title?: string,
  description?: string,
  estimatedCost?: decimal,
  totalSessions?: int,
  status?: "in_progress" | "finished" | "cancelled"
}
```

---

### 8.5. Complete Project

```
POST /api/projects/{id}/complete
```

**Business Logic:**
1. Встановити status = `finished`
2. Перевірити що всі appointments оплачені
3. Якщо є неоплачені — попередження

---

### 8.6. Upload Project Photo

```
POST /api/projects/{id}/photos
```

Аналогічно до appointments.

---

### 8.7. Get Client Active Projects (for appointment creation)

```
GET /api/clients/{clientId}/projects/active
```

**Response DTO: `ActiveProjectDto[]`**
```typescript
[
  {
    id: UUID,
    title: string,
    completedSessions: int,
    totalSessions: int,
    label: string               // "Рукав Дракон — 3/10 сеансів"
  }
]
```

Використовується в CreateAppointmentModal для вибору проєкту.

---

## 9. Requests (Leads) Module

### 9.1. List Requests

```
GET /api/requests
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `status` | string | `new`, `replied`, `converted`, `spam` |
| `source` | string | `instagram`, `web`, `phone`, `referral` |

**Response DTO: `RequestListResponse`**
```typescript
{
  data: RequestDto[],
  pagination: PaginationDto,
  stats: {
    new: int,
    replied: int,
    converted: int,
    spam: int
  }
}
```

**DTO: `RequestDto`**
```typescript
{
  id: UUID,
  source: "instagram" | "web" | "phone" | "referral",
  clientName: string,
  clientNickname: string | null,
  message: string,
  phone: string | null,
  instagram: string | null,
  status: "new" | "replied" | "converted" | "spam",
  createdAt: Timestamp
}
```

---

### 9.2. Get Request

```
GET /api/requests/{id}
```

---

### 9.3. Create Request (Webhook)

```
POST /api/requests
```

Для інтеграцій з Instagram, сайтом тощо.

**Request DTO: `CreateRequestRequest`**
```typescript
{
  source: "instagram" | "web" | "phone" | "referral",
  clientName: string,
  clientNickname: string | null,
  message: string,
  phone: string | null,
  instagram: string | null
}
```

---

### 9.4. Update Request Status

```
PATCH /api/requests/{id}/status
```

**Request DTO: `UpdateRequestStatusRequest`**
```typescript
{
  status: "new" | "replied" | "converted" | "spam"
}
```

---

### 9.5. Convert Request to Client

```
POST /api/requests/{id}/convert
```

**Request DTO: `ConvertRequestRequest`**
```typescript
{
  createAppointment: boolean,
  appointmentData: {           // Якщо createAppointment = true
    artistId: UUID,
    serviceId: UUID,
    locationId: UUID,
    startTime: DateTime
  } | null
}
```

**Response DTO: `ConvertResultDto`**
```typescript
{
  client: ClientDto,
  appointment: AppointmentDto | null
}
```

**Business Logic:**
1. Створити клієнта з даних заявки
2. Встановити status = `converted`
3. Якщо createAppointment — створити запис
4. Зв'язати request.convertedClientId = client.id

---

### 9.6. Delete Request

```
DELETE /api/requests/{id}
```

Hard delete (не soft delete).

---

## 10. Transactions Module

### 10.1. List Transactions

```
GET /api/transactions
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `type` | string | `income` or `expense` |
| `category` | string | `service`, `rent`, `supplies`, `salary`, `merch`, `other` |
| `from` | Date | From date |
| `to` | Date | To date |
| `staffId` | UUID | Filter by staff who performed |
| `paymentMethod` | string | `cash`, `card`, `split` |

**Headers:**
- `X-Location-Id` — фільтрація по локації

**Permission Logic:**
- `owner`, `admin` — всі транзакції
- `artist` — тільки свої (де staffId = current user)

**Response DTO: `TransactionListResponse`**
```typescript
{
  data: TransactionDto[],
  pagination: PaginationDto,
  summary: {
    totalIncome: decimal,
    totalExpense: decimal,
    netProfit: decimal,
    byPaymentMethod: {
      cash: decimal,
      card: decimal
    }
  }
}
```

**DTO: `TransactionDto`**
```typescript
{
  id: UUID,
  type: "income" | "expense",
  category: "service" | "rent" | "supplies" | "salary" | "merch" | "other",
  amount: decimal,
  paymentMethod: "cash" | "card" | "split",
  description: string | null,
  
  appointmentId: UUID | null,
  appointment: {
    id: UUID,
    clientName: string,
    serviceName: string
  } | null,
  
  staffId: UUID | null,
  staff: {
    id: UUID,
    firstName: string,
    lastName: string
  } | null,
  
  locationId: UUID,
  location: {
    id: UUID,
    name: string
  },
  
  date: DateTime,
  createdAt: Timestamp
}
```

---

### 10.2. Get Transaction

```
GET /api/transactions/{id}
```

---

### 10.3. Create Transaction (Manual)

```
POST /api/transactions
```

**Permission**: `owner`, `admin`

**Request DTO: `CreateTransactionRequest`**
```typescript
{
  type: "income" | "expense",
  category: "service" | "rent" | "supplies" | "salary" | "merch" | "other",
  amount: decimal,               // Required, > 0
  paymentMethod: "cash" | "card",
  description: string | null,
  date: DateTime,                // Required
  locationId: UUID               // Required
}
```

---

### 10.4. Delete Transaction

```
DELETE /api/transactions/{id}
```

**Permission**: `owner` only

**Business Logic:**
- Soft delete
- Якщо пов'язана з appointment — error (тільки через refund)

---

### 10.5. Get Finance Stats

```
GET /api/transactions/stats
```

**Query Params:**
| Param | Type | Description |
|-------|------|-------------|
| `period` | string | `day`, `week`, `month`, `year` |
| `from` | Date | Custom period start |
| `to` | Date | Custom period end |

**Response DTO: `FinanceStatsDto`**
```typescript
{
  period: {
    from: Date,
    to: Date
  },
  
  summary: {
    totalRevenue: decimal,
    totalExpenses: decimal,
    netProfit: decimal,
    averageCheck: decimal,
    transactionsCount: int
  },
  
  byCategory: {
    service: decimal,
    rent: decimal,
    supplies: decimal,
    salary: decimal,
    merch: decimal,
    other: decimal
  },
  
  byPaymentMethod: {
    cash: decimal,
    card: decimal
  },
  
  byArtist: [
    {
      artistId: UUID,
      artistName: string,
      revenue: decimal,
      appointmentsCount: int
    }
  ],
  
  byLocation: [
    {
      locationId: UUID,
      locationName: string,
      revenue: decimal
    }
  ],
  
  dailyBreakdown: [
    {
      date: Date,
      income: decimal,
      expense: decimal
    }
  ]
}
```

---

## 11. Waivers Module

### 11.1. List Waiver Templates

```
GET /api/waivers/templates
```

**Response DTO: `WaiverTemplateListResponse`**
```typescript
{
  data: WaiverTemplateDto[]
}
```

**DTO: `WaiverTemplateDto`**
```typescript
{
  id: UUID,
  title: string,               // "Згода на татуювання"
  content: string,             // HTML content with placeholders
  version: int,
  isActive: boolean,
  checkboxes: [                // Обов'язкові checkbox'и
    { id: string, label: string, required: boolean }
  ],
  createdAt: Timestamp
}
```

---

### 11.2. Create Waiver Template

```
POST /api/waivers/templates
```

**Permission**: `owner`, `admin`

**Request DTO: `CreateWaiverTemplateRequest`**
```typescript
{
  title: string,
  content: string,             // HTML з плейсхолдерами {{clientName}}, {{date}}
  checkboxes: [
    { id: "age18", label: "Мені виповнилось 18 років", required: true },
    { id: "health", label: "Я не маю медичних протипоказань", required: true }
  ]
}
```

---

### 11.3. Update Waiver Template

```
PATCH /api/waivers/templates/{id}
```

**Permission**: `owner`, `admin`

**Business Logic:**
- Створюється нова версія, стара деактивується
- Вже підписані залишаються з попередньою версією

---

### 11.4. Get Waiver to Sign

```
GET /api/appointments/{appointmentId}/waiver
```

**Response DTO: `WaiverToSignDto`**
```typescript
{
  templateId: UUID,
  title: string,
  content: string,              // HTML з заповненими даними клієнта
  checkboxes: [
    { id: string, label: string, required: boolean }
  ],
  clientName: string,
  appointmentDate: DateTime
}
```

---

### 11.5. Sign Waiver

```
POST /api/appointments/{appointmentId}/waiver/sign
```

**Request DTO: `SignWaiverRequest`**
```typescript
{
  templateId: UUID,
  signatureData: string,        // Base64 PNG або SVG path data
  checkboxes: {
    age18: boolean,
    health: boolean
  },
  clientIp: string              // Передається фронтом для аудиту
}
```

**Response DTO: `SignedWaiverDto`**
```typescript
{
  id: UUID,
  appointmentId: UUID,
  clientId: UUID,
  templateId: UUID,
  signatureData: string,
  signedAt: Timestamp,
  pdfUrl: string               // URL згенерованого PDF
}
```

**Business Logic:**
1. Зберегти підпис
2. Згенерувати PDF з усіма даними
3. Оновити appointment.waiverSigned = true

---

### 11.6. Download Waiver PDF

```
GET /api/waivers/{id}/pdf
```

**Response**: PDF file

---

### 11.7. List Client Signed Waivers

```
GET /api/clients/{clientId}/waivers
```

**Response: `SignedWaiverDto[]`**

---

## 12. Locations Module

### 12.1. List Locations

```
GET /api/locations
```

**Response DTO: `LocationListResponse`**
```typescript
{
  data: LocationDto[]
}
```

**DTO: `LocationDto`**
```typescript
{
  id: UUID,
  name: string,
  address: string,
  phone: string | null,
  googleMapsLink: string | null,
  color: string,               // HEX для візуалізації
  isActive: boolean,
  createdAt: Timestamp
}
```

---

### 12.2. Get Location

```
GET /api/locations/{id}
```

**Response DTO: `LocationDetailDto`**
```typescript
{
  id: UUID,
  name: string,
  address: string,
  phone: string | null,
  googleMapsLink: string | null,
  color: string,
  isActive: boolean,
  
  staff: StaffSummaryDto[],     // Призначені майстри
  stats: {
    appointmentsThisMonth: int,
    revenueThisMonth: decimal
  },
  
  createdAt: Timestamp,
  updatedAt: Timestamp
}
```

---

### 12.3. Create Location

```
POST /api/locations
```

**Permission**: `owner`

**Request DTO: `CreateLocationRequest`**
```typescript
{
  name: string,                 // Required
  address: string,              // Required
  phone: string | null,
  googleMapsLink: string | null,
  color: string                 // Required, HEX
}
```

---

### 12.4. Update Location

```
PATCH /api/locations/{id}
```

**Permission**: `owner`

**Request DTO: `UpdateLocationRequest`**
```typescript
{
  name?: string,
  address?: string,
  phone?: string | null,
  googleMapsLink?: string | null,
  color?: string,
  isActive?: boolean
}
```

---

### 12.5. Delete Location

```
DELETE /api/locations/{id}
```

**Permission**: `owner`

**Business Logic:**
1. Перевірити що немає майбутніх appointments
2. Soft delete (isActive = false)
3. Перепризначити майстрів на інші локації

---

### 12.6. Assign Staff to Location

```
POST /api/locations/{id}/staff
```

**Permission**: `owner`, `admin`

**Request DTO: `AssignStaffRequest`**
```typescript
{
  staffIds: UUID[]
}
```

---

## 13. Settings Module

### 13.1. Get Company Settings

```
GET /api/settings/company
```

**Permission**: `owner`, `admin`

**Response DTO: `CompanySettingsDto`**
```typescript
{
  id: UUID,
  name: string,
  logo: string | null,
  subdomain: string,            // e.g., "blackink" → blackink.inkflow.app
  
  currency: "UAH" | "USD" | "EUR",
  timezone: string,             // e.g., "Europe/Kyiv"
  language: "ua" | "en",
  
  smsReminders: boolean,
  telegramReminders: boolean,
  emailReminders: boolean,
  
  reminderHoursBefore: int,     // За скільки годин нагадувати
  
  workingHours: {
    start: "09:00",
    end: "22:00"
  },
  
  bookingSettings: {
    allowOnlineBooking: boolean,
    minAdvanceHours: int,       // Мін. за скільки годин можна записатись
    maxAdvanceDays: int         // Макс. за скільки днів можна записатись
  }
}
```

---

### 13.2. Update Company Settings

```
PATCH /api/settings/company
```

**Permission**: `owner`

**Request DTO: `UpdateCompanySettingsRequest`**
```typescript
{
  name?: string,
  logo?: string,
  currency?: "UAH" | "USD" | "EUR",
  timezone?: string,
  language?: "ua" | "en",
  smsReminders?: boolean,
  telegramReminders?: boolean,
  emailReminders?: boolean,
  reminderHoursBefore?: int,
  workingHours?: {
    start: string,
    end: string
  },
  bookingSettings?: { ... }
}
```

---

### 13.3. Upload Company Logo

```
POST /api/settings/company/logo
```

**Permission**: `owner`

**Request**: `multipart/form-data`
```
file: File (image/png, image/jpeg, max 2MB)
```

**Response:**
```json
{
  "logoUrl": "https://storage.inkflow.app/logos/tenant-123.png"
}
```

---

## 14. RLS Policies

### 14.1. Enable RLS on Tables

```sql
-- Включити RLS для всіх таблиць
ALTER TABLE clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE waivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE signed_waivers ENABLE ROW LEVEL SECURITY;
```

### 14.2. Tenant Isolation Policy

```sql
-- Базова політика для tenant isolation
CREATE POLICY tenant_isolation_policy ON clients
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE POLICY tenant_isolation_policy ON staff
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE POLICY tenant_isolation_policy ON appointments
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

-- І так для всіх таблиць...
```

### 14.3. Artist-specific Policies

```sql
-- Artists бачать тільки свої appointments
CREATE POLICY artist_appointments_policy ON appointments
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant')::uuid
        AND (
            -- Owner/Admin бачать все
            current_setting('app.current_role') IN ('owner', 'admin')
            OR
            -- Artist бачить тільки свої
            artist_id = current_setting('app.current_user')::uuid
        )
    );
```

### 14.4. Location Filtering

```sql
-- Фільтрація по локації (якщо вказана)
CREATE POLICY location_filter_policy ON appointments
    FOR SELECT
    USING (
        tenant_id = current_setting('app.current_tenant')::uuid
        AND (
            -- Якщо локація не вказана — показати всі
            current_setting('app.current_location', true) IS NULL
            OR
            -- Якщо вказана — фільтрувати
            location_id = current_setting('app.current_location')::uuid
        )
    );
```

### 14.5. Spring Boot Integration

```java
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) {
        
        // Витягуємо дані з JWT
        String tenantId = extractTenantIdFromJwt(request);
        String userId = extractUserIdFromJwt(request);
        String role = extractRoleFromJwt(request);
        String locationId = request.getHeader("X-Location-Id");

        // Встановлюємо в PostgreSQL
        jdbcTemplate.execute("SET app.current_tenant = '" + tenantId + "'");
        jdbcTemplate.execute("SET app.current_user = '" + userId + "'");
        jdbcTemplate.execute("SET app.current_role = '" + role + "'");
        
        if (locationId != null) {
            // Валідація доступу до локації
            validateLocationAccess(userId, locationId);
            jdbcTemplate.execute("SET app.current_location = '" + locationId + "'");
        }

        chain.doFilter(request, response);
    }
}
```

---

## 15. Enums & Constants

### 15.1. Appointment Status

```typescript
enum AppointmentStatus {
  NEW = "new",               // Нова заявка
  CONFIRMED = "confirmed",   // Підтверджено (є передоплата)
  IN_PROGRESS = "in_progress", // Клієнт прийшов, сеанс почався
  DONE = "done",             // Завершено і оплачено
  CANCELLED = "cancelled"    // Скасовано
}
```

### 15.2. User Roles

```typescript
enum UserRole {
  OWNER = "owner",           // Власник салону (повний доступ)
  ADMIN = "admin",           // Адміністратор (майже повний)
  ARTIST = "artist"          // Майстер (обмежений)
}
```

### 15.3. Payment Methods

```typescript
enum PaymentMethod {
  CASH = "cash",
  CARD = "card",
  SPLIT = "split"            // Частково готівка + частково карта
}
```

### 15.4. Request Sources

```typescript
enum RequestSource {
  INSTAGRAM = "instagram",
  WEB = "web",
  PHONE = "phone",
  REFERRAL = "referral"
}
```

### 15.5. Client Status

```typescript
enum ClientStatus {
  ACTIVE = "active",
  LOST = "lost",             // > 3 місяців без візиту
  BANNED = "banned"          // Заблокований
}
```

### 15.6. Staff Status

```typescript
enum StaffStatus {
  WORKING = "working",
  VACATION = "vacation",
  SICK = "sick"
}
```

### 15.7. Project Status

```typescript
enum ProjectStatus {
  IN_PROGRESS = "in_progress",
  FINISHED = "finished",
  CANCELLED = "cancelled"
}
```

### 15.8. Transaction Types

```typescript
enum TransactionType {
  INCOME = "income",
  EXPENSE = "expense"
}

enum TransactionCategory {
  SERVICE = "service",       // Оплата послуги
  RENT = "rent",             // Оренда
  SUPPLIES = "supplies",     // Матеріали
  SALARY = "salary",         // Зарплата
  MERCH = "merch",           // Мерч
  OTHER = "other"
}
```

### 15.9. Gallery Stages

```typescript
enum GalleryStage {
  SKETCH = "sketch",         // Ескіз
  STENCIL = "stencil",       // Трафарет
  FRESH = "fresh",           // Свіжа робота
  HEALED = "healed"          // Зажила
}
```

### 15.10. Medical Conditions

```typescript
const MEDICAL_CONDITIONS = [
  "Allergy",
  "Diabetes", 
  "Hemophilia",
  "Epilepsy",
  "Heart Condition",
  "Pregnancy",
  "Skin Conditions",
  "HIV/AIDS",
  "Hepatitis"
] as const;
```

### 15.11. Client Tags

```typescript
const CLIENT_TAGS = [
  "VIP",
  "Problematic",
  "Pain: High",
  "Pain: Low",
  "First Timer",
  "Regular",
  "No Show Risk"
] as const;
```

---

## 📋 Підсумок

### Загальна статистика API

| Module | Endpoints | Ключові операції |
|--------|-----------|------------------|
| Auth | 2 | Login, Get current user |
| Staff | 8 | CRUD, Invite, Pricing, Schedule |
| Clients | 7 | CRUD, Projects, Appointments |
| Services | 6 | CRUD, Price for artist |
| Appointments | 9 | CRUD, Checkout, Reschedule, Photos |
| Projects | 7 | CRUD, Complete, Photos |
| Requests | 6 | CRUD, Convert to client |
| Transactions | 5 | CRUD, Stats |
| Waivers | 7 | Templates CRUD, Sign, Download |
| Locations | 6 | CRUD, Assign staff |
| Settings | 3 | Get, Update, Logo |
| **Total** | **66** | |

### Критичні ендпоінти для MVP

1. ✅ `POST /api/appointments` — створення запису
2. ✅ `POST /api/appointments/{id}/checkout` — оплата
3. ✅ `GET /api/appointments/calendar` — календар
4. ✅ `POST /api/clients` — створення клієнта
5. ✅ `GET /api/clients/{clientId}/projects/active` — активні проєкти для select
6. ✅ `POST /api/transactions` — ручні витрати
7. ✅ `GET /api/transactions/stats` — фінансова статистика

---

**Версія документа**: 1.0  
**Дата**: 25 січня 2026  
**Статус**: Ready for implementation

---

**Кінець документації**
