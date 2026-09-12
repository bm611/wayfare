#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
IOS_DIR="$PROJECT_ROOT/ios"
PROJECT="$IOS_DIR/Wayfare.xcodeproj"
DERIVED_DATA="$IOS_DIR/DerivedData-device"
APP_PATH="$DERIVED_DATA/Build/Products/Debug-iphoneos/Wayfare.app"
BUNDLE_ID="com.wayfare.app.ios"

if ! xcrun --find devicectl >/dev/null 2>&1; then
  echo "❌ This command requires Xcode 15 or later with its Command Line Tools selected."
  exit 1
fi

REQUESTED_DEVICE="${1:-${IOS_DEVICE:-}}"
DEVICE_JSON=$(mktemp)
trap 'rm -f "$DEVICE_JSON"' EXIT
xcrun devicectl list devices --json-output "$DEVICE_JSON" >/dev/null
DEVICE_DETAILS=$(/usr/bin/python3 - "$DEVICE_JSON" "$REQUESTED_DEVICE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    devices = json.load(source).get("result", {}).get("devices", [])
devices = [
    device for device in devices
    if device.get("hardwareProperties", {}).get("platform") == "iOS"
]
requested = sys.argv[2].casefold()
if requested:
    devices = [
        device for device in devices
        if requested in {
            str(device.get("identifier", "")).casefold(),
            str(device.get("deviceProperties", {}).get("name", "")).casefold(),
            str(device.get("hardwareProperties", {}).get("udid", "")).casefold(),
            str(device.get("hardwareProperties", {}).get("serialNumber", "")).casefold(),
        }
    ]
if devices:
    device = devices[0]
    print("\t".join([
        str(device.get("identifier", "")),
        str(device.get("connectionProperties", {}).get("pairingState", "unknown")),
    ]))
PY
)
IFS=$'\t' read -r DEVICE PAIRING_STATE <<< "$DEVICE_DETAILS"

if [ -z "$DEVICE" ]; then
  if [ -n "$REQUESTED_DEVICE" ]; then
    echo "❌ iOS device '$REQUESTED_DEVICE' was not found."
  else
    echo "❌ No connected iPhone or iPad was found."
  fi
  echo ""
  echo "1. Connect and unlock the device."
  echo "2. Run: npm run ios:device"
  exit 1
fi

TEAM="${IOS_DEVELOPMENT_TEAM:-${DEVELOPMENT_TEAM:-}}"
if [ -z "$TEAM" ] && [ -f "$IOS_DIR/Config/Local.xcconfig" ]; then
  TEAM=$(sed -n 's/^[[:space:]]*DEVELOPMENT_TEAM[[:space:]]*=[[:space:]]*\([^[:space:]#]*\).*/\1/p' \
    "$IOS_DIR/Config/Local.xcconfig" | tail -1)
fi
if [ -z "$TEAM" ]; then
  TEAM=$(xcodebuild -project "$PROJECT" -scheme Wayfare \
    -destination 'generic/platform=iOS' -showBuildSettings 2>/dev/null \
    | sed -n 's/^[[:space:]]*DEVELOPMENT_TEAM = //p' | head -1 || true)
fi

if [ -z "$TEAM" ]; then
  echo "❌ Apple development team is not configured."
  echo "In Xcode, open ios/Wayfare.xcodeproj, select the Wayfare target,"
  echo "then choose your Personal Team under Signing & Capabilities."
  exit 1
fi

if [ "$PAIRING_STATE" != "paired" ]; then
  echo "==> Pairing with $DEVICE..."
  echo "    Keep the device unlocked and tap 'Trust' if prompted."
  xcrun devicectl manage pair --device "$DEVICE"
fi

echo "==> Building Wayfare for physical iOS device..."
xcodebuild -project "$PROJECT" -scheme Wayfare \
  -configuration Debug -destination 'generic/platform=iOS' \
  -derivedDataPath "$DERIVED_DATA" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM="$TEAM" CODE_SIGN_STYLE=Automatic \
  CODE_SIGNING_ALLOWED=YES CODE_SIGNING_REQUIRED=YES \
  CODE_SIGN_IDENTITY="Apple Development" build -quiet

if [ ! -d "$APP_PATH" ]; then
  echo "❌ Build artifact not found at $APP_PATH"
  exit 1
fi

echo "==> Installing on $DEVICE..."
xcrun devicectl device install app --device "$DEVICE" "$APP_PATH"

echo "==> Launching $BUNDLE_ID..."
xcrun devicectl device process launch --device "$DEVICE" \
  --terminate-existing "$BUNDLE_ID"

echo ""
echo "✅ App running on $DEVICE"
