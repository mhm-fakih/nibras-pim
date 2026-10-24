#!/usr/bin/env bash
# Install/refresh the timer from the copies in this repo, which are the source
# of truth. Re-run after editing either unit here.
set -euo pipefail
DEST="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
mkdir -p "$DEST"
install -m 644 "$(dirname "$0")/file-watch.service" "$DEST/"
install -m 644 "$(dirname "$0")/file-watch.timer"   "$DEST/"
systemctl --user daemon-reload
systemctl --user enable --now file-watch.timer
systemctl --user list-timers file-watch.timer --no-pager
