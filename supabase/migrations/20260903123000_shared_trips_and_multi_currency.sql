-- Shared trips + multi-currency entries.
-- Mirrors the migration applied to the hosted project.

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

alter table public.trips alter column currency set default 'EUR';

alter table public.expenses
  add column original_amount numeric(12,2) check (original_amount > 0),
  add column original_currency text check (char_length(original_currency) = 3),
  add column fx_rate numeric(18,8) check (fx_rate > 0);

create table public.trip_members (
  trip_id uuid not null references public.trips(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  primary key (trip_id, user_id)
);

create index trip_members_user_id_idx on public.trip_members (user_id);

alter table public.trips add column share_code text unique;

create or replace function private.new_share_code()
returns text language plpgsql volatile set search_path = '' as $$
declare
  alphabet text := 'ABCDFGHJKMNPQRSTVWXYZ23456789';
  candidate text;
begin
  loop
    candidate := '';
    for _i in 1..8 loop
      candidate := candidate || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.trips t where t.share_code = candidate);
  end loop;
  return candidate;
end;
$$;

create or replace function public.handle_new_trip()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.share_code is null then
    new.share_code := private.new_share_code();
  end if;
  return new;
end;
$$;

create or replace function public.enroll_trip_owner()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.trip_members (trip_id, user_id, role)
  values (new.id, new.user_id, 'owner')
  on conflict do nothing;
  return new;
end;
$$;

create trigger trips_assign_share_code
  before insert on public.trips
  for each row execute function public.handle_new_trip();

create trigger trips_enroll_owner
  after insert on public.trips
  for each row execute function public.enroll_trip_owner();

update public.trips set share_code = private.new_share_code() where share_code is null;

insert into public.trip_members (trip_id, user_id, role)
select id, user_id, 'owner' from public.trips
on conflict do nothing;

-- SECURITY DEFINER so these read past RLS without recursing through the very
-- policies that call them.
create or replace function private.is_trip_member(p_trip uuid)
returns boolean language sql security definer stable set search_path = '' as $$
  select exists (
    select 1 from public.trip_members m
    where m.trip_id = p_trip and m.user_id = (select auth.uid())
  );
$$;

create or replace function private.owns_trip(p_trip uuid)
returns boolean language sql security definer stable set search_path = '' as $$
  select exists (
    select 1 from public.trips t
    where t.id = p_trip and t.user_id = (select auth.uid())
  );
$$;

create or replace function private.shares_trip_with(p_user uuid)
returns boolean language sql security definer stable set search_path = '' as $$
  select exists (
    select 1
    from public.trip_members mine
    join public.trip_members theirs on theirs.trip_id = mine.trip_id
    where mine.user_id = (select auth.uid()) and theirs.user_id = p_user
  );
$$;

grant execute on function private.is_trip_member(uuid) to authenticated;
grant execute on function private.owns_trip(uuid) to authenticated;
grant execute on function private.shares_trip_with(uuid) to authenticated;

alter table public.trip_members enable row level security;

drop policy "trips: read own"   on public.trips;
drop policy "trips: update own" on public.trips;
drop policy "trips: delete own" on public.trips;

create policy "trips: read as member" on public.trips
  for select to authenticated using (private.is_trip_member(id));
create policy "trips: update as owner" on public.trips
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "trips: delete as owner" on public.trips
  for delete to authenticated using ((select auth.uid()) = user_id);

create policy "members: read fellow members" on public.trip_members
  for select to authenticated using (private.is_trip_member(trip_id));
create policy "members: leave or be removed" on public.trip_members
  for delete to authenticated
  using ((select auth.uid()) = user_id or private.owns_trip(trip_id));

drop policy "expenses: read own"   on public.expenses;
drop policy "expenses: insert own" on public.expenses;
drop policy "expenses: update own" on public.expenses;
drop policy "expenses: delete own" on public.expenses;

create policy "expenses: read trip" on public.expenses
  for select to authenticated using (private.is_trip_member(trip_id));
create policy "expenses: insert as member" on public.expenses
  for insert to authenticated
  with check ((select auth.uid()) = user_id and private.is_trip_member(trip_id));
create policy "expenses: update own or owned trip" on public.expenses
  for update to authenticated
  using ((select auth.uid()) = user_id or private.owns_trip(trip_id))
  with check (private.is_trip_member(trip_id));
create policy "expenses: delete own or owned trip" on public.expenses
  for delete to authenticated
  using ((select auth.uid()) = user_id or private.owns_trip(trip_id));

drop policy "profiles: read own" on public.profiles;
create policy "profiles: read own or fellow traveller" on public.profiles
  for select to authenticated
  using ((select auth.uid()) = id or private.shares_trip_with(id));

-- Deliberately callable by signed-in users: RLS hides trips you are not yet a
-- member of, so this RPC is the only way in.
create or replace function public.join_trip(p_code text)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  v_trip uuid;
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'You must be signed in to join a trip.' using errcode = '42501';
  end if;

  select id into v_trip from public.trips where share_code = upper(trim(p_code));

  if v_trip is null then
    raise exception 'That invite code does not match any trip.' using errcode = 'no_data_found';
  end if;

  insert into public.trip_members (trip_id, user_id, role)
  values (v_trip, v_user, 'member')
  on conflict do nothing;

  return v_trip;
end;
$$;

revoke execute on function public.join_trip(text) from public;
revoke execute on function public.join_trip(text) from anon;
grant execute on function public.join_trip(text) to authenticated;

revoke execute on function public.handle_new_trip() from public, anon, authenticated;
revoke execute on function public.enroll_trip_owner() from public, anon, authenticated;
