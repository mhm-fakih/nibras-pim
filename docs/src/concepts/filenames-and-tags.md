# File names and tags

Every Nibras note carries its metadata **in its file name**. This one convention is shared,
byte for byte, by the phone, desktop, terminal, web app and server.

## The scheme

```text
<timestamp>~<type>~p<priority>~<category>.md
```

```text
2026_06_10-143025~T~p2~A.md
│                 │  │  └─ category  A
│                 │  └──── priority  2
│                 └─────── type      T
└───────────────────────── timestamp 2026-06-10 14:30:25
```

| Field | Format | Notes |
|-------|--------|-------|
| timestamp | `yyyy_MM_dd-HHmmss` | When the note was created. Never changes afterwards, so it is the note's stable identity and its "date created". |
| type | a short code, usually one letter | *What kind of thing* this is — e.g. `T` task, `N` note, `J` journal. May be empty. |
| priority | `p` + a number | How important it is. The `p` prefix lets apps find this field unambiguously. |
| category | a short code, usually one letter | *Which area* it belongs to (a department, a subject, a project). May be empty. |

`~` separates the fields. Both type and category may be blank, so this is a valid note too:

```text
2026_06_11-090512~~p3~.md
```

### Same-second collisions

If two notes would get the same name, the app adds `-2`, `-3`, … to the timestamp:

```text
2026_06_10-143025~N~p3~B.md
2026_06_10-143025-2~N~p3~B.md
```

The suffix is ignored when reading the creation date.

### Codes come from your vocabulary

The letters mean whatever your vocabulary says they mean:

- **Without a server**, each app keeps its own list: the desktop stores it per folder in
  `.knotes.json`, the phone in its settings. Edit them freely.
- **With a server**, the capture apps download the vocabulary: types come from the server's
  **modules** and categories from its **bookmarked departments**. That way a note's letters map
  exactly onto a record type and department when it is imported. See [Sync](sync.md#vocabulary-download).

Typical server type codes:

| Code | Module | | Code | Module |
|------|--------|-|------|--------|
| `T` | Tasks | | `G` | Goals |
| `N` | Notes | | `W` | Writings |
| `J` | Journals | | `R` | Resources |
| `P` | Planners | | | |

## Retagging is renaming

Changing a note's type, priority or category simply **renames the file**. The timestamp stays, so
the note keeps its place in time. This is what makes
[capture now, classify later](capture-then-classify.md) cheap.

!!! note "Created vs. updated"
    The apps read "created" from the **file name's timestamp** and "updated" from the file's own
    modification time. Retagging or editing a note bumps its modification time but never its
    creation date — and a file-sync tool that rewrites modification times can't scramble your
    timeline either.

## Which files count as notes

The rule used to *count* notes is the same everywhere:

- only `.md` files,
- whose name matches the scheme above,
- directly in the folder (never in subfolders).

Everything else is either ignored (dotfiles, housekeeping folders, companion folders) or shown as a
**needs metadata** item for you to settle.

!!! warning "Keep the default timestamp format"
    The desktop and phone let you change the timestamp pattern, but the shared counting rule
    expects `yyyy_MM_dd-HHmmss`. Notes named with another pattern won't be recognised as canonical
    notes by the other apps or the server.

## Legacy formats

| Format | Where it came from | How it is handled |
|--------|--------------------|-------------------|
| `<timestamp>~p<priority>~<subjects>.txt` (several subjects joined with `-`) | Early phone versions | The phone still lists and reads them, and rewrites them in the current `.md` scheme the next time you edit one. Other apps show them as *needs metadata*. |
| Any `.md` or `.txt` with a free-form name | Dropped in by another program | Phone: listed with no tags, managed once edited. Desktop: *needs metadata*; settling it renames it into the scheme, keeps its original name as the first line, and dates it by the file's own modification time. |
| Any other file or folder | Downloads, exports, scans | Desktop: settling it creates a new note and moves the item into that note's [companion folder](companion-folders.md). |

## How each app reads the name

| App | Behaviour |
|-----|-----------|
| [Phone](../mobile/index.md) | Reads all canonical notes plus legacy `.txt`. Filter panel with type / priority / category facets, plus two clean-up queues: **non-standard names** and **bad taxonomy** (a well-formed name using a code your vocabulary doesn't define). |
| [Desktop](../desktop/index.md) | Board and kanban grouped by the three tags. `Ctrl+Shift+<letter>` retags the selected note; `Ctrl+Shift+<digit>` sets its priority. Same two clean-up queues in the sidebar. |
| [Terminal](../terminal/index.md) | Uses the desktop's storage code. A line such as `t3d buy milk` saves a note with type `t`, priority `3`, category `d`. |
| [Web app](../web/index.md) | The **Local notes** page reads the notes folder on the server's disk. When you write a note there, its category letter is taken from the note's first `#X.something` hashtag. |
| Server import | Needs a one-letter type that matches a module and a non-empty category; takes the date from the timestamp and the priority from the `p` token. |

!!! tip "Tag order on screen"
    The desktop shows a note's tags as **type · priority · category** (e.g. `T 2 A`) — the same
    order as in the file name — and keeps them left to right even on an Arabic note.
