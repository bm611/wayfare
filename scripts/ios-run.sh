#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
IOS_DIR="$PROJECT_ROOT/ios"
BUNDLE_ID="com.wayfare.app.ios"

# 1. Build the app
echo "==> Building Wayfare for iOS Simulator..."
xcodebuild -project "$IOS_DIR/Wayfare.xcodeproj" -scheme Wayfare \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$IOS_DIR/DerivedData" \
  CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO build -quiet

APP_PATH="$IOS_DIR/DerivedData/Build/Products/Debug-iphonesimulator/Wayfare.app"

if [ ! -d "$APP_PATH" ]; then
  echo "❌ Build artifact not found at $APP_PATH"
  exit 1
fi

# 2. Ensure Simulator is open and booted
open -a Simulator

BOOTED_ID=$(xcrun simctl list devices | grep "Booted" | head -1 | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' || true)
if [ -z "$BOOTED_ID" ]; then
  DEVICE_NAME="${1:-iPhone 17 Pro}"
  echo "==> Booting $DEVICE_NAME..."
  xcrun simctl boot "$DEVICE_NAME" 2>/dev/null || true
  BOOTED_ID=$(xcrun simctl list devices | grep "Booted" | head -1 | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' || true)
fi

echo "==> Installing app on booted simulator ($BOOTED_ID)..."
xcrun simctl install booted "$APP_PATH"

echo "==> Launching $BUNDLE_ID..."
xcrun simctl launch booted "$BUNDLE_ID" >/dev/null

echo "✅ App running on iOS Simulator!"
