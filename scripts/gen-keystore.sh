#!/usr/bin/env bash
# Generates the release signing key for Puzzler and writes keystore.properties for Gradle.
# The keystore lives outside the repo; keystore.properties is gitignored.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
keystore="${PUZZLER_KEYSTORE_FILE:-$HOME/.android-keys/puzzler-release.jks}"
alias="${PUZZLER_KEY_ALIAS:-puzzler}"
props="$repo_root/keystore.properties"

if [[ -e "$keystore" ]]; then
    echo "Keystore already exists at $keystore — refusing to overwrite it." >&2
    echo "Losing or replacing it means installed copies can't be updated. Delete it yourself if you really mean to." >&2
    exit 1
fi

read -r -s -p "Keystore password (min 6 chars): " store_pw; echo
read -r -s -p "Repeat password: " store_pw2; echo
[[ "$store_pw" == "$store_pw2" ]] || { echo "Passwords don't match." >&2; exit 1; }
[[ ${#store_pw} -ge 6 ]] || { echo "Password too short." >&2; exit 1; }
read -r -p "Certificate name (CN) [Puzzler]: " cn
cn="${cn:-Puzzler}"

mkdir -p "$(dirname "$keystore")"
chmod 700 "$(dirname "$keystore")"

# PKCS12 keystores use the store password for the key as well. Passed via env so it never shows in `ps`.
export PUZZLER_GEN_PW="$store_pw"
keytool -genkeypair \
    -keystore "$keystore" -storetype PKCS12 \
    -alias "$alias" -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=$cn" \
    -storepass:env PUZZLER_GEN_PW -keypass:env PUZZLER_GEN_PW
chmod 600 "$keystore"

umask 077
cat > "$props" <<PROPS
storeFile=$keystore
storePassword=$store_pw
keyAlias=$alias
keyPassword=$store_pw
PROPS

echo
echo "Created $keystore and $props"
echo "BACK UP the keystore and password somewhere safe. Without them you can't ship updates to installed copies."
keytool -list -v -keystore "$keystore" -storepass:env PUZZLER_GEN_PW -alias "$alias" | grep -E 'SHA256:' || true
