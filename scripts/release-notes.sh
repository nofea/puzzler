#!/usr/bin/env bash
# Prints Markdown release notes: the feat and fix commits since the last vX.Y.Z tag before [rev].
# Usage: release-notes.sh [rev]    (rev defaults to HEAD)
set -euo pipefail

rev="${1:-HEAD}"
last="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' "$rev" 2>/dev/null || true)"
range="${last:+$last..}$rev"

section() {
    local title="$1" pattern="$2" lines
    lines="$(git log --format='%s (%h)' "$range" | grep -E "$pattern" | sed -E 's/^[a-z]+(\([^)]*\))?!?: /- /' || true)"
    [[ -n "$lines" ]] && printf '## %s\n\n%s\n\n' "$title" "$lines"
    return 0
}

section "Features" '^feat(\([^)]*\))?!?: '
section "Bug fixes" '^fix(\([^)]*\))?!?: '
