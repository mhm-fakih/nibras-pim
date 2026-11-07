#!/usr/bin/env bash
# Refresh the API documentation in ./dist from a RUNNING backend.
#
#   ./tools/dump_api_docs.sh [base-url]        # default http://127.0.0.1:8081
#
# Writes three files:
#   dist/nibras-api-openapi.json    the spec — point a client generator at this
#   dist/nibras-api-openapi.yaml    the same, for reading
#   dist/nibras-api-adding-records.md   the short version for writing a script by hand
#
# Generated from the live server rather than hand-written, so it cannot drift from the code: a new
# module, a renamed field or a dropped endpoint shows up here the next time this is run. The
# endpoints themselves need no auth (springdoc serves them openly); using them does.
set -euo pipefail

BASE="${1:-http://127.0.0.1:8081}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$HERE/dist"
mkdir -p "$DIST"

curl -fsS -m 30 "$BASE/v3/api-docs"      -o "$DIST/nibras-api-openapi.json"
curl -fsS -m 30 "$BASE/v3/api-docs.yaml" -o "$DIST/nibras-api-openapi.yaml"
python3 "$HERE/tools/build_api_guide.py" "$DIST/nibras-api-openapi.json" "$DIST/nibras-api-adding-records.md"
echo "Wrote $DIST/nibras-api-openapi.{json,yaml} and nibras-api-adding-records.md from $BASE"
