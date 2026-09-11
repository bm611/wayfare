#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

echo "==> Building debug APK..."
cd "$ANDROID_DIR"
./gradlew assembleDebug

APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
  echo ""
  echo "✅ APK ready at:"
  echo "   $APK_PATH"
  echo ""
  if [ "$1" == "--open" ] || [ "$1" == "-o" ]; then
    open -R "$APK_PATH"
  fi
fi
