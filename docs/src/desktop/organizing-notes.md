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

## Merge notes

Merging folds several notes into one — useful when a thought was captured in pieces.

1. Select the note that should **survive** and press **Ctrl+M** (or **Ctrl+click** it). The first
   card you mark is the survivor; it keeps its file name and tags.
2. Mark the other notes the same way, in the order you want them appended. The title bar shows how
   many are marked.
3. Press **Ctrl+Shift+M** and confirm.

The survivor gets each absorbed note's text appended under a `---` line that names the original
file, so its old tags and timestamp aren't lost. Absorbed notes move to a hidden `.merged/` folder,
and their attached files move into the survivor's companion folder.

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
[own tag options](tagging.md#define-your-own-codes) and can even talk to its
[own Nibras server](nibras-server.md).

## Files and folders knotes ignores

These never appear on the board:

- Hidden files and folders (names starting with `.`), including `.deleted`, `.merged`,
  `.imported` and `.knotes.json`.
- Housekeeping folders named `deleted`, `merged`, `imported`, `trash` or `trashed`, in any case.
- Companion `.d/` folders that belong to a note.
- `notes.sqlite` (and its `-wal`, `-shm`, `-journal` files) and `claude.md`.
