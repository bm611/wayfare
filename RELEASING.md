# Releasing to Google Play

The code side of this is done and scripted; the rest is account setup, policy
forms and review, which only the account owner can do. Work through it in order —
several later steps are blocked by earlier ones.

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

## 4. Account deletion (done in code, needs applying)

The app, the website and the database function are in place:

- `supabase/migrations/20260926120000_delete_account.sql` — apply this migration.
- Android: Account menu → Delete account.
- Web: `/delete-account`, reachable without signing in.

Two follow-ups:

- Replace `SUPPORT_EMAIL` in `src/routes/DeleteAccount.tsx` with a monitored
  address. The page is what satisfies the policy, and it currently points at a
  placeholder.
- Play asks for the deletion URL in App content. It will be
  `https://getwayfare.netlify.app/delete-account` once this is deployed.

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

- The privacy policy page itself.
- Tablet (7"/10") screenshots, if the app is distributed to tablets.
