#!/usr/bin/env bash
# Rasterises the Play Store artwork. The SVGs are the source of truth; the PNGs
# next to them are what the Play Console asks for.
#
#   store/icon.svg                  -> store/icon-512.png              (512x512)
#   store/feature-graphic.svg       -> store/feature-graphic-1024x500.png
#
# Manrope ships with the app rather than as a system font, so fontconfig is
# pointed at the bundled TTFs for the duration of the render. Without that the
# wordmark silently falls back to a system sans and the artwork stops matching
# the app.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FONT_DIR="$(cd "$SCRIPT_DIR/../ios/Wayfare/Resources" && pwd)"

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "❌ rsvg-convert not found. Install it with: brew install librsvg" >&2
  exit 1
fi

FONTCONF_DIR="$(mktemp -d)"
trap 'rm -rf "$FONTCONF_DIR"' EXIT

cat > "$FONTCONF_DIR/fonts.conf" <<EOF
<?xml version="1.0"?>
<!DOCTYPE fontconfig SYSTEM "fonts.dtd">
<fontconfig>
  <dir>$FONT_DIR</dir>
  <cachedir>$FONTCONF_DIR/cache</cachedir>
</fontconfig>
EOF

export FONTCONFIG_FILE="$FONTCONF_DIR/fonts.conf"

render() {
  local src="$1" out="$2" width="$3" height="$4"
  echo "==> $src -> $out (${width}x${height})"
  rsvg-convert -w "$width" -h "$height" "$SCRIPT_DIR/$src" -o "$SCRIPT_DIR/$out"

  # Play rejects artwork in the wrong dimensions, and rsvg-convert will happily
  # letterbox rather than fail, so check before it becomes a console error.
  local actual
  if command -v magick >/dev/null 2>&1; then
    actual="$(magick identify -format '%wx%h' "$SCRIPT_DIR/$out")"
  elif command -v sips >/dev/null 2>&1; then
    actual="$(sips -g pixelWidth -g pixelHeight "$SCRIPT_DIR/$out" | awk '/pixel/ {printf "%s", $2; if (++n == 1) printf "x"}')"
  else
    echo "   (no image tool available to verify dimensions)"
    return 0
  fi

  if [ "$actual" != "${width}x${height}" ]; then
    echo "   ❌ expected ${width}x${height}, got $actual" >&2
    exit 1
  fi
  echo "   ✅ $actual"
}

render icon.svg icon-512.png 512 512
render feature-graphic.svg feature-graphic-1024x500.png 1024 500

echo ""
echo "✅ Store artwork up to date in $SCRIPT_DIR"
echo "   Screenshots still come from a real device — see README.md."
