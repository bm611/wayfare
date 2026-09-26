# Store assets

Everything the Play Console asks for that can be produced from this repository.
The console also asks for text, which lives in the listing itself, not here.

| File | Where it goes | Requirements |
| --- | --- | --- |
| `icon-512.png` | Store listing → App icon | 512×512 PNG, no transparency, no rounded corners |
| `feature-graphic-1024x500.png` | Store listing → Feature graphic | 1024×500 PNG or JPEG |
| `screenshots/*.png` | Store listing → Phone screenshots | 2–8 images, 320–3840px per side, aspect ≤ 2:1 |

```bash
npm run store:render                     # re-rasterise the icon and feature graphic
npm run store:screenshots -- --name trips --caption "Every trip, one ledger"
```

The SVGs are the source of truth for the artwork, and the render script fails if
a PNG comes out at the wrong size — Play rejects those at upload time, not at
build time. Manrope is pulled from `ios/Wayfare/Resources` rather than the system,
so the wordmark always matches the app.

## Screenshots

`screenshots.sh` captures whatever is on screen right now and frames it onto a
1080×1920 canvas with a caption. Framing is not decoration: a modern phone
captures at roughly 9:20, which is taller than Play accepts, and the script fits
the capture to the canvas whatever device produced it.

```bash
npm run android:run                             # get the app on a device or emulator
npm run store:screenshots -- --name 01-sign-in --caption "Track what a trip actually costs."
# sign in, open the screen you want, then:
npm run store:screenshots -- --name 02-trips --caption "Every trip, one ledger"
npm run store:screenshots -- --name 03-budget --caption "Know what is left, daily"
npm run store:screenshots -- --name 04-add --caption "Log a cost in seconds"
```

`01-sign-in.png` is committed as a working example and a first listing image.
The rest need a signed-in account with a real trip on it — the script cannot
invent that, and the listing is the wrong place for an empty ledger.

Use a **demo account** for this, with a trip that looks like a trip. Reviewers
are also given those credentials under App access in the console, so the account
has to keep working for as long as the app is listed.

## Still produced outside this repository

- The 7" and 10" tablet screenshots, if the app is distributed to tablets.
- The promo video, if you want one on the listing (YouTube URL).
- The privacy policy page and its URL.

## Before uploading

- Android 13+ themed icon: `res/drawable/ic_launcher_monochrome.xml`.
- Confirm the listing icon and the launcher icon still agree — both come from
  the same geometry in `icon.svg` and `ic_launcher_foreground.xml`.
