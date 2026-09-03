-- Wayfare: trip budget tracker schema

create extension if not exists "pgcrypto";

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  home_currency text not null default 'USD',
  created_at timestamptz not null default now()
);

create table public.trips (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  destination text check (char_length(destination) <= 120),
  start_date date,
  end_date date,
  budget numeric(12,2) not null default 0 check (budget >= 0),
  currency text not null default 'USD' check (char_length(currency) = 3),
  accent text not null default 'clay',
  created_at timestamptz not null default now(),
  constraint trips_date_order check (end_date is null or start_date is null or end_date >= start_date)
);

create index trips_user_id_created_at_idx on public.trips (user_id, created_at desc);

create table public.expenses (
  id uuid primary key default gen_random_uuid(),
  trip_id uuid not null references public.trips(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 120),
  amount numeric(12,2) not null check (amount > 0),
  category text not null default 'other'
    check (category in ('flights','stays','food','activities','transport','shopping','other')),
  spent_on date not null default current_date,
  note text check (char_length(note) <= 500),
  created_at timestamptz not null default now()
);

create index expenses_trip_id_spent_on_idx on public.expenses (trip_id, spent_on desc, created_at desc);
create index expenses_user_id_idx on public.expenses (user_id);

alter table public.profiles enable row level security;
alter table public.trips    enable row level security;
alter table public.expenses enable row level security;

create policy "profiles: read own"   on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy "profiles: insert own" on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy "profiles: update own" on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

create policy "trips: read own"   on public.trips for select to authenticated using ((select auth.uid()) = user_id);
create policy "trips: insert own" on public.trips for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "trips: update own" on public.trips for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "trips: delete own" on public.trips for delete to authenticated using ((select auth.uid()) = user_id);

create policy "expenses: read own"   on public.expenses for select to authenticated using ((select auth.uid()) = user_id);
create policy "expenses: insert own" on public.expenses for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "expenses: update own" on public.expenses for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "expenses: delete own" on public.expenses for delete to authenticated using ((select auth.uid()) = user_id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)))
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
