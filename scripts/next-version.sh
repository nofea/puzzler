#!/usr/bin/env bash
# Prints the next release version (X.Y.Z) from the Conventional Commits since the last vX.Y.Z tag,
# or prints nothing if no release is due.
#   feat: ... -> MINOR    fix: ... -> PATCH    anything else -> no release
# MAJOR is never inferred from commits (not even `feat!:`); pass --major to bump it.
# Usage: next-version.sh [--major] [rev]    (rev defaults to HEAD)
set -euo pipefail

major=false
if [[ "${1:-}" == "--major" ]]; then major=true; shift; fi
rev="${1:-HEAD}"

last="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' "$rev" 2>/dev/null || true)"
if [[ -z "$last" ]]; then
    # First release: the version the app was built as before tagging started.
    echo "1.0.0"
    exit 0
fi

IFS=. read -r maj min pat <<<"${last#v}"
if $major; then
    echo "$((maj + 1)).0.0"
    exit 0
fi

subjects="$(git log --format=%s "$last..$rev")"
if grep -qE '^feat(\([^)]*\))?!?: ' <<<"$subjects"; then
    echo "$maj.$((min + 1)).0"
elif grep -qE '^fix(\([^)]*\))?!?: ' <<<"$subjects"; then
    echo "$maj.$min.$((pat + 1))"
fi
