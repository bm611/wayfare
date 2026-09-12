#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
IOS_DIR="$PROJECT_ROOT/ios"

echo "==> Building Wayfare for iOS Simulator..."
xcodebuild -project "$IOS_DIR/Wayfare.xcodeproj" -scheme Wayfare \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$IOS_DIR/DerivedData" \
  CODE_SIGN_IDENTITY="-" CODE_SIGNING_REQUIRED=NO build -quiet

APP_PATH="$IOS_DIR/DerivedData/Build/Products/Debug-iphonesimulator/Wayfare.app"

if [ -d "$APP_PATH" ]; then
  echo ""
  echo "✅ App ready at:"
  echo "   $APP_PATH"
  echo ""
  if [ "$1" == "--open" ] || [ "$1" == "-o" ]; then
    open -R "$APP_PATH"
  fi
fi
