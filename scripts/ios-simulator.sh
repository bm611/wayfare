#!/usr/bin/env bash
set -e

# Default device to boot if none is booted
DEVICE_NAME="${1:-iPhone 17 Pro}"

# Open the Simulator app
open -a Simulator

# Check if any simulator is already booted
BOOTED_ID=$(xcrun simctl list devices | grep "Booted" | head -1 | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' || true)

if [ -z "$BOOTED_ID" ]; then
  echo "==> Booting $DEVICE_NAME..."
  xcrun simctl boot "$DEVICE_NAME" 2>/dev/null || true
  BOOTED_ID=$(xcrun simctl list devices | grep "Booted" | head -1 | sed -E 's/.*\(([0-9A-F-]+)\).*/\1/' || true)
fi

echo "✅ iOS Simulator ready ($BOOTED_ID)"
