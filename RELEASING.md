# Releasing to Google Play

The code side of this is done and scripted; the rest is account setup, policy
forms and review, which only the account owner can do. Work through it in order —
several later steps are blocked by earlier ones.

## Status (2026-09-26)

Done:

- [x] Signed release build, R8 rules, adaptive icon, release scripts and CI workflow
- [x] Upload key created (`android/keystore/wayfare-upload.jks` + `keystore.properties`)
- [x] Account deletion: `delete_account` migration applied, in-app menu item, and
      `/delete-account` live on the site
- [x] `assetlinks.json` live, with the upload key fingerprint only
- [x] Store icon, feature graphic and the first screenshot (`01-sign-in.png`)

Remaining, in order:

1. [ ] **Back up the upload key.** Copy `android/keystore/` and
       `android/keystore.properties` somewhere off this machine (password manager).
2. [ ] **Settle the name and package id** (step 0). `com.wayfare.app` is permanent
       after the first upload.
3. [ ] **Create the Play developer account** (one-time fee, identity verification).
       Personal or organisation decides whether step 12 applies.
4. [ ] **Write the privacy policy page.** There is no `/privacy` route yet; the URL
       currently falls through to the app. Play requires one before any track.
5. [ ] **Replace `SUPPORT_EMAIL`** in `src/routes/DeleteAccount.tsx` with a monitored
       address, and deploy.
6. [ ] **Create a demo account** with a realistic trip on it. It is used for the
       screenshots and given to reviewers under App access.
7. [ ] **Capture screenshots 02–04** with that account (step 5 below).
8. [ ] **Write the listing text**: app name, short description (≤ 80 characters),
       full description (≤ 4000).
9. [ ] **Build the bundle**: `npm run android:release`. For CI builds instead, add
       the repository secrets listed in step 2; none are set yet.
10. [ ] **Console setup** (step 6): create the app, fill in App content (privacy
        policy, ads, content rating, target audience, data safety, deletion URL
        `https://getwayfare.netlify.app/delete-account`), and the store listing.
11. [ ] **Internal testing**: upload the `.aab`, enrol in Play App Signing, install
        from the Play link and test sign-in, joining a trip and adding an expense. Then
        add the Play signing key everywhere it is needed (step 3): `PLAY_SHA256=…
        npm run android:assetlinks`, commit and deploy, and register its SHA-1 on the
        Android OAuth client in Google Cloud.
12. [ ] **Closed testing**, personal accounts only: 12 testers opted in for 14
        continuous days before production access can be requested.
13. [ ] **Production**: apply for access if required, submit, and wait for review.

## 0. Decisions that become permanent

- **App name and package id.** `com.wayfare.app` is frozen at the first upload.
  The repository is called `bugtrip` while the app, the website and this document
  say Wayfare. Settle it before uploading, not after.
- **Account type.** A new *personal* developer account must run closed testing
  with at least 12 testers opted in for 14 continuous days before it can apply for
  production. An organisation account skips that but needs D-U-N-S verification.
  Budget the two weeks.
- **Who owns the upload key.** See step 1.

## 1. The upload key

```bash
npm run android:keystore          # writes android/keystore/ + keystore.properties
```

Both files are gitignored, and the password is generated on disk rather than
printed, so it never reaches shell history or a chat log. **Back both up
somewhere you will still have in five years.** If the key is lost after the first
upload you can reset the upload key in the Play Console, but only because Play App
Signing holds the real signing key — enrol in it when the console offers.

## 2. Signed bundle

```bash
npm run android:release                 # build + verify the signature
npm run android:release -- --bump       # next release: versionCode + 1
```

The script refuses to finish on an unsigned bundle, which is the failure that
otherwise only shows up as a Play upload error. `--install` also smoke-tests the
release build on an attached device (needs `brew install bundletool`), which
matters because the release build is R8-minified and the debug build is not.

`--bump` edits the `versionCode` default in `app/build.gradle.kts`; commit that
before tagging. It is the only version counter, for local and CI builds alike.

CI does the same on a tag: `git tag v0.1.0 && git push origin v0.1.0`. It builds
the committed `versionCode` and takes `versionName` from the tag. It needs
the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
`SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` repository secrets.

## 3. App Links, after the first upload

Play App Signing re-signs the bundle, so the certificate that ships is **not** the
upload key. `public/.well-known/assetlinks.json` has to list both:

```bash
PLAY_SHA256="AA:BB:..." npm run android:assetlinks
```

Find that fingerprint in Play Console → Test and release → Setup → App signing.
Without it `/join/<code>` links open the website instead of the app in
production, while still working in internal testing — which is exactly the kind of
bug that survives a release.

The same Play fingerprint is what Google Sign-In needs: the Android OAuth client
in Google Cloud is registered against a package name and a SHA-1, so add the Play
one there too, or native Google sign-in stops working for installed builds.

## 4. Account deletion

The app, the website and the database function are in place:

- `supabase/migrations/20260926120000_delete_account.sql` — applied to production.
- Android: Account menu → Delete account.
- Web: `/delete-account`, reachable without signing in.

Two follow-ups:

- Replace `SUPPORT_EMAIL` in `src/routes/DeleteAccount.tsx` with a monitored
  address. The page is what satisfies the policy, and it currently points at a
  placeholder.
- Play asks for the deletion URL in App content:
  `https://getwayfare.netlify.app/delete-account` (live).

## 5. Store listing

```bash
npm run store:render                                      # icon + feature graphic
npm run store:screenshots -- --name 01-sign-in --caption "..."
```

See `store/README.md` for the sizes Play accepts and the screenshot workflow. The
screenshots need a signed-in demo account with a real trip on it — that account's
credentials also go into App content → App access, because reviewers cannot get
past the sign-in screen otherwise, and a listing that cannot be reviewed is a
listing that gets rejected.

Still to write by hand: the store description, and a privacy policy URL. Both are
required, and the privacy policy has to cover what the app actually does — email
and name from Supabase auth, and the trip and expense data users enter.

## 6. Console setup

1. Create the app, choose the default language, and confirm the package id.
2. **App content**: privacy policy URL, ads (none), content rating questionnaire,
   target audience, data safety form, government apps declaration, and the
   account deletion URL from step 4.
3. Data safety: email address, name, user ids and financial info are collected and
   not shared, encrypted in transit, deletable. It has to match the Data safety
   answers to what the app really does — the backend is Supabase, so this is
   straightforward, but it is a declaration you are accountable for.
4. **Internal testing** first: upload the `.aab`, add your own account, install
   from the Play link, and check the release build actually works — sign in, join
   a trip, add an expense, see it on the web.
5. **Closed testing** if the account needs the 12-tester runway, then
   **Production**.

## 7. Before every release

```bash
npm run test:unit && npm run lint      # web
npm run test:ui
cd android && ./gradlew testDebugUnitTest lintDebug
```

`targetSdk` is 37, comfortably above Play's current minimum. `minSdk` is 26, which
covers effectively every device that can install from Play today.

## Not done yet

- Tablet (7"/10") screenshots, if the app is distributed to tablets.
