#!/usr/bin/env bash
# Quick note adder for Nibras PIM: open the note editor PRE-FILLED with the current CLIPBOARD text,
# let you tweak it, then save it as a single markdown note using the filename format the importer
# expects:
#
#     YYYY_MM_DD-HHMMSS~<TYPE>~p<PRIORITY>~<DEPT>.md      e.g.  2026_06_17-215115~N~p2~X.md
#
# The first non-empty line becomes the record title; the whole text is the body. The file lands in
# the import folder. Prints the full path and a success flag (✓ / ✗); also fires a notification.
# Override the destination for testing with QUICK_NOTE_DIR=/tmp/... ./quick-note.sh
set -euo pipefail

NOTES_DIR="${QUICK_NOTE_DIR:-$HOME/new/knotes}"   # same folder the backend imports from
TYPE="N"        # module code: N = note (index card)
PRIORITY="2"    # -> p2
DEPT="X"        # department code

notify() { command -v notify-send >/dev/null 2>&1 && notify-send "$1" "$2" || true; }

# --- read the clipboard (X11 xclip, else Wayland wl-paste) into a temp file -------------------------
clip() {
  if command -v xclip >/dev/null 2>&1; then xclip -selection clipboard -o 2>/dev/null
  elif command -v wl-paste >/dev/null 2>&1; then wl-paste -n 2>/dev/null
  fi
}
tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
clip > "$tmp" || true   # pre-fill the editor with the clipboard (empty is fine — just type)

# --- editor opens with the clipboard already pasted in; Cancel => do nothing ------------------------
text="$(zenity --text-info --editable --filename="$tmp" --width=520 --height=340 \
        --title="Quick note   (#${TYPE}  p${PRIORITY}  ${DEPT})" 2>/dev/null)" || exit 0
if [ -z "$(printf '%s' "$text" | tr -d '[:space:]')" ]; then
  echo "✗ FAILED — empty note; nothing saved."
  notify "Quick note ✗" "Empty note — nothing saved"
  exit 1
fi

mkdir -p "$NOTES_DIR"

# --- unique filename to the second (bump if one already exists for this second) ---------------------
make_name() { printf '%s~%s~p%s~%s.md' "$(date +%Y_%m_%d-%H%M%S)" "$TYPE" "$PRIORITY" "$DEPT"; }
name="$(make_name)"
while [ -e "$NOTES_DIR/$name" ]; do sleep 1; name="$(make_name)"; done
path="$NOTES_DIR/$name"

# --- write + report full path and success flag ------------------------------------------------------
if printf '%s\n' "$text" > "$path"; then
  echo "✓ SAVED  $path"
  notify "Quick note ✓" "$path"
else
  echo "✗ FAILED to write  $path"
  notify "Quick note ✗" "Could not write $path"
  exit 1
fi
