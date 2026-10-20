#!/usr/bin/env bash
# Creates (once) and boots a headless API 35 emulator for UI tests. Pass --window to see it.
set -euo pipefail

avd="${PUZZLER_AVD:-puzzler-api35}"
image="system-images;android-35;google_apis;x86_64"
window_flag="-no-window"
[[ "${1:-}" == "--window" ]] && window_flag=""

if ! avdmanager list avd -c | grep -qx "$avd"; then
    echo "Creating AVD $avd"
    echo no | avdmanager create avd -n "$avd" -k "$image" -d pixel_6 --force
fi

emulator -avd "$avd" $window_flag -no-audio -no-snapshot -gpu swiftshader_indirect &
echo "Waiting for boot…"
adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 2; done
# Animations off makes UI tests steadier.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
echo "Emulator $avd is ready."
