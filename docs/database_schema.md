# Gatherly — Database Schema

> **📋 Note to reviewer:** This document is a living plan and is subject to change as development progresses and new requirements are identified. It reflects design decisions made during the planning phase and may be revised based on instructor feedback, technical constraints, or scope adjustments discovered during implementation.

---

## Table of Contents

1. [Overview](#overview)
2. [User Roles](#user-roles)
3. [Event Lifecycle](#event-lifecycle)
4. [Tables](#tables)
   - [profiles](#1-profiles)
   - [events](#2-events)
   - [categories](#3-categories)
   - [event_categories](#4-event_categories)
   - [rsvps](#5-rsvps)
   - [event_log](#6-event_log)
5. [Relationships](#relationships)
6. [Triggers](#triggers)
7. [Business Rules & Edge Cases](#business-rules--edge-cases)
8. [RLS Policies](#rls-policies)
9. [Stretch Goals](#stretch-goals)

---

## Overview

Gatherly is a Canada-wide community event board platform. Users can discover and RSVP for local events, organizers can create and manage them, and moderators can flag inappropriate content. All database interactions are mediated through the Spring Boot API — the frontend never communicates with the database directly.

**Tech stack:** Spring Boot · PostgreSQL · Supabase Auth · Next.js · Cloudinary

---

## User Roles

Roles are stored on the `profiles` table and enforced at the API level via Spring Security RBAC.

| Role | Description |
|---|---|
| `user` | Default role. Can manage their own events and RSVPs. |
| `moderator` | Can flag any event with a mandatory reason. Cannot delete events outright. |
| `admin` | Full platform access. Can promote or demote users between `user` and `moderator`. |

> **Note:** All new registrations default to the `user` role. Only the admin can promote a user to `moderator` and vice versa.

---

## Event Lifecycle

Events move through the following states. State transitions are enforced at the API level and by scheduled CRON jobs.

```
ACTIVE ──────────────────────────────────────────────────────► ARCHIVED
  │         (end time passes — CRON job)                          │
  │                                                               │
  │ (organizer soft deletes)                                      │ (flagged event's
  ▼                                                               │  start time passes
SOFT_DELETED                                                      │  — CRON job)
  │                                                               │
  │ (organizer restores within 7 days)                           ▼
  │◄─────────────────────────────────────────────────────── FLAGGED
  │                                                    (moderator/admin flags event)
  │ (7 days pass — CRON job purges)
  ▼
PURGED (removed from database)
```

| Status | Visible on Main Listing | Visible on Dashboard | RSVPs Accepted |
|---|---|---|---|
| `active` | ✅ | ✅ | ✅ Until start time |
| `flagged` | ❌ | ✅ With flag reason | ❌ |
| `archived` | ❌ | ✅ In dedicated section | ❌ |
| `soft_deleted` | ❌ | ❌ | ❌ |
| `purged` | ❌ | ❌ | ❌ |

> **Note:** Once an event's **start time** passes, RSVPs close regardless of status. Once the **end time** passes, the event is archived by the CRON job.

---

## Tables

### 1. `profiles`

Mirrors Supabase Auth users. Created automatically when a user registers. Stores all application-owned user data.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. Matches the Supabase Auth user ID. |
| `full_name` | `VARCHAR(100)` | ❌ | User's full name as provided at registration. |
| `email` | `VARCHAR(255)` | ❌ | Must be unique. Mirrors the email stored in Supabase Auth. |
| `role` | `ENUM('user', 'moderator', 'admin')` | ❌ | Defaults to `user` on registration. |
| `avatar_url` | `TEXT` | ✅ | Cloudinary URL for the user's profile picture. |
| `address_line1` | `VARCHAR(255)` | ✅ | Street address. Optional — used for future "near me" filtering. |
| `address_line2` | `VARCHAR(255)` | ✅ | Apartment, suite, unit number. |
| `city` | `VARCHAR(100)` | ✅ | City of residence. |
| `province` | `ENUM(...)` | ✅ | One of Canada's 13 provinces and territories. |
| `postal_code` | `VARCHAR(7)` | ✅ | Canadian postal code format (e.g. A1A 1A1). |
| `created_at` | `TIMESTAMPTZ` | ❌ | Timestamp of account creation. Defaults to `NOW()`. |
| `updated_at` | `TIMESTAMPTZ` | ❌ | Timestamp of last profile update. Updated via trigger. |

**Province ENUM values:**
`AB`, `BC`, `MB`, `NB`, `NL`, `NS`, `NT`, `NU`, `ON`, `PE`, `QC`, `SK`, `YT`

**Indexes:**
- `UNIQUE` on `email`

**Relationships:**
- One `profile` → many `events` (as organizer)
- One `profile` → many `rsvps`
- One `profile` → many `event_log` entries (as actor)

---

### 2. `events`

Core table storing all event metadata. Content such as rich text descriptions is stored directly in this table using a text column, rendered on the frontend via TipTap or Quill.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. Generated via `gen_random_uuid()`. |
| `organizer_id` | `UUID` | ❌ | Foreign key → `profiles.id`. The user who created the event. |
| `title` | `VARCHAR(150)` | ❌ | Display title of the event. |
| `description` | `TEXT` | ❌ | Rich text content stored as HTML or JSON (TipTap/Quill format). |
| `cover_image_url` | `TEXT` | ✅ | Cloudinary URL for the event's cover image. Optional. |
| `event_type` | `ENUM('virtual', 'in_person', 'hybrid')` | ❌ | **Immutable after creation.** Determines which location fields are required. |
| `admission_type` | `ENUM('free', 'paid')` | ❌ | **Immutable after creation.** Protects users who RSVP based on admission terms. |
| `admission_fee` | `NUMERIC(10, 2)` | ✅ | **Immutable after creation.** Ticket price in CAD. Required and must be `> 0.00` if `admission_type = 'paid'`. Must be `NULL` if `admission_type = 'free'`. |
| `meeting_link` | `TEXT` | ✅ | Required if `event_type` is `virtual` or `hybrid`. Validated as a valid URL. |
| `address_line1` | `VARCHAR(255)` | ✅ | Required if `event_type` is `in_person` or `hybrid`. |
| `address_line2` | `VARCHAR(255)` | ✅ | Apartment, suite, unit. Optional. |
| `city` | `VARCHAR(100)` | ✅ | Required if `event_type` is `in_person` or `hybrid`. |
| `province` | `ENUM(...)` | ✅ | Required if `event_type` is `in_person` or `hybrid`. |
| `postal_code` | `VARCHAR(7)` | ✅ | Required if `event_type` is `in_person` or `hybrid`. |
| `timezone` | `VARCHAR(50)` | ❌ | IANA timezone string selected by organizer (e.g. `America/Toronto`). |
| `start_time` | `TIMESTAMPTZ` | ❌ | Event start date and time. RSVPs close when this passes. |
| `end_time` | `TIMESTAMPTZ` | ❌ | Event end date and time. Event archives when this passes. Must be after `start_time`. |
| `max_capacity` | `INTEGER` | ❌ | Maximum number of attendees. Can be increased but never decreased after creation. |
| `rsvp_count` | `INTEGER` | ❌ | Denormalized count of confirmed RSVPs. Defaults to `0`. Updated via trigger. |
| `status` | `ENUM('active', 'flagged', 'archived', 'soft_deleted')` | ❌ | Current lifecycle state of the event. Defaults to `active`. |
| `flag_reason` | `ENUM('off_topic', 'nsfw', 'spam', 'misleading', 'other')` | ✅ | Populated when status is set to `flagged`. Null otherwise. |
| `flagged_by` | `UUID` | ✅ | Foreign key → `profiles.id`. The moderator or admin who flagged the event. |
| `flagged_at` | `TIMESTAMPTZ` | ✅ | Timestamp of when the event was flagged. |
| `deleted_at` | `TIMESTAMPTZ` | ✅ | Timestamp of soft deletion. Null if not deleted. Used by CRON job for purging after 7 days. |
| `created_at` | `TIMESTAMPTZ` | ❌ | Timestamp of event creation. Defaults to `NOW()`. |
| `updated_at` | `TIMESTAMPTZ` | ❌ | Timestamp of last update. Updated via trigger. |

**Constraints:**
```sql
CONSTRAINT admission_fee_check CHECK (
  (admission_type = 'paid' AND admission_fee IS NOT NULL AND admission_fee > 0)
  OR
  (admission_type = 'free' AND admission_fee IS NULL)
)
```

**Indexes:**
- Index on `status` for filtering active events on the main listing
- Index on `start_time` for sorting and CRON job queries
- Index on `end_time` for CRON job archiving queries
- Index on `deleted_at` for CRON job purge queries
- Index on `organizer_id` for dashboard queries

**Relationships:**
- Many `events` → one `profile` (organizer)
- One `event` → many `event_categories`
- One `event` → many `rsvps`
- One `event` → many `event_log` entries

**Conditional validation (enforced at API level):**
- If `event_type = 'virtual'` → `meeting_link` required
- If `event_type = 'in_person'` → `address_line1`, `city`, `province`, `postal_code` required
- If `event_type = 'hybrid'` → both `meeting_link` and address fields required
- If `admission_type = 'paid'` → `admission_fee` required and must be `> 0.00`
- If `admission_type = 'free'` → `admission_fee` must be `null`

---

### 3. `categories`

Predefined list of event categories managed by the admin. Organizers select up to 3 when creating an event.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. |
| `name` | `VARCHAR(50)` | ❌ | Display name of the category (e.g. `Meetup`, `Family-Focused`, `Live Performance`). |
| `slug` | `VARCHAR(50)` | ❌ | URL-friendly version of the name (e.g. `live-performance`). Used for filtering. |
| `created_at` | `TIMESTAMPTZ` | ❌ | Defaults to `NOW()`. |

**Indexes:**
- `UNIQUE` on `slug`
- `UNIQUE` on `name`

**Relationships:**
- One `category` → many `event_categories`

---

### 4. `event_categories`

Junction table linking events to their selected categories. An event can have a maximum of 3 categories.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. |
| `event_id` | `UUID` | ❌ | Foreign key → `events.id`. |
| `category_id` | `UUID` | ❌ | Foreign key → `categories.id`. |
| `created_at` | `TIMESTAMPTZ` | ❌ | Defaults to `NOW()`. |

**Indexes:**
- `UNIQUE` on `(event_id, category_id)` — prevents duplicate category assignments
- Index on `event_id` for join queries

**Relationships:**
- Many `event_categories` → one `event`
- Many `event_categories` → one `category`

**Business rule:** Maximum of 3 category assignments per event. Enforced at the API level.

---

### 5. `rsvps`

Records confirmed RSVPs from users. A record existing means the user is attending. Cancellation sets status to `cancelled` rather than deleting the row, preserving history.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. |
| `event_id` | `UUID` | ❌ | Foreign key → `events.id`. |
| `user_id` | `UUID` | ❌ | Foreign key → `profiles.id`. |
| `status` | `ENUM('confirmed', 'cancelled')` | ❌ | Defaults to `confirmed` on creation. |
| `created_at` | `TIMESTAMPTZ` | ❌ | Timestamp of original RSVP. Defaults to `NOW()`. |
| `updated_at` | `TIMESTAMPTZ` | ❌ | Timestamp of last status change. Updated via trigger. |

**Indexes:**
- `UNIQUE` on `(event_id, user_id)` — prevents a user from RSVPing to the same event more than once
- Index on `user_id` for dashboard reservation management queries

**Relationships:**
- Many `rsvps` → one `event`
- Many `rsvps` → one `profile`

**Business rules:**
- RSVPs cannot be created after `events.start_time` has passed
- RSVPs cannot be created if `rsvp_count >= max_capacity`
- A user can cancel their RSVP at any time before the event start time
- Both confirmed and cancelled RSVPs count against the unique constraint — a user cannot re-RSVP after cancelling (enforced at API level)

---

### 6. `event_log`

Immutable audit trail recording significant lifecycle events for every event on the platform. Entries are never updated or deleted.

| Column | Type | Nullable | Description |
|---|---|---|---|
| `id` | `UUID` | ❌ | Primary key. |
| `event_id` | `UUID` | ❌ | Foreign key → `events.id`. Retained even after event is purged for audit purposes. |
| `actor_id` | `UUID` | ✅ | Foreign key → `profiles.id`. The user who performed the action. Null for system actions. |
| `action` | `ENUM('created', 'updated', 'flagged', 'unflagged', 'deleted', 'restored')` | ❌ | The type of action that occurred. |
| `flag_reason` | `ENUM('off_topic', 'nsfw', 'spam', 'misleading', 'other')` | ✅ | Populated only when `action = 'flagged'`. |
| `metadata` | `JSONB` | ✅ | Optional additional context. For `updated` actions, stores a summary of which fields changed. |
| `created_at` | `TIMESTAMPTZ` | ❌ | Timestamp of the action. Defaults to `NOW()`. |

**Indexes:**
- Index on `event_id` for retrieving the full history of a specific event
- Index on `actor_id` for querying actions performed by a specific user
- Index on `action` for filtering by action type

**Relationships:**
- Many `event_log` entries → one `event`
- Many `event_log` entries → one `profile` (actor)

> **Note:** `event_id` uses a regular foreign key rather than a cascading delete, meaning log entries are preserved even if the event is purged. This ensures the audit trail remains intact for accountability purposes.

---

## Relationships

```
profiles ──────────────────────────────────────────────────────────────────────┐
  │                                                                             │
  │ 1:many                                                                      │
  ▼                                                                             │
events ──────────────────── event_categories ──────── categories               │
  │          1:many              many:1                                         │
  │                                                                             │
  │ 1:many                                                                      │
  ▼                                                                             │
rsvps ◄────────────────────────────────────────────────────── profiles (user)  │
                                                                                │
event_log ◄──────────────────────────────────────────────── profiles (actor) ──┘
```

---

## Triggers

### 1. `update_updated_at`
**Fires on:** `UPDATE` on `profiles`, `events`, `rsvps`
**Purpose:** Automatically sets `updated_at` to `NOW()` on any row update so the application never has to manage this manually.

---

### 2. `enforce_max_capacity_increase_only`
**Fires on:** `BEFORE UPDATE` on `events`
**Purpose:** Prevents `max_capacity` from being decreased after an event is created. If the new value is less than the current value, the trigger raises an exception.

```sql
IF NEW.max_capacity < OLD.max_capacity THEN
  RAISE EXCEPTION 'max_capacity cannot be decreased after event creation';
END IF;
```

---

### 3. `enforce_immutable_fields`
**Fires on:** `BEFORE UPDATE` on `events`
**Purpose:** Prevents `event_type` and `admission_type` from being changed after creation. If either field differs from the current value, the trigger raises an exception.

```sql
IF NEW.event_type <> OLD.event_type THEN
  RAISE EXCEPTION 'event_type cannot be changed after event creation';
END IF;
IF NEW.admission_type <> OLD.admission_type THEN
  RAISE EXCEPTION 'admission_type cannot be changed after event creation';
END IF;
IF NEW.admission_fee <> OLD.admission_fee THEN
  RAISE EXCEPTION 'admission_fee cannot be changed after event creation';
END IF;
```

---

### 4. `sync_rsvp_count`
**Fires on:** `AFTER INSERT OR UPDATE` on `rsvps`
**Purpose:** Keeps the denormalized `rsvp_count` on the `events` table in sync. Increments on confirmed RSVP, decrements on cancellation.

```sql
UPDATE events
SET rsvp_count = (
  SELECT COUNT(*) FROM rsvps
  WHERE event_id = NEW.event_id AND status = 'confirmed'
)
WHERE id = NEW.event_id;
```

---

### 5. `log_event_actions` *(recommended)*
**Fires on:** `AFTER INSERT OR UPDATE` on `events`
**Purpose:** Automatically writes an entry to `event_log` when an event is created, updated, flagged, soft deleted, or restored. Reduces the risk of the application forgetting to log an action manually.

---

## Business Rules & Edge Cases

### Immutable Fields
- `event_type`, `admission_type`, and `admission_fee` cannot be changed after creation — enforced by trigger and API validation
- `max_capacity` can only be increased, never decreased — enforced by trigger

### Conditional Location Validation
- `virtual` events require `meeting_link`
- `in_person` events require `address_line1`, `city`, `province`, `postal_code`
- `hybrid` events require all of the above
- Enforced via Spring Boot Bean Validation (`@Valid`) on the request DTO

### RSVP Rules
- RSVPs close when `start_time` passes — enforced at API level by checking `events.start_time` before allowing RSVP creation
- RSVPs cannot be created when `rsvp_count >= max_capacity`
- A user cannot RSVP twice for the same event — enforced by `UNIQUE(event_id, user_id)`
- A cancelled RSVP cannot be re-confirmed — enforced at API level
- Organizers can RSVP for their own events

### Event Archiving
- Events remain visible on the main listing until `end_time` passes
- RSVPs close when `start_time` passes, even while the event is still visible
- Flagged events whose `start_time` passes are transitioned to `archived` by the CRON job

### Soft Delete & Restoration
- Only the organizer can soft delete their own event
- Moderators and admins cannot delete events — they can only flag them
- Soft deleted events are restorable by the organizer within 7 days
- On restoration: if `end_time` has not passed → status returns to `active`; if `end_time` has passed → status returns to `archived`
- After 7 days, the CRON job permanently purges soft deleted events

### Flagging
- Only moderators and admins can flag events
- A flag reason is mandatory
- Flagged events are hidden from the main listing but visible to the organizer on their dashboard
- A flagged event transitions to `archived` automatically when its `start_time` passes
- There is currently no unflagging mechanism — this is reserved for the dispute system stretch goal

### Event Categories
- An event can have a maximum of 3 categories
- Categories are predefined and managed by the admin only
- Enforced at API level before insert into `event_categories`

### Audit Log
- All significant event lifecycle actions are recorded in `event_log`
- Log entries are never updated or deleted
- `event_log` entries are preserved even after an event is purged

---

## RLS Policies

> **Note:** Since all database access is mediated through the Spring Boot API, Row Level Security serves as a secondary defence-in-depth layer rather than the primary access control mechanism. Primary enforcement is handled by Spring Security and service layer logic.

### `profiles`
| Policy | Applies To | Rule |
|---|---|---|
| Users can read their own profile | `SELECT` | `auth.uid() = id` |
| Users can update their own profile | `UPDATE` | `auth.uid() = id` |
| Admin can read all profiles | `SELECT` | `role = 'admin'` |
| Admin can update any profile | `UPDATE` | `role = 'admin'` |

### `events`
| Policy | Applies To | Rule |
|---|---|---|
| Anyone can read active events | `SELECT` | `status = 'active'` |
| Organizer can read their own events regardless of status | `SELECT` | `auth.uid() = organizer_id` |
| Organizer can update their own events | `UPDATE` | `auth.uid() = organizer_id` |
| Organizer can soft delete their own events | `UPDATE` | `auth.uid() = organizer_id` |
| Moderator/admin can flag any event | `UPDATE` | `role IN ('moderator', 'admin')` |

### `rsvps`
| Policy | Applies To | Rule |
|---|---|---|
| Users can read their own RSVPs | `SELECT` | `auth.uid() = user_id` |
| Users can create RSVPs for themselves | `INSERT` | `auth.uid() = user_id` |
| Users can cancel their own RSVPs | `UPDATE` | `auth.uid() = user_id` |

### `event_log`
| Policy | Applies To | Rule |
|---|---|---|
| Admin can read all log entries | `SELECT` | `role = 'admin'` |
| No inserts, updates or deletes permitted via client | `ALL` | Deny all direct client access — writes via API only |

### `categories`
| Policy | Applies To | Rule |
|---|---|---|
| Anyone can read categories | `SELECT` | `true` |
| Only admin can insert, update or delete | `ALL` | `role = 'admin'` |

---

## Stretch Goals

The following features have been identified but are out of scope for the current MVP. They are documented here to ensure the schema is designed with future extensibility in mind.

---

### Priority Stretch Goal — Admin Dashboard

The admin dashboard is the highest priority stretch goal and is designed to be built on top of the existing schema with no structural changes required.

**Overview:** A dedicated, protected frontend route accessible only to users with the `admin` role. The dashboard provides full platform visibility and user management capabilities.

**Frontend route:** `/admin` — protected by Next.js `middleware.ts`, redirects any non-admin user back to the main dashboard.

**Features and their API requirements:**

| Feature | Description | Endpoint Needed |
|---|---|---|
| **User list** | Paginated table of all registered users showing name, email, role, and join date | `GET /api/admin/users` |
| **Edit user details** | Modify a user's full name or email via a modal form | `PUT /api/admin/users/{id}` |
| **Promote to moderator** | Elevate a `user` to `moderator` role | `PATCH /api/admin/users/{id}/role` |
| **Demote to user** | Revert a `moderator` back to `user` role | `PATCH /api/admin/users/{id}/role` |
| **View all events** | Full event list regardless of status — including flagged, archived and soft deleted | `GET /api/admin/events` |
| **View event log** | Audit trail for any specific event showing all lifecycle actions | `GET /api/admin/events/{id}/log` |
| **Manage categories** | Create, rename or delete event categories | `POST/PUT/DELETE /api/admin/categories` |

**Spring Security considerations:** All `/api/admin/**` endpoints should be restricted to the `admin` role via `@PreAuthorize("hasRole('ADMIN')")`. This ensures that even if the frontend route protection is bypassed, the API layer rejects unauthorized requests.

**Schema impact:** No new tables required. The admin dashboard reads from and writes to existing tables (`profiles`, `events`, `event_log`, `categories`) through dedicated admin-scoped endpoints that bypass the standard user-level RLS and service restrictions.

---

### Other Stretch Goals

| Feature | Notes |
|---|---|
| **Personalized username / handle** | Add `username VARCHAR(50) UNIQUE` to `profiles`. |
| **"Near me" filtering** | Add `latitude` and `longitude` columns to `events` and `profiles`. Requires PostGIS extension and a geocoding API integration. |
| **Email notifications** | Notify organizers when their event is flagged. Requires an email service like Resend and a notifications queue. |
| **Dispute system** | Allow organizers to dispute a flag. Requires a `disputes` table with appeal reason, resolution status, and admin response. Plugs into `event_log`. |
| **Three-tier RBAC** | Fully implemented moderator tools including event management and a moderation queue. |
| **Recurring events** | Complex schema addition — would require an `event_recurrences` table and significant changes to the archiving and RSVP logic. |
| **Comments & Q&A** | Requires a `comments` table with its own moderation and soft delete lifecycle. |
| **Waitlist** | Add a `waitlist` table. Requires notification logic to promote users when capacity increases or an RSVP is cancelled. |
| **Search** | Full text search across event titles and descriptions. PostgreSQL's `tsvector` and `tsquery` or a dedicated search service like Typesense. |
| **Re-RSVP after cancellation** | Currently a cancelled RSVP cannot be re-confirmed. Revisit the unique constraint logic to support this if needed. |
