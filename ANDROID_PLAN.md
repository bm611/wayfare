# Native Android implementation plan

## Direction

Build a Kotlin Android app with Jetpack Compose in `android/` in this repository. Reuse the existing Supabase database, Auth, Storage, RPCs, and Netlify cover-generation endpoint. Existing users should see the same trips and expenses on web and Android. The Android UI and client logic will be implemented in Kotlin; React components and hooks serve as behavior references.

Planning assumptions: Android only, phone-first with adaptive tablet layouts, existing feature parity before new product features, and an internal testing release before public distribution. Proposed minimum Android version: Android 8 (API 26), subject to intended device coverage. Choose compatible stable dependency versions and the required target SDK when implementation starts.

## What exists today

- React/Vite frontend with email/password signup, confirmation, sign-in, and password recovery.
- Trip creation, owner editing/deletion, date-based grouping, budget summaries, destination covers, and loading/error/empty states.
- Expense creation/editing/deletion, seven categories, notes, date grouping, search, and category/payer filters.
- EUR ledger totals with 30 payment currencies, stored original amounts and FX rates, a 12-hour rate cache, and a bundled fallback.
- Shared trip codes and `/join/<code>` links; `join_trip(p_code)` admits authenticated users through an RPC.
- Supabase membership policies, profiles, and owner/member controls.
- Server-side cover generation through `/.netlify/functions/trip-cover-background`, followed by polling for cover status.

Sources inspected: `README.md`, `src/lib/types.ts`, `src/lib/fx.ts`, `src/lib/format.ts`, trip/expense/member/cover hooks, and database migrations. This is a source-code review, not verification of the deployed database or service configuration.

Important boundaries:

- Cached FX rates do not provide offline expense saving; current saves call Supabase directly.
- Shared budgets track group spending, not debt settlement.
- The web UI makes other travellers' expenses read-only, but the checked-in SQL also permits trip owners to update/delete those expenses. Resolve this explicitly before implementing permission tests; proposed app behavior preserves the current UI, with any database tightening treated as a separate compatible migration.
- The repository is called Bugtrip while its README/branding uses Wayfare. Confirm the public app name and permanent package ID before signing/distribution.

## Technology and structure

| Concern | Proposed implementation |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 components with the existing custom visual theme |
| State | Screen ViewModels, immutable UI state, StateFlow, coroutines, lifecycle-aware collection |
| Navigation | Jetpack navigation for Compose, typed destinations, app-link entry points |
| Backend | Supabase Kotlin client behind small repositories; Ktor for FX and cover requests |
| Local preferences | DataStore for per-trip payment currency and non-sensitive preferences |
| Images | Coil for cover loading and caching |
| Offline milestone | Room for account-scoped cached data and an outbox; WorkManager for durable retries |
| Validation | JVM business-rule tests, Compose UI tests, staging Supabase integration tests |

Start with one Gradle app module, organized into `core/`, `data/`, and `feature/auth`, `feature/trips`, `feature/expenses`, and `feature/sharing`. Use straightforward constructor injection and a small application container. Split modules only when build time or ownership warrants it.

Use `BigDecimal` for monetary calculations and explicit decimal serialization compatible with Postgres numeric columns. Round EUR amounts consistently to two decimals at save time and preserve original amount/currency and the applied rate. Use `LocalDate` for trip/expense dates, keeping date-only values separate from timestamps. Capture cross-client fixtures for rounding, day boundaries, undated trips, and budget calculations before porting them.

## Ordered milestones

### 1. Foundation and end-to-end connection

- Add the Android Studio project, Gradle wrapper, environment configuration, theme, navigation, and build checks.
- Recreate the paper/clay palette, typography, ticket shell, and budget strip as reusable Compose components.
- Implement session restoration, email/password authentication, signup confirmation, recovery, and sign-out.
- Configure auth redirect allowlists and Android callbacks; preserve a pending invite through sign-in and process recreation.
- Use the Supabase publishable key plus user sessions. Keep privileged and AI provider keys on the server; review session storage and Android backup exclusions.
- Prove one vertical slice: sign in, load real staging trips, create an expense, and see it in the web app.

Exit: installable debug APK; authentication survives restart; recovery works from an email; both clients read the same saved expense.

### 2. Trips and budget overview

