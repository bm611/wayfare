-- Account deletion.
--
-- Google Play requires an app that lets people create an account to let them
-- delete it — from inside the app, and from a page anyone can reach without
-- signing in. Both paths end up here.
--
-- Almost everything is already handled by the foreign keys, which cascade from
-- auth.users:
--
--   auth.users -> profiles       -> (cascade)
--              -> trips          -> expenses, trip_members (cascade)
--              -> expenses       -> (cascade)
--              -> trip_members   -> (cascade)
--
-- Two things the cascade cannot reach:
--
--   1. Cover images. They are files in the `trip-covers` bucket named
--      `<trip_id>/<file>`, with no foreign key to `trips`. They cannot be
--      removed here: Supabase guards storage.objects with a statement-level
--      trigger (storage.protect_delete) that rejects any direct DELETE, even one
--      matching no rows. So the clients delete the covers of the trips the user
--      owns through the Storage API before calling this function.
--   2. The user row itself, which lives in the `auth` schema and is only
--      reachable from a SECURITY DEFINER function.
--
-- Deleting an account deletes the trips it owns, and a shared trip goes for
-- every member on it. Both clients say so, with counts, before confirming.
--
-- Access tokens issued before deletion stay valid until they expire, but they
-- can no longer read anything: every row they would match is gone. Refresh
-- tokens die with the cascade through auth.sessions.

create or replace function public.delete_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'Not signed in.' using errcode = '28000';
  end if;

  delete from auth.users where id = v_uid;
end;
$$;

revoke execute on function public.delete_account() from public, anon;
grant  execute on function public.delete_account() to authenticated;
