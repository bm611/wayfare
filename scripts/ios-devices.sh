#!/usr/bin/env bash
echo "Available iOS Simulators (Booted first):"
echo "----------------------------------------"
xcrun simctl list devices | grep -E "Booted" || true
echo ""
echo "All Available Devices:"
echo "----------------------------------------"
xcrun simctl list devices available | grep -E "iPhone|iPad"
