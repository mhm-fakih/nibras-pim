# Attached files

Any note can have files attached — a PDF, photos, a recording, a diagram. They live in the note's
**companion folder**: a folder next to the note with the same name plus `.d`.

```
~/Knotes/
├── 2026_06_10-143025~N~p2~F.md
└── 2026_06_10-143025~N~p2~F.d/
    ├── receipt.pdf
    └── talk.mp3
```

Because it's just a folder, you add files with your normal file manager, and any sync tool carries
them along. See [Companion folders](../concepts/companion-folders.md) for the idea behind it.

## Attach files to a note

From the app:

1. Select the note's card, and open the side pane (**Ctrl+E**) if it's closed.
2. In the **Files** panel under the note, click **add files…** and pick the files — or drag them
   from your file manager and drop them on the panel.

The files are copied into the companion folder, which is created the moment the first file arrives.

From your file manager:

1. Select the note's card.
2. Press **Ctrl+O**, or click **open folder** on the card.
3. The companion folder is created (if it doesn't exist yet) and opened in your file manager.
   Drop your files in.

To file things from your downloads folder into a **new** note, tick them in the
[inbox list](writing-notes.md#the-inbox-file-downloads-into-a-note) before you save the note.

!!! tip "Capture sessions"
    Turn on the folder button on the toolbar (**Auto-open the note's files folder after each
    save**). Every note you save then opens its companion folder straight away, ready for files.

## The files panel

The **Files** panel sits under the note in the side pane (**Ctrl+E**) and lists the selected note's
companion folder — folders first, then files with their sizes. Click a row to open it with your
system's default app. **open folder** opens the whole folder in your file manager (**Ctrl+O**).
A note without files shows **No files yet — add some, or drop them here**; looking at a note never
creates an empty folder.

On the board, the card's action row counts the files instead, e.g. `· 3 files`.

## Find a note by its files

[Search](board-and-filters.md#search) also matches the names of the files and folders inside
each note's companion folder, at any depth — type part of a PDF's or recording's name to find the
note it belongs to.

## What happens to the files when you…

| Action | Attached files |
|---|---|
| Retag the note | The folder is renamed along with the note |
| Merge notes | All attached files move into the surviving note's folder, and each part's file names are listed under its text |
| Delete the note | Not allowed while the folder still holds files — empty it first |

!!! warning "Deleting a note with files"
    knotes refuses to delete a note that still has attached files, so they can't end up forgotten
    in a hidden folder. The delete button is disabled on such cards, and **Ctrl+D** explains why.
    Move or delete the files first (**Ctrl+O** opens the folder), then delete the note.

## Recordings and time marks

If a companion folder holds `.tmk` bookmark files (written by the
[audio player](../audio-player/index.md) or the phone), long notes show a **time marks** count on
their card. See [Bookmarks (.tmk)](../concepts/bookmarks-tmk.md).
