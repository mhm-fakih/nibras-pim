#!/usr/bin/env bash
# Install/refresh the capture timers from the copies in this repo, which are the
# source of truth. A timer is only enabled when its CAPTURE_*_ARGS line is set
# in ../config.env — no arguments, no idea which chat or profile to capture.
# Re-run after editing a unit or config.env.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
CONF="$HERE/../config.env"
[[ -f "$CONF" ]] || { echo "No $CONF — run ../setup.sh first." >&2; exit 1; }
# shellcheck source=/dev/null
source "$CONF"

mkdir -p "$DEST"
enabled=()
for tool in telegram firefox; do
    var="CAPTURE_${tool^^}_ARGS"
    if [[ -z "${!var:-}" ]]; then
        echo "skip capture-$tool: $var is not set in config.env"
        systemctl --user disable --now "capture-$tool.timer" 2>/dev/null || true
        continue
    fi
    install -m 644 "$HERE/capture-$tool.service" "$HERE/capture-$tool.timer" "$DEST/"
    enabled+=("capture-$tool.timer")
done
systemctl --user daemon-reload
for t in "${enabled[@]}"; do systemctl --user enable --now "$t"; done
[[ ${#enabled[@]} -gt 0 ]] && systemctl --user list-timers "${enabled[@]}" --no-pager || true
