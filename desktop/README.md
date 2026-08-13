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
| `filters.py` | Pure-Python filter model: which notes match the sidebar's filters, and how many notes are behind every individual option. No GUI, like `notes.py`. |
| `sidebar.py` | The GTK3 filter sidebar — calendar heat map, tag facets, hashtag cloud, counts. Draws what `filters.py` decides. |
| `knotes.py` | The GTK3 window: note list, tagging input bar, editing, search, settings, shortcuts. |
| `style.css` | Appearance. Plain hex colours — change the accent in one place. |

## Keyboard

Everything is reachable from the keyboard (press **F1** in the app for this list):

| Keys | Action |
|------|--------|
| `Ctrl+Enter` (or plain `Enter` if "save on enter" is on) | Save / send the note |
| `Esc` | Text field ⇄ board (once → field · again → board) |
| `Ctrl+Shift+<letter>` | Type/category: retags the selected card (renames its file), else sets the add form's chip |
| `Ctrl+Shift+<digit>` | Priority: retags the selected card, else sets the add form's priority |
| Click a card / Arrows | Select a card · move between cards (↑↓ in a column, ←→ across) |
| `Enter` (card selected) | Open the selected note in your editor |
| `Ctrl+D` (card selected) | Move the selected note to the `.deleted/` folder (with confirmation) |
| `Ctrl+M` / `Ctrl+click` | Mark/unmark a card for merge (first marked = survivor) · `Esc` clears |
| `Ctrl+Shift+M` | Merge marked notes into the survivor; the rest move to `.merged/` |
| `Space` (card selected) | Quick-append a comment to the note (inline field · `Enter` adds · `Esc` cancels) |
| `Ctrl+O` (card selected) | Open the note's companion files folder (created on demand) |
| `Ctrl+1 … Ctrl+9` | Switch to the 1st … 9th configured note **folder/repository** — not type; priority uses `Ctrl+Shift+<digit>` instead |
| `Ctrl+F` | Toggle search |
| `Ctrl+B` | Show / hide the **filter sidebar** (calendar heat map, tag facets, hashtag cloud, counts) |
| `Ctrl+R` | Refresh from disk (pick up notes synced from another machine) |
| `Ctrl+T` | Toggle light / dark theme |
| `Ctrl+L` | Toggle **minimal side-note mode** (board hidden) |
| `F12` | Toggle focus between the card area and the add field (in minimal mode, selects the lone card so you can retag/delete it) |
| `Ctrl+,` | Settings |
| `F1` | This help (shown live, always in sync with the code) |
| `Ctrl+Q` | Quit (saves an unsent draft first) |

There's no `Alt`-based or bare-`Delete` shortcut — every note-editing shortcut is `Ctrl`(`+Shift`)`+<key>`, and priority is `Ctrl+Shift+<digit>`, not `Alt+<digit>`. Press **F1** in the app any time — this table is generated from the same source, so it can't drift.

## Notes on behaviour

