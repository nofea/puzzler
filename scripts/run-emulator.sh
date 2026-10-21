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

# devenv puts build-tools/*/lib64 on LD_LIBRARY_PATH, whose older libc++ breaks the emulator; its own libs go first.
LD_LIBRARY_PATH="$ANDROID_HOME/emulator/lib64:${LD_LIBRARY_PATH:-}" \
    emulator -avd "$avd" $window_flag -no-audio -no-snapshot -gpu swiftshader_indirect &
emulator_pid=$!
echo "Waiting for boot…"
# Poll instead of `adb wait-for-device`, so a crashed emulator fails the script instead of hanging it.
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    kill -0 "$emulator_pid" 2>/dev/null || { echo "Emulator exited before booting." >&2; exit 1; }
    sleep 2
done
# Animations off makes UI tests steadier.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
echo "Emulator $avd is ready."
