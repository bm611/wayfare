-- Execute with the caller's table permissions and RLS, including expense visibility.
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
