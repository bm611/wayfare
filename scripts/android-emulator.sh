#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

AVD_NAME="Pixel_9_Pro_XL_API_35"
FOREGROUND=false

for arg in "$@"; do
  case "$arg" in
    -f|--foreground)
      FOREGROUND=true
      ;;
    *)
      if [ -n "$arg" ] && [[ "$arg" != -* ]]; then
        AVD_NAME="$arg"
      fi
      ;;
  esac
done

# Check if an emulator is already running
if command -v adb >/dev/null 2>&1; then
  if adb devices | grep -q "emulator-"; then
    echo "ℹ️  An Android emulator is already running."
    exit 0
  fi
fi

if [ "$FOREGROUND" = true ]; then
  echo "==> Starting Android emulator in foreground: $AVD_NAME"
  emulator -avd "$AVD_NAME"
else
  echo "==> Starting Android emulator in background: $AVD_NAME"
  nohup emulator -avd "$AVD_NAME" >/dev/null 2>&1 &
  disown
  echo "✅ Emulator launched! You can safely close this terminal tab."
fi
