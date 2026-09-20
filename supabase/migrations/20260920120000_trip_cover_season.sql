-- Trip covers now read both form fields: the destination still decides the
-- place, and the trip name contributes the season ("finland winter '26" bound
-- for "Finland" draws Finland under snow).
--
-- That widens what counts as a stale cover. The original trigger only redrew
-- when coalesce(destination, name) changed, so with a destination set, adding
-- "winter" to the name would have left the old summer artwork in place. It
-- still must not redraw for an ordinary rename, so the season is compared on
-- its own rather than the whole name.

-- Kept deliberately small, and mirrored by SEASONS in
-- netlify/functions/trip-cover-background.mts. The two lists have to agree:
-- a word here that is missing there redraws a cover that comes back identical,
-- and a word there that is missing here quietly keeps the wrong season.
create or replace function public.trip_cover_season(p_name text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_name ~* '\m(winter|snow|snowy|ski|skiing|christmas|xmas|new year|aurora|northern lights)\M' then 'winter'
    when p_name ~* '\m(spring|blossom|blossoms|easter)\M' then 'spring'
    when p_name ~* '\m(summer|beach|seaside|sun|sunny|heatwave)\M' then 'summer'
    when p_name ~* '\m(autumn|fall|foliage|harvest)\M' then 'autumn'
  end;
$$;

revoke execute on function public.trip_cover_season(text) from public, anon;
grant  execute on function public.trip_cover_season(text) to authenticated;

-- The subject is what the picture is *of*. Renaming a trip that has a real
-- destination must not throw the artwork away, so only a change to the string
-- we actually drew from -- or to the season we drew it in -- invalidates it.
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
    new.cover_path := null;
    new.cover_subject := null;
    new.cover_status := 'idle';
    new.cover_claimed_at := null;
  end if;
  return new;
end;
$$;

revoke execute on function public.reset_trip_cover() from public, anon, authenticated;
