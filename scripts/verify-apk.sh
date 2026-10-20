#!/usr/bin/env bash
# Verifies an APK's signature and prints the signing certificate fingerprint.
set -euo pipefail

apk="${1:-app/build/outputs/apk/release/app-release.apk}"
[[ -f "$apk" ]] || { echo "No APK at $apk — run build-release first." >&2; exit 1; }

apksigner="$(command -v apksigner || true)"
if [[ -z "$apksigner" ]]; then
    apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
fi
[[ -x "$apksigner" ]] || { echo "apksigner not found; run inside 'devenv shell'." >&2; exit 1; }

"$apksigner" verify --verbose --print-certs "$apk"
