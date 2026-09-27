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

`20260927_trip_cover_stamps` replaces the generated image with a passport stamp:
a few lines of SVG path data drawn by a text model, validated and normalised to
absolute M/L/C/Q/Z on a 64×64 grid by the background function, and stored in
`trips.cover_art`. It drops `cover_path` and the `trip-covers` bucket, and sends
every trip back to `idle` so the clients ask for a stamp on the next load. The
claim / set / status handshake above is unchanged.
