# Changelog

Newest first. Entries below the top one correspond to commits.

## Unreleased

- **Move key.** `Alt+M` moves the current image to a folder chosen from the new
  **Move →** header button and shows the next one, for pulling a subset out of
  a large folder one key per keeper. Saves the label and pending marks first.
  No confirmation: a move is undone with `mv`. The move is logged in the
  destination's `.scr-annotator-log.tsv` from the original name, so the label
  is still recognised there, and the `.originals/` copy follows. Follows
  `TOOL_KEYS_NEED_ALT` like the tool letters. Tests in `test_naming.py`.
- **Renamed on screen** to Nibras Image Annotator. `scr-annotator` stays as the
  file names, `StartupWMClass` and the DBus id so the launcher and single
  instance keep working. `APP_NAME` and `APP_ID` settings.
- **Solarized light chrome** matching the audio-player; the canvas stays
  near-black.
- **Rotate** with `[` and `]`, pending like a mark, one write for any number of
  turns. Written before the marks so they do not land sideways.
- **Copy path** button and `Ctrl+Shift+C`.
- **Folder rail fits the names.** `TREE_WIDTH = 0` measures the folder names
  with Pango and opens the rail that wide, clamped by `TREE_MIN`/`TREE_MAX`.
  Name and count are separate columns so the count no longer takes half the
  rail. `PgUp`/`PgDn` page images even with focus in the tree.
- **Icon** regenerated as shared line-art with the audio player; `make_icon.py`
  emits `icon.svg` and the PNGs from one geometry.

## cfc3890 — Single instance, open a named file, Alt+letter tools, F3 for the tree

- One window per desktop via `Gtk.Application`; a second launch hands its
  command line to the running instance.
- A file argument opens that file, switching folders if needed.
- Tool letters need `Alt` so they can still be typed into a label
  (`TOOL_KEYS_NEED_ALT`).
- `F3` shows or hides the folder tree.

## 220bc99 — Show what is unsaved, add F7 to apply it, and put the folder in the title

- Persistent status line listing pending crop, marks and turns.
- `F7` burns pending edits without leaving the image.
- Folder in the window title so the capture script can find the right
  instance.

## 4bdf253 — Folder tree, four marking tools from a toolbar, and a Print-key workflow

- Folder tree with per-folder image counts, one `scandir` per shown directory.
- Pen, highlight, rectangle and crop from a header toolbar; crop applied last.
- Folder watching: a screenshot taken while the window is open is adopted and
  shown.

## b4b2a54 — Bring the README up to date with the app it documents

## ea94cbd — Delete on Del with a confirmation, Solarized chrome, and a title bar

- `Del`/`F8` to trash via `gio trash`, the one confirming dialog.
- Solarized chrome, neutral canvas.

## f40113a — Keyboard-first screenshot review with autosaved labels and drawing

- Labels become filename suffixes; the rename log recovers the original stem
  so labels replace rather than stack.
- Pristine copies in `.originals/` before the first modification.
- `test_naming.py`.
