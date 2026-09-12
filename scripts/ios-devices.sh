#!/usr/bin/env bash

echo "Connected or paired physical iOS devices:"
echo "----------------------------------------"
xcrun devicectl list devices 2>/dev/null || echo "Requires Xcode 15 or later."
echo ""
echo "Available iOS Simulators (Booted first):"
echo "----------------------------------------"
xcrun simctl list devices | grep -E "Booted" || true
echo ""
echo "All Available Devices:"
echo "----------------------------------------"
xcrun simctl list devices available | grep -E "iPhone|iPad"
