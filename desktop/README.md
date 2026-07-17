# knotes (desktop)

A small GTK3 note taker — the Linux desktop port of the knotes Android homepage. Notes are
plain-text files whose **names encode their tags**, so a desktop note and a phone note can
live in the same (e.g. Syncthing-synced) folder.

```
<timestamp>~<type>~p<priority>~<category>.md
e.g.  2026_06_10-143025~W~p3~A.md
```

## Run

```bash
./run.sh          # checks for GTK, then launches
# or directly:
python3 knotes.py
```

### Menu entry + pin to the Cinnamon panel

```bash
./install.sh
```

This writes `~/.local/share/applications/knotes.desktop` with absolute paths to `run.sh` and the
icon (`knotes.svg`). Launch **knotes** from the menu, then right-click its panel icon and choose
**Pin**. The launcher sets `StartupWMClass=knotes` to match the window's `WM_CLASS`, so the running
window stays grouped under the pinned icon. Re-run `install.sh` if you move the project.

Requires PyGObject + GTK 3:

```bash
# Debian / Ubuntu
sudo apt install python3-gi gir1.2-gtk-3.0
# Fedora
sudo dnf install python3-gobject gtk3
# Arch
sudo pacman -S python-gobject gtk3
```

## Files

| File | What it is |
|------|------------|
| `notes.py`  | Pure-Python storage: the file-name tag scheme, parsing, list/save/update/delete. No GUI — easy to read and reuse. |
| `config.py` | App-wide `Settings` (saved next to the program in `config.json`) **and** `RepoConfig` — the per-folder tag options saved inside each note folder as `.knotes.json`. |
| `knotes.py` | The GTK3 window: note list, tagging input bar, editing, search, settings, shortcuts. |
| `style.css` | Appearance. Plain hex colours — change the accent in one place. |

## Keyboard

Everything is reachable from the keyboard (press **F1** in the app for this list):

| Keys | Action |
|------|--------|
| `Ctrl+Enter` | Save / send the note |
| `Esc` | Cancel editing · clear the field · close search |
| `Ctrl+1 … Ctrl+9` | Choose the 1st … 9th **type** |
| `Alt+1 … Alt+9` | Choose **priority** 1 … 9 |
| `Ctrl+Alt+1 … 9` | Choose the 1st … 9th **category** |
| `Ctrl+F` | Toggle search |
| `Ctrl+L` | Toggle **minimal side-note mode** (board hidden) |
| `F12` | Toggle focus between the card area and the add field (in minimal mode, selects the lone card so you can retag/delete it) |
| `Ctrl+T` | Toggle light / dark theme |
| `Ctrl+D` (card selected) | Move the selected note to the `Deleted/` folder (with confirmation) |
| `Ctrl+,` | Settings |
| `Ctrl+Q` | Quit |
| `Tab` / arrows / `Space` | Move between chips and toggle them |
| `↑`/`↓` in the list, `Enter` | Select a note and open it for editing |
| `Delete` (in the list) | Delete the selected note |

## Notes on behaviour

- **View mode + sorting on the toolbar.** A linked kanban / list / minimal button group and a
  sort dropdown (Date / Category / Type / Priority) live in the header bar — they're switched
  too often for the Settings dialog. Radio-style (exactly one mode active), persisted, applied
  immediately; `Ctrl+L` still toggles minimal mode and keeps the group in sync. The kanban only
  shows category columns that have notes.
- **Auto-open the files folder after save.** A toolbar toggle (folder icon): every saved note
  immediately opens its companion `.d/` folder in the file manager (the `Ctrl+O` action) —
  for capture sessions where each note gets files dropped in right after writing. Persisted.
- **Per-folder backend.** Each note folder can bind its OWN nibras backend (URL/user/password in
  its `.knotes.json`, editable in the Settings dialog's per-folder section; blank falls back to
  the global "default backend" fields). Taxonomy sync talks to the active folder's backend and
  re-syncs on every folder switch — so different folders can feed different servers/contexts.
- **Per-folder tag options.** Types, categories and priorities are **per note folder**, not global.
  Each folder keeps its own options in a `.knotes.json` file inside it; switching folders (the picker
  next to the add button, or `Ctrl+1…9`) reloads that folder's options and its default selection.
  Editing Types / Categories / Priorities in **Settings** (`Ctrl+,`) applies to the folder that was
  active when the dialog opened — the dialog header shows which one. On first run every configured
  folder is seeded from your then-current settings, so nothing changes until you diverge a folder.
- **Compact, multi-row add form.** The Type / Priority / Category chips are stacked on three rows
  (aligned to a common left edge) instead of one long line, so the window can stay narrow for
  focused reading. The text field, folder picker and send button share one uniform height. The
  window opens at its minimal width (not maximised).
- **Minimal side-note mode (`Ctrl+L`).** For quick side note-taking in the least screen space: the
  board and the full chip rows are hidden, leaving only the just-added card, a one-line summary of
  the selected tags, and the text field. Change the type/priority/subject by their shortcut keys
  (nothing else is shown). Toggling it off returns to whatever list/kanban view was active. The mode
  is remembered across launches. Press **F12** to select that card, then `Ctrl+Shift+<code>` retags
  it (e.g. `Ctrl+Shift+G` → Goal) or `Ctrl+D` deletes it; F12 again returns to the field.
- **Keyboard-layout indicator.** A badge left of the add field shows the active layout (`EN` / `ع`)
  and updates live when you switch layouts (e.g. via Caps Lock).
- **"Just added" card.** Shows the tags in a fixed left-to-right order — **type · priority · subject**
  — then a word preview: the first *N* and last *M* words of the note plus its word count
  (e.g. `buy milk … the shop  (7w)`). *N*/*M* are configurable in Settings ("Preview words head/tail").
- **Default type/priority/subject.** A `*` on a code in Settings marks the default that a new note
  starts with (and is used when nothing is selected). The `*` is only a marker — it's stripped from
  the code everywhere (chip, tag, file name); if several are starred, the first one wins.
- **Safe delete.** `Ctrl+D` on a selected note **moves it to a `Deleted/` subfolder** (recoverable)
  rather than erasing it; the confirmation shows a summary of the note's content (and defaults to the
  "Yes" button) so you don't delete the wrong one.
- **Paste-ready on focus.** Whenever the window gains focus, the cursor jumps into the add field
  (unless you're navigating cards or searching) so a paste + `Ctrl+Enter` is immediate.
- **Per-line text direction.** Mixed Arabic/English is handled per line, the way xed/kate do:
  GTK auto-detects each paragraph's base direction, so each line reads (and aligns) correctly.
- **Chronological list.** Oldest at the top, newest at the bottom. Saving a note scrolls the list to
  the bottom so the just-added note is visible (immediate feedback). Click (or `Enter`) a note to
  edit it; editing keeps its original timestamp and only renames the file if you change its tags.
- **Cross-compatible filenames.** The scheme matches the Android app exactly, so the same notes
  directory works on both.
