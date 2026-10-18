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

1. Select the note's card.
2. Press **Ctrl+O**, or click **open folder** on the card.
3. The companion folder is created (if it doesn't exist yet) and opened in your file manager.
   Drop your files in.

The card's 📎 row updates on the next refresh.

!!! tip "Capture sessions"
    Turn on the folder button on the toolbar (**Auto-open the note's files folder after each
    save**). Every note you save then opens its companion folder straight away, ready for files.

## Open attached files

The 📎 row on a card lists up to five file names. Click a name to open that file with your system's
default app. Click the trailing **…** or the count to open the whole folder; hover it to see every
file name.

## What happens to the files when you…

| Action | Attached files |
|---|---|
| Retag the note | The folder is renamed along with the note |
| Merge notes | All attached files move into the surviving note's folder |
| Delete the note | Not allowed while the folder still holds files — empty it first |

!!! warning "Deleting a note with files"
    knotes refuses to delete a note that still has attached files, so they can't end up forgotten
    in a hidden folder. The delete button is disabled on such cards, and **Ctrl+D** explains why.
    Move or delete the files first (**Ctrl+O** opens the folder), then delete the note.

## Recordings and time marks

If a companion folder holds `.tmk` bookmark files (written by the
[audio player](../audio-player/index.md) or the phone), long notes show a **time marks** count on
their card. See [Bookmarks (.tmk)](../concepts/bookmarks-tmk.md).