- **Filter sidebar (`Ctrl+B`).** A collapsible left nav bar that filters the board, with **the
  number of notes behind every option** shown next to it:
  - **Tags** — a cloud of the `#hashtags` written in note **bodies**, each sized by how often it is
    used and carrying its count. Free-form, unlike the single type/category codes in the file name:
    nothing has to be declared before use and a note can carry any number, which is what a cloud
    suits and a fixed row list doesn't. Clicking one narrows the board to the notes carrying it —
    **one tag at a time**, so a second click moves the selection rather than intersecting. A
    `## Markdown heading` and a `http://…#fragment` are not tags.

    It sits **first**, above the calendar, and that is deliberate. Seven expanded sections come to
    roughly twice a laptop sidebar's height, so whatever lands fifth is invisible without a long
    scroll — and the tag cloud is the one dimension you go *looking* for rather than pick from a
    list already on screen. It is also the most compact of them. A folder whose notes carry no
    hashtags hides the section entirely, header and all, rather than spending that slot on an
    empty box.
  - a **calendar heat map** — one cell per day, shaded by how many notes that day has, so a month
    of note-taking is legible at a glance. A **Created / Updated** switch drives both the shading
    and what a clicked day means: *Created* uses the note's **file-name timestamp** (never the
    file's own dates — retagging rewrites the file and would otherwise drag every note to today),
    *Updated* uses the file's mtime, i.e. the "revisited it" signal. The two use different colour
    ramps (blue vs. teal) so a day of capture never looks like a day of review. Click a day to
    filter to it, shift-click for a range; each cell also carries its own count, and its tooltip
    always shows both numbers.
  - **Priority / Type / Category** facets — every option the folder defines (even at 0), plus any
    *foreign* code the notes actually use but the folder doesn't define (shown in italic amber,
    e.g. a `~Dev` category synced in from a folder with a different taxonomy).
  - **Non-standard** — include / hide / **only**. Non-standard means a visible file or folder in
    the note directory whose **name isn't the canonical `<timestamp>~<type>~p<n>~<subject>.md`
    format** — exactly the "needs metadata" items. (Hidden dotfiles, companion `.d/` folders and
    the `.deleted`/`.merged`/`.imported` housekeeping folders are never in scope.) Tags don't enter
    into it: `…~T~p3~Dev.md` is standard even where `Dev` isn't a configured category — that shows
    up as a foreign-code row instead. "Only" is the clean-up queue; select an item and
    `Ctrl+Shift+<code>` files it in.
  - **Bad taxonomy** — include / hide / **only**. The *other* clean-up queue, and deliberately
    separate from Non-standard: that one is about the file name's **shape**, this one about its
    **values**. `2026_08_08-101500~Y~p3~A.md` is perfectly well-formed but invisible to every type
    filter when no type `Y` exists. The fixes differ — retag vs. rename — so a file is never counted
    in both, and a *blank* tag is not a bad tag.

  Counts are *faceted*: each option is counted with all the **other** filters applied but its own
  dimension ignored, so the number next to *Type W* always means "how many I'd see if I also picked
  W" rather than dropping to zero because another type is selected. The numbers follow the search
  box too. Collapsed, the whole bar shrinks to a ~26px rail. Sections collapse individually and the
  layout is remembered; **which options are picked deliberately isn't**, so a stale filter can never
  greet you with an empty board on the next launch.
- **View mode + sorting on the toolbar.** A linked kanban / list / columns / minimal button group
  and a sort dropdown (Date / Category / Type / Priority) live in the header bar — they're switched
  too often for the Settings dialog. Radio-style (exactly one mode active), persisted, applied
  immediately; `Ctrl+L` still toggles minimal mode and keeps the group in sync. The kanban only
  shows category columns that have notes.
- **Columns view.** The list view, wrapped: no headings and no grouping, just every note in the
  chosen sort order read left→right and then down, in as many columns as the window fits. Cards are
  pinned to **Card max width** (Settings) rather than sharing the width out, so they stay readable
  on a wide screen instead of stretching into long lines — which is why widening the window adds a
  column instead of fattening the cards. Arrow keys walk it like the kanban (↑↓ within a column,
  ←→ across); it re-flows on resize only when the column count actually changes.
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
- **Safe delete.** `Ctrl+D` on a selected note **moves it to a `.deleted/` subfolder** (recoverable)
  rather than erasing it; the confirmation shows a summary of the note's content (and defaults to the
  "Yes" button) so you don't delete the wrong one.
- **Hidden housekeeping folders.** The app's own subfolders — `.deleted/` (soft-deletes), `.merged/`
  (absorbed notes) and `.imported/` (reserved for a future local import archive) — are all hidden
  (leading dot) and lowercase, so they never clutter the folder or show on the board. Legacy or
  alternate-cased variants (`Deleted`, `Trash`, `Imported`, …) are recognised and kept off the board
  too.
- **Paste-ready on focus.** Whenever the window gains focus, the cursor jumps into the add field
  (unless you're navigating cards or searching) so a paste + `Ctrl+Enter` is immediate.
- **Per-line text direction.** Mixed Arabic/English is handled per line, the way xed/kate do:
  GTK auto-detects each paragraph's base direction, so each line reads (and aligns) correctly.
- **Chronological list.** Oldest at the top, newest at the bottom. Saving a note scrolls the list to
  the bottom so the just-added note is visible (immediate feedback). Click (or `Enter`) a note to
  edit it; editing keeps its original timestamp and only renames the file if you change its tags.
- **Mass-tagging keeps your place.** Retagging the selected card with `Ctrl+Shift+<code>` renames its
  file but leaves the view where it is — the list no longer snaps to the top on every edit — so you can
  select a card partway down and rattle off type/priority/category changes without losing your spot.
  In both list and kanban the view then follows the selection as you arrow to the next card. Pair it
  with the **Date created** sort (so a retagged note doesn't relocate) for a smooth top-to-bottom pass.
- **Cross-compatible filenames.** The scheme matches the Android app exactly, so the same notes
  directory works on both.
