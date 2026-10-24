# Nibras Image Annotator

`scr-annotator` on disk, **Nibras Image Annotator** on screen: the filenames,
the `.desktop` `StartupWMClass` and the DBus id keep the old name so the
launcher icon and the single-instance handoff go on working.

Keyboard-first review of a folder of screenshots. Pick a folder in the tree,
type a label, mark up the image, move on. Labels and marks save the moment you
navigate away — there is no save key. Two dialogs in the whole app: the one
that deletes a file, and the folder chooser behind the **Move →** button.

Built for a folder of interval captures that had grown past 270 files, of which
42 had ever been labelled. The bottleneck was never the typing; it was the six
clicks around it. First real session: **11 files labelled in 2m 23s**, about
eleven seconds each including reading the screenshot.

## Files

| File | Purpose |
|---|---|
| `scr_annotator.py` | the app — one file, no package |
| `make_icon.py` | regenerates `icon.svg` and `icon*.png`, the window/Alt+Tab icon |
| `icon.svg` | the mark at source; generated, not hand-edited |
| `scr-annotator.desktop` | menu entry; `StartupWMClass` pairs window to icon |
| `test_naming.py` | tests for the half that can silently ruin a folder |
| `CHANGELOG.md` | what changed, per commit |

## Running

```bash
./scr_annotator.py                      # defaults to ~/new
./scr_annotator.py "/some/other/folder"
./scr_annotator.py "/path/to/shot.jpg"  # opens that file
```

**Single instance.** Launching it again does not open a second window: the new
command line goes to the running one over DBus, and it switches to whatever was
named — whichever folder it happened to be showing.

Non-images are ignored. **Newest file first**, because a screenshot is usually
reviewed seconds after it is taken.

### From the Print key

`~/prj/screenshots/sch.sh` is bound to Shift+Print in Cinnamon (`sch-region.sh` to Print). It captures, copies the new
file's path to the clipboard, and brings the reviewer up:

```bash
flameshot screen -p /home/akil/new      # `screen`, not `full` — see below
sleep 1                                 # flameshot returns before the file lands
… newest file → xclip …
… raise the running reviewer, or launch one …
```

`flameshot full` grabs the whole X canvas, which across two monitors is one
3600×1200 image containing both screens. `flameshot screen` with no `-n` takes
the screen the cursor is on.

The app **watches the folder**, so pressing Print while it is already open
makes it jump straight to the new shot — no relaunch, no rescan.

## Keys

| | |
|---|---|
| `Enter` · `↓` · `PgDn` | save, next image |
| `↑` · `PgUp` · scroll | save, previous |
| `←` `→` | move the text cursor — they edit, they do **not** navigate |
| `Backspace` | delete a character — `Del` belongs to the file |
| `Alt+P` `Alt+H` `Alt+R` `Alt+C` | pen · highlight · rectangle · crop |
| drag | apply the selected tool |
| right-click · `Ctrl+Z` | undo the last mark |
| `[` `]` | rotate a quarter turn left / right |
| `F7` | apply pending edits now, without leaving the image |
| `F3` | show/hide the folder tree |
| `Ctrl+L` | the path field above the tree — type or paste a folder or image path, `Tab` completes, `Enter` opens, `Esc` goes back |
| `Alt+M` | move this image to the chosen folder and show the next — the **Move →** button in the header picks the folder |
| `Tab` | focus the tree, then arrows and `Enter` — `PgUp`/`PgDn` keep paging images |
| `Ctrl+Shift+C` | copy the full path of this image to the clipboard — also the **Copy path** button in the header |
| `Del` · `F8` | move the file to the trash — asks first, `Enter` confirms |
| `F5` | reload from disk, discarding unsaved marks |
| `F11` / `F1` | fullscreen / help |
| `Esc` · `Ctrl+Q` | save and quit |

**Almost no modes.** The keyboard always edits the label and the mouse always
marks, so there is nothing to switch between for the common case. The one piece
of state is *which* mark the mouse makes, and it is on screen in the toolbar
rather than remembered.

Navigation deliberately avoids `←`/`→`: the label field is always focused, and
stealing the arrow keys would make it impossible to fix a typo mid-word.

The label field pre-selects on load, so a run of frames from one task is
`Enter` `Enter` `Enter` with no typing, and typing over it replaces.

## The tools

The letters need **Alt**. The label field is always focused, so a bare `p`
goes into the label — binding bare letters would mean `p`, `h`, `r` and `c`
could never be typed into one, and "crop", "http" and "chart" all contain them.
`TOOL_KEYS_NEED_ALT = False` switches to bare letters if you would rather have
the keystroke; the button captions follow the setting.

| Tool | Drag does | Written as |
|---|---|---|
| **Pen** | freehand line | green, 3px |
| **Highlight** | horizontal band; the drag sets width only, the thickness is fixed | translucent yellow, composited not painted |
| **Rect** | outlined box | green, 3px |
| **Crop** | the region to keep; everything else dims | applied last, one per image |

Marks stay **pending** until you navigate away — visible, adjustable, undoable.
That is the same contract the label has, and it means a stray drag costs one
`Ctrl+Z` rather than a trip to `.originals/`.

