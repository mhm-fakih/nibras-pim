#!/usr/bin/env bash
# Launch the knotes desktop app. Run from anywhere: ./run.sh
set -euo pipefail

# Resolve this script's own directory so the app finds style.css / its modules.
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Friendly check for the GTK bindings before we try to import them.
if ! python3 -c "import gi; gi.require_version('Gtk', '3.0'); from gi.repository import Gtk" 2>/dev/null; then
    echo "PyGObject + GTK 3 are missing. Install them with:" >&2
    echo "  Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-3.0" >&2
    echo "  Fedora:        sudo dnf install python3-gobject gtk3" >&2
    echo "  Arch:          sudo pacman -S python-gobject gtk3" >&2
    exit 1
fi

exec python3 "$HERE/knotes.py" "$@"
