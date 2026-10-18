#!/usr/bin/env bash
# Run a capture tool with the venv from setup.sh and the settings in config.env.
#
#   ./run.sh telegram [args]    Telegram chat → N note      (telegram/telegram_chat_download.py)
#   ./run.sh firefox  [args]    Firefox bookmarks → R note  (firefox/firefox_bookmarks.py)
#   ./run.sh media    [args] URL...  yt-dlp → R note + media (media/media-dl.sh)
#
# Each tool's --help (-h for media) lists its options.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "$DIR/config.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$DIR/config.env"
    set +a
fi
# config.env is sourced, so a "~/..." value arrives unexpanded when quoted.
[[ -n "${NIBRAS_KNOTES_DIR:-}" ]] && export NIBRAS_KNOTES_DIR="${NIBRAS_KNOTES_DIR/#\~/$HOME}"

[[ -x "$DIR/.venv/bin/python" ]] || { echo "No venv yet — run $DIR/setup.sh first." >&2; exit 1; }
export PATH="$DIR/.venv/bin:$PATH"          # media-dl.sh finds the venv's yt-dlp

tool="${1:-}"; shift || true
case "$tool" in
    telegram) exec python "$DIR/telegram/telegram_chat_download.py" "$@" ;;
    firefox)  exec python "$DIR/firefox/firefox_bookmarks.py" "$@" ;;
    media)    exec "$DIR/media/media-dl.sh" -U "$@" ;;   # -U: setup.sh keeps yt-dlp current
    *) sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
