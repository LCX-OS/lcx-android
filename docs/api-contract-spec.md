# LCX API Contract Specification

**Last Updated:** March 2, 2026
**Version:** 1.0
**Audience:** Android Development Team (Kotlin)
**Purpose:** Single source of truth for all API integrations in the mobile client

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Error Handling](#error-handling)
4. [Data Models](#data-models)
5. [Enumerations](#enumerations)
6. [Ticket Endpoints](#ticket-endpoints)
   - [POST /api/tickets](#post-apiticktes)
   - [PATCH /api/tickets/:id/status](#patch-apiticketsidstatus)
   - [PATCH /api/tickets/:id/payment](#patch-apiticketsidpayment)
7. [Important Behaviors](#important-behaviors)
8. [Implementation Notes](#implementation-notes)

---

## Overview

The LCX API provides endpoints for managing laundry service tickets. This specification documents the contract for the three critical ticket management endpoints used by the Android client.

**Base URL:** To be configured per environment (`dev`/`prod`)

### Key Principles

- All responses follow a consistent shape
- Server responses are the source of truth (normalization may occur)
- Audit logging is fire-and-forget
- Status transitions follow a strict linear flow
- Ticket numbering is auto-assigned by the server

---

## Authentication

### Bearer Token (Mobile Path)

All endpoints accept Supabase access tokens via the `Authorization` header:

```
Authorization: Bearer <supabase_access_token>
```

Tokens are obtained via Supabase Auth with email/password credentials.

### Cookie Authentication (Web Path)

Endpoints also accept Supabase session cookies for web clients:

```
Cookie: sb-access-token=<token>; sb-refresh-token=<token>
```

### Auth Provider

- **Provider:** Supabase Auth
- **Method:** Email/password
- **Token Type:** JWT
- **Token Lifespan:** As configured in Supabase project settings

---

## Error Handling

### Standard Error Response

All error responses follow this schema:

```json
{
  "error": "Human-readable error message",
  "code": "MACHINE_CODE",
  "details": "Optional additional context"
}
```

### HTTP Status Codes

| Status | Meaning | Example Codes |
|--------|---------|---------------|
| 200 | Success | (no code field for success responses) |
| 401 | Not authenticated | `NOT_AUTHENTICATED` |
| 403 | Not authorized | (insufficient permissions) |
| 404 | Not found | (resource does not exist) |
| 409 | Conflict | `OPENING_CHECKLIST_BLOCKING_OPERATION`, `TICKET_NUMBER_CONFLICT` |
| 422 | Validation error | `INVALID_CUSTOMER_NAME`, `INVALID_SERVICE_TYPE`, etc. |
| 500 | Server error | (unexpected server failure) |

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `NOT_AUTHENTICATED` | 401 | Missing or invalid authentication token |
| `OPENING_CHECKLIST_BLOCKING_OPERATION` | 409 | Today's opening checklist exists but is not completed |
| `TICKET_NUMBER_CONFLICT` | 409 | Ticket number collision (retry with exponential backoff) |
| `INVALID_CUSTOMER_NAME` | 422 | Customer name missing or invalid format |
| `INVALID_CUSTOMER_PHONE` | 422 | Phone required when customer_id not provided |
| `INVALID_SERVICE_TYPE` | 422 | Service type not in enum (in-store, wash-fold) |
| `INVALID_PAYMENT_METHOD` | 422 | Payment method not in enum (cash, card, transfer) |
| `INVALID_PAYMENT_STATUS` | 422 | Payment status not in enum (pending, prepaid, paid) |
| `INVALID_STATUS_TRANSITION` | 422 | Status change violates linear flow |
| `INSUFFICIENT_PERMISSIONS` | 403 | User role lacks required permissions |

---

## Data Models

### Ticket Model

The Ticket object represents a single laundry service request.

| Field | Type | Required | Mutable | Description |
|-------|------|----------|---------|-------------|
| `id` | UUID | ✓ | ✗ | Unique ticket identifier (auto-generated) |
| `ticket_number` | String | ✓ | ✗ | Human-readable ticket number; format: `T-YYYYMMDD-NNNN` |
| `ticket_date` | Date | ✓ | ✗ | Date ticket was created (YYYY-MM-DD) |
| `daily_folio` | Integer | ✓ | ✗ | Auto-incremented daily counter (unique per ticket_date) |
| `source` | Enum | ✓ | ✗ | Origin of ticket: "encargo" or "venta" |
| `customer_id` | UUID | ✗ | ✗ | Reference to customer record (optional) |
| `customer_name` | String | ✓ | ✓ | Name of customer receiving service |
| `customer_phone` | String | ✓ if no customer_id | ✓ | Contact phone number |
| `service_type` | Enum | ✓ | ✓ | Service category: "in-store" or "wash-fold" |
| `service` | String | ✓ | ✓ | Specific service description/name |
| `weight` | Decimal | ✗ | ✓ | Item weight in kilograms (optional) |
| `status` | Enum | ✓ | ✓ | Current ticket status (see Enumerations) |
| `notes` | String | ✗ | ✓ | Internal notes or special instructions |
| `total_amount` | Decimal | ✗ | ✓ | Total service cost |
| `payment_method` | Enum | ✗ | ✓ | Payment method: "cash", "card", or "transfer" |
| `payment_status` | Enum | ✓ | ✓ | Payment status: "pending", "prepaid", or "paid" |
| `paid_amount` | Decimal | ✓ | ✓ | Amount already paid (0 if payment_status is "pending") |
| `paid_at` | Timestamp | ✗ | ✓ | UTC timestamp when payment was completed (null if not paid) |
| `actual_pickup_date` | Date | ✗ | ✓ | Date customer picked up completed service |
| `created_at` | Timestamp | ✓ | ✗ | UTC timestamp of ticket creation |
| `created_by` | UUID | ✓ | ✗ | User ID who created the ticket |
| `updated_at` | Timestamp | ✓ | ✓ | UTC timestamp of last modification |
| `updated_by` | UUID | ✓ | ✓ | User ID who last modified the ticket |

---

## Enumerations

### ticket_status

Represents the lifecycle state of a ticket. Status transitions must follow the linear flow: `received` → `processing` → `ready` → `delivered`.

```kotlin
enum class TicketStatus {
    RECEIVED,      // Ticket newly created, awaiting processing
    PROCESSING,    // Ticket actively being serviced
    READY,         // Service complete, awaiting pickup
    DELIVERED      // Customer has picked up or service delivered
    // PAID is legacy (read-only, exists in DB but API rejects for writes)
}
```

### payment_status

Represents the payment state of a ticket.

```kotlin
enum class PaymentStatus {
    PENDING,       // Not yet paid
    PREPAID,       // Prepayment received (may auto-promote to PAID)
    PAID           // Fully paid
}
```

### payment_method

Represents the method used or intended for payment.

```kotlin
enum class PaymentMethod {
    CASH,          // Cash payment
    CARD,          // Credit/debit card payment
    TRANSFER       // Bank transfer or digital transfer
}
```

### service_type

Represents the category of service provided.

```kotlin
enum class ServiceType {
    IN_STORE,      // Service performed at store location
    WASH_FOLD      // Wash and fold service (home/pickup delivery)
}
```

### user_role

Represents the role of a user in the system (for authorization).

```kotlin
enum class UserRole {
    EMPLOYEE,      // Standard employee (limited permissions)
    MANAGER,       // Manager (elevated permissions)
    SUPERADMIN     // Administrator (full permissions)
}
```

---

## Ticket Endpoints

### POST /api/tickets

Create one or more tickets in a single batch request.

#### Request

**Authentication:** Required (Bearer token or session cookie)

**Body Schema:**

```json
{
  "source": "encargo" | "venta",
  "tickets": [
    {
      "customer_name": "string",
      "customer_phone": "string",
      "customer_id": "uuid (optional)",
      "service_type": "in-store" | "wash-fold",
      "service": "string",
      "weight": "decimal (optional)",
      "status": "string (optional, defaults to 'received')",
      "notes": "string (optional)",
      "total_amount": "decimal (optional)",
      "payment_method": "cash" | "card" | "transfer" (optional)",
      "payment_status": "pending" | "prepaid" | "paid" (optional, defaults to 'pending')",
      "paid_amount": "decimal (optional, defaults to 0)"
    }
  ]
}
```

#### TicketDraft Validation Rules

| Field | Rules |
|-------|-------|
| `customer_name` | Required, non-empty string |
| `customer_phone` | Required if `customer_id` is not provided |
| `customer_id` | Optional UUID reference to existing customer |
| `service_type` | Required, must be "in-store" or "wash-fold" |
| `service` | Required, non-empty string |
| `weight` | Optional, positive decimal if provided |
| `status` | Optional, defaults to "received"; write operations reject legacy "paid" status |
| `total_amount` | Optional, non-negative decimal if provided |
| `payment_method` | Optional, must be "cash", "card", or "transfer" if provided |
| `payment_status` | Optional, defaults to "pending" |
| `paid_amount` | Optional, non-negative decimal if provided |

#### Pre-conditions

1. **Opening Checklist Gate:** If an opening checklist for today exists in the database but has not been completed, the request returns HTTP 409 with code `OPENING_CHECKLIST_BLOCKING_OPERATION`.
2. **Batch Size:** Between 1 and 30 tickets per request.

#### Successful Response (HTTP 200)

```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "ticket_number": "T-20260302-0001",
      "ticket_date": "2026-03-02",
      "daily_folio": 1,
      "source": "encargo",
      "customer_id": null,
      "customer_name": "Juan García",
      "customer_phone": "+56912345678",
      "service_type": "wash-fold",
      "service": "Lavado y Planchado",
      "weight": 5.5,
      "status": "received",
      "notes": "Entregar mañana",
      "total_amount": 25000,
      "payment_method": "cash",
      "payment_status": "pending",
      "paid_amount": 0,
      "paid_at": null,
      "actual_pickup_date": null,
      "created_at": "2026-03-02T10:30:00Z",
      "created_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
      "updated_at": "2026-03-02T10:30:00Z",
      "updated_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    }
  ]
}
```

#### Error Response (HTTP 401)

```json
{
  "error": "Missing or invalid authentication token",
  "code": "NOT_AUTHENTICATED"
}
```

#### Error Response (HTTP 409 - Opening Checklist)

```json
{
  "error": "Opening checklist must be completed before creating tickets",
  "code": "OPENING_CHECKLIST_BLOCKING_OPERATION"
}
```

#### Error Response (HTTP 409 - Ticket Number Conflict)

```json
{
  "error": "Ticket number collision; server generated duplicate",
  "code": "TICKET_NUMBER_CONFLICT",
  "details": "Retry with exponential backoff"
}
```

#### Error Response (HTTP 422 - Validation)

```json
{
  "error": "Validation failed",
  "code": "INVALID_CUSTOMER_NAME",
  "details": "customer_name is required"
}
```

#### Error Response (HTTP 500)

```json
{
  "error": "Unexpected server error",
  "code": "INTERNAL_SERVER_ERROR"
}
```

#### Notes

- Batch creation is atomic; all tickets in the request succeed or all fail.
- The server generates `ticket_number` and `daily_folio` automatically via database trigger.
- Responses contain the server-normalized ticket objects; use these as the source of truth.
- Audit log entries are written asynchronously (fire-and-forget).

---

### PATCH /api/tickets/:id/status

Update the status of an existing ticket.

#### Request

**Authentication:** Required (Bearer token or session cookie)

**Path Parameter:**

- `:id` (UUID) — The ticket ID to update

**Body Schema:**

```json
{
  "status": "received" | "processing" | "ready" | "delivered"
}
```

#### Status Transition Rules

- Must follow the linear flow: `received` → `processing` → `ready` → `delivered`
- Backward transitions are not allowed
- Cannot transition to the current status (no-op)
- Legacy "paid" status is read-only; API rejects writes to "paid"

#### Side Effects

1. **When status is set to "delivered":**
   - Server automatically sets `actual_pickup_date` to today's date

2. **Audit Log:**
   - An audit log entry is written for every successful status change (fire-and-forget, does not block response)

#### Successful Response (HTTP 200)

```json
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "ticket_number": "T-20260302-0001",
    "ticket_date": "2026-03-02",
    "daily_folio": 1,
    "source": "encargo",
    "customer_id": null,
    "customer_name": "Juan García",
    "customer_phone": "+56912345678",
    "service_type": "wash-fold",
    "service": "Lavado y Planchado",
    "weight": 5.5,
    "status": "delivered",
    "notes": "Entregar mañana",
    "total_amount": 25000,
    "payment_method": "cash",
    "payment_status": "paid",
    "paid_amount": 25000,
    "paid_at": "2026-03-02T14:20:00Z",
    "actual_pickup_date": "2026-03-02",
    "created_at": "2026-03-02T10:30:00Z",
    "created_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "updated_at": "2026-03-02T14:25:00Z",
    "updated_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  }
}
```

#### Error Response (HTTP 401)

```json
{
  "error": "Missing or invalid authentication token",
  "code": "NOT_AUTHENTICATED"
}
```

#### Error Response (HTTP 403)

```json
{
  "error": "Insufficient permissions to update ticket status",
  "code": "INSUFFICIENT_PERMISSIONS"
}
```

#### Error Response (HTTP 404)

```json
{
  "error": "Ticket not found",
  "code": "NOT_FOUND"
}
```

#### Error Response (HTTP 422 - Invalid Status Transition)

```json
{
  "error": "Invalid status transition",
  "code": "INVALID_STATUS_TRANSITION",
  "details": "Cannot transition from 'ready' to 'processing'"
}
```

#### Error Response (HTTP 500)

```json
{
  "error": "Unexpected server error",
  "code": "INTERNAL_SERVER_ERROR"
}
```

---

### PATCH /api/tickets/:id/payment

Update the payment status and method for an existing ticket.

#### Request

**Authentication:** Required (Bearer token or session cookie)

**Path Parameter:**

- `:id` (UUID) — The ticket ID to update

**Body Schema:**

```json
{
  "payment_status": "pending" | "prepaid" | "paid",
  "payment_method": "cash" | "card" | "transfer" (optional)",
  "paid_amount": "decimal (optional)"
}
```

#### Payment Update Rules

| payment_status | Behavior |
|----------------|----------|
| `pending` | `paid_amount` is set to 0; `paid_at` is set to null |
| `prepaid` | `paid_at` is set to current UTC timestamp; may auto-promote to `paid` if `paid_amount >= total_amount` |
| `paid` | `paid_at` is set to current UTC timestamp if not already set |

#### Server Normalization

When the request body is processed:

1. If `payment_status` is "pending":
   - Set `paid_amount` to 0
   - Set `paid_at` to null
   - Ignore any `paid_amount` or `payment_method` in the request

2. If `payment_status` is "prepaid" or "paid":
   - Set `paid_at` to current UTC timestamp (now)
   - Apply requested `paid_amount` if provided
   - Apply requested `payment_method` if provided

3. **Auto-promotion trigger:** Database trigger may automatically promote "prepaid" to "paid" if `paid_amount >= total_amount`

#### Side Effects

1. **Audit Log:**
   - An audit log entry is written for every successful payment change (fire-and-forget)

#### Successful Response (HTTP 200)

```json
{
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "ticket_number": "T-20260302-0001",
    "ticket_date": "2026-03-02",
    "daily_folio": 1,
    "source": "encargo",
    "customer_id": null,
    "customer_name": "Juan García",
    "customer_phone": "+56912345678",
    "service_type": "wash-fold",
    "service": "Lavado y Planchado",
    "weight": 5.5,
    "status": "ready",
    "notes": "Entregar mañana",
    "total_amount": 25000,
    "payment_method": "card",
    "payment_status": "paid",
    "paid_amount": 25000,
    "paid_at": "2026-03-02T14:20:00Z",
    "actual_pickup_date": null,
    "created_at": "2026-03-02T10:30:00Z",
    "created_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "updated_at": "2026-03-02T14:25:00Z",
    "updated_by": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
  }
}
```

#### Error Response (HTTP 401)

```json
{
  "error": "Missing or invalid authentication token",
  "code": "NOT_AUTHENTICATED"
}
```

#### Error Response (HTTP 403)

```json
{
  "error": "Insufficient permissions to update ticket payment",
  "code": "INSUFFICIENT_PERMISSIONS"
}
```

#### Error Response (HTTP 404)

```json
{
  "error": "Ticket not found",
  "code": "NOT_FOUND"
}
```

#### Error Response (HTTP 422 - Validation)

```json
{
  "error": "Invalid payment status",
  "code": "INVALID_PAYMENT_STATUS",
  "details": "payment_status must be 'pending', 'prepaid', or 'paid'"
}
```

#### Error Response (HTTP 500)

```json
{
  "error": "Unexpected server error",
  "code": "INTERNAL_SERVER_ERROR"
}
```

---

## Important Behaviors

### 1. Prepaid Auto-Promotion

When a ticket's `payment_status` is "prepaid", the database may automatically promote it to "paid" if:

```
paid_amount >= total_amount
```

This promotion happens server-side via a database trigger. The response will reflect the promoted status.

**Android Implementation Note:** Always treat the response `payment_status` as the source of truth. Do not assume a "prepaid" request will result in "prepaid" status if the amount meets the promotion threshold.

### 2. Strict Linear Status Flow

Ticket statuses follow a one-way progression:

```
RECEIVED → PROCESSING → READY → DELIVERED
```

- Backward transitions (e.g., READY → PROCESSING) are rejected with HTTP 422
- Sideways transitions (e.g., PROCESSING → READY skipping intermediate states) are rejected
- No-op transitions (e.g., READY → READY) are rejected

### 3. Legacy "paid" Status

The "paid" status exists in the database for historical compatibility but is read-only:

- GET requests may return tickets with status "paid"
- PATCH requests that attempt to set status to "paid" are rejected with HTTP 422
- The linear flow uses "delivered" as the terminal state for all new tickets

### 4. Audit Logging is Fire-and-Forget

Audit log entries are written asynchronously:

- Response does not wait for audit log completion
- If audit logging fails, the primary operation still succeeds
- Audit logs should not be used for critical decision-making on the client

### 5. Auto-Generated Ticket Numbering

Ticket numbers are generated server-side by a database trigger:

- Format: `T-YYYYMMDD-NNNN` (e.g., T-20260302-0001)
- YYYYMMDD: Date of ticket creation
- NNNN: Auto-incremented 4-digit daily counter
- Unique constraint: (ticket_date, daily_folio)

**Rare Collision Scenario:** In case of simultaneous batch requests that cause a collision, the server returns HTTP 409 with code `TICKET_NUMBER_CONFLICT`. Client should retry with exponential backoff.

### 6. Opening Checklist Gate

Before any tickets can be created:

- If an opening checklist for today exists in the database
- AND that checklist has not been completed
- THEN ticket creation is blocked with HTTP 409 `OPENING_CHECKLIST_BLOCKING_OPERATION`

This gate ensures all starting inventory is recorded before sales begin.

### 7. Response Data is Source of Truth

The `data` field in a successful response reflects the server-normalized state:

- Server may normalize field values (e.g., empty strings → null, payment amounts)
- Client must not assume the request body values match the response
- Use response data for all subsequent operations and display

### 8. Timestamp Formats

All timestamp fields use ISO 8601 format with UTC timezone:

- Example: `2026-03-02T10:30:00Z`
- Date fields use YYYY-MM-DD format
- Treat all times as UTC on the client side

---

## Implementation Notes

### Kotlin Error Handling Example

```kotlin
sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val statusCode: Int, val code: String, val message: String) : ApiResult<T>()
}

fun handleTicketError(statusCode: Int, code: String): ApiResult.Error<*> {
    return when {
        statusCode == 401 && code == "NOT_AUTHENTICATED" -> {
            // Handle: Refresh token or force login
        }
        statusCode == 409 && code == "OPENING_CHECKLIST_BLOCKING_OPERATION" -> {
            // Handle: Show message "Complete opening checklist first"
        }
        statusCode == 409 && code == "TICKET_NUMBER_CONFLICT" -> {
            // Handle: Retry with exponential backoff
        }
        statusCode == 422 -> {
            // Handle: Show validation error to user
        }
        else -> {
            // Handle: Generic error
        }
    }
}
```

### Retrofit 2 Integration Example

```kotlin
interface LcxTicketService {
    @POST("/api/tickets")
    suspend fun createTickets(
        @Body request: CreateTicketsRequest
    ): Response<TicketsResponse>

    @PATCH("/api/tickets/{id}/status")
    suspend fun updateTicketStatus(
        @Path("id") ticketId: String,
        @Body request: UpdateStatusRequest
    ): Response<TicketResponse>

    @PATCH("/api/tickets/{id}/payment")
    suspend fun updateTicketPayment(
        @Path("id") ticketId: String,
        @Body request: UpdatePaymentRequest
    ): Response<TicketResponse>
}
```

### Request/Response Models Example

```kotlin
// Request models
data class CreateTicketsRequest(
    val source: String,  // "encargo" or "venta"
    val tickets: List<TicketDraft>
)

data class TicketDraft(
    val customer_name: String,
    val customer_phone: String?,
    val customer_id: String?,
    val service_type: String,  // "in-store" or "wash-fold"
    val service: String,
    val weight: Double?,
    val status: String? = "received",
    val notes: String?,
    val total_amount: Double?,
    val payment_method: String?,
    val payment_status: String? = "pending",
    val paid_amount: Double? = 0.0
)

// Response models
data class TicketsResponse(
    val data: List<Ticket>
)

data class TicketResponse(
    val data: Ticket
)

data class Ticket(
    val id: String,
    val ticket_number: String,
    val ticket_date: String,
    val daily_folio: Int,
    val source: String,
    val customer_id: String?,
    val customer_name: String,
    val customer_phone: String,
    val service_type: String,
    val service: String,
    val weight: Double?,
    val status: String,
    val notes: String?,
    val total_amount: Double?,
    val payment_method: String?,
    val payment_status: String,
    val paid_amount: Double,
    val paid_at: String?,
    val actual_pickup_date: String?,
    val created_at: String,
    val created_by: String,
    val updated_at: String,
    val updated_by: String
)

// Update request models
data class UpdateStatusRequest(
    val status: String  // "received", "processing", "ready", "delivered"
)

data class UpdatePaymentRequest(
    val payment_status: String,  // "pending", "prepaid", "paid"
    val payment_method: String?,
    val paid_amount: Double?
)
```

### Authentication Setup Example

```kotlin
// Interceptor for adding Bearer token
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenProvider()

        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(newRequest)
    }
}

// Retrofit client configuration
val httpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor { supabaseAuth.currentAccessToken })
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.lcx.example.com")
    .client(httpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-03-02 | Initial specification document |

---

**Document Owner:** Platform Engineering
**Last Reviewed:** 2026-03-02
**Next Review:** 2026-04-02
