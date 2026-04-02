# CRON — Purge soft-deleted events (7-day grace)

This project uses **soft delete** for events: `DELETE /api/events/{id}` sets `events.status = 'soft_deleted'` and records `events.deleted_at`.

After a **7-day grace period**, a scheduled job purges (hard-deletes) those rows.

## Database behavior (cascades)

Verified in Supabase (live schema):

- `public.rsvps.event_id -> public.events.id` is `ON DELETE CASCADE`
- `public.event_categories.event_id -> public.events.id` is `ON DELETE CASCADE`
- `public.event_log.event_id -> public.events.id` was `ON DELETE RESTRICT` (this **blocked** purging)

For MVP, we keep `event_log` rows even after purging an event, so we **drop the FK** from `event_log` to `events`.

## Migration (applied)

Migration file to commit:

- `supabase/migrations/20260402190000_drop_event_log_fk_add_purge_soft_deleted_events_fn.sql`

It does two things:

1. Drops `event_log_event_id_fkey` so deleting an event doesn’t fail due to `RESTRICT`.
2. Creates `public.purge_soft_deleted_events()` which deletes events where:
   - `status = 'soft_deleted'`
   - `deleted_at < (now() at time zone 'utc') - interval '7 days'`
   - returns the count of deleted events

## Scheduled execution

We deploy a Supabase **Edge Function** named `purge-soft-deleted-events` (JWT verification disabled) that calls:

- `rpc('purge_soft_deleted_events')`

Repo entrypoint:

- `supabase/functions/purge-soft-deleted-events/index.ts`

Configure the schedule in the **Supabase Dashboard** to invoke the Edge Function on your preferred cadence (e.g. daily at `03:00` UTC).

