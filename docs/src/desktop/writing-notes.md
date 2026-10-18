# Writing notes

The bottom of the window is the **add form**: three rows of tag chips (**Type**, **Prio**,
**Cat**), a text field, a folder picker and a send button. Whatever you type there becomes a new
note file.

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

The pane has two modes, switched with the **read** and **edit** buttons at its top:

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
