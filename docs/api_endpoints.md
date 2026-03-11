# Gatherly — API Endpoints (First Draft)

> **📋 Note:** This document is a first draft and is subject to change during development. Endpoint paths, request bodies, and responses may be adjusted as implementation progresses. All protected endpoints require a valid JWT token in the `Authorization: Bearer <token>` header unless marked as public.

---

## Table of Contents

1. [Base URL](#base-url)
2. [Authentication](#authentication)
3. [Roles & Access Levels](#roles--access-levels)
4. [Public Endpoints](#public-endpoints)
   - [Auth](#auth)
   - [Events](#events-public)
   - [Categories](#categories-public)
5. [Protected Endpoints](#protected-endpoints)
   - [Events](#events-protected)
   - [RSVPs](#rsvps)
   - [Profiles](#profiles)
6. [Admin Endpoints](#admin-endpoints)
   - [Users](#admin--users)
   - [Events](#admin--events)
   - [Categories](#admin--categories)
7. [Error Responses](#error-responses)

---

## Base URL

```
Local:      http://localhost:8080/api
Production: https://gatherly-api.onrender.com/api
```

---

## Authentication

Supabase Auth issues JWT tokens on login and registration. All protected endpoints validate the token via Spring Security before the request reaches the controller. Tokens are passed in the request header as follows:

```
Authorization: Bearer <token>
```

A missing, expired, or invalid token on a protected endpoint always returns `401 Unauthorized`.

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

### Auth

---

#### `POST /auth/register`
Creates a new Supabase Auth user and a corresponding `profiles` record.

**Request body:**
```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "password": "securepassword123"
}
```

**Responses:**

| Status | Description |
|---|---|
| `201 Created` | Registration successful. Returns the new user profile and JWT token. |
| `400 Bad Request` | Validation failed (e.g. missing fields, invalid email format, password too short). |
| `409 Conflict` | An account with this email already exists. |

**Success response body:**
```json
{
  "token": "eyJhbGci...",
  "user": {
    "id": "uuid",
    "fullName": "Jane Doe",
    "email": "jane@example.com",
    "role": "user",
    "createdAt": "2026-03-11T10:00:00Z"
  }
}
```

---

#### `POST /auth/login`
Authenticates an existing user via Supabase Auth and returns a JWT token.

**Request body:**
```json
{
  "email": "jane@example.com",
  "password": "securepassword123"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Login successful. Returns user profile and JWT token. |
| `400 Bad Request` | Missing or malformed request body. |
| `401 Unauthorized` | Invalid email or password. |

**Success response body:**
```json
{
  "token": "eyJhbGci...",
  "user": {
    "id": "uuid",
    "fullName": "Jane Doe",
    "email": "jane@example.com",
    "role": "user",
    "createdAt": "2026-03-11T10:00:00Z"
  }
}
```

---

### Events (Public)

---

#### `GET /events`
Returns a paginated list of all active events, sorted by start time. Hot events (≥ 80% capacity) appear first.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | `integer` | ❌ | Page number. Defaults to `0`. |
| `size` | `integer` | ❌ | Events per page. Defaults to `25`. |

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

> **Note:** The `organizer` field is only included when a valid JWT is present in the request. Unauthenticated requests receive the full event details excluding the organizer block.

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
  { "id": "uuid", "name": "Meetup", "slug": "meetup" },
  { "id": "uuid", "name": "Family-Focused", "slug": "family-focused" },
  { "id": "uuid", "name": "Live Performance", "slug": "live-performance" }
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

**Request body:**
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
  "coverImageUrl": "https://res.cloudinary.com/...",
  "maxCapacity": 50,
  "categoryIds": ["uuid", "uuid"]
}
```

**Responses:**

| Status | Description |
|---|---|
| `201 Created` | Event created successfully. Returns the created event. |
| `400 Bad Request` | Validation failed (e.g. missing required fields, end time before start time, more than 3 categories, missing meeting link for virtual event). |
| `401 Unauthorized` | Missing or invalid token. |

---

#### `PUT /events/{id}`
Updates an existing event. Only the organizer can edit their own event. `eventType` and `admissionType` are immutable and will be ignored if included.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Request body:** Same shape as `POST /events` excluding `eventType` and `admissionType`.

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Event updated successfully. Returns the updated event. |
| `400 Bad Request` | Validation failed. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not the organizer. |
| `404 Not Found` | Event not found. |
| `409 Conflict` | Attempted to decrease `maxCapacity` below current value. |

---

#### `DELETE /events/{id}`
Soft deletes an event. Only the organizer can delete their own event. Sets `deleted_at` to the current timestamp.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `204 No Content` | Event soft deleted successfully. |
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

**Request body:**
```json
{
  "reason": "off_topic"
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
Returns all events created by the authenticated user, grouped by status — including active, archived, flagged and soft deleted (within the 7 day window).

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `string` | ❌ | Filter by status: `active`, `archived`, `flagged`, `soft_deleted`. Returns all if omitted. |
| `page` | `integer` | ❌ | Defaults to `0`. |
| `size` | `integer` | ❌ | Defaults to `25`. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns paginated list of the user's events. |
| `401 Unauthorized` | Missing or invalid token. |

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
| `404 Not Found` | Event not found or not active. |
| `409 Conflict` | User has already RSVPed for this event. |
| `422 Unprocessable Entity` | Event is at maximum capacity. |

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
| `200 OK` | RSVP cancelled successfully. |
| `400 Bad Request` | Event start time has already passed. |
| `401 Unauthorized` | Missing or invalid token. |
| `404 Not Found` | No active RSVP found for this user and event. |

---

#### `GET /rsvps/my`
Returns all RSVPs belonging to the authenticated user, split into upcoming and past.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `string` | ❌ | Filter by `confirmed` or `cancelled`. Returns all if omitted. |
| `page` | `integer` | ❌ | Defaults to `0`. |
| `size` | `integer` | ❌ | Defaults to `25`. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns paginated list of the user's RSVPs with associated event summaries. |
| `401 Unauthorized` | Missing or invalid token. |

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

---

## Admin Endpoints

> All endpoints in this section require a valid JWT and the `admin` role. Any non-admin request returns `403 Forbidden`.

---

### Admin — Users

---

#### `GET /admin/users`
Returns a paginated list of all registered users.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `page` | `integer` | ❌ | Defaults to `0`. |
| `size` | `integer` | ❌ | Defaults to `25`. |
| `role` | `string` | ❌ | Filter by `user`, `moderator`, or `admin`. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns paginated list of all users. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |

---

#### `PUT /admin/users/{id}`
Updates a user's details. Intended for admin corrections — name and email only.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The user ID. |

**Request body:**
```json
{
  "fullName": "Jane Doe",
  "email": "jane.updated@example.com"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | User updated successfully. Returns updated profile. |
| `400 Bad Request` | Validation failed. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | User not found. |
| `409 Conflict` | Email already in use by another account. |

---

#### `PATCH /admin/users/{id}/role`
Promotes or demotes a user's role between `user` and `moderator`. Cannot be used to assign or remove the `admin` role.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The user ID. |

**Request body:**
```json
{
  "role": "moderator"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Role updated successfully. Returns updated profile. |
| `400 Bad Request` | Invalid role value or attempted assignment of `admin` role. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | User not found. |

---

### Admin — Events

---

#### `GET /admin/events`
Returns a paginated list of all events regardless of status. Intended for full platform oversight.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `status` | `string` | ❌ | Filter by `active`, `flagged`, `archived`, `soft_deleted`. |
| `page` | `integer` | ❌ | Defaults to `0`. |
| `size` | `integer` | ❌ | Defaults to `25`. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns paginated list of all events. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |

---

#### `GET /admin/events/{id}/log`
Returns the full audit log for a specific event.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The event ID. |

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Returns list of all log entries for the event in descending chronological order. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | Event not found. |

---

### Admin — Categories

---

#### `POST /admin/categories`
Creates a new event category.

**Request body:**
```json
{
  "name": "Fundraiser",
  "slug": "fundraiser"
}
```

**Responses:**

| Status | Description |
|---|---|
| `201 Created` | Category created. Returns the new category. |
| `400 Bad Request` | Missing or invalid fields. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `409 Conflict` | A category with this name or slug already exists. |

---

#### `PUT /admin/categories/{id}`
Updates an existing category's name or slug.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The category ID. |

**Request body:**
```json
{
  "name": "Fundraiser Event",
  "slug": "fundraiser-event"
}
```

**Responses:**

| Status | Description |
|---|---|
| `200 OK` | Category updated. Returns the updated category. |
| `400 Bad Request` | Validation failed. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | Category not found. |
| `409 Conflict` | Name or slug already in use. |

---

#### `DELETE /admin/categories/{id}`
Deletes a category. Should be used with caution — consider the impact on events currently assigned this category.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | `UUID` | The category ID. |

**Responses:**

| Status | Description |
|---|---|
| `204 No Content` | Category deleted successfully. |
| `401 Unauthorized` | Missing or invalid token. |
| `403 Forbidden` | Authenticated user is not admin. |
| `404 Not Found` | Category not found. |

---

## Error Responses

All error responses follow a consistent structure to make client-side handling predictable.

**Error response body:**
```json
{
  "timestamp": "2026-03-11T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "path": "/api/events"
}
```

**Validation error response body** (for `400` responses with multiple field errors):
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

**Common HTTP status codes used across the API:**

| Status | Meaning |
|---|---|
| `200 OK` | Request succeeded. |
| `201 Created` | Resource created successfully. |
| `204 No Content` | Request succeeded with no response body. |
| `400 Bad Request` | Validation failed or malformed request. |
| `401 Unauthorized` | Missing or invalid JWT token. |
| `403 Forbidden` | Valid token but insufficient role/permissions. |
| `404 Not Found` | Resource not found. |
| `409 Conflict` | Request conflicts with existing data (e.g. duplicate email, immutable field change). |
| `422 Unprocessable Entity` | Request is valid but cannot be processed (e.g. event at capacity). |
| `500 Internal Server Error` | Unexpected server error. |
