#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
fi

echo "Connected Android devices:"
$ADB devices -l
