-- Fixes trip creation, which had been failing with 42501 since the shared
-- trips migration.
--
-- Creating a trip round-trips through `insert ... returning` (the client calls
-- .insert().select().single()), and RETURNING is checked against the SELECT
-- policy, not just the INSERT one. `trips: read as member` asked
-- private.is_trip_member(id), but the membership row is written by an AFTER
-- INSERT trigger, which has not fired at the moment RETURNING is evaluated. So
-- the owner failed the read on the row they were in the middle of creating and
-- the whole statement was rejected.
--
-- The previous policy, `trips: read own`, compared auth.uid() to user_id and
-- was satisfied immediately, which is why this only broke once trips became
-- shareable.
--
-- Owning a trip is reason enough to read it, with or without a membership row.

drop policy "trips: read as member" on public.trips;

create policy "trips: read as member" on public.trips
  for select to authenticated
  using ((select auth.uid()) = user_id or private.is_trip_member(id));
