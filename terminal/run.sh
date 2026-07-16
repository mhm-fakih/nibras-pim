#!/usr/bin/env bash
# Create a local venv (first run), install deps, and launch the knotes terminal.
#   ./run.sh                         # connect to http://127.0.0.1:8081
#   NIBRAS_URL=http://host:8081 ./run.sh
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$HERE/.venv"

# A venv without pip is a half-created leftover (e.g. an interrupted first run) — rebuild it,
# rather than trusting the directory's mere existence.
if [ ! -x "$VENV/bin/pip" ]; then
    rm -rf "$VENV"
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install --quiet --upgrade pip
    "$VENV/bin/pip" install --quiet -r "$HERE/requirements.txt"
fi
exec "$VENV/bin/python" "$HERE/knotes_tui.py" "$@"
