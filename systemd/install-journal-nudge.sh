#!/usr/bin/env bash
# Install/refresh the journal-nudge timer from the copies in this repo, which are the source of
# truth. Re-run after editing either unit here. Also reminds you to set up
# ~/.config/nibras/journal-nudge.conf if it doesn't exist yet (see journal-nudge.sh's header).
set -euo pipefail
DEST="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
mkdir -p "$DEST"
install -m 644 "$(dirname "$0")/journal-nudge.service" "$DEST/"
install -m 644 "$(dirname "$0")/journal-nudge.timer"   "$DEST/"
systemctl --user daemon-reload
systemctl --user enable --now journal-nudge.timer
systemctl --user list-timers journal-nudge.timer --no-pager

CONF="${XDG_CONFIG_HOME:-$HOME/.config}/nibras/journal-nudge.conf"
if [ ! -f "$CONF" ]; then
    echo
    echo "No config yet — create $CONF with:"
    echo "  NIBRAS_USER=<username>"
    echo "  NIBRAS_PASS=<password>"
    echo "(NIBRAS_URL and the work-hours/days defaults are optional — see journal-nudge.sh's header)"
fi
