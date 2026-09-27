-- Trip covers become passport stamps: a handful of line-art SVG paths drawn by
-- a text model, instead of a generated JPEG in the `trip-covers` bucket.
--
-- The drawing is small enough to live on the row, so it does. `cover_art` holds
--   { "v": 1, "label": "LISBON", "paths": ["M6 52 L30 13 ...", ...], "fallback": false }
-- where every path has already been validated and normalised server side to
-- absolute M/L/C/Q/Z on a 0 0 64 64 grid. `fallback` is true when the model's
-- own drawing was rejected and a built-in generic icon was stored instead.
--
-- The claim / set / status handshake is unchanged; only what `set_trip_cover`
-- records is.

-- trip_summaries is `select t.*`, which Postgres expands at creation time, so
-- it pins every column of trips. Drop it while the columns change.
drop view public.trip_summaries;

alter table public.trips
  add column cover_art jsonb
    check (cover_art is null or (jsonb_typeof(cover_art) = 'object' and jsonb_typeof(cover_art -> 'paths') = 'array')),
  drop column cover_path;

create view public.trip_summaries with (security_invoker = true) as
select t.*, coalesce(s.spent, 0) as spent, coalesce(s.entries, 0) as entries
from public.trips t
left join (
  select trip_id, sum(amount) as spent, count(*) as entries
  from public.expenses
  group by trip_id
) s on s.trip_id = t.id;

revoke all on public.trip_summaries from anon;
grant select on public.trip_summaries to authenticated;

-- Every existing trip was drawn as a photograph; send them all back to 'idle'
-- so the clients ask for a stamp on the next load.
update public.trips
   set cover_subject = null,
       cover_status = 'idle',
       cover_claimed_at = null;

-- Same staleness rule as before (destination or season changed), now clearing
-- the stamp rather than an image path.
create or replace function public.reset_trip_cover()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  old_subject text := coalesce(nullif(btrim(old.destination), ''), old.name);
  new_subject text := coalesce(nullif(btrim(new.destination), ''), new.name);
  old_season  text := public.trip_cover_season(old.name);
  new_season  text := public.trip_cover_season(new.name);
begin
  if new_subject is distinct from old_subject
     or new_season is distinct from old_season then
    new.cover_art := null;
    new.cover_subject := null;
    new.cover_status := 'idle';
    new.cover_claimed_at := null;
  end if;
  return new;
end;
$$;

revoke execute on function public.reset_trip_cover() from public, anon, authenticated;

drop function public.set_trip_cover(uuid, text, text);

-- A null drawing records a failure, exactly as a null path used to.
create function public.set_trip_cover(p_trip uuid, p_art jsonb, p_subject text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.is_trip_member(p_trip) then
    raise exception 'That trip is not yours.' using errcode = '42501';
  end if;

  update public.trips
     set cover_art        = coalesce(p_art, cover_art),
         cover_subject    = coalesce(p_subject, cover_subject),
         cover_status     = case when p_art is null then 'failed' else 'ready' end,
         -- Kept on failure so the claim's retry window has something to measure.
         cover_claimed_at = now()
   where id = p_trip;
end;
$$;

revoke execute on function public.set_trip_cover(uuid, jsonb, text) from public, anon;
grant  execute on function public.set_trip_cover(uuid, jsonb, text) to authenticated;

-- The bucket goes. Supabase guards storage tables with storage.protect_delete,
-- which only lets a direct DELETE through when this setting is on; `local`
-- keeps it to this migration's transaction.
drop policy if exists "covers: read" on storage.objects;
drop policy if exists "covers: write as member" on storage.objects;
drop policy if exists "covers: replace as member" on storage.objects;
drop policy if exists "covers: delete as member" on storage.objects;

set local storage.allow_delete_query = 'true';
delete from storage.objects where bucket_id = 'trip-covers';
delete from storage.buckets where id = 'trip-covers';
