#!/usr/bin/env bash
#
# Installs the dependencies of the capture tools into a local virtualenv
# (./.venv), used by ./run.sh. Re-run now and then: it also upgrades yt-dlp,
# whose site extractors go stale within weeks.
#
# Usage:
#   ./setup.sh          install / update dependencies
#   ./setup.sh --force  delete ./.venv and start clean
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/.venv"
REQ_FILE="$SCRIPT_DIR/requirements.txt"
PY_BIN="${PYTHON:-python3}"

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m warn:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[[ "${1:-}" == "--force" ]] && { say "Removing existing venv"; rm -rf "$VENV_DIR"; }

# ---- 1. Check the interpreter ------------------------------------------------
command -v "$PY_BIN" >/dev/null 2>&1 || die "$PY_BIN not found. Install Python 3.10+ first."

"$PY_BIN" - <<'PY' || die "Python 3.10 or newer is required."
import sys
sys.exit(0 if sys.version_info >= (3, 10) else 1)
PY
say "Using $($PY_BIN --version) at $(command -v "$PY_BIN")"

[[ -f "$REQ_FILE" ]] || die "requirements.txt not found next to this script."

# ---- 2. Create the venv ------------------------------------------------------
if [[ ! -x "$VENV_DIR/bin/python" ]]; then
    say "Creating virtualenv at $VENV_DIR"
    if ! "$PY_BIN" -m venv "$VENV_DIR" 2>/dev/null; then
        rm -rf "$VENV_DIR"
        warn "'$PY_BIN -m venv' failed — the venv/ensurepip module is probably missing."
        cat >&2 <<MSG

Install it, then re-run this script:
  Debian/Ubuntu : sudo apt install python3-venv python3-dev
  Fedora        : sudo dnf install python3-virtualenv
  Arch          : sudo pacman -S python
MSG
        exit 1
    fi
else
    say "Reusing existing virtualenv at $VENV_DIR"
fi

VPY="$VENV_DIR/bin/python"

# ---- 3. Install dependencies -------------------------------------------------
say "Upgrading pip"
"$VPY" -m pip install --quiet --upgrade pip setuptools wheel

say "Installing from requirements.txt (upgrading yt-dlp)"
if ! "$VPY" -m pip install --upgrade -r "$REQ_FILE"; then
    warn "Full install failed — retrying without the optional 'cryptg' speedup."
    grep -v '^[[:space:]]*cryptg' "$REQ_FILE" | "$VPY" -m pip install --upgrade -r /dev/stdin \
        || die "Could not install the required packages."
fi

# ---- 4. Verify ---------------------------------------------------------------
say "Verifying"
"$VPY" - <<'PY'
import telethon, yt_dlp.version
print(f"  telethon {telethon.__version__}  OK")
print(f"  yt-dlp   {yt_dlp.version.__version__}  OK")
try:
    import cryptg  # noqa: F401
    print("  cryptg            OK (fast Telegram media transfers)")
except ImportError:
    print("  cryptg            not installed (Telegram downloads will be slower, but it works)")
PY

# ---- 5. Config + external tools ----------------------------------------------
if [[ ! -f "$SCRIPT_DIR/config.env" ]]; then
    cp "$SCRIPT_DIR/config.example.env" "$SCRIPT_DIR/config.env"
    chmod 600 "$SCRIPT_DIR/config.env"
    warn "Created config.env from the example — fill in the Telegram credentials."
fi
command -v ffmpeg >/dev/null 2>&1 || warn "ffmpeg not found: media/ needs it (sudo apt install ffmpeg)."

cat <<MSG

Done. Run a tool with:

  ./run.sh telegram | firefox | media URL
MSG
