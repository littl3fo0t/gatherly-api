begin;

-- Allow hard-deleting events while retaining audit rows in event_log.
-- (We intentionally keep event_log rows even after an event is purged.)
alter table public.event_log
  drop constraint if exists event_log_event_id_fkey;

-- Purge events that have been soft-deleted past the 7-day grace window.
-- Returns the number of event rows removed.
create or replace function public.purge_soft_deleted_events()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_deleted_count integer;
begin
  delete from public.events e
  where e.status = 'soft_deleted'
    and e.deleted_at is not null
    and e.deleted_at < (now() - interval '7 days');

  get diagnostics v_deleted_count = row_count;
  return v_deleted_count;
end;
$$;

commit;

