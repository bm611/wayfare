#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
fi

APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"

attached_devices() {
  $ADB devices | awk '$2=="device" {print $1}'
}

# Wireless debugging hands out a fresh port on every reboot or toggle, so the
# address can't be remembered. Rediscover the already-paired phone over mDNS.
CONNECTED_ADDR=""
wireless_connect() {
  local want="$1" services service addr resolved

  local i
  for i in 1 2 3; do
    services=$($ADB mdns services 2>/dev/null | grep '_adb-tls-connect\._tcp' || true)
    [ -n "$services" ] && break
    [ "$i" -lt 3 ] && sleep 1
  done
  [ -z "$services" ] && return 1

  # mDNS names look like adb-<serial>-XXXXXX, so prefer the requested serial.
  addr=""
  if [ -n "$want" ]; then
    addr=$(printf '%s\n' "$services" | awk -v w="$want" 'index($1, w) > 0 {print $3; exit}')
  fi
  [ -z "$addr" ] && addr=$(printf '%s\n' "$services" | awk '{print $3; exit}')
  [ -z "$addr" ] && return 1

  # Some Android/adb versions advertise 0.0.0.0 even though the mDNS service
  # resolves correctly. Resolve the service name with macOS Bonjour in that case.
  if [[ "$addr" == 0.0.0.0:* ]] && command -v dns-sd >/dev/null 2>&1; then
    service=$(printf '%s\n' "$services" | awk -v a="$addr" '$3 == a {print $1; exit}')
    if [ -n "$service" ]; then
      resolved=$(dns-sd -L "$service" _adb-tls-connect._tcp local. 2>&1 & pid=$!; sleep 2; kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true)
      addr=$(printf '%s\n' "$resolved" | sed -n 's/.*can be reached at \([^:]*\):\([0-9][0-9]*\).*/\1:\2/p' | head -1)
    fi
  fi
  [ -z "$addr" ] && return 1

  echo "==> No device attached; found paired phone on Wi-Fi at $addr"
  $ADB connect "$addr" >/dev/null 2>&1 || true
  sleep 1

  if attached_devices | grep -qx "$addr"; then
    CONNECTED_ADDR="$addr"
    return 0
  fi
  return 1
}

echo "==> Building debug APK..."
cd "$ANDROID_DIR"
./gradlew assembleDebug

# Target one device with `npm run android:run -- <serial>` or ANDROID_SERIAL=<serial>.
# Otherwise install on every authorized device, independently.
TARGET="${1:-$ANDROID_SERIAL}"

if [ -z "$(attached_devices)" ]; then
  wireless_connect "$TARGET" || true
fi

if [ -n "$TARGET" ]; then
  # A USB serial won't match once the phone is on Wi-Fi; use the address we got.
  if ! attached_devices | grep -qx "$TARGET" && [ -n "$CONNECTED_ADDR" ]; then
    echo "==> Using Wi-Fi address $CONNECTED_ADDR for $TARGET"
    TARGET="$CONNECTED_ADDR"
  fi
  DEVICES="$TARGET"
else
  DEVICES=$(attached_devices)
fi

if [ -z "$DEVICES" ]; then
  # Auto-boot emulator if available
  if command -v emulator >/dev/null 2>&1; then
    echo "==> No active device found. Booting Android emulator (Pixel_9_Pro_XL_API_35)..."
    nohup emulator -avd Pixel_9_Pro_XL_API_35 >/dev/null 2>&1 &
    disown
    echo "==> Waiting for emulator to boot..."
    $ADB wait-for-device
    while [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
      sleep 2
    done
    DEVICES=$(attached_devices)
  fi
fi

if [ -z "$DEVICES" ]; then
  echo ""
  echo "⚠️  No authorized Android devices or emulators found."
  echo "Over USB:"
  echo "  1. Plug the phone in, with USB Debugging on in Developer Options"
  echo "  2. Accept the 'Allow USB debugging' prompt on your phone screen"
  echo "Over Wi-Fi:"
  echo "  1. Turn on Developer Options > Wireless debugging"
  echo "  2. Make sure the phone is on the same network as this Mac"
  echo "  3. First time only, pair it: adb pair <ip>:<port> (phone shows a code)"
  echo "Run 'npm run android:devices' to check status."
  exit 1
fi

INSTALLED=""
FAILED=""

for dev in $DEVICES; do
  echo "==> Installing on $dev..."
  LOG=$($ADB -s "$dev" install -r -d "$APK_PATH" 2>&1) || true

  if printf '%s' "$LOG" | grep -q '^Success'; then
    INSTALLED="$INSTALLED $dev"
  else
    FAILED="$FAILED $dev"
    echo "   ❌ Install failed on $dev"

    if printf '%s' "$LOG" | grep -qiE 'not enough space|INSUFFICIENT_STORAGE'; then
      FREE=$($ADB -s "$dev" shell df -h /data 2>/dev/null | tail -1 | awk '{print $4}')
      echo "   Out of storage (${FREE:-unknown} free on /data)."
      echo "   Free space on the device, or for an emulator wipe it:"
      echo "     Android Studio > Device Manager > ⋮ > Wipe Data"
    else
      printf '%s\n' "$LOG" | grep -iE 'failure|error' | head -3 | sed 's/^/   /'
    fi
  fi
done

for dev in $INSTALLED; do
  echo "==> Launching app on $dev..."
  $ADB -s "$dev" shell am start -n com.wayfare.app/.MainActivity >/dev/null
done

echo ""
[ -n "$INSTALLED" ] && echo "✅ App running on:$INSTALLED"
[ -n "$FAILED" ] && echo "⚠️  Skipped (install failed):$FAILED"

# Succeed as long as the app landed somewhere.
[ -n "$INSTALLED" ]
