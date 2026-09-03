Applied in order. `20260903_redenominate_all_trips_to_eur` is a one-time data
normalisation: it re-denominates any trip created before Wayfare became a
euro-only ledger. Entries originally typed in EUR are restored to their exact
figure; anything else converts at an ECB snapshot taken 2026-09-02.

`20260903_trip_covers` adds the generated destination shot behind each boarding
pass. `cover_status` is the handshake between the browser and the Netlify
background function that draws it: the browser asks, `claim_trip_cover` hands
out exactly one job at a time, and `set_trip_cover` records the result. Both are
`SECURITY DEFINER` on purpose — a member who does not own the trip still has to
be able to finish a job they started, and `trips` is owner-only for updates.

