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

On Windows, `run.bat` (see [Windows](#windows) below).

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

## Windows

The app itself is portable — plain-text notes, stdlib-only storage, GTK3 for the UI. The only real
work is getting **PyGObject + GTK 3** onto the machine, since Windows has no system GTK. Two ways;
MSYS2 is the reliable one.

### MSYS2 (recommended)

Install [MSYS2](https://www.msys2.org/), then in the **MSYS2 UCRT64** shell:

```bash
pacman -S mingw-w64-ucrt-x86_64-python \
          mingw-w64-ucrt-x86_64-python-gobject \
          mingw-w64-ucrt-x86_64-gtk3 \
          mingw-w64-ucrt-x86_64-librsvg      # so the SVG window icon loads

cd /c/path/to/desktop
python knotes.py
```

`librsvg` is only for the icon — without it the window just falls back to a default one.

### pip + a GTK 3 runtime

`pip install pygobject` needs GTK 3 already present and on `PATH` (e.g. a
[gvsbuild](https://github.com/wingtk/gvsbuild) build). Workable, but expect to fight the build
step; MSYS2 avoids all of it.

### Launching

`run.bat` (double-click, or run from a shell) resolves its own directory, prefers `pythonw.exe` so
no console window hangs around, and prints a hint if Python or GTK is missing.

For a Start-menu entry or a taskbar pin, right-click `run.bat` → **Create shortcut**, then set the
shortcut's icon (Properties → Change Icon) and drop it in
`%APPDATA%\Microsoft\Windows\Start Menu\Programs`. There's no `install.sh` equivalent — `.desktop`
files are a freedesktop thing.

### What differs on Windows

| Behaviour | Linux | Windows |
|-----------|-------|---------|
| Open a note (Enter / click) | configured opener, else `xdg-open` | configured opener, else the file's default app (`os.startfile`) |
| Reveal a note's files folder (`Ctrl+O`, auto-open) | Nemo, as a tab in the window already open | Explorer, one window per reveal |
| Panel pinning | `StartupWMClass` matched by the `.desktop` file | `set_wmclass` is a no-op; pin the shortcut instead |

Everything else — config next to the program, the filename tag scheme, `.deleted`/`.merged`
folders, the Syncthing-shared notes directory — behaves identically. Set `notes_dir` in the
settings to a Windows path (`C:\Users\you\knotes`); `~` still expands.

## Files

| File | What it is |
|------|------------|
| `notes.py`  | Pure-Python storage: the file-name tag scheme, parsing, list/save/update/delete. No GUI — easy to read and reuse. |
| `config.py` | App-wide `Settings` (saved next to the program in `config.json`) **and** `RepoConfig` — the per-folder tag options saved inside each note folder as `.knotes.json`. |
| `filters.py` | Pure-Python filter model: which notes match the sidebar's filters, and how many notes are behind every individual option. No GUI, like `notes.py`. |
| `sidebar.py` | The GTK3 filter sidebar — calendar heat map, tag facets, hashtag cloud, counts. Draws what `filters.py` decides. |
| `tagbank.py` | Pure-Python tag bank: reads `tag-bank.md` and ranks matches for the `#` completer. No GUI, so the matching rules — the part most likely to need adjusting — can be exercised without a display. |
| `backend.py` | Optional nibras sync: fetches the type/category vocabulary so captures map to real modules. Entirely skippable — a blank URL means fully offline. |
| `knotes.py` | The GTK3 window: note list, tagging input bar, editing, search, settings, shortcuts, the side editor pane. |
| `style.css` | Appearance. Plain hex colours — change the accent in one place. |
| `tag-bank.md` | **Yours to edit.** The hashtag vocabulary the `#` completer offers: one tag per line, canonical first, then aliases. Comments and blank lines ignored; re-read whenever it changes. |
| `run.sh` / `run.bat` | Launchers for Linux and Windows. Each checks for GTK first and points at the install command. |

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
| `Ctrl+E` | Show / hide the **side editor pane** — reads the selected note, or edits it (autosaved) |
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
    used and followed by its count. Free-form, unlike the single type/category codes in the file
    name: nothing has to be declared before use and a note can carry any number, which is what a
    cloud suits and a fixed row list doesn't. Clicking one narrows the board to the notes carrying
    it — **one tag at a time**, so a second click moves the selection rather than intersecting. A
    `## Markdown heading` and a `http://…#fragment` are not tags.

    They're drawn as **links, not buttons** — coloured words with their counts beside them, the
    picked one bold, accented and underlined. They're still toggle buttons underneath (that's what
    carries the picked state and keyboard focus), but a cloud of framed buttons at five different
    font sizes reads as a ransom note and spends the sidebar's best slot on chrome.

    Only `.md`/`.txt` content is scanned, so an unsettled `.drawio` or `.pdf` sitting in the folder
    can't seed a tag out of its own markup (see "Settling an unsettled item").

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

    **Unsettled items always sit on today**, on *both* date metrics — see "Settling an unsettled
    item" below for why and what filing one does.
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
- **Every count is grouped in threes** — `19,123`, not `19123`. One helper, `notes.fmt_count`, backs
  all of them (header bar, sidebar header, facet rows, calendar cells and their tooltips, the tag
  cloud, the hashtag strip, word counts, attachment counts, the merge hint), so they can't drift
  apart. Always a comma, never the locale's separator: under a locale that groups with a full stop
  "19.123" reads as a decimal, and a folder of nineteen thousand notes would look like nineteen. The
  build number in the title and the calendar's day-of-month numbers are identifiers, not counts, and
  stay ungrouped.
- **Opens on today.** The one filter that *is* applied at launch: today's day, exactly as if you'd
  clicked it in the calendar — the board starts on what you've captured today, and the calendar
  shows the day selected so **Clear all filters** gets you the whole board back. It can't go stale
  the way a remembered selection could: it's always today, never a day you left behind. Turn it off
  with **Open filtered to today's notes** in Settings.
- **Unsettled items sit on today.** A file or folder dropped into the note directory that isn't a
  canonical note yet (the *non-standard* set) is filed under **today** by the calendar, whichever
  date metric is selected — never under its own mtime. It has no real date to be placed by: there's
  no file-name timestamp to read, and the mtime is whatever the download, copy or sync that dropped
  it there happened to set, often months old. Filed on that, it would land on a day nobody visits
  and the default today view would never surface it. On today it keeps presenting itself, every day,
  until it's settled — outstanding work rather than a thing you have to remember to go looking for.
- **The backlog is on the title bar.** Straight after the note count: `… · 412 notes · 3 to settle`
  — how many unsettled items are waiting, so the size of the job is visible without hunting for red
  cards. It's a *work* count, not a note count, so it ignores the filters entirely and always
  reports the whole folder; a today-filtered board that shows none of them still tells you they're
  there. At zero it disappears rather than reading "0 to settle" — nothing to do says itself.
- **Settling an unsettled item** (`Ctrl+Shift+<code>` on a selected one) has three outcomes, and
  which one you get depends on *what the thing is* — never on whether its bytes happen to decode as
  text:
  - a **folder** becomes a new note named after it, with the folder itself turned into that note's
    companion `.d/` folder, so everything inside comes along as attachments;
  - a **`.md` or `.txt` file** is renamed into the canonical format and its original name becomes
    line 1 of the body — the content *was* the note, and the name would otherwise be lost once it
    turns into a timestamp;
  - **any other file** — `.drawio`, `.pdf`, an image, a spreadsheet — becomes an **attachment**: a
    new note is written whose single line is the original file name, and the file moves into that
    note's `.d/` folder keeping its own name. Its bytes are an artifact, not prose, so nothing is
    reinterpreted: `diagram.drawio` becomes a note reading "diagram.drawio" with the diagram
    attached, not a note whose body is a screenful of XML.

  Extension decides, deliberately, because "does it decode as UTF-8" is the wrong question — a
  `.drawio` is valid XML and an `.svg` or `.csv` is plain ASCII, so a decode test waves all of them
  through and lands their markup in a note body. Both file cases take their timestamp from the
  file's own mtime, so a settled item drops off today's board and lands at its real chronological
  position.
- **A blank board always says why.** Rather than nothing at all, the board carries one muted line:
  **"No notes entered today"** on a day you haven't captured anything (the launch state above, and
  the reason it's safe to filter on open), or "No notes match the search" / "No notes match the
  filters" / "No notes in this folder yet" as the case warrants. The today wording is used only for
  *exactly* the opening state — today's day and no other filter — so a hand-picked filter that
  happens to match nothing never claims to be about today.
- **Attached files are clickable.** The `📎` row on a card lists up to 5 names, each truncated to
  **Card file characters** (Settings) — click one to open that file with the system default handler
  (never the configured `opener`, which is a text editor picked for `.md` notes and wouldn't know
  what to do with a PDF). Hovering a name shows it in full; the trailing `…` and the `(n)` count
  open the companion `.d/` folder, and their tooltip lists **every** attached file, which is the
  only place names past the fifth are readable.
- **Long cards report how far through them you are.** Past **Counts shown from** characters
  (Settings, default 120) the word count grows two companions, and all three sit at the end of the
  **action row** rather than trailing the title:
  `1,204 words · 2 highlights · 4 time marks`. The title is what the eye lands on when scanning the
  board, and a parenthetical of three numbers in front of the note's own first words got in the way
  of reading it; down with the actions it reads as a footer.
- **Every card shows when it was made and when it was last touched**, at the right-hand end of the
  action row: `2026-08-09 11:50  →  2026-08-14`. The two come from different places on purpose —
  creation is read from the FILE NAME's timestamp, fixed at capture and untouched by later edits,
  while the modification date is the filesystem's mtime, which any edit bumps. Comparing the pair is
  the only way to see that a note captured a fortnight ago is *still* being worked on; neither date
  alone can tell you that. The modification date turns **orange** when it is more than 3 days after
  the creation date and **red** past 7 — a long-running note is either a live document or something
  that quietly outgrew being a note, and either way it's worth seeing from across the board.
  Measured in whole calendar days, because these read as dates and "three days" should mean three
  dates on the calendar rather than 72 hours to the minute.
  - **Highlights** — how many `==marked==` clauses the note has, counted as *runs*, so one long
    marked sentence counts once. Shown even at **zero**, because on a long note that is precisely
    the signal: nothing worked through yet.
  - **Time marks** — one per non-blank line across every `.tmk` file in the note's companion
    folder. A `.tmk` sits beside the media it belongs to (`talk.mp3` → `talk.tmk`) and holds one
    timestamp per line, `[0:43]` — a position marked while listening. The lines are counted, never
    parsed, so a hand-edited or differently-stamped file still counts. Unlike the highlight count
    this one is **hidden at zero**: a note with no recording has no notion of a time mark to be at
    zero of.
  - Short captures get neither — the whole note is already on the card. `0` puts them on every
    card. The `.tmk` files are read lazily and memoised, and only for notes whose (already
    in-memory) file list contains one, so a board of thousands doesn't open a folder per note.
- **Side editor pane (`Ctrl+E`, or the pencil on the toolbar).** A panel down the right-hand edge,
  running the full height of the main split — beside the board, the "just added" strip *and* the add
  form, not stopping above them, so a long note gets the whole window to be read in. It shows
  whichever note the board has selected: clicking or arrowing to a card is what "open this one"
  means, so there's no second gesture to learn. Drag its left edge to resize; the width and whether
  it's open are remembered, but never *which* note it had — that always comes from the selection.
  It is deliberately not a second way to create notes; the add form below still owns "new".
  - **Reading mode (the default).** The note laid out to be read: the `==` highlight markers hidden,
    the clauses they mark painted, and no cursor. **Clicking a clause toggles its highlight** — the
    clause being the run of text between sentence breaks (`. , ; : ! ?` and their Arabic
    counterparts `، ؛ ؟`, plus the line end; dashes are not breaks, so `- call Sara` stays one
    clause). Clicking a clause that's only partly marked marks all of it; clicking a fully marked
    one clears it. **The pointer is a highlighter pen**, drawn with its nib in the configured
    colour, so both what a click does and what colour it will paint are legible before anything is
    clicked; editing mode gets the usual I-beam, and a note that can't be written gets the plain
    arrow rather than a pen promising a mark it won't make. Clicking in the empty space past the
    end of a short line marks that line's last clause — the commonest way to aim at a short line —
    while a click on a blank line, or below the text entirely, does nothing. Marking a clause does
    **not** move the view: reading mode doesn't render the markers, so the text on screen is
    unchanged and only the painting is redone. The mark is stored in the file as `==like this==` — the same notation Obsidian
    and GitHub markdown use, so it survives Syncthing, means something in any editor, and is the
    same text the phone sees. Reading is the default because re-reading a capture is the commoner
    act, and because a mode whose only possible edit is a highlight is the safer one to land in.
  - **Editing mode.** The raw text, markers and all, **saved automatically** ~0.9s after you stop
    typing (`editor_autosave_ms` in `config.json`) and always before anything that could outrun it:
    retagging, deleting, merging, quick-appending, switching folders, closing the pane, and quitting
    all flush first. The status line under the pane says which state it's in — `Editing…`,
    `Saved 12:04:33`, or the error if a write ever fails, because a save with no feedback is
    indistinguishable from a click that missed. `Esc` or `Ctrl+Enter` saves and hands the keyboard
    back to the board.
  - An **unsettled item** is shown but never written — read-only in both modes, with the reason in
    the status line. Writing one through the normal path would rename it into the canonical format:
    a silent promotion, from nothing but a keystroke or a stray click.
  - **Arabic notes get an RTL pane.** Each paragraph picks its own direction from its own text (as
    everywhere else in the app), but the *pane* follows the note — which is what decides lines with
    no strong character of their own: a bare number, a date, the empty line you're about to type
    Arabic into. Following the locale instead would put the cursor on the wrong side of every new
    line.
- **Highlight colour** is a Settings colour button, stored as `#rrggbb`. The text on top switches
  between black and white by luminance, so a dark pick stays readable rather than vanishing into the
  theme's own foreground — and the same colour paints the same clause on the cards, so a highlight
  looks the same wherever the note is shown.
- **Cards keep their line breaks, and line 1 is bold.** A knotes note's first line *is* its title in
  practice — it's how they're written, what a settled file is named after, and what the phone side
  shows — so the card renders it bold and the rest of the note beneath it at normal weight, breaks
  intact, instead of flattening everything into one run of text where the title can't be picked out
  (`Note.card_preview`). Blank lines are dropped and each line is
  stripped: a card has about four visible lines, and the usual `title / blank / body` shape would
  spend one of them on nothing — a card is a preview, not the document. **Preview characters** and
  **Preview tail characters** cap the whole text exactly as before. Cards also paint `==highlighted==`
  clauses and never show the markers themselves: the app writes that notation, so it owes every view
  a clean rendering of it. (`card_preview` returns `(text, highlighted)` runs and does its tidying
  and capping over `(character, flag)` pairs, so the marks move with the text through every slice
  instead of the two being kept in step by hand.)
- **Every card carries its actions.** A muted row under each note: **open folder**, **open note**
  (icon *and* words — the two reached for constantly), then two icon-only buttons, copy the file
  name and copy the full path. The two copies had no way in at all before; the two opens only had
  `Ctrl+O` and `Enter`, which you had to have read F1 to know — so each tooltip names its shortcut
  and the row teaches the keys rather than replacing them. A copy's tooltip is the exact string it
  will put on the clipboard, which is also what tells the two apart; the status bar then confirms
  it for four seconds, since a copy that worked otherwise looks just like a click that missed.
  Clicking any of them selects the card, so the keyboard lands on the note you just acted on. On a
  **needs metadata** placeholder, *open folder* is shown but disabled — the item has no companion
  folder until it's settled, and the tooltip says so instead of the button quietly doing nothing.
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
  too — `trash`/`Trash`/`.trash`/`Trashed` included, dotted or not.
- **A `#` offers the tag bank.** Typing `#` while writing a note — in the side editor **or** the add
  field, since a `#` means the same thing in both — opens a list of matching tags from
  **`tag-bank.md`** (next to the program, or wherever `tag_bank_path` points). `↑`/`↓` move,
  `Enter`/`Tab` insert, `Esc` dismisses, and a click picks.
  - The bank is one tag per line: the canonical tag first, then aliases, comma-separated. Comments
    (`#` at line start) and blank lines are ignored, so it can carry its own headings. It is
    re-read whenever it changes on disk — edit it in another window and the next `#` sees the edit.
  - **Picking an alias inserts the canonical tag.** That is what aliases are *for*: `#تفسير` and
    `#tafsir` both put `#A.tafsir` in the note, so a vocabulary can be searched in either language
    without fraying into three spellings of the same idea. The alias that matched is shown beside
    the canonical tag in the list, which is what explains why a Latin tag turned up under an Arabic
    query.
  - Matching is case-folded and substring-based over both the canonical tag and its aliases —
    deliberately not fuzzy. A few hundred tags is small enough that substring matching stays
    predictable, and a completer that guesses at typos inserts the wrong tag with total confidence.
  - A `#` mid-word (`C#`, `rgb#fff`) doesn't start a tag, and neither does one with a space after it
    — so a markdown heading is still a heading. A missing bank file simply means the list never
    appears.
  - Hashtags now admit **dots and hyphens** (`#A.quran`, `#آية-اليوم`) in both the card chips and the
    sidebar's cloud; a trailing `.` or `-` belongs to the sentence and is trimmed. Without that the
    completer would insert tags the board couldn't see — `#A.quran` would have registered as `#A`.
- **Ignored sidecar files.** `notes.sqlite` — plus the `-wal`, `-shm` and `-journal` files SQLite
  writes beside it while it's open — and `claude.md` (an instruction file for a tool that reads the
  folder, not a note) are hidden as if they were dotfiles. It isn't dotted, so nothing
  else would keep it off the board, and it would otherwise land there as a **needs metadata** card
  dated today asking to be settled — which is exactly what it must never be, since promoting it
  renames the live database. The sidecars matter as much as the file: they blink in and out as the
  database is used, so ignoring only `notes.sqlite` would still let two mystery cards flicker onto
  the board. Matched case-folded (`NoteRepository.IGNORED_FILE_STEMS`), so a `Notes.sqlite` or
  `CLAUDE.md` synced from another platform is caught too. Only these exact names —
  `my-notes.sqlite`, `notes.sqlite.bak` and `claude.md.bak` are still yours to settle.
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