- Build grouped trip lists, create/edit forms, trip detail, owner deletion, and loading/error/empty states.
- Port current budget behavior: remaining amount, days left including today, zero daily availability when over budget, and inclusion of pre-trip bookings in average spending.
- Support native date pickers, keyboard/inset handling, Back navigation, unsaved-change confirmation, and state restoration.
- Display covers from existing Storage paths; call the configured absolute Netlify endpoint with the user's bearer token and use bounded, foreground-only polling.

Exit: trip lifecycle works on Android; edits appear on web; pending/failed covers never block trip use.

### 3. Expense ledger and currencies

- Build add/edit expense flows, category/date/payment-currency controls, optional notes, and deletion confirmation.
- Port ledger ordering, search, payer/category filters, spending breakdown, and per-trip remembered payment currency.
- Port live FX conversion and cached/fallback rates; visibly distinguish stale estimates and never recalculate historical saved entries.
- For this online milestone, preserve failed form input and show success only after server acknowledgement.

Exit: Android/web totals agree on shared fixtures, including conversion and rounding; failed requests do not lose input or falsely report success.

### 4. Sharing and native integration

- Add code redemption, member lists, removal/leave flows, and Android's share sheet.
- Configure verified HTTPS App Links for `/join/<code>` with the hosted `assetlinks.json` and signing certificate fingerprints; retain the existing web fallback.
- Test cold/warm app launches, logged-out users, repeated redemption, invalid codes, removed members, and deleted trips.
- Refresh on foreground and pull-to-refresh for the first release; add realtime subscriptions only if observed collaboration needs justify them.
- Verify owner/member/non-member permissions directly against staging, not only by hiding controls.

Exit: an owner can invite a second user, both can contribute, and unauthorized reads/writes fail at the backend.

### 5. Travel-friendly offline support

Recommended after the online beta and before positioning the app as usable without connectivity.

- Cache previously loaded trips, members, expenses, and rates in Room; show freshness and pending status.
- Initially support offline creation of expenses for existing cached trips. Keep trip/membership changes and edits/deletes of existing expenses online to bound conflict handling.
- Persist each new expense and outbox operation atomically, with a client-generated UUID. Reuse that UUID on retries and reconcile an uncertain response before resending to avoid duplicates.
- Retain the exact amount/rate captured at entry time. Distinguish pending totals from confirmed totals.
- Retry via WorkManager with backoff. Stop retrying terminal failures such as revoked membership or deleted trips; retain an actionable failed entry for the user.
- Scope caches and queued operations to the signed-in account; clear or isolate them on sign-out/account changes and prevent another account from sending them.
- On refresh, reconcile deleted remote records and revoked access; cached access cannot be revoked while the device remains offline.

Exit: an expense created in airplane mode survives process death and syncs exactly once after reconnecting; permission loss produces a recoverable failure.

### 6. Release readiness

- Test TalkBack, large fonts, touch targets, contrast, small phones, tablet/window resizing, rotation, process recreation, slow networks, expired sessions, and repeated save taps.
- Run unit tests for money/date/budget rules, Compose tests for critical forms, and staging integration tests for authentication and permissions.
- Add CI build/lint/test jobs and produce a signed Android App Bundle for an internal testing track.
- Before public release, verify current Play requirements, privacy policy, Data safety declarations, account deletion requirements/implementation, signing ownership, and store assets.

Exit: internal testers complete signup → create/join trip → add expense → verify totals without blocking failures. Public release follows acceptance of that build.

## Scope and delivery

First usable build: milestone 1's vertical slice. Online feature-parity beta: milestones 1–4. Offline travel release: milestone 5 plus release checks.

Rough planning range for one experienced Android engineer: 4–6 weeks for an online beta; another 2–3 weeks for offline support and release hardening. These are estimates, not commitments; revise after the authentication/deep-link vertical slice. Store review and account setup time are additional.

Defer receipt scanning, debt splitting, maps, itinerary planning, push notifications, widgets, and iOS to keep the first Android release focused.

Decisions needed before distribution: public name/package ID, intended minimum device support, signing/Play account ownership, and whether offline expense entry is required in the first public release. These do not block the initial prototype.

## Platform references

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations): Compose, ViewModels, repositories, and unidirectional state.
- [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first): local persistence, queued writes, and durable background work.
- [Supabase Android Kotlin quickstart](https://supabase.com/docs/guides/getting-started/quickstarts/kotlin): connecting a native Android client to the existing backend.
- [Supabase Kotlin initialization and deep links](https://supabase.com/docs/reference/kotlin/initializing): mobile authentication callbacks.
