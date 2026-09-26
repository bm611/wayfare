#!/usr/bin/env bash
# Creates the Play upload key for com.wayfare.app.
#
# The password is generated here and written only to android/keystore.properties.
# It is never echoed, so it does not land in shell history, CI logs or a screen
# share. If you lose this file and the keystore, you lose the ability to upload
# updates under this package name, so back both up somewhere durable.
#
# Usage:
#   bash scripts/android-keystore.sh                 # create if missing
#   bash scripts/android-keystore.sh --force         # replace an existing key
#   bash scripts/android-keystore.sh --out ~/keys    # keep the .jks outside the repo
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

ALIAS="wayfare-upload"
KEYSTORE_DIR="$ANDROID_DIR/keystore"
PROPERTIES="$ANDROID_DIR/keystore.properties"
FORCE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --force) FORCE="--force" ;;
    --out) shift; KEYSTORE_DIR="$1" ;;
    --alias) shift; ALIAS="$1" ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

KEYSTORE="$KEYSTORE_DIR/$ALIAS.jks"

if [ -f "$KEYSTORE" ] && [ -z "$FORCE" ]; then
  echo "⚠️  A keystore already exists at:"
  echo "   $KEYSTORE"
  echo ""
  echo "Refusing to overwrite it — replacing an upload key that already shipped a"
  echo "build means Play will reject future uploads until you reset the upload key"
  echo "in the Play Console. Pass --force only if you are sure."
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "❌ keytool not found. Install a JDK (brew install openjdk@17) and retry." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"

# Alphanumeric only: the value goes into a properties file that Gradle reads,
# and punctuation there is a class of bug worth not having.
PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)"

echo "==> Generating 4096-bit RSA upload key (valid 27 years)..."
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=Wayfare, OU=Android, O=Wayfare" \
  -noprompt 2>/dev/null

if [ ! -f "$KEYSTORE" ]; then
  echo "❌ keytool did not produce a keystore." >&2
  exit 1
fi

umask 077
cat > "$PROPERTIES" <<EOF
# Upload key for com.wayfare.app. Gitignored — do not commit, do not paste into
# issues. Both this file and the .jks need backing up.
storeFile=$KEYSTORE
storePassword=$PASSWORD
keyAlias=$ALIAS
keyPassword=$PASSWORD
EOF
chmod 600 "$PROPERTIES"

echo ""
echo "✅ Upload key ready."
echo "   keystore:   $KEYSTORE"
echo "   password:   $PROPERTIES (mode 600, never printed)"
echo ""
echo "Next:"
echo "  1. Back both files up somewhere you will still have in five years."
echo "  2. npm run android:release      # signed .aab for the Play Console"
echo "  3. npm run android:assetlinks   # App Links fingerprints for the site"
