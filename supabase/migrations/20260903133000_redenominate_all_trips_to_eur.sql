-- Wayfare is a euro-denominated ledger: the only currency choice is what an
-- expense was paid in. Re-denominate any trip still carrying a legacy currency
-- from before that decision.
--
-- Entries that were originally typed in EUR are restored to their exact
-- original figure. Anything else converts at an ECB snapshot (2026-09-02).

do $$
declare
  -- Units of each currency per 1 EUR.
  rates jsonb := '{
    "EUR":1,"USD":1.1578,"GBP":0.8587,"CHF":0.9424,"SEK":11.1575,"NOK":10.809,
    "DKK":7.475,"PLN":4.3265,"CZK":24.191,"HUF":368.2,"RON":5.2558,"ISK":140.6,
    "TRY":55.9145,"JPY":184.78,"CNY":7.7822,"HKD":9.0801,"SGD":1.4741,
    "KRW":1577.57,"INR":109.962,"IDR":20511.18,"MYR":4.6839,"PHP":72.415,
    "THB":38.468,"ILS":3.5064,"AUD":1.6199,"NZD":1.9868,"CAD":1.6122,
    "BRL":5.9635,"MXN":19.6835,"ZAR":18.6341
  }'::jsonb;
  t record;
  r numeric;
begin
  for t in select id, currency from public.trips where currency <> 'EUR' loop
    r := (rates ->> t.currency)::numeric;
    if r is null then
      raise exception 'No EUR reference rate for currency %', t.currency;
    end if;

    -- Order matters: convert the non-EUR originals BEFORE clearing the EUR
    -- ones, or the cleared rows get picked up and converted a second time.
    update public.expenses
       set amount            = round(amount / r, 2),
           original_amount   = coalesce(original_amount, amount),
           original_currency = coalesce(original_currency, t.currency),
           fx_rate           = case
                                 when fx_rate is null then round(1 / r, 8)
                                 else round(fx_rate / r, 8)
                               end
     where trip_id = t.id
       and (original_currency is null or original_currency <> 'EUR');

    -- Typed in euros originally, so the euro figure is exact, not derived.
    update public.expenses
       set amount            = original_amount,
           original_amount   = null,
           original_currency = null,
           fx_rate           = null
     where trip_id = t.id
       and original_currency = 'EUR';

    update public.trips
       set currency = 'EUR',
           budget   = round(budget / r, 2)
     where id = t.id;
  end loop;
end $$;
