# Type, priority & category

Every note carries three tags, and all three live in its file name:

```
2026_06_10-143025~W~p3~A.md
                  │  │  └── category  A
                  │  └───── priority  3
                  └──────── type      W
```

Type and category are short codes (usually one letter); priority is a number. Any of the codes may
be empty. See [File names and tags](../concepts/filenames-and-tags.md) for the full scheme.

## Tag a new note

The add form has three chip rows:

| Row | What it sets | Shortcut |
|---|---|---|
| **Type** | The note's type code | **Ctrl+Shift+<letter>** |
| **Prio** | The priority (**P1**, **P2**, …) | **Ctrl+Shift+<digit>** |
| **Cat** | The category code | **Ctrl+Shift+<letter>** |

Click a chip, or press its shortcut, before saving. Pressing a type or category shortcut again
clears it. Hover a chip to see its full name and shortcut. Your choice is **sticky** — it stays
selected for the next note.

Shortcuts follow the physical key, so they keep working while you type in Arabic.

!!! warning "Keep type and category codes distinct"
    Type and category shortcuts share **Ctrl+Shift+<letter>**. If the same letter is both a type
    and a category, the shortcut picks the type. Give them different letters.

## Retag an existing note

1. Select a card on the board (click it or use the arrow keys).
2. Press **Ctrl+Shift+<letter>** to set its type or category, or **Ctrl+Shift+<digit>** to set its
   priority.

The file is renamed on the spot; its content, its creation time and its
[attached files](attached-files.md) stay with it. The view does not jump, so you can work down a
column retagging card after card.

!!! tip "Mass retagging"
    Choose **Date created** in the toolbar's sort menu before a big retagging session. With the
    default **Last updated** sort, a retagged note moves to the end because its file just changed.

Retagging from the board always *sets* a value; it never clears one.

## Define your own codes

The available codes are set **per note folder** — each folder can have its own vocabulary. Open
**Settings** (**Ctrl+,**) and scroll to **Tag options for folder “X”**:

| Setting | Format |
|---|---|
| **Priority min** / **Priority max** | The range of priority chips, e.g. 1 to 5 |
| **Default priority** | The priority a new note starts with |
| **Types (code:name per line; * = default)** | One `code:name` per line |
| **Categories (code:name per line; * = default)** | One `code:name` per line |

Put a `*` on the code that new notes should start with. For example:

```
N*:Note
W:Work
H:Home
I:Ideas
T:Todo
```

The `*` only marks the default; it never becomes part of the code or the file name. If you star
several, the first one wins.

These options are saved in a hidden `.knotes.json` file inside the folder, so they travel with the
folder. The first time a folder is used, it's seeded from your current settings.

!!! note "Codes from a Nibras server"
    If the folder is connected to a Nibras server, its types and categories are replaced by the
    server's codes on each launch. See [Connecting to a Nibras server](nibras-server.md).

## Tags that don't match

Notes synced from elsewhere may carry codes this folder doesn't define (say, type `Y`). They still
show on the board, and the sidebar lists the unknown code in its facet in *italic amber*. To find
them all, use the sidebar's **Bad taxonomy** section — see
[Board, sidebar & filters](board-and-filters.md#clean-up-queues).
