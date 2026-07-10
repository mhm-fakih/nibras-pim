#!/usr/bin/env bash
# Install a desktop launcher so knotes shows up in the menu and can be pinned to the
# Cinnamon panel. Re-run any time the project moves; it rewrites absolute paths.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APPS_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
DEST="$APPS_DIR/knotes.desktop"

mkdir -p "$APPS_DIR"
sed "s#@HERE@#$HERE#g" "$HERE/knotes.desktop.in" > "$DEST"
chmod +x "$HERE/run.sh" "$HERE/knotes.py"

# Refresh the menu database if the tool is available (harmless if not).
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS_DIR" || true

echo "Installed launcher: $DEST"
echo
echo "Next:"
echo "  1. Launch 'knotes' from the menu (or run ./run.sh)."
echo "  2. Right-click its icon in the Cinnamon panel's window list and choose 'Pin'."
echo "     (StartupWMClass=knotes keeps the running window grouped under the pinned icon.)"
