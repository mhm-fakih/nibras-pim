#!/usr/bin/env bash
# Install/refresh the user service from the copy in this repo, which is the
# source of truth. Re-run after editing the unit here.
set -euo pipefail
DEST="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
mkdir -p "$DEST"
install -m 644 "$(dirname "$0")/window-tracker.service" "$DEST/"
systemctl --user daemon-reload
systemctl --user enable --now window-tracker.service
systemctl --user status window-tracker.service --no-pager -n 5
