# Writing notes

The bottom of the window is the **add form**: three rows of tag chips (**Type**, **Prio**,
**Cat**), a text field, a folder picker and a send button. Left of the text field, a small arrow
button switches between the full form and the [short form](#the-short-form). Whatever you type
there becomes a new note file.

## Save a note

1. Click into the text field (it also grabs focus every time the window does, so you can just
   paste).
2. Type your note. The first line works as its title — cards show it in bold.
3. Pick a type, priority and category with the chips or their shortcuts (see
   [Type, priority & category](tagging.md)).
4. Press **Ctrl+Enter**, or click the send button.

The note is saved to the active folder, the field clears, and the **Just added** strip above the
form shows the new note's tags and a short word preview. Your tag choice stays selected for the
next note.

!!! tip "Save with plain Enter"
    Prefer chat-style entry? Turn on **Save on Enter** in [Settings](settings.md). Then **Enter**
    saves and **Shift+Enter** inserts a new line.

Nothing is lost on quit: **Ctrl+Q** saves an unsent draft as a note first.

## The short form

When you just want the thought down and will sort it out later, press **Ctrl+N** (or click the
arrow button left of the text field). The chip rows disappear, and every note is saved as
**N · P2 · X** — type N, priority 2, category X — ready to be retagged later. If the folder doesn't
define one of those codes, the note gets the chips' current choice for it instead. Hover the arrow
button to see exactly which tags the short form will use.

Press **Ctrl+N** again to bring the chips back. The app remembers which form you used last.

## The inbox: file downloads into a note

Downloads, screenshots and other files often land in one folder first — by default `~/new`. The
add form lists that folder's files and folders under the text field, in a fold-out line such as
**5 to settle in ~/new**, newest first. It updates by itself as files arrive.

1. Unfold the line and tick the items that belong with the note you're writing. The line counts
   them: **· 2 go into the new note**.
2. Write the note (or leave the text empty) and save it.

The ticked items are **moved** into the new note's [companion folder](attached-files.md), so the
inbox empties as you write notes for what's in it. If you saved without any text, the note's text
is the names of the items it took in.

Choose the folder with **Inbox folder** in [Settings](settings.md); leave it blank to turn the list
off. Folders that live in the inbox but aren't inbox items go in **Inbox: leave out (one path per
line)**. Your note folders are always left out, even when they sit inside the inbox.
## Arabic and mixed text

Each line picks its own direction, so Arabic and English can share one note and both read
correctly. A badge left of the text field shows the active keyboard layout — **EN** or **ع** — and
updates as you switch. All `Ctrl` shortcuts work whichever layout is active. See also
[Languages](../concepts/languages.md).

## Hashtags and the tag bank

Write `#hashtags` anywhere in a note's text. They show up on the card, in the sidebar's tag cloud
and in the one-line hashtag strip under the add field.

Typing `#` (in the add field or the side editor) opens a list of suggestions from your **tag
bank**:

| Key | Action |
|---|---|
| **↑** / **↓** | Move through the suggestions |
| **Enter** or **Tab** | Insert the highlighted tag |
| **Esc** | Close the list |

The tag bank is a plain file, `tag-bank.md`, created next to the program the first time it's
needed. One tag per line — the preferred spelling first, then any aliases separated by commas.
Lines starting with `#` are comments.

```
# my tags
work, job, office
reading, books
```

Picking an alias inserts the preferred spelling, so `#job` becomes `#work`. Matching is simple
substring matching, case-insensitive, over both spellings. Edits to the file are picked up
immediately — no restart. To keep one vocabulary on several machines, point **tag_bank_path** in
`config.json` at a copy in your synced folder (see [Settings](settings.md#config-file-only)).

!!! note
    Tags may contain dots and hyphens (`#project.alpha`, `#to-read`). A `#` in the middle of a word
    (`C#`) or followed by a space (a Markdown heading) does not start a tag.

## Read and edit in the side pane

Press **Ctrl+E** (or the pencil button on the toolbar) to open the **side editor pane** on the
right. It always shows the note selected on the board — click or arrow to another card to switch.
Drag its left edge to resize it.

The pane has two modes, switched with the **read** and **edit** buttons at its top. In both,
Markdown such as `**bold**`, `*italic*`, `` `code` ``, `# headings`, links and `#hashtags` is styled
in place, with the marker characters dimmed rather than hidden.

=== "read"

    The note laid out for reading. Highlight markers are hidden and highlighted passages are
    painted. **Click a phrase to highlight it**; click a highlighted phrase to clear it. A phrase
    runs between punctuation marks (`. , ; : ! ?` and their Arabic equivalents) or line ends.

    Highlights are stored in the file as `==like this==`, the same notation Obsidian and other
    Markdown tools use, so they survive syncing and show on the phone too.

=== "edit"

    The raw text, markers and all. Changes are **saved automatically** shortly after you stop
    typing, and always before you retag, delete, merge, switch folders, close the pane or quit.
    The status line under the pane says **Editing…**, **Saved** with the time, or an error.

    **Esc** or **Ctrl+Enter** saves and returns the keyboard to the board.

The pane is not a way to create notes — new notes always come from the add form. Loose files that
aren't proper notes yet (see [Organizing notes](organizing-notes.md#settle-loose-files)) are shown
read-only.

Choose the mode the pane starts in with **Editor pane starts in**, and the highlight colour with
**Highlight colour**, both in [Settings](settings.md).

Under the note, the **Files** panel lists the note's attached files — see
[Attached files](attached-files.md#the-files-panel).

## Quick notes and quick tasks

Some things don't deserve a note at all — a phone number, a thing to buy. For those, two lists sit
under the side pane: **Quick notes** on the left and **Quick tasks**, with a checkbox per line, on
the right.

- Press **Ctrl+J** to jump into the quick-note field, or **Ctrl+K** for the quick-task field. The
  side pane and the lists open if they were closed.
- Type a line and press **Enter**. The field keeps the keyboard, so you can add several in a row.
  **Esc** returns to the board.
- Tick a task when it's done — it fades rather than being struck through, so Arabic stays
  readable. The count in the header shows what's left. The **×** on a row removes that line.
- The list button at the top of the side pane shows or hides both lists; drag the divider above them to
  make them taller.

The lists are two plain Markdown files at the top of the note folder, `quick-notes.md` and
`quick-tasks.md` — a bullet list and a task list. They never appear on the board and never need
settling, and they sync and can be edited anywhere like any other file. Edits made elsewhere show
up by themselves. Each note folder has its own pair, so switching folders switches the lists.

## Quick-append a comment

To add a line to an existing note without opening it:

1. Select the card (click it, or use the arrow keys).
2. Press **Space**. A field appears under the card.
3. Type your comment and press **Enter** (or **Esc** to cancel).

The comment is added at the end of the note under a dated separator. By default that looks like:

```
---2026-07-22 10:30:
your comment
```

Change it with **Append separator** and **Append date format** in [Settings](settings.md).

## Open a note in another editor

Select a card and press **Enter** (or double-click it, or click **open note** on the card). The
note opens in the editor set under **Open notes with** in Settings — for example `xed` — or in your
system's default app for `.md` files.

## Minimal side-note mode

Press **Ctrl+L** (or the last button of the view group on the toolbar) for a tiny note window: the
board and chip rows disappear, leaving only the **Just added** card, a one-line summary of the
selected tags, and the text field. Change tags with their shortcuts.

In minimal mode, press **F12** to select the just-added card — then **Ctrl+Shift+<code>** retags it
or **Ctrl+D** deletes it. **F12** again returns to the text field. **Ctrl+L** brings back your
previous view.
