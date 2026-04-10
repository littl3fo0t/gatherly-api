# Gatherly — API Endpoints

> This document describes the REST API as implemented in this repository. The **OpenAPI** definition served at `/v3/api-docs` and **Swagger UI** at `/swagger-ui.html` (both permitted without auth) are authoritative for schemas; this guide focuses on behavior and examples.

All paths below are relative to the [base URL](#base-url). Protected endpoints require a valid JWT in the `Authorization: Bearer <token>` header unless the route is explicitly public.

---

## Table of Contents

1. [Base URL](#base-url)
2. [Authentication (Supabase + JWT)](#authentication-supabase--jwt)
3. [Roles & Access Levels](#roles--access-levels)
4. [Public Endpoints](#public-endpoints)
   - [Events](#events-public)
   - [Categories](#categories-public)
5. [Protected Endpoints](#protected-endpoints)
   - [Events](#events-protected)
   - [RSVPs](#rsvps)
   - [Profiles](#profiles)
6. [Admin Endpoints](#admin-endpoints)
   - [Implemented](#implemented)
   - [Deferred (not implemented)](#deferred-not-implemented)
7. [Error Responses](#error-responses)

---

## Base URL

```
Local:      http://localhost:8080/api
Production: https://gatherly-api-production.up.railway.app/api
```

Interactive docs: append `/swagger-ui.html` to the same host and port as the API (e.g. `http://localhost:8080/swagger-ui.html` locally).

---

## Authentication (Supabase + JWT)

This service is an **OAuth2 resource server**. It does **not** expose `POST /auth/register` or `POST /auth/login`; sign-up and sign-in happen in your app via the **Supabase Auth** client (or Supabase dashboard). After the user signs in, send Supabase’s **access token** to this API:

```
Authorization: Bearer <access_token>
```

Spring Security validates the JWT before the request reaches the controller. A missing, expired, or invalid token on a protected endpoint returns **`401 Unauthorized`** with a JSON body (see [Error Responses](#error-responses)).

**Application roles** (`user`, `moderator`, `admin`) are read from the JWT for authorization. Resolution order: `app_metadata.role` (e.g. from a Supabase access-token hook), then `user_metadata.role`, then top-level `role` if it is not `authenticated` or `anon`.

**Profiles:** API responses use the profile shape returned by [`GET /profiles/me`](#get-profilesme). Your registration or post-sign-in flow should ensure a `profiles` row exists for the JWT `sub` (user id) before calling endpoints that require a profile (for example creating an event).

---

## Roles & Access Levels

| Level | Description |
|---|---|
| **Public** | No token required. |
| **Protected** | Valid JWT required. Any authenticated user. |
| **Moderator** | Valid JWT required. Role must be `moderator` or `admin`. |
| **Admin** | Valid JWT required. Role must be `admin` only. |

---

## Public Endpoints

### Events (Public)

---

#### `GET /events`
Returns a paginated list of all **active** events. Sort order: **hot** events first (≥ 80% of `maxCapacity` when capacity is positive), then by **`startTime` ascending**, then by `id` for a stable order.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | `integer` | ❌ | Zero-based page index. Defaults to `0`. |
| `size` | `integer` | ❌ | Page size. Defaults to `25`. Maximum `100` per request. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns paginated list of active events. |

**Success response body:**
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Spring Meetup 2026",
      "eventType": "in_person",
      "admissionType": "free",
      "admissionFee": null,
      "startTime": "2026-04-01T18:00:00Z",
      "endTime": "2026-04-01T21:00:00Z",
      "timezone": "America/Toronto",
      "city": "Toronto",
      "province": "ON",
      "coverImageUrl": "https://res.cloudinary.com/...",
      "rsvpCount": 42,
      "maxCapacity": 50,
      "isHot": true,
      "categories": ["Meetup", "Tech"]
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 100,
  "totalPages": 4
}
```

---

#### `GET /events/{id}`
Returns full details for a single active event. Organizer name is only included if the request includes a valid JWT.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns full event details. |
| `404 Not Found` | No active event found with the given ID. |

**Success response body:**
```json
{
  "id": "uuid",
  "title": "Spring Meetup 2026",
  "description": "<p>Rich text content here...</p>",
  "eventType": "in_person",
  "admissionType": "free",
  "admissionFee": null,
  "startTime": "2026-04-01T18:00:00Z",
  "endTime": "2026-04-01T21:00:00Z",
  "timezone": "America/Toronto",
  "address": {
    "addressLine1": "123 Main St",
    "addressLine2": null,
    "city": "Toronto",
    "province": "ON",
    "postalCode": "M5V 1A1"
  },
  "coverImageUrl": "https://res.cloudinary.com/...",
  "rsvpCount": 42,
  "maxCapacity": 50,
  "isHot": true,
  "categories": ["Meetup", "Tech"],
  "organizer": {
    "id": "uuid",
    "fullName": "Jane Doe"
  }
}
```

> **Notes:**
> - The `organizer` field is only included when a valid JWT is present in the request. Unauthenticated requests receive the same payload **excluding** the `organizer` object.
> - The public detail response does **not** include `meetingLink`. For virtual or hybrid events, the join link is stored server-side but is only exposed on the organizer dashboard ([`GET /events/my`](#get-eventsmy)) as `meetingLink` on each item.

---

### Categories (Public)

---

#### `GET /categories`
Returns the full list of available event categories.

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns all categories. |

**Success response body:**
```json
[
  {
    "id": "uuid",
    "name": "Meetup",
    "slug": "meetup",
    "createdAt": "2026-03-01T12:00:00Z"
  },
  {
    "id": "uuid",
    "name": "Family-Focused",
    "slug": "family-focused",
    "createdAt": "2026-03-01T12:00:00Z"
  },
  {
    "id": "uuid",
    "name": "Live Performance",
    "slug": "live-performance",
    "createdAt": "2026-03-01T12:00:00Z"
  }
]
```

---

## Protected Endpoints

> All endpoints in this section require a valid JWT in the `Authorization` header.

---

### Events (Protected)

---

#### `POST /events`
Creates a new event. The authenticated user becomes the organizer.

**Request body:** Shape depends on `eventType`:

- **`virtual`:** `meetingLink` is required. Address fields may be omitted or null. Swagger UI lists named examples (**Virtual**, **InPerson**, **Hybrid**).
- **`in_person`:** `addressLine1`, `city`, `province`, and `postalCode` are required. `meetingLink` may be omitted or null.
- **`hybrid`:** Both `meetingLink` and the same address fields as in-person are required.

**URL fields:** When `meetingLink` or `coverImageUrl` is non-empty, it must be a valid **`http` or `https` URL with a host** (otherwise the API returns `400` with a message like `meetingLink must be a valid http or https URL.`).

**Example — in-person (free):**
```json
{
  "title": "Spring Meetup 2026",
  "description": "<p>Rich text content here...</p>",
  "eventType": "in_person",
  "admissionType": "free",
  "startTime": "2026-04-01T18:00:00Z",
  "endTime": "2026-04-01T21:00:00Z",
  "timezone": "America/Toronto",
  "addressLine1": "123 Main St",
  "addressLine2": null,
  "city": "Toronto",
  "province": "ON",
  "postalCode": "M5V 1A1",
  "meetingLink": null,
  "admissionFee": null,
  "coverImageUrl": "https://cdn.example.com/events/banner.jpg",
  "maxCapacity": 50,
  "categoryIds": ["uuid", "uuid"]
}
```

**Example — virtual (free):**
```json
{
  "title": "Online AMA",
  "description": "<p>Join from anywhere</p>",
  "eventType": "virtual",
  "admissionType": "free",
  "startTime": "2026-04-02T18:00:00Z",
  "endTime": "2026-04-02T19:30:00Z",
  "timezone": "America/Toronto",
  "addressLine1": null,
  "addressLine2": null,
  "city": null,
  "province": null,
  "postalCode": null,
  "meetingLink": "https://meet.example.com/room/abc",
  "admissionFee": null,
  "coverImageUrl": null,
  "maxCapacity": 100,
  "categoryIds": []
}
```

**Responses:**

| Status | Description |
|---|---|
| `201 Created` | Event created successfully. Returns the created event. |
| `400 Bad Request` | Validation failed (e.g. missing required fields, end time before start time, more than 3 categories, missing meeting link for virtual or hybrid, missing address for in-person or hybrid, invalid or non-http(s) `meetingLink` / `coverImageUrl`, `admissionFee` provided for a free event, `admissionFee` missing or zero for a paid event). |
| `401 Unauthorized` | Missing or invalid token. |
| `404 Not Found` | No profile row for the authenticated user (`sub`); create a profile before creating events. |

---

#### `PUT /events/{id}`
Updates an existing event. Only the organizer can edit their own event. `eventType`, `admissionType` and `admissionFee` are immutable and will be ignored if included.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Request body:** Same shape as `POST /events` excluding `eventType`, `admissionType` and `admissionFee`. Location and link requirements follow the **stored** event type (virtual / in_person / hybrid), same rules as create. Non-empty `meetingLink` and `coverImageUrl` must be valid **`http` or `https` URLs with a host**. OpenAPI includes named examples (**Virtual**, **InPerson**, **Hybrid**) for this body.

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Event updated successfully. Returns the updated event. |
| `400 Bad Request` | Validation failed (including invalid URLs or missing link/address for the event’s type). |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not the organizer. |
| `404 Not Found` | Event not found. |
| `409 Conflict` | Attempted to decrease `maxCapacity` below current value. |

---

#### `DELETE /events/{id}`
Soft deletes an event. Only the organizer can delete their own event. Sets `deleted_at` to the current timestamp when transitioning from another status to `soft_deleted`.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `204 No Content` | Event soft deleted successfully. **Idempotent:** if the event is already `soft_deleted`, the API still returns `204`. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not the organizer. |
| `404 Not Found` | Event not found. |

---

#### `PATCH /events/{id}/restore`
Restores a soft deleted event within the 7 day window. Only available to the organizer who deleted it.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Event restored. Returns the restored event with status `active` or `archived` depending on whether the end time has passed. |
| `400 Bad Request` | Event is not in a soft deleted state. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not the organizer. |
| `404 Not Found` | Event not found or purge window has already passed. |

---

#### `PATCH /events/{id}/flag`
Flags an event. Restricted to `moderator` and `admin` roles only.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Request body:** `reason` must be one of these lowercase labels (as stored in the database):

`off_topic`, `nsfw`, `spam`, `misleading`, `other`

```json
{
  "reason": "spam"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Event flagged successfully. |
| `400 Bad Request` | Missing or invalid flag reason. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user does not have moderator or admin role. |
| `404 Not Found` | Event not found. |
| `409 Conflict` | Event is already flagged. |

---

#### `GET /events/my`
Returns a **complete dashboard view** of every event the authenticated user has created that still exists in the database — more detail than the public listing (`GET /events`) and across all lifecycle states you care about as the organizer.

**Included:**

| Group | Meaning |
|---|---|
| **Active** | Live on the public listing; accepting RSVPs. |
| **Archived** | Past end time; no longer shown on the main public listing. |
| **Flagged** | Hidden from the public; you still see the event with `flagReason`, `flaggedAt`, and `flaggedBy` so you know why it was flagged. |
| **Soft deleted** | Only rows still within the **7-day grace** window after `deleted_at` (before a future CRON job purges them permanently). |

**Excluded:**

| Case | Reason |
|---|---|
| **Purged** | Hard-deleted from the database — nothing to return. |
| **Other users’ events** | Scoped strictly to the authenticated organizer. |
| **Soft deleted (expired grace)** | `deleted_at` is older than 7 days; row may still exist until purge runs, but it does not appear here. |

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `string` | ❌ | Filter to one lifecycle group: `active`, `archived`, `flagged`, `soft_deleted`. Omit to return all groups that pass the rules above. |
| `page` | `integer` | ❌ | Zero-based page index. Defaults to `0`. |
| `size` | `integer` | ❌ | Page size. Defaults to `25`. Maximum `100` per request. |

Results are sorted by **`startTime` descending**, then by `id` for a stable order.

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Paginated list of your events (see success body below). |
| `400 Bad Request` | Invalid `status` query value (not one of the allowed enum labels). |
| `401 Unauthorized` | Missing or invalid token. |

**Success response body:**
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Spring Meetup 2026",
      "description": "<p>Rich text content here...</p>",
      "eventType": "in_person",
      "admissionType": "free",
      "admissionFee": null,
      "meetingLink": null,
      "startTime": "2026-04-01T18:00:00Z",
      "endTime": "2026-04-01T21:00:00Z",
      "timezone": "America/Toronto",
      "address": {
        "addressLine1": "123 Main St",
        "addressLine2": null,
        "city": "Toronto",
        "province": "ON",
        "postalCode": "M5V 1A1"
      },
      "coverImageUrl": "https://res.cloudinary.com/...",
      "rsvpCount": 42,
      "maxCapacity": 50,
      "isHot": true,
      "categories": ["Meetup", "Tech"],
      "status": "active",
      "flagReason": null,
      "flaggedAt": null,
      "flaggedBy": null,
      "deletedAt": null,
      "organizer": {
        "id": "uuid",
        "fullName": "Jane Doe"
      }
    }
  ],
  "page": 0,
  "size": 25,
  "totalElements": 12,
  "totalPages": 1
}
```

> **Notes:** For **flagged** events, `flagReason`, `flaggedAt`, and `flaggedBy` are populated (`flaggedBy` uses the same `{ id, fullName }` shape as the public detail `organizer` block). For **soft deleted** events in grace, `deletedAt` is set and `status` is `soft_deleted`. Virtual/hybrid events include `meetingLink` when present.

---

### RSVPs

---

#### `POST /events/{id}/rsvp`
Creates a confirmed RSVP for the authenticated user on the specified event.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `201 Created` | RSVP confirmed. Returns the RSVP record. |
| `400 Bad Request` | Event start time has already passed — admissions closed. |
| `401 Unauthorized` | Missing or invalid token. |
| `404 Not Found` | Event not found or not active, or no profile for the authenticated user. |
| `409 Conflict` | User has already RSVPed for this event. |
| `422 Unprocessable Entity` | Event is at maximum capacity. |

**Success response body** (`201`):
```json
{
  "id": "uuid",
  "eventId": "uuid",
  "userId": "uuid",
  "status": "confirmed",
  "createdAt": "2026-04-01T10:00:00Z",
  "updatedAt": "2026-04-01T10:00:00Z"
}
```

---

#### `PATCH /events/{id}/rsvp/cancel`
Cancels the authenticated user's RSVP for the specified event.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | RSVP cancelled successfully. Returns the updated RSVP row (`status` is `cancelled`). |
| `400 Bad Request` | Event start time has already passed — admissions closed. |
| `401 Unauthorized` | Missing or invalid token. |
| `404 Not Found` | Event not found, or no **confirmed** RSVP for this user and event. |

**Success response body** (`200`):
```json
{
  "id": "uuid",
  "eventId": "uuid",
  "userId": "uuid",
  "status": "cancelled",
  "createdAt": "2026-04-01T10:00:00Z",
  "updatedAt": "2026-04-01T11:30:00Z"
}
```

---

#### `GET /rsvps/my`
Returns the authenticated user’s RSVPs in **two independent pages**: **upcoming** (event `startTime` after now, UTC) and **past** (start time at or before now). Each side uses the same `page` and `size` query parameters.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `string` | ❌ | Filter RSVPs by status: `confirmed` or `cancelled` (exact enum names). Omit for all statuses. |
| `page` | `integer` | ❌ | Zero-based page index applied to **both** `upcoming` and `past`. Defaults to `0`. |
| `size` | `integer` | ❌ | Page size for **both** slices. Defaults to `25`. Maximum `100` per request. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns `upcoming` and `past`, each a paginated `PageResponse` of RSVP + event summary rows. |
| `400 Bad Request` | Invalid `status` filter (not `confirmed` or `cancelled`). |
| `401 Unauthorized` | Missing or invalid token. |

**Success response body:**
```json
{
  "upcoming": {
    "content": [
      {
        "rsvpId": "uuid",
        "rsvpStatus": "confirmed",
        "rsvpCreatedAt": "2026-04-01T10:00:00Z",
        "rsvpUpdatedAt": "2026-04-01T10:00:00Z",
        "eventId": "uuid",
        "eventTitle": "Spring Meetup 2026",
        "eventType": "in_person",
        "admissionType": "free",
        "admissionFee": null,
        "startTime": "2026-04-15T18:00:00Z",
        "endTime": "2026-04-15T21:00:00Z",
        "timezone": "America/Toronto",
        "city": "Toronto",
        "province": "ON",
        "coverImageUrl": "https://cdn.example.com/banner.jpg"
      }
    ],
    "page": 0,
    "size": 25,
    "totalElements": 3,
    "totalPages": 1
  },
  "past": {
    "content": [],
    "page": 0,
    "size": 25,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

---

### Profiles

---

#### `GET /profiles/me`
Returns the authenticated user's full profile.

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns the user's profile. |
| `401 Unauthorized` | Missing or invalid token. |

**Success response body:**
```json
{
  "id": "uuid",
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "role": "user",
  "avatarUrl": "https://res.cloudinary.com/...",
  "addressLine1": "456 Queen St",
  "addressLine2": null,
  "city": "Ottawa",
  "province": "ON",
  "postalCode": "K1A 0A9",
  "createdAt": "2026-03-11T10:00:00Z",
  "updatedAt": "2026-03-11T10:00:00Z"
}
```

---

#### `PUT /profiles/me`
Updates the authenticated user's profile. Email and role cannot be changed through this endpoint.

**Request body:**
```json
{
  "fullName": "Jane Smith",
  "avatarUrl": "https://res.cloudinary.com/...",
  "addressLine1": "456 Queen St",
  "addressLine2": null,
  "city": "Ottawa",
  "province": "ON",
  "postalCode": "K1A 0A9"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Profile updated successfully. Returns updated profile. |
| `400 Bad Request` | Validation failed. |
| `401 Unauthorized` | Missing or invalid token. |

**Success response body:** Same shape as [`GET /profiles/me`](#get-profilesme) (all fields reflect persisted values after update).

---

## Admin Endpoints

> Routes under `/api/admin/**` require a valid JWT whose application role is **`admin`**. Non-admins receive **`403 Forbidden`** with the standard JSON error body (`message` is the literal `Forbidden` — see [Error Responses](#error-responses)).

### Implemented

#### `PATCH /admin/users/{id}/role`

Promotes or demotes a user between **`user`** and **`moderator`**. Cannot assign, remove, or change the **`admin`** role through this endpoint.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | Target user’s profile id. |

**Request body:** `role` must be the JSON string `user` or `moderator` (lowercase, matching the API enum).

```json
{
  "role": "moderator"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Role updated. Returns the updated profile (same shape as [`GET /profiles/me`](#get-profilesme)). |
| `400 Bad Request` | Invalid body, invalid role, or business rule violation (e.g. cannot modify an admin account this way). |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | User not found. |

**Success response body** (`200`): identical fields to **`GET /profiles/me`**, for example:

```json
{
  "id": "uuid",
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "role": "moderator",
  "avatarUrl": null,
  "addressLine1": null,
  "addressLine2": null,
  "city": null,
  "province": null,
  "postalCode": null,
  "createdAt": "2026-03-11T10:00:00Z",
  "updatedAt": "2026-04-09T15:00:00Z"
}
```

### Deferred (not implemented)

The following were planned for a later iteration and are **not** exposed by the current API (calling them will **not** hit a Gatherly handler):

| Area | Intended endpoints (roadmap) |
|---|---|
| **Users** | `GET /api/admin/users`, `PUT /api/admin/users/{id}` |
| **Events** | `GET /api/admin/events`, `GET /api/admin/events/{id}/log` |
| **Categories** | `POST /api/admin/categories`, `PUT /api/admin/categories/{id}`, `DELETE /api/admin/categories/{id}` |

Categories remain **read-only** for clients via public [`GET /categories`](#get-categories).

---

## Error Responses

Most errors use the same JSON envelope (`timestamp`, `status`, `error`, `message`, `path`, optional `errors`). `timestamp` is an ISO-8601 instant in UTC.

**Typical error body** (single message, no field list):
```json
{
  "timestamp": "2026-03-11T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Malformed JSON request body.",
  "path": "/api/events"
}
```

**Bean validation** (`@Valid` failures) sets `message` to **`Validation failed.`** and includes an `errors` array:

```json
{
  "timestamp": "2026-03-11T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed.",
  "errors": [
    { "field": "title", "message": "Title is required." },
    { "field": "endTime", "message": "End time must be after start time." }
  ],
  "path": "/api/events"
}
```

**`401 Unauthorized`** (Spring Security resource server / invalid bearer token): `message` is **`Missing or invalid JWT token.`** When the Spring profile **`development`** is active, the API may add `exception` and `debugMessage` for troubleshooting; those fields are not returned in production.

**`403 Forbidden`** (authenticated but not allowed, e.g. wrong role): `message` is the literal **`Forbidden`**.

**Common HTTP status codes used across the API:**

| Status | Meaning |
|---|---|
| `200 OK` | Request succeeded. |
| `201 Created` | Resource created successfully. |
| `204 No Content` | Request succeeded with no response body. |
| `400 Bad Request` | Validation failed, bad query parameter, or malformed JSON (`message` varies; see above). |
| `401 Unauthorized` | Missing or invalid JWT (`message`: `Missing or invalid JWT token.`). |
| `403 Forbidden` | Valid token but insufficient role or ownership (`message`: `Forbidden`). |
| `404 Not Found` | Resource not found or hidden (e.g. inactive event). |
| `409 Conflict` | Request conflicts with existing data (e.g. duplicate RSVP). |
| `422 Unprocessable Entity` | Request understood but cannot be applied (e.g. event at capacity). |
| `500 Internal Server Error` | Unexpected server error (`message`: `An unexpected error occurred.`). |
