#!/bin/bash
# Select a region with flameshot, then open the saved image in the annotator.
# Cancelling the selection, or copying it to the clipboard only, saves no file
# and so opens nothing.

dir="$HOME/new"
annotator="$(dirname "$(readlink -f "$0")")/../scr-annotator/scr_annotator.py"

marker=$(mktemp) || exit 1
trap 'rm -f "$marker"' EXIT   # exec below skips this, so it removes the marker itself

flameshot gui -p "$dir" || exit 0

# The file can land just after flameshot returns; ~/new also receives downloads,
# so only images written since the capture started count.
for _ in {1..20}; do
    shot=$(find "$dir" -maxdepth 1 -type f -newer "$marker" \
        \( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' \) \
        -printf '%T@ %p\n' | sort -n | tail -n 1 | cut -d' ' -f2-)
    if [ -n "$shot" ]; then
        rm -f "$marker"
        exec "$annotator" "$shot"
    fi
    sleep 0.1
done
