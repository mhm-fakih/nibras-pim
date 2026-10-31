# Organizing notes

knotes never erases anything by itself. Deleting and merging move files into hidden folders inside
your note folder, so a mistake is always recoverable with a file manager.

## Delete a note

1. Select the card.
2. Press **Ctrl+D**, or click the trash button on the card.
3. The confirmation shows the note's tags and first lines. Press **Enter** (or click **Yes**) to
   confirm.

The note moves into a hidden `.deleted/` folder inside the note folder. To restore it, move the
file back out.

!!! note
    A note that still has [attached files](attached-files.md) can't be deleted. Empty its
    companion folder first.

### Delete several notes

Mark the cards first — **Ctrl+click** each one, or select it and press **Ctrl+M** — then select one
of them and press **Ctrl+D**. One confirmation lists every note that will go, e.g.
**Move 5 notes to the .deleted folder?**. Notes that still have attached files are kept, and the
dialog names them under **Kept — they still have attached files**.

## Merge notes

Merging folds several notes into one — useful when a thought was captured in pieces.

1. Select the note that should **survive** and press **Ctrl+M** (or **Ctrl+click** it). The first
   card you mark is the survivor; it keeps its file name and tags.
2. Mark the other notes the same way, in the order you want them appended. The title bar shows how
   many are marked, e.g. `⛓ 3 marked — Ctrl+Shift+M to merge, Esc to clear`.
3. Press **Ctrl+Shift+M** and confirm.

The survivor gets each absorbed note's text appended under a `---` line that names the original
file, so its old tags and timestamp aren't lost. Absorbed notes move to a hidden `.merged/` folder,
and their attached files move into the survivor's companion folder. Because all the files then
share one folder, each part that had files gets a **Files:** list of their names under its text —
the record of which file came with which note.

**Esc** clears the marks without merging.

## Settle loose files

You can drop anything into a note folder — a text file, a PDF, a whole folder. Until it has a
proper note name, knotes shows it as a red **needs metadata** card, files it under *today* in the
calendar, and counts it in the title bar (`· 3 to settle`).

To settle one, select its card and press any tag shortcut — **Ctrl+Shift+<letter>** for a type or
category, or **Ctrl+Shift+<digit>** for a priority. What happens depends on what it is:

| Item | Becomes |
|---|---|
| A folder | A new note named after the folder, with the folder turned into its [companion folder](attached-files.md) |
| A `.md` or `.txt` file | A proper note; its old file name becomes the first line of the text |
| Any other file (PDF, image, diagram, …) | A new note whose text is the file name, with the file attached |

Settled files keep their own date (from when the file was last changed), so they land at their
real place in time.

!!! tip "Find everything that needs settling"
    In the sidebar, set **Non-standard** to **Only**. See
    [Board, sidebar & filters](board-and-filters.md#clean-up-queues).

Something that lives in the note folder but should never become a note — a script, a website
folder — can be left out for good: add its path to **Note folders: leave out (one path per line)**
in [Settings](settings.md). It then gets no **needs metadata** card and isn't counted as
**to settle**.

## Several note folders

You can keep separate folders — say, personal and work — and switch between them.

1. Open **Settings** (**Ctrl+,**).
2. In **Note folders**, list one folder per line as `code:path`, and mark the active one with `*`:

    ```
    A*:~/Knotes
    B:~/Work-notes
    ```

3. Click **Save**.

A folder picker now appears next to the send button. Switch folders with it, or with
**Ctrl+1** … **Ctrl+9** for the first nine folders. Each folder keeps its
[own tag options](tagging.md#define-your-own-codes), its own
[quick lists](writing-notes.md#quick-notes-and-quick-tasks), its sidebar filters for the session,
and can even talk to its [own Nibras server](nibras-server.md).

So you always know which folder you're in, each one tints the toolbar, sidebar and status bar with
its own colour. Pick the colours under **Folder colours** in [Settings](settings.md); the clear
button next to a colour means no tint. Folders without a colour get light defaults — none for the
first, then light blue, green, lavender and sand.

## Files and folders knotes ignores

These never appear on the board:

- Hidden files and folders (names starting with `.`), including `.deleted`, `.merged`,
  `.imported` and `.knotes.json`.
- Housekeeping folders named `deleted`, `merged`, `imported`, `trash` or `trashed`, in any case,
  and `checklists` — where the [mobile app](../mobile/index.md) keeps its weekly checklists.
- Companion `.d/` folders that belong to a note.
- `notes.sqlite` (and its `-wal`, `-shm`, `-journal` files), `claude.md`, and the
  [quick lists](writing-notes.md#quick-notes-and-quick-tasks) `quick-notes.md` and `quick-tasks.md`.
- Anything listed under **Note folders: leave out (one path per line)** in Settings.