`[` and `]` turn the image a quarter turn and are pending in the same way, so
four presses cost one write and `Ctrl+Z` takes them back one at a time. They are
the one printable key that does not reach the label. A turn is written before
the marks are drawn, so anything already pending when you rotate is written out
first — otherwise it would land sideways.

## What it writes

**The label becomes a filename suffix**, appended to whatever the file is
already called:

```
2025-04-22_09-55-54 sch.png   +  "fixing vcn script"
→ 2025-04-22_09-55-54 sch fixing vcn script.png
```

Nothing is parsed out of the original name — there is no format to rely on, and
the app works the same on `holiday photo 2019.jpg`.

**Marks are burnt into the file** at full resolution, written to a temp file and
moved into place so a crash can never leave a half-written image.

> **JPEG loses a little every time.** Marking re-encodes the whole file at
> quality 95. Once is invisible; marking the same JPEG across several sessions
> compounds. Labelling is a pure rename and always lossless, and `.originals/`
> always holds the pristine first copy.

Three things appear in the folder: the renamed files, `.originals/`, and
`.scr-annotator-log.tsv`.

## Design notes

These exist because the obvious implementation is wrong in a way you would not
notice until the folder is ruined.

**Labels replace, they do not accumulate.** On a second visit the app cannot
tell which part of `a b c.png` was the label — a stem of `a` with label `b c`
is indistinguishable from a stem of `a b c` with none. Guessing wrong makes the
name grow on every pass: `shot.png` → `shot a.png` → `shot a a b.png`. So every
rename is recorded in `.scr-annotator-log.tsv`, and startup replays that log to
recover each file's original stem. Editing a label then replaces it, and
clearing it restores the original name exactly.

**A failed save must block navigation.** With no save key, an autosave that
fails quietly is how work disappears. `commit()` returns a boolean and every
caller — next, previous, quit, folder switch, and the window close button —
refuses to proceed when it is false, leaving the label and the marks on screen.
This is not hypothetical: the first version wrote its temp file as
`<name>.png.scr-tmp`, Pillow could not infer a format from `.scr-tmp` and
raised, and the broad `except` swallowed it. The mark was lost *and* the rename
was skipped, with nothing but a two-second toast to show for it.

**Crop is applied last**, whatever order it was drawn in. The other marks were
placed in the coordinates of the uncropped image, so cropping first would move
them out from under the pen.

**The folder is watched, not rescanned.** The capture hotkey is pressed *at*
this app — you take a shot in order to annotate it — so a listing read once at
startup would miss exactly the file you wanted. A `Gio.FileMonitor` picks new
images up, waits 500ms for the writer to finish, and jumps to them.

**Moving is a sort, so it does not ask.** `Alt+M` sends the image to the folder
named on the **Move →** button and shows the next one, which is how a subset is
pulled out of a large folder: pick the folder once, then one key per keeper.
It is not confirmed because, unlike the trash, it is undone with a plain `mv`,
and a confirmation would double the keystrokes that are the point. The label
and any pending marks are saved first, so what lands there is what was on
screen. The file keeps its name, and the move is written to the destination's
`.scr-annotator-log.tsv` as a rename from the original, so the label is still
recognised as a label there and can be edited or cleared; the copy in
`.originals/` follows it. The letter needs `Alt` for the same reason the tools
do, and follows `TOOL_KEYS_NEED_ALT` with them.

**Deleting is the one action that asks.** Everything else autosaves precisely
because it is reversible — a label is a rename, a mark has a copy in
`.originals/`. Removing a file is not, so it gets the only dialog in the app,
with OK focused and set as the default response: `Del`, `Enter`. It goes to the
trash rather than being unlinked, so even a confirmed mistake is recoverable.

The cost of that choice: `Del` is taken from the text field, and `Enter`
confirms the dialog while also being the advance key everywhere else. A
mistyped `Del` followed by a reflexive `Enter` will trash a file. Moving focus
to Cancel is a two-line change if that trade stops being worth it.

**The chrome is Solarized, the canvas is not.** The header bar, tree and status
bar use Solarized light — base3 paper, base2 dark beige on the bars, matching
the audio-player — but the surround behind the image stays a neutral
near-black. A tinted background shifts how you read the colours of the
screenshot sitting on it, which is the one thing an image review tool must not
do.

**A path field sits above the tree.** When the folder is known, the tree is a
dozen expand-and-scroll clicks to reach it. The field takes a folder or a single
image (`~`, quotes and `file://` are fine), `Tab` completes like a shell, and
`Enter` opens it and expands the tree down to it — so a folder with no images of
its own still lands you one arrow key from its subfolders. A path outside `$HOME`
re-roots the tree at `/`. While the field has focus every key is its own, ahead
of the tool letters: a path is full of `c`s and `p`s.

