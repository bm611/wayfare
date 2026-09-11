#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-env.sh"

PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_ROOT/android"

"$SCRIPT_DIR/android-build.sh"

APK_DIR="$ANDROID_DIR/app/build/outputs/apk/debug"

# Determine local Wi-Fi IP address
IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
PORT=8085

echo ""
echo "=========================================================="
echo "📱 Open this link in Chrome/browser on your Android phone:"
echo "   http://$IP:$PORT/app-debug.apk"
echo "=========================================================="
echo "(Both Mac and Phone must be connected to the same Wi-Fi)"
echo "Press Ctrl+C to stop the server."
echo ""

cd "$APK_DIR"
python3 -m http.server $PORT
