-- Destination covers: a generated establishing shot per trip, rendered behind
-- the boarding pass on the trips list.
--
-- Generation happens in a Netlify background function because the image model
-- takes ~15s, well past a synchronous function's budget. That makes the status
-- column the contract between the two halves: the client kicks a job off and
-- polls this row rather than holding a request open.

alter table public.trips
  add column cover_path text,
  add column cover_subject text,
  add column cover_status text not null default 'idle'
    check (cover_status in ('idle', 'pending', 'ready', 'failed')),
  add column cover_claimed_at timestamptz;

-- Existing trips have never had a cover; leave them 'idle' so the client picks
-- them up on the next load.

-- The subject is what the picture is *of*. Renaming a trip that has a real
-- destination must not throw the artwork away, so only a change to the string
-- we actually drew from invalidates it.
create or replace function public.reset_trip_cover()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
  old_subject text := coalesce(nullif(btrim(old.destination), ''), old.name);
  new_subject text := coalesce(nullif(btrim(new.destination), ''), new.name);
begin
  if new_subject is distinct from old_subject then
    new.cover_path := null;
    new.cover_subject := null;
    new.cover_status := 'idle';
    new.cover_claimed_at := null;
  end if;
  return new;
end;
$$;

create trigger trips_reset_cover
  before update on public.trips
  for each row execute function public.reset_trip_cover();

-- Claiming is what stops two phones (or a reload mid-flight) from paying for
-- the same picture twice. Returns false when someone else already holds it.
create or replace function public.claim_trip_cover(p_trip uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_claimed boolean;
begin
  if not private.is_trip_member(p_trip) then
    raise exception 'That trip is not yours.' using errcode = '42501';
  end if;

  update public.trips
     set cover_status = 'pending',
         cover_claimed_at = now()
   where id = p_trip
     and (cover_status = 'idle'
          -- A holder that never reported back is presumed dead.
          or (cover_status = 'pending' and cover_claimed_at < now() - interval '3 minutes')
          -- A failure is worth one more try, but not on every page load.
          or (cover_status = 'failed' and cover_claimed_at < now() - interval '1 hour'))
  returning true into v_claimed;

  return coalesce(v_claimed, false);
end;
$$;

-- Members who do not own the trip still need to finish a job they started, and
-- the trips update policy is owner-only, so the write goes through here.
create or replace function public.set_trip_cover(p_trip uuid, p_path text, p_subject text)
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
     set cover_path       = coalesce(p_path, cover_path),
         cover_subject    = coalesce(p_subject, cover_subject),
         cover_status     = case when p_path is null then 'failed' else 'ready' end,
         -- Kept on failure so the retry window above has something to measure.
         cover_claimed_at = now()
   where id = p_trip;
end;
$$;

revoke execute on function public.claim_trip_cover(uuid) from public, anon;
grant  execute on function public.claim_trip_cover(uuid) to authenticated;

revoke execute on function public.set_trip_cover(uuid, text, text) from public, anon;
grant  execute on function public.set_trip_cover(uuid, text, text) to authenticated;

revoke execute on function public.reset_trip_cover() from public, anon, authenticated;

-- Public bucket: the images are generated landscapes keyed by an unguessable
-- trip uuid, and serving them straight from the CDN keeps the card render to a
-- plain <img> with no signing round trip.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('trip-covers', 'trip-covers', true, 4194304, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Objects live at <trip_id>/<name>, so the first path segment is the authority
-- on who may write.
create policy "covers: read" on storage.objects
  for select to public
  using (bucket_id = 'trip-covers');

create policy "covers: write as member" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'trip-covers'
    and private.is_trip_member(((storage.foldername(name))[1])::uuid)
  );

create policy "covers: replace as member" on storage.objects
  for update to authenticated
  using (
    bucket_id = 'trip-covers'
    and private.is_trip_member(((storage.foldername(name))[1])::uuid)
  )
  with check (
    bucket_id = 'trip-covers'
    and private.is_trip_member(((storage.foldername(name))[1])::uuid)
  );

create policy "covers: delete as member" on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'trip-covers'
    and private.is_trip_member(((storage.foldername(name))[1])::uuid)
  );
