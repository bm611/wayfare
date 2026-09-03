# Wayfare

A mobile-first budget tracker for trips. Create a trip, set what you're willing to
spend, then log flights, stays, food, activities, transport and shopping against it.

Built with React + Vite + Tailwind v4, backed by Supabase auth and Postgres.

---

## Design

The interface is built around a single physical metaphor: a **boarding pass**.

| Token | Value | Use |
| --- | --- | --- |
| Paper | `#f7f2e9` | Page ground |
| Card | `#fffcf6` | Ticket face |
| Ink | `#1a1714` | Primary type (never pure black) |
| Clay | `#b5543c` | The only accent |
| Line | `#e3d9ca` | Hairlines, notch rims |

- **Type** — `Outfit` for display, `Geist` for UI, `Geist Mono` for every number.
  Money is always tabular so digits never jitter mid-animation.
- **Ticket** (`src/components/Ticket.tsx`) — the shared shell. Corners are rounded
  by hand rather than clipped so the punched notches can hang over the card edges.
  Includes a deterministic `Barcode` (same trip, same bars, forever) and a rotated
  `Postmark` carrying an airport-style three letter code derived from the destination.
- **BudgetMeter** — a 30-segment boarding strip. Segments reveal through a pure CSS
  delay cascade, so a long list of trips costs nothing at runtime.
- **Category colours** live in `src/lib/categories.ts` — seven desaturated hues that
  sit on paper without shouting over the clay accent.

Loading, empty and error states are all implemented (`src/components/States.tsx`);
skeletons match the real ticket silhouette so the list never jumps on load.

---

## Running it

```bash
npm install
cp .env.example .env.local   # fill in your project URL + publishable key
npm run dev
```

`.env.local` is already populated for the provisioned project and is gitignored
via `*.local`.

| Script | Does |
| --- | --- |
| `npm run dev` | Vite dev server |
| `npm run build` | Typecheck + production bundle |
| `npm run preview` | Serve the built bundle |

---

## Currency

The ledger is denominated in **EUR** end to end — there is no trip-level currency
picker, because the only currency question that matters is what you actually paid
in. An expense can be entered in any of 30 currencies via the *Paid in* dropdown — the sheet shows the converted figure
and the rate live as you type, then stores:

| Column | Holds |
| --- | --- |
| `amount` | the figure in the trip's currency — every total reads only this |
| `original_amount` / `original_currency` | what was actually typed, when it differed |
| `fx_rate` | the rate applied, so an old entry stays auditable |

Rates come from [Frankfurter](https://frankfurter.dev) (European Central Bank
reference rates, no API key) and are cached in `localStorage` for twelve hours.
`src/lib/fx.ts` carries a bundled snapshot so the app still records costs offline
rather than refusing the entry — the sheet labels those as `offline rate`.

## Sharing a trip

Every trip gets an eight character invite code (no vowels, no `0/O/1/I/L`, so it
survives being read down a phone). The organiser opens the share sheet, copies the
code or the `/join/<code>` link, and anyone signed in can redeem it. Members log
their own spending against the same budget; the ledger shows who paid once more
than one traveller is on a trip.

- Only the trip owner can rename, delete, or remove members.
- Members can edit and delete their own entries, and can leave the trip.
- Redeeming happens through `public.join_trip(code)` — a `SECURITY DEFINER` RPC,
  which is deliberate: RLS hides trips you are not yet a member of, so this is the
  only way in. `EXECUTE` is revoked from `anon`.

## Data model

Migrations live in `supabase/migrations/` and are already applied to the project.

```
profiles       id -> auth.users, display_name, home_currency
trips          user_id, name, destination, start_date, end_date, budget,
               currency, share_code
trip_members   trip_id, user_id, role (owner | member), joined_at
expenses       trip_id, user_id, title, amount, original_amount,
               original_currency, fx_rate, category, spent_on, note
```

- Row level security is **on** for all four tables, scoped to trip *membership*
  rather than ownership. The helpers that do the scoping live in a `private`
  schema that PostgREST does not expose, and are `SECURITY DEFINER` so a policy
  on `trips` can consult `trip_members` without recursing into itself.
- `category` is constrained to the seven keys in `src/lib/types.ts`.
- `amount` must be positive; `budget` must be non-negative; `end_date` cannot land
  before `start_date`. The UI validates the same rules before it ever hits the wire.
- A trigger on `auth.users` creates the matching `profiles` row on signup. Its
  `EXECUTE` grant is revoked from `anon` and `authenticated` so PostgREST does not
  expose it as an RPC endpoint.

To apply the schema to a different project:

```bash
supabase link --project-ref <your-ref>
supabase db push
```

---

## Auth

Email + password via Supabase. New projects require email confirmation by default,
so the first sign-up lands on a "check your inbox" state. To skip that while
developing, turn off **Confirm email** under Authentication → Sign In / Providers →
Email in the Supabase dashboard.
