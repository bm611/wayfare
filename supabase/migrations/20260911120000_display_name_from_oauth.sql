-- A Google sign-up never carries `display_name` — Google puts the person's name in
-- `full_name`/`name` — so the old fallback named them after the local part of their
-- address, and a trip would list "broxdeez" instead of a real name. Prefer whatever
-- name the identity provider sent; the email fallback still covers the rest.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name)
  values (
    new.id,
    coalesce(
      nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
      nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
      nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
      split_part(new.email, '@', 1)
    )
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

-- `create or replace` resets grants to the default, so re-apply the lockdown from
-- 20260903121900: this is trigger-only and must not surface as a PostgREST RPC.
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.handle_new_user() from anon;
revoke execute on function public.handle_new_user() from authenticated;
