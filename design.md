# Wayfare native design

The iOS and Android apps use a white canvas, one soft pastel accent,
watercolor cover art at hero scale, and disciplined grayscale for everything
else. This system applies to native screens, not the separately
maintained website in `src/`.

Source tokens live in `ios/Wayfare/Theme.swift` and Android's `ui/Theme.kt`.
The two files mirror each other name for name; change both or neither.

## Palette

| Role | Token | sRGB |
| --- | --- | --- |
| Primary CTA fill | Accent | `#C6DAEF` (dark `#B3CDE8`) |
| Label on accent fill | On Accent | `#1E2B3A` |
| Accent text, icons, meter fill | Accent Ink | `#3E6A93` (dark `#A9C8E8`) |
| Page background | Canvas White | `#FFFFFF` |
| Subsurface | Soft Cloud | `#F7F7F7` |
| Border, divider | Hairline Gray | `#DDDDDD` |
| Heading and body text | Ink Black | `#222222` |
| Focused input text | Charcoal | `#3F3F3F` |
| Secondary text | Ash Gray | `#6A6A6A` |
| Disabled | Mute Gray | `#929292` |
| Tertiary divider, icon stroke | Stone Gray | `#C1C1C1` |
| Error | Error Red | `#C13515` |
| Pressed error | Deep Error | `#B32505` |
| Legal / informational link | Info Blue | `#428BFF` |

Accent fills only the one primary action on a surface, and is too light for
white text: labels on it use On Accent. Anything that must read against the
canvas — the wordmark, links, the spent amount, the budget meter, progress —
uses Accent Ink. Neither is used decoratively. Ink
Black carries roughly 90% of all text and is never pure `#000000`. No accent
outside that pastel blue may be introduced — expense
categories are distinguished by their glyph, not by colour.

## Typography

One family carries every label, from an 11pt badge to a 32pt display. Airbnb
Cereal VF is proprietary, so both clients use the bundled Manrope, referenced
by PostScript name on iOS and by variable-weight instance on Android.

Only weights 500, 600 and 700 exist in the system. There is no 400-regular:
body copy sits at 500, which reads a notch more deliberate than the platform
default. On Android, `FontWeight.Normal` is deliberately mapped to 500 so text
that never names a weight still lands on the body weight.

| Slot | Size | Weight | Tracking | Use |
| --- | --- | --- | --- | --- |
| `displaySmall` | 32 | 700 | -0.6 | Page display |
| `headlineMedium` | 28 | 700 | -0.5 | Section heading |
| `headlineSmall` | 22 | 600 | -0.44 | Subsection heading |
| `titleLarge` | 20 | 600 | -0.18 | Listing title |
| `titleMedium` | 16 | 600 | 0 | Subtitle bold, card lead |
| `bodyLarge` | 16 | 500 | 0 | Body copy |
| `bodyMedium` | 14 | 500 | 0 | Metadata, subtitles |
| `bodySmall` | 13 | 500 | 0 | Dates, micro-metadata |
| `labelLarge` | 16 | 500 | 0 | Button label |
| `labelMedium` | 14 | 600 | 0 | Numeric stats, emphasis |
| `labelSmall` | 11 | 600 | 0 | Compact badge |

Display sizes compress tracking to feel chiselled; body sizes stay at zero for
readability. Line heights run 1.18–1.25 for display type and open to 1.43 for
body and caption.

**No all-caps.** The single uppercase role in the system is an 8pt superscript
for price footnotes, and nothing currently needs it. This is why native
`shortDate` renders `Sep 12` where the website's `src/lib/format.ts` still
renders `SEP 12` — the two are deliberately allowed to diverge on casing.

## Radius

| Radius | Use |
| --- | --- |
| 4 | Inline tags and chips |
| 8 | Buttons, text inputs, dropdowns |
| 14 | Listing photography, containers, badges, dialogs |
| 20 | Pill buttons, hero images, booking panel |
| 32 | Search pill |
| 50% | Every icon button and avatar |

Circular is the system's signature geometry. An icon button is never rounded to
anything but 50%.

## Elevation

Listing cards have no shadow at all: they sit on the white canvas, and the
whitespace between them plus the radius of the photograph does the separating.
The only lift in the system is the booking panel's, built from three stacked
low-opacity shadows (~2%, 4%, 10%) that read as one cohesive elevation —
`panelElevation()` on both platforms.

## Components

- **Primary CTA** — Accent fill, On Accent 16/500 label, 8pt radius, 48pt tall.
  Pressing scales to 0.92; it never tints or lifts. One per surface.
- **Secondary button** — white, 1pt Hairline border, Ink label, 8pt radius, or
  20pt when used as a pill (filter menus, currency picker).
- **Circular icon button** — 44pt, 50% radius, Soft Cloud ground; white with a
  hairline ring when it sits on photography.
- **Listing card** — a 4:3 photograph at 14pt radius with the trip name,
  destination, dates and spend stacked directly underneath on bare canvas at
  4–8pt gaps. No border, no shadow. A white phase badge rides the top-left
  corner of the photograph. Cards are separated by 24pt of canvas.
- **Booking panel** — the trip detail budget card: white, 1pt Hairline border,
  14pt radius, 24pt padding, layered elevation, with the figure set at display
  size the way a nightly price is. A hairline strip below it carries remaining,
  daily average and entry count.
- **Reserve bar** — on a phone the panel's action collapses to a bottom-anchored
  bar: the figure on the left, the one accent button on the right, hairline above.
- **Amenity rows** — the category breakdown: a 24pt monochrome glyph, a 16pt
  label, a hairline between every row.
- **Review rows** — the ledger: a 40pt circular glyph where an avatar would sit,
  title in 16/600, payer in 14/500 Ash, amount right-aligned.
- **Text input** — white, 1pt Hairline border, 8pt radius, switching to Ink on
  focus and Error Red on failure. The accent never tints a field.
- **Search** — a full 32pt pill with a hairline border and one soft shadow.

## Layout

Screen margins are 24pt. Base spacing unit is 8: 4–8pt within a metadata group
so it reads as one unit, 24pt between cards and sections so each photograph
reads as a distinct object. Card internal padding is 24pt on the booking panel
and 16pt on amenity rows.

All interactive elements meet 44pt. Text never sits on top of a photograph;
captions always go below it. Native press, focus, disabled and loading states;
no decorative entrances. Keep success quiet and errors explanatory, and never
substitute decoration or invented statistics for actual trip and expense data.
