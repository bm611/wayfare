#!/usr/bin/env bash
# Captures Play Store screenshots from a connected phone or emulator and frames
# them onto the 1080x1920 canvas the listing expects.
#
# Play takes 2-8 phone screenshots, between 320px and 3840px on each side, with an
# aspect ratio no wider than 2:1. A raw device capture does not always fit — a tall
# phone gives roughly 1:2.2 — so framing onto 9:16 is what makes any device's
# output acceptable.
#
# Usage:
#   bash store/screenshots.sh --name trips --caption "Every trip, one ledger"
#   bash store/screenshots.sh --name expense --caption "Log a cost in seconds" --device emulator-5554
#   bash store/screenshots.sh --list
#
# Captures the screen as it is right now, so sign in and open the screen you want
# first. Raw captures are kept next to the framed output.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../scripts/android-env.sh"

OUT_DIR="$SCRIPT_DIR/screenshots"
RAW_DIR="$OUT_DIR/raw"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then ADB="adb"; fi

NAME=""
CAPTION=""
DEVICE="${ANDROID_SERIAL:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --name) shift; NAME="$1" ;;
    --caption) shift; CAPTION="$1" ;;
    --device) shift; DEVICE="$1" ;;
    --list)
      "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
      exit 0
      ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

if [ -z "$NAME" ]; then
  echo "❌ --name is required, e.g. --name trips" >&2
  exit 1
fi

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "❌ rsvg-convert not found. Install it with: brew install librsvg" >&2
  exit 1
fi

ADB_ARGS=()
[ -n "$DEVICE" ] && ADB_ARGS=(-s "$DEVICE")

if ! "$ADB" "${ADB_ARGS[@]}" get-state >/dev/null 2>&1; then
  echo "❌ No device. Connect a phone over USB/Wi-Fi, boot the emulator, then retry." >&2
  echo "   Attached right now:" >&2
  "$ADB" devices | sed 's/^/     /' >&2
  exit 1
fi

mkdir -p "$RAW_DIR"

RAW="$RAW_DIR/$NAME.png"
FRAMED="$OUT_DIR/$NAME.png"

echo "==> Capturing the current screen to ${RAW#$SCRIPT_DIR/}"
"$ADB" "${ADB_ARGS[@]}" exec-out screencap -p > "$RAW"

# A locked or sleeping device still writes a file, so check the PNG magic bytes
# rather than trusting the exit code. `grep` is unreliable on binary input here.
if [ "$(od -An -tx1 -N4 "$RAW" | tr -d ' \n')" != "89504e47" ]; then
  echo "❌ The capture is not a PNG. Is the screen locked or off?" >&2
  rm -f "$RAW"
  exit 1
fi

# XML-escape the caption so an ampersand or angle bracket does not break the SVG.
escape_xml() {
  printf '%s' "$1" | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' -e 's/"/\&quot;/g'
}

CITATION="$(escape_xml "$CAPTION")"
SVG="$RAW_DIR/$NAME.svg"

# Read the capture's real size out of its IHDR chunk (bytes 16-23), so the frame
# fits whatever device took it. A 9:16 phone fills the width; a taller one is
# scaled to fit the height instead of being cropped.
read -r SHOT_W SHOT_H <<< "$(od -An -tu1 -j16 -N8 "$RAW" |
  awk '{ print $1*16777216+$2*65536+$3*256+$4, $5*16777216+$6*65536+$7*256+$8 }')"

TOP=356        # clears the caption
MAX_W=800
MAX_H=1524     # 1880 is the lowest the frame may reach
if [ $((MAX_W * SHOT_H)) -le $((MAX_H * SHOT_W)) ]; then
  DRAW_W=$MAX_W
  DRAW_H=$((MAX_W * SHOT_H / SHOT_W))
else
  DRAW_H=$MAX_H
  DRAW_W=$((MAX_H * SHOT_W / SHOT_H))
fi
DRAW_X=$(((1080 - DRAW_W) / 2))
RADIUS=$((DRAW_W / 24))

# 1080x1920 canvas: the capture over the soft cloud surface, the one pastel accent
# behind it, and a caption in the app's own Manrope.
cat > "$SVG" <<SVGEOF
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
     width="1080" height="1920" viewBox="0 0 1080 1920">
  <defs>
    <clipPath id="phone">
      <rect x="$DRAW_X" y="$TOP" width="$DRAW_W" height="$DRAW_H" rx="$RADIUS"/>
    </clipPath>
  </defs>

  <rect width="1080" height="1920" fill="#F7F7F7"/>
  <circle cx="1010" cy="120" r="300" fill="#C6DAEF"/>
  <circle cx="70" cy="1830" r="220" fill="#C6DAEF"/>

  <text x="540" y="196" text-anchor="middle" font-family="Manrope" font-weight="700"
        font-size="58" letter-spacing="-1.4" fill="#222222">$CITATION</text>

  <g clip-path="url(#phone)">
    <image x="$DRAW_X" y="$TOP" width="$DRAW_W" height="$DRAW_H"
           preserveAspectRatio="xMidYMid slice" xlink:href="$(basename "$RAW")"/>
  </g>
  <rect x="$DRAW_X" y="$TOP" width="$DRAW_W" height="$DRAW_H" rx="$RADIUS"
        fill="none" stroke="#DDDDDD" stroke-width="2"/>
</svg>
SVGEOF

echo "==> Framing ${SHOT_W}x${SHOT_H} onto 1080x1920"
FONTCONF_DIR="$(mktemp -d)"
trap 'rm -rf "$FONTCONF_DIR"' EXIT

cat > "$FONTCONF_DIR/fonts.conf" <<EOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$(cd "$SCRIPT_DIR/../ios/Wayfare/Resources" && pwd)</dir>
  <cachedir>$FONTCONF_DIR/cache</cachedir>
</fontconfig>
EOF

# rsvg-convert resolves the <image> href relative to the SVG, which sits in raw/.
FONTCONFIG_FILE="$FONTCONF_DIR/fonts.conf" rsvg-convert -w 1080 -h 1920 "$SVG" -o "$FRAMED"

ACTUAL="$(magick identify -format '%wx%h' "$FRAMED" 2>/dev/null || echo '1080x1920')"
echo ""
echo "✅ $FRAMED ($ACTUAL)"
echo "   raw capture kept at ${RAW#$SCRIPT_DIR/}"
echo ""
echo "Play takes 2-8 phone screenshots. Repeat for each screen you want to show:"
echo "  sign in, open the screen, then run this script again with a new --name."
