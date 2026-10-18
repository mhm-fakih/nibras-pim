#!/usr/bin/env bash
# Launch THIS working copy as the development build: its own app ID (so it runs alongside the
# installed copy instead of raising that window) and "(dev)" in the title. See release.sh for how a
# tested version reaches the installed copy.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KNOTES_DEV=1 exec "$HERE/run.sh" "$@"
