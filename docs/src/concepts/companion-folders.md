# Companion folders

A note is a single text file, but a capture often comes with *things*: a PDF, a photo, a
recording, a diagram. Those live in the note's **companion folder** — a folder next to the note,
named after it, ending in `.d`.

```text
notes/
├── 2026_06_10-143025~N~p2~B.md
└── 2026_06_10-143025~N~p2~B.d/
    ├── receipt.pdf
    ├── whiteboard.jpg
    ├── meeting.m4a
    └── meeting.tmk
```

The rule is simply: **note file name minus `.md`, plus `.d`**. There is no index linking the two;
the name *is* the link. The same rule is used by the phone, desktop, terminal and server.

## Created only when needed

A plain note has no companion folder. One appears only when you attach something:

| Where | How |
|-------|-----|
| [Desktop](../desktop/index.md) | `Ctrl+O` (or the *open folder* button on a card) creates the folder and opens it in your file manager — drop files in. A toolbar toggle can open it automatically after every saved note. |
| Desktop, settling a stray item | A **folder** dropped into the notes folder becomes a new note with that folder as its companion. Any **non-text file** (PDF, image, drawing…) becomes a new note whose only line is the file's name, with the file moved into the companion folder. |
| [Phone](../mobile/index.md), *Collect files* | Gathers screenshots, camera photos, downloads and recordings into one new note (in the collection folder set in Settings), with all the files in its companion folder. |

## What the apps do with it

- **Cards show the attachments.** Desktop cards show a 📎 count and the first few file names, each
  clickable; phone cards list the files and open them — media in the app's own player, anything
  else in the app Android offers.
- **The folder follows the note.** On the desktop and in the terminal, retagging (which renames the
  note), soft-deleting and merging move the companion folder along. A merge folds every absorbed
  note's files into the surviving note's folder, renaming on clashes.
- **Files are protected from accidental deletion.** A note whose companion folder holds any file
  cannot be deleted — on desktop or phone. Move or delete the files first, deliberately, so nothing
  is stranded under a name nobody will look for.
- **Import carries them to the server.** When a note is
  [imported](capture-then-classify.md#import-from-the-folder), the files are copied into the new
  record's attachment folder, and the companion folder is archived into `imported/` with the note.
- **Bookmarks count.** `.tmk` [bookmark files](bookmarks-tmk.md) inside a companion folder show up
  as a *time marks* count on the desktop card.

!!! warning "Retag notes with attachments on the desktop"
    The phone renames a note when you change its tags but does not yet rename its companion
    folder. For notes that have attachments, change tags in the desktop or terminal app, which
    move the folder along — or rename the `.d` folder by hand to match.

## Never mistaken for a note

Companion folders are skipped when listing notes, and a `.d` folder is only treated as a companion
when a matching `.md` sits right next to it. On the desktop, a stray folder that happens to end in
`.d` with no matching note is shown as a *needs metadata* item instead.
