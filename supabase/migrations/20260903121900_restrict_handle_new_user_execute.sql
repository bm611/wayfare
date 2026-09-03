-- handle_new_user is only ever meant to run from the auth.users trigger.
-- Stop PostgREST exposing it as /rest/v1/rpc/handle_new_user.
revoke execute on function public.handle_new_user() from public;
revoke execute on function public.handle_new_user() from anon;
revoke execute on function public.handle_new_user() from authenticated;
