# Board, sidebar & filters

The middle of the window is the **board**: one card per note. The left edge holds the **filter
sidebar**, and the toolbar across the top switches views and sort order.

## Views

The view buttons on the toolbar choose how the board is laid out. Your choice is remembered.

| View | What you see |
|---|---|
| **Kanban** | One column per category, grouped by type inside each column. Click a type heading to fold it. Only categories that have notes get a column. Columns are spread over 1–3 rows (**Kanban rows** in Settings). |
| **Simple list** | All notes in one column, oldest at the top, newest at the bottom. |
| **Columns** | The list, wrapped into as many columns as fit the window. Card width is capped by **Card max width** in Settings. |
| **Minimal** | Board hidden — just the last note and the text field (**Ctrl+L**). See [Writing notes](writing-notes.md#minimal-side-note-mode). |

Move between cards with the **arrow keys**: ↑↓ within a column, ←→ across columns.

## Sort order

The sort menu on the toolbar orders the cards:

| Option | Order |
|---|---|
| **Last updated** | By the file's last change, newest last (the default) |
| **Date created** | By the timestamp in the file name, newest last — stable while you retag |
| **Category** / **Type** | In the order the folder defines them |
| **Priority** | Highest first |
| **Note length** | Longest first — handy for finding notes that have grown too big |

## Reading a card

Each card shows:

- The **type**, **priority** and **category** in a small gutter on the left.
- The note text, with the **first line in bold**. Long notes show their beginning and end (set the
  amounts with **Preview characters (head)** and **(tail)** in Settings). Highlighted passages are
  painted.
- Any `#hashtags`, and a 📎 row listing up to five [attached files](attached-files.md) — click a
  name to open it.
- An **action row**: **open folder**, **open note**, copy file name, copy full path, and delete.
- Counts on long notes: words, highlights and — if the note has recordings — time marks from
  [`.tmk` bookmark files](../concepts/bookmarks-tmk.md).
- Two dates: when the note was **created** (from its file name) and when it was **last changed**.
  The second turns orange after 3 days and red after 7 — a note you keep coming back to.

## Search

Press **Ctrl+F** (or the magnifier button) and type. The board narrows to notes whose text — or
type or category code — contains what you typed. **Esc** closes the search.

## The filter sidebar

Press **Ctrl+B** to show or hide the sidebar. Hidden, it shrinks to a thin **FILTERS** rail that
still shows how many filters are active.

Every option shows **how many notes are behind it**. The counts are *faceted*: each number tells
you how many notes you'd see if you also picked that option, taking your other filters and the
search box into account.

### Sections

| Section | What it does |
|---|---|
| **Tags** | A cloud of the `#hashtags` used in note text, sized by how often they appear. Click one to show only notes that carry it (one tag at a time). Hidden when no note has a hashtag. |
| **Calendar** | A month heat map — each day shaded by how many notes it has. Click a day to filter to it; **Shift+click** another day to select a range. |
| **Priority** / **Type** / **Category** | Every option the folder defines, plus any unknown codes the notes actually use (in *italic amber*). Pick one or more. |
| **Non-standard** | **Include** / **Hide** / **Only** — loose files and folders that aren't proper notes yet. |
| **Bad taxonomy** | **Include** / **Hide** / **Only** — notes whose codes the folder doesn't define. |

The calendar has a **Created** / **Updated** switch. **Created** uses the date in the note's file
name, so retagging never moves a note in time. **Updated** uses the date the file last changed —
the "when did I revisit this" view. Below the month are **Today** (jump back to this month) and
**Clear days**.

**Clear all filters** at the foot of the sidebar resets everything. Sections fold individually and
the layout is remembered — but your picks are not, so you never start the day on a stale filter.

!!! note "Opening on today"
    The one filter applied at launch is *today* in the calendar (turn it off with
    **Open filtered to today's notes** in Settings). An empty board always says why — for example
    **No notes entered today**, **No notes match the search** or **No notes match the filters**.

### Clean-up queues

Two sections exist to help you tidy up:

- **Non-standard → Only** lists files and folders in the note folder whose name isn't in the
  `<timestamp>~<type>~p<n>~<category>.md` format — things you dropped in that still need tags.
  They're always filed under *today* in the calendar until you settle them. See
  [Settle loose files](organizing-notes.md#settle-loose-files).
- **Bad taxonomy → Only** lists notes with a well-formed name but a code the folder doesn't define.
  Select one and retag it with **Ctrl+Shift+<code>**.

The title bar also shows the backlog, e.g. `412 notes · 3 to settle`.

## Hashtag strip

Under the text field, a one-line strip lists every hashtag in the folder with its count. Turn off
**List every #hashtag under the add field** in Settings to show just a compact total instead.

## Refresh

The board updates by itself when files change on disk — for example when a sync tool brings in
notes from another machine. **Ctrl+R** (or the refresh button) re-reads the folder on demand.
