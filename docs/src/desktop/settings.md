# Settings

Open **Settings** with **Ctrl+,** or the gear button on the toolbar. Click **Save** to apply your
changes, or **Cancel** to discard them.

The dialog has three parts: app-wide settings, the default Nibras backend, and the tag options of
the folder that was active when you opened it.

## App-wide settings

### Note folders

**Note folders (code:path per line; * = active)** — one folder per line as `code:path`. Mark the
active folder with `*`. Example:

```
A*:~/Knotes
B:~/Work-notes
```

With more than one folder, a picker appears next to the send button and **Ctrl+1** … **Ctrl+9**
switch between them. See [Organizing notes](organizing-notes.md#several-note-folders).

### All app-wide settings

| Setting | What it does | Default |
|---|---|---|
| **Timestamp format** | How new file names start, as a Python `strftime` pattern | `%Y_%m_%d-%H%M%S` |
| **Open notes with** | Command that opens a note on **Enter**, e.g. `xed`. Blank = system default | blank |
| **Preview characters (head)** | Characters shown from the start of a note on its card | 120 |
| **Counts shown from** | Notes at least this long show highlight and time-mark counts. 0 = every card | 120 |
| **Editor pane starts in** | **Read (highlights, no editing)** or **Edit (type straight in)** | Read |
| **Preview words (head)** | Words from the start shown on the **Just added** card | 2 |
| **Preview words (tail)** | Words from the end shown on the **Just added** card | 2 |
| **Preview characters (tail)** | Characters shown from the end of a long note. 0 = none | 40 |
| **Saving** → **Save on Enter (Shift+Enter for a newline)** | Plain **Enter** saves a note | off |
| **Tags** → **List every #hashtag under the add field** | Off shows a compact total instead | on |
| **Startup** → **Open filtered to today's notes** | Start with today selected in the calendar | on |
| **Highlight colour** | Colour behind highlighted passages | yellow |
| **Append separator** | Inserted before a quick-appended comment. `$date` = current time, `\n` = new line | `\n---$date:\n` |
| **Append date format** | `strftime` format for `$date` | `%Y-%m-%d %H:%M` |
| **Kanban rows** | Rows the kanban columns are spread over (1–3) | 2 |
| **Card max width (columns view)** | Widest a card may get in the columns view | 340 |

!!! warning "Changing the timestamp format"
    Other knotes apps expect the default `YYYY_MM_DD-HHMMSS` format. Change it only if every app
    that reads the folder uses the same format.

## Nibras backend — default

**Backend URL**, **Backend username** and **Backend password** — the server that folders without
their own backend use for code sync. Blank URL = offline. See
[Connecting to a Nibras server](nibras-server.md).

## Tag options for the active folder

These are saved inside the folder (in `.knotes.json`) and apply only to it. The heading names the
folder, e.g. **Tag options for folder “A”**.

| Setting | What it does |
|---|---|
| **Priority min** / **Priority max** | The range of priority chips |
| **Default priority** | Priority a new note starts with |
| **Types (code:name per line; * = default)** | This folder's types; `*` marks the default |
| **Categories (code:name per line; * = default)** | This folder's categories; `*` marks the default |
| **This folder's backend URL** / **username** / **password** | A Nibras server for this folder only. Blank = use the default backend |

See [Type, priority & category](tagging.md#define-your-own-codes).

## Toolbar settings

These are switched so often that they live on the toolbar and are remembered automatically:

- View: **Kanban**, **Simple list**, **Columns**, **Minimal**
- Sort order
- Light / dark theme (**Ctrl+T**)
- Side editor pane open or closed, and its width (**Ctrl+E**)
- Filter sidebar shown or hidden, which sections are folded, and the calendar's **Created** / **Updated** switch (**Ctrl+B**)
- Auto-open the files folder after each save

## The config file

All settings are stored in `config.json` **next to the program**, so a copied program folder takes
its settings with it. Close the app before editing the file by hand.

### Config-file only

A few options have no control in the dialog:

| Key | What it does | Default |
|---|---|---|
| `tag_bank_path` | Path to your `tag-bank.md`. Blank = next to the program. Point it into a synced folder to share the vocabulary across machines | blank |
| `card_file_chars` | Characters of each attached file name shown on a card | 12 |
| `editor_autosave_ms` | Pause (in milliseconds) before the side editor saves | 900 |
