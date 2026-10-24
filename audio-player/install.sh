#!/usr/bin/env bash
# Install a menu entry (and pinnable panel launcher) for the audio player, with absolute paths to
# this checkout. Re-run after moving the folder. Mirrors desktop/knotes' install.sh.
set -e
cd "$(dirname "$0")"
HERE="$(pwd)"

DEST="$HOME/.local/share/applications/audio-player.desktop"
mkdir -p "$(dirname "$DEST")"
sed -e "s|@RUN@|$HERE/run.sh|g" -e "s|@ICON@|$HERE/audio-player.svg|g" \
    audio-player.desktop.in > "$DEST"
chmod +x run.sh
update-desktop-database "$(dirname "$DEST")" 2>/dev/null || true

echo "Installed: $DEST"
echo "Launch 'Nibras Audio Player' from the menu, then right-click its panel icon → Pin."
