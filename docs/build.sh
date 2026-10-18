#!/usr/bin/env bash
# Build the public docs site (docs/src → docs/site) for upload to https://nib-ras.org/pkm/docs/.
# Only docs/src is published; docs/dev (developer docs) is never part of the build.
# Usage: docs/build.sh            build + zip into docs/site-<date>.zip
#        docs/build.sh serve      live preview at http://127.0.0.1:8000/pkm/docs/
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
if [[ ! -x .venv/bin/mkdocs ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -q "mkdocs-material==9.6.*" "mkdocs-static-i18n==1.3.*"
fi
if [[ "${1:-}" == serve ]]; then exec .venv/bin/mkdocs serve; fi
.venv/bin/mkdocs build --clean --strict
zip="site-$(date +%F).zip"
rm -f "$zip"
( cd site && zip -qr "../$zip" . )
echo "Built docs/site/ ($(du -sh site | cut -f1)) and docs/$zip"
echo "Upload the CONTENTS of docs/site/ to the web root folder pkm/docs/ on nib-ras.org."
