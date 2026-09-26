#!/usr/bin/env bash
# Builds the signed Android App Bundle for the Play Console.
#
# The bundle is what Play wants: it re-signs and splits it per device, which a
# plain APK cannot do. Upload the .aab, not an APK, unless you are side-loading
# for a smoke test.
#
# Usage:
#   npm run android:release                 # build and verify
#   npm run android:release -- --bump       # versionCode + 1 first
#   npm run android:release -- --install     # also install the release build on
#                                            # the attached device to smoke test it
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"
GRADLE_FILE="$ANDROID_DIR/app/build.gradle.kts"
PROPERTIES="$ANDROID_DIR/keystore.properties"

BUMP=""
INSTALL=""

while [ $# -gt 0 ]; do
  case "$1" in
    --bump) BUMP="yes" ;;
    --install) INSTALL="yes" ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

if [ ! -f "$PROPERTIES" ] && [ -z "${KEYSTORE_FILE:-}" ]; then
  echo "❌ No upload key. Create one first:"
  echo "     npm run android:keystore"
  exit 1
fi

if [ -n "$BUMP" ]; then
  CURRENT=$(sed -n 's/.*wayfare.versionCode").*?: \([0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)
  CURRENT=${CURRENT:-1}
  NEXT=$((CURRENT + 1))
  # Play rejects an upload that reuses a versionCode, so bumping is a release step
  # rather than something to remember at the last minute.
  sed -i '' "s/wayfare.versionCode\").orNull?.toInt() ?: $CURRENT/wayfare.versionCode\").orNull?.toInt() ?: $NEXT/" "$GRADLE_FILE"
  echo "==> versionCode $CURRENT -> $NEXT"
fi

echo "==> Building signed release bundle..."
cd "$ANDROID_DIR"
./gradlew bundleRelease

AAB="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.aab"

if [ ! -f "$AAB" ]; then
  echo "❌ No bundle at $AAB" >&2
  exit 1
fi

# An unsigned bundle is the failure mode worth catching here: it builds happily
# and Play rejects it minutes later, after the upload.
echo "==> Verifying the signature..."
if ! jarsigner -verify "$AAB" 2>/dev/null | grep -q "jar verified"; then
  echo "❌ The bundle is not signed. Check keystore.properties." >&2
  exit 1
fi
echo "   ✅ signed"

VERSION_CODE=$(unzip -p "$AAB" base/manifest/AndroidManifest.xml 2>/dev/null | strings | grep -o "versionCode=[0-9]*" | head -1 || true)
SIZE=$(du -h "$AAB" | cut -f1)

echo ""
echo "✅ Bundle ready: $AAB ($SIZE)"
echo "   Upload it at https://play.google.com/console — Internal testing first."
echo ""

if [ -n "$INSTALL" ]; then
  # The bundle has to be split back into APKs before a device will take it, which
  # is what bundletool does; without it the only way to smoke test is a Play track.
  if ! command -v bundletool >/dev/null 2>&1; then
    echo "⚠️  bundletool not found, so --install was skipped."
    echo "   brew install bundletool, or push the build to internal testing."
    exit 0
  fi

  # Same precedence as build.gradle.kts: env first, then keystore.properties.
  prop() { [ -f "$PROPERTIES" ] && sed -n "s/^$1=//p" "$PROPERTIES" | head -1; }
  KS_FILE="${KEYSTORE_FILE:-$(prop storeFile)}"
  KS_ALIAS="${KEY_ALIAS:-$(prop keyAlias)}"

  # bundletool's file: form wants a file holding only the password. Written with
  # mode 600 and removed on exit, so the password never shows up in `ps`.
  SECRETS="$(mktemp -d)"
  trap 'rm -rf "$SECRETS"' EXIT
  (umask 077
    printf '%s' "${KEYSTORE_PASSWORD:-$(prop storePassword)}" > "$SECRETS/ks"
    printf '%s' "${KEY_PASSWORD:-$(prop keyPassword)}" > "$SECRETS/key")

  APKS="$ANDROID_DIR/app/build/outputs/bundle/release/app-release.apks"
  echo "==> Extracting device APKs and installing..."
  if ! bundletool build-apks --overwrite --bundle="$AAB" --output="$APKS" \
    --ks="$KS_FILE" \
    --ks-key-alias="$KS_ALIAS" \
    --ks-pass="file:$SECRETS/ks" \
    --key-pass="file:$SECRETS/key"; then
    echo "⚠️  Could not build APKs from the bundle. Test through a Play track instead."
    exit 0
  fi
  bundletool install-apks --apks="$APKS"
  echo "   ✅ Release build installed — open it and check the ledger loads."
fi
