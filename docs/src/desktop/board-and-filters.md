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
| **Week calendar** | Your journal (**J**) and planner (**P**) notes laid out on a week, in their time slots. See [Week calendar](#week-calendar). |
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
  painted. Each line takes its own direction, so Arabic lines read and align from the right and
  English lines from the left, even within one note.
- The note's `#hashtags`, once, as tinted words after the last line.
- An **action row**: **open folder**, **open note**, copy file name, copy full path, import into the
  Nibras server, and delete. Hover a button to see its shortcut.
- Counts on long notes: words, highlights and — if the note has recordings — time marks from
  [`.tmk` bookmark files](../concepts/bookmarks-tmk.md), plus how many
  [attached files](attached-files.md) it has, e.g. `1,204 words · 2 highlights · 4 time marks · 3 files`.
- Two dates at the right of the action row: when the note was **created** (from its file name) and
  when it was **last changed**. The second turns orange after 3 days and red after 7 — a note you
  keep coming back to.

## The title bar

The title bar shows the app's version and how many notes the folder holds:
`Nibras Notes v1.3.2.b57 · 412 notes · 3 to settle`.

- Only **real notes** are counted — the same number the [terminal app](../terminal/index.md)
  shows. Loose files waiting for tags are counted separately as **· N to settle**, which disappears
  at zero (see [Clean-up queues](#clean-up-queues)).
- While a search or filter narrows the board, it shows both numbers: `12 / 412 notes`.
- While cards are marked, it adds a reminder such as `⛓ 3 marked — Ctrl+Shift+M to merge, Esc to clear`.

## Search

Press **Ctrl+F** (or the magnifier button), type, and press **Enter**. The board narrows to notes
whose:

- **text** contains what you typed,
- **type or category code** contains it,
- **file name** contains it — paste a whole name to find one note, or type a fragment of the tag
  block such as `N~p2` to list every note tagged that way, or
- [**attached files**](attached-files.md) include a file or folder with that in its name, at any
  depth.

Matching is plain text and ignores case. The search runs over the whole folder: pressing **Enter**
sets your sidebar filters aside, and they come back when the search ends. Empty the box (or press
**Esc**) to end the search and see the board as it was.

## What's filtering the board

Whenever a search or a filter narrows the board, a line above the cards says so — for example
`4 of 8 │ Search "n~p2" │ Type N · Note │ Priority P2`. Its **clear** button empties the search box
and returns to the launch view (today and yesterday); click it again to see the whole folder.

An empty board always says why: **No notes entered today or yesterday** (the launch view),
**No notes entered today**, **No note's text, file name or attached file name contains “…”**,
**No notes match the filters**, or **No notes in this folder yet**.

## The filter sidebar

Press **Ctrl+B** to show or hide the sidebar. Hidden, it shrinks to a thin **FILTERS** rail that
still shows how many filters are active. Drag its right edge to make it wider or narrower; the width
is remembered.

Every option shows **how many notes are behind it**. The counts are *faceted*: each number tells
you how many notes you'd see if you also picked that option, taking your other filters and the
search box into account.

### Sections

From top to bottom:

| Section | What it does |
|---|---|
| **Calendar** | A month view — each day that has notes shows its number in bold. Click a day to filter to it; **Shift+click** another day to select a range. Picked days are filled with the accent colour. |
| **Type** / **Priority** / **Category** | Side by side: type above priority on the left, categories on the right (Arabic names read from the right). Every option the folder defines, plus any unknown codes the notes actually use (in *italic amber*). Pick one or more. |
| **Non-standard** | **Include** / **Hide** / **Only** — loose files and folders that aren't proper notes yet. |
| **Bad taxonomy** | **Include** / **Hide** / **Only** — notes whose codes the folder doesn't define. |
| **Tags** | A cloud of the `#hashtags` used in note text, sized by how often they appear. Click one to show only notes that carry it (one tag at a time). Hidden when no note has a hashtag. |

The calendar has a **Created** / **Updated** switch. **Created** uses the date in the note's file
name, so retagging never moves a note in time. **Updated** uses the date the file last changed —
the "when did I revisit this" view. Hover a day to see both numbers. Below the month are **Today**
(jump back to this month) and **Clear days** (back to today and yesterday; click again for every
day).

**Clear all filters** at the foot of the sidebar returns to the launch view; click it again to see
the whole folder. Sections fold individually and the layout is remembered.

!!! note "The launch view"
    The app opens on **today and yesterday**, with non-standard items hidden — turn these off with
    **Open filtered to today's and yesterday's notes** and **Hide non-standard items by default**
    in [Settings](settings.md). Other picks are not kept from one launch to the next, so you never
    start the day on a stale filter. Within a session, **each note folder keeps its own filters**:
    switching folders and back brings them back, and a folder you haven't visited yet opens on the
    launch view.

### Clean-up queues

Two sections exist to help you tidy up:

- **Non-standard → Only** lists files and folders in the note folder whose name isn't in the
  `<timestamp>~<type>~p<n>~<category>.md` format — things you dropped in that still need tags.
  They're always filed under *today* in the calendar until you settle them. See
  [Settle loose files](organizing-notes.md#settle-loose-files). (They're hidden by default, so
  choose **Include** or **Only** to see them.)
- **Bad taxonomy → Only** lists notes with a well-formed name but a code the folder doesn't define.
  Select one and retag it with **Ctrl+Shift+<code>**.

The title bar shows the backlog, e.g. `412 notes · 3 to settle`, whatever the filters. Files or
folders that belong in the note folder but are not notes — a script, a website folder — can be
taken out of this count for good with **Note folders: leave out (one path per line)** in
[Settings](settings.md).

## Week calendar

The calendar button in the view group shows a week the way a desk diary does: seven day columns,
Monday first, in half-hour slots. Every **journal (J)** and **planner (P)** note in the folder sits
in the slot its file-name time falls in — including notes written from the add form, on the phone
or by hand. Each entry shows its time and the first two lines of its title; hover it to read the
whole note.

| To… | Do this |
|---|---|
| Change week | **‹** / **›** or **Ctrl+PgUp** / **Ctrl+PgDn**; **Today** returns to this week |
| Add an entry | Click an empty slot. The **New calendar entry** form opens with that day and time (both adjustable), type **Journal** for a past slot or **Planner** for a future one, priority, category and the text. **Ctrl+Enter** saves. |
| Move an entry | Drag it to another slot |
| Edit an entry | Double-click it to change its date, time, type, tags or text |
| Read or edit it in the pane | Click it (the arrow keys walk the entries like cards) |

The note is saved with the slot's time instead of "now", so yesterday's journal lands on
yesterday. Moving an entry only renames the file — the time lives in its name — and its attached
files go with it. Entries have no end time. The search box narrows the week; the sidebar filters
don't apply to it.

## Hashtag strip

Under the text field, a one-line strip lists every hashtag in the folder with its count. Turn off
**List every #hashtag under the add field** in Settings to show just a compact total instead.

## Refresh

The board updates by itself when files change on disk — for example when a sync tool brings in
notes from another machine. **Ctrl+R** (or the refresh button) re-reads the folder on demand.
