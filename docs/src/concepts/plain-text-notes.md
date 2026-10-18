# Plain-text notes

In Nibras a note is **a plain UTF-8 text file with a `.md` extension**, sitting in a folder you
choose. There is no hidden database holding the real content: the file *is* the note.

```text
notes/
├── 2026_06_10-143025~T~p2~A.md
├── 2026_06_10-151200~N~p3~B.md
└── 2026_06_11-090512~~p3~.md
```

## Why plain text

- **You own it.** Open any note in any editor, grep it, back it up, version it with git. Nothing
  breaks if every Nibras app disappears tomorrow.
- **It syncs with anything.** A folder of small text files is exactly what file-sync tools are good
  at — see [Sync](sync.md).
- **Every app can share it.** The phone, desktop and terminal apps all read and write the same folder,
  so a note captured on one appears on the others with no conversion step.
- **It stays fast.** The apps scan the folder directly; there is no index that can fall out of step
  with what is on disk.

## What a note looks like

The body is free text. Markdown is welcome but not required. A few conventions are understood by
the apps:

| In the text | Meaning |
|-------------|---------|
| First line | Treated as the note's title: shown bold on cards, and used as the record title on import. |
| `#tag` | A free-form hashtag, collected into the tag cloud of the filter panels (Arabic hashtags work too). |
| `==marked text==` | A highlight — the same notation Obsidian and GitHub use. The desktop and phone readers paint it. |

All the *structured* information — when the note was written, what kind of thing it is, how
important it is, which area of life it belongs to — lives in the **file name**, not inside the text.
That is described in [File names and tags](filenames-and-tags.md).

## What lives in the folder besides notes

| Item | What it is |
|------|------------|
| `<note name>.d/` | The note's [companion folder](companion-folders.md) of attachments. |
| `.deleted/`, `.merged/`, `.imported/`, `imported/` | Housekeeping folders: soft-deleted notes, notes absorbed by a merge, notes already imported to the server. Never shown as notes. |
| `.knotes.json` | The desktop app's per-folder settings (its list of types and categories). |
| Other dotfiles (e.g. a sync tool's own `.stfolder`) | Ignored. |
| Anything else | Shown as **needs metadata** — a file or folder waiting to be turned into a proper note. |

!!! info "Nothing is erased by surprise"
    On the desktop, deleting a note moves it to `.deleted/` and merging moves the absorbed notes to
    `.merged/`, so both can be undone by hand. A note that still has attached files cannot be
    deleted at all until you deal with those files — on any device.

## Next

- [File names and tags](filenames-and-tags.md)
- [Companion folders](companion-folders.md)
