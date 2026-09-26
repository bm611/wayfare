#!/usr/bin/env bash
# Rewrites public/.well-known/assetlinks.json from the signing certificates.
#
# Android verifies an https://getwayfare.netlify.app/join/<code> link against this
# file before it hands the link to the app instead of the browser. Get it wrong
# and nothing breaks loudly — the link just opens the website, which looks exactly
# like the deep link "not working".
#
# Two fingerprints matter, and they are not the same key:
#
#   * the upload key   — used by local release builds and internal testing
#   * the Play app signing key — what Google re-signs your build with, and the one
#     that ships to users. Find it in Play Console > Test and release > Setup >
#     App signing. Until it is added here, verified links work in internal testing
#     and silently stop working in production.
#
# Usage:
#   bash scripts/android-assetlinks.sh
#   PLAY_SHA256="AA:BB:..." bash scripts/android-assetlinks.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"
PROPERTIES="$ANDROID_DIR/keystore.properties"
TARGET="$PROJECT_ROOT/public/.well-known/assetlinks.json"

PACKAGE="com.wayfare.app"
HOST="getwayfare.netlify.app"

# Upload key: from keystore.properties when it is there, or KEYSTORE_FILE plus the
# password variables when a CI job materialised the key somewhere temporary.
STORE_FILE=""
STORE_PASSWORD=""
KEY_ALIAS=""

if [ -f "$PROPERTIES" ]; then
  STORE_FILE=$(sed -n 's/^storeFile=//p' "$PROPERTIES")
  STORE_PASSWORD=$(sed -n 's/^storePassword=//p' "$PROPERTIES")
  KEY_ALIAS=$(sed -n 's/^keyAlias=//p' "$PROPERTIES")
fi

STORE_FILE="${KEYSTORE_FILE:-$STORE_FILE}"
STORE_PASSWORD="${KEYSTORE_PASSWORD:-$STORE_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS:-wayfare-upload}"

UPLOAD_SHA256=""
if [ -n "$STORE_FILE" ] && [ -f "$STORE_FILE" ] && [ -n "$STORE_PASSWORD" ]; then
  UPLOAD_SHA256=$(keytool -list -v -keystore "$STORE_FILE" -alias "$KEY_ALIAS" \
    -storepass "$STORE_PASSWORD" 2>/dev/null |
    sed -n 's/^[[:space:]]*SHA256: //p' | head -1)
fi

if [ -z "$UPLOAD_SHA256" ]; then
  echo "⚠️  No upload keystore found, so only the Play app signing key will be written." >&2
  echo "   Run scripts/android-keystore.sh first for a complete file." >&2
fi

PLAY_SHA256="${PLAY_SHA256:-}"

if [ -z "$UPLOAD_SHA256" ] && [ -z "$PLAY_SHA256" ]; then
  echo "❌ Nothing to write: no upload key and no PLAY_SHA256." >&2
  exit 1
fi

mkdir -p "$(dirname "$TARGET")"

# Both entries carry the same relation: any of these certificates is allowed to
# claim the package.
emit() {
  printf '    {\n'
  printf '      "relation": ["delegate_permission/common.handle_all_urls"],\n'
  printf '      "target": {\n'
  printf '        "namespace": "android_app",\n'
  printf '        "package_name": "%s",\n' "$PACKAGE"
  printf '        "sha256_cert_fingerprints": [\n'
  printf '          "%s"\n' "$1"
  printf '        ]\n'
  printf '      }\n'
  printf '    }'
}

{
  printf '[\n'
  if [ -n "$UPLOAD_SHA256" ]; then
    emit "$UPLOAD_SHA256"
    [ -n "$PLAY_SHA256" ] && printf ',\n' || printf '\n'
  fi
  if [ -n "$PLAY_SHA256" ]; then
    emit "$PLAY_SHA256"
    printf '\n'
  fi
  printf ']\n'
} > "$TARGET"

echo "==> Wrote $TARGET"
[ -n "$UPLOAD_SHA256" ] && echo "    upload key:          $UPLOAD_SHA256"
[ -n "$PLAY_SHA256" ] && echo "    Play app signing:    $PLAY_SHA256"
[ -z "$PLAY_SHA256" ] && {
  echo ""
  echo "⚠️  The Play app signing fingerprint is still missing. Until it is added,"
  echo "   /join/ links work in internal testing and fall back to the browser in"
  echo "   production. Re-run with:"
  echo "     PLAY_SHA256=\"...\" bash scripts/android-assetlinks.sh"
}
exit 0