**The rail is measured, not guessed.** It opens exactly as wide as the folder
names need and no wider, which means measuring the text with Pango: the name
cell ellipsizes, and an ellipsizing cell tells GTK it needs a few characters,
so asking the widget would open the rail on rows reading `sy…wt`. The answer is
clamped by `TREE_MIN` and `TREE_MAX`, and set once — expanding a deep folder
afterwards scrolls the tree sideways rather than pushing the image over. Set
`TREE_WIDTH` to a pixel count to go back to a fixed rail.

**The tree counts, it does not walk.** Each directory shown costs one
`scandir`, which yields both its image count and whether it has children — so
the expander arrow never lies and the count is never a recursive total.

**Every gi namespace needs pinning, not just Gtk.** `Gdk` sorts before `Gtk` in
the import list, so without its own `require_version` it loads as 4.0 and Gtk
3.0 then refuses to start.

**The icon is the shared mark, not its own thing.** Same `#133155` ground and
the same white line-art on the same 24-unit grid at the same 1.6 stroke as the
audio player's, so the two sit together in the launcher as one family; only the
glyph differs — a picture here, a sound wave there. `make_icon.py` holds that
geometry once and emits both the SVG and the PNGs from it, so the vector and
the bitmaps cannot drift apart.

**`set_prgname` is what makes Alt+Tab show the icon.** GTK 3 derives `WM_CLASS`
from the program name, and it has to match the `.desktop` file's
`StartupWMClass` or the launcher and the window are treated as unrelated. This
is what the deprecated `set_wmclass` used to do.

**Originals are kept.** Marking is destructive and saves without asking, so the
first time a file is modified a pristine copy goes to `.originals/`. Restoring
one is `cp`. Set `KEEP_ORIGINALS = False` to turn this off and save the disk.

## Configuration

At the top of `scr_annotator.py`:

| Setting | Default | Meaning |
|---|---|---|
| `DEFAULT_FOLDER` | `~/new` | where a bare launch starts |
| `SORT_NEWEST_FIRST` | `True` | newest file first |
| `IMAGE_EXTS` | png jpg jpeg webp bmp gif | what counts as reviewable |
| `PEN_RGB` / `PEN_WIDTH` | green / `3` | the pen |
| `HL_RGB` / `HL_ALPHA` / `HL_HEIGHT` | yellow / `0.35` / `26` | the highlighter band |
| `RECT_RGB` / `RECT_WIDTH` | green / `3` | the rectangle |
| `CROP_DIM` | `0.62` | how far the discarded area dims |
| `JPEG_QUALITY` | `95` | re-encode quality when marking a JPEG |
| `APP_NAME` / `APP_ID` | Nibras Image Annotator / `scr-annotator` | shown name, desktop identity |
| `SOL_*` / `CANVAS_RGB` | Solarized / near-black | chrome and canvas |
| `TREE_WIDTH` | `0` | folder rail width; `0` fits it to the names |
| `TREE_MIN` / `TREE_MAX` | `110` / `300` | how far that fit may go |
| `SHOW_HIDDEN` | `False` | dotted directories in the folder tree |
| `KEEP_ORIGINALS` / `ORIGINALS_DIR` | `True` / `.originals` | pristine copies |
| `LOG_NAME` | `.scr-annotator-log.tsv` | rename log; **deleting it breaks label replacement** |
| `MAX_LABEL` / `CACHE_MAX` | `150` / `7` | name cap, decoded neighbours |

## Undoing

**Renames** — the log is `timestamp⇥old⇥new`, so reversing a session is a short
`awk` over the tail of it:

```bash
cd "$FOLDER"
tac .scr-annotator-log.tsv | awk -F'\t' '{printf "mv -n -- \"%s\" \"%s\"\n", $3, $2}'
```

Check that output before piping it to `sh`.

**Marks and crops** — copy back from `.originals/`, which is keyed by each
file's *original* name, so pair it with the log if the file has also been
relabelled.

**Deletions** — they went to the trash: `gio trash --restore`, or your file
manager.

## Tests

```bash
python3 test_naming.py
```

Covers the half that can silently ruin a folder: labels replacing rather than
stacking, the mapping surviving a restart, collisions not overwriting, slashes
not becoming path separators, files the app has never seen before, and a move
to another folder keeping the label recoverable there with its `.originals/`
copy alongside.

The GUI half is verified end-to-end under `Xvfb` with `xdotool` driving real
keystrokes and real mouse drags — asserting that the rename lands, that a
relabel replaces rather than stacks, that pen/highlight/rect reach the pixels,
that a crop actually changes the image dimensions, that `.originals/` gets its
copy, that `Del`+`Escape` deletes nothing while `Del`+`Enter` deletes exactly
one, and that a file dropped into the folder is adopted and jumped to. Those
scripts are not in the repo; they need a display and a scratch folder of
throwaway copies.

## Requirements

`python3-gi` (GTK 3), `python3-cairo`, `Pillow`, `gio` for the trash, and
`xclip` + `wmctrl` for the capture script. All were already present on Linux
Mint.

## Not built

Bulk operations, tag vocabularies, zoom/pan, text annotations, and any
deduplication — `jdupes` handles that separately and better.

## License

AGPL-3.0 — see [`LICENSE`](LICENSE). The icons are generated by `make_icon.py`.
