# Wayfare for iOS

Native SwiftUI implementation of the Kotlin Android app, targeting iOS 17+ on iPhone and iPad. No WebView, React Native, or embedded web app. Uses the same Supabase tables, membership policies, Auth, Storage, and Netlify cover endpoint as Android/web; no database migration is needed.

## Open and run

1. Install full **Xcode 16 or later**, including an iOS simulator runtime. Command Line Tools alone cannot build or run iOS apps.
2. Copy `Config/Local.xcconfig.example` to `Config/Local.xcconfig`. Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` to the same public values used by Android/web. Keep the `https:/$()/` syntax: ordinary `//` begins an xcconfig comment. Never use a service-role key.
3. Open `ios/Wayfare.xcodeproj`, choose the **Wayfare** scheme and an iPhone simulator, and Run. For a physical device, select your team under Signing & Capabilities. The provisional bundle ID is `com.wayfare.app.ios`.
4. In Supabase's redirect allowlist, allow `wayfare-ios://auth` before testing confirmation, recovery, and Google sign-in. Google uses the existing Supabase provider through Apple's `ASWebAuthenticationSession` with PKCE. No Google client secret belongs in the app.

To build, install, and launch on a trusted physical iPhone or iPad over USB,
sign into Xcode, select your Personal Team for the Wayfare target under Signing
& Capabilities, enable Developer Mode on the device, then run:

```sh
npm run ios:devices
npm run ios:device
```

Pass a device name or identifier when more than one is paired:

```sh
npm run ios:device -- "My iPhone"
```

The generated Xcode project is checked in. If adding source files, regenerate it from the repository root with:

```sh
xcodegen generate --spec ios/project.yml
```

## Implemented

- Email/password sign-in, signup, confirmation resend, recovery, Google OAuth, Keychain session restoration, and sign-out.
- Active/upcoming/undated/past trips, creation/editing/deletion, optional dates and budgets, cover display/generation, pull-to-refresh.
- Expense creation/editing/deletion, seven categories, notes, date groups, title/note search, category and traveller filters.
- Decimal ledger arithmetic, daily averages and remaining allowance, 30 payment currencies, 12-hour FX cache, explicitly labelled offline estimates, and preserved historical FX rates.
- Invite-code redemption, native share sheet, travellers, owner removal and member leave. Other travellers' expenses are read-only in the UI.
- Account-scoped cached data and atomically persisted expense queue; UUID reconciliation prevents duplicate inserts after ambiguous responses. Failed entries remain accessible from the trips screen even if membership is removed.
- Native forms, date pickers, navigation, destructive-action confirmations, unsaved-change protection, system typography/Dynamic Type, and Android's warm paper/clay palette.

## Boundaries and outstanding device verification

This is an initial native port, not an App Store-ready or visually verified release. Development was performed on a Mac with Command Line Tools but **without full Xcode or an iOS simulator**. Core/data code compiles on macOS; the SwiftUI source has been syntax-checked but has not been iOS SDK typechecked, launched, or screenshot-reviewed. Live Supabase/Google flows have not been exercised and no shared backend configuration was changed.

- Offline **new expense creation** is supported. Editing/deleting saved expenses and trip/membership mutations require connectivity. The queue retries on refresh, network reconnect while running, and every 30 seconds while foregrounded. It does **not** promise background execution after iOS suspends/terminates the app. Reopen Wayfare to sync. Sign-out explicitly clears local data, including pending entries, after confirmation.
- HTTPS invite links retain the web fallback. Users can enter the share code in iOS. `wayfare-ios://join/ABCDEFGH` opens the native join form and preserves the invite through login. Verified HTTPS Universal Links still require your Apple Team ID, an Associated Domains entitlement, and a hosted `apple-app-site-association` file; these were not guessed or deployed.
- Covers use a native placeholder until the existing backend generates an image. Foreground refresh picks up completed covers. Layout follows Android's screen structure but uses native iOS controls/system typography, not pixel-identical Compose rendering.
- Store distribution still needs an app icon, signing, privacy/App Store declarations, account-deletion review, and review of Apple's sign-in requirements. No release, deployment, or production write was performed.

Before beta distribution, run on a small iPhone and iPad with large text, inspect screenshots of auth/trips/detail/forms/failed sync, and exercise:

1. Signup → email confirmation → login → app restart → password recovery; Google login and cancellation.
2. Create/join a trip with two staging accounts; compare expenses and totals against Android/web; verify backend permission denial for non-members.
3. Record a non-EUR expense offline, terminate/reopen, reconnect, and verify exactly one server row. Revoke membership with an entry queued, then confirm a failed entry is retained.
4. Edit only a historical expense's note and confirm amount/FX stay unchanged. Test dates around local midnight and DST, over-budget trips, invalid input, and failed saves.

## Local checks

From the repository root:

```sh
TZ=Europe/Amsterdam swift test --package-path ios
swiftc -frontend -parse ios/Wayfare/*.swift ios/Wayfare/Data/*.swift
plutil -lint ios/Config/Info.plist ios/Wayfare.xcodeproj/project.pbxproj
```

Tests use isolated temporary directories, in-memory session storage, and a mocked URLSession; they neither contact Supabase nor touch the real Keychain. The explicit Swift Testing package supports this Command Line Tools installation, which lacks the bundled Testing module; newer toolchains may emit its deprecation warning.

With full Xcode selected:

```sh
xcodebuild -project ios/Wayfare.xcodeproj -scheme Wayfare \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath ios/DerivedData CODE_SIGNING_ALLOWED=NO build
```
