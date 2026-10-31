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

**Folder colours** — one colour button per folder: the toolbar, sidebar and status bar take that
colour while the folder is active. The clear button next to it means no tint. Folders you haven't
coloured get light defaults (none for the first). A folder you've just added to the list above gets
its button the next time you open Settings.

### Inbox and leave-out lists

| Setting | What it does | Default |
|---|---|---|
| **Inbox folder** | The folder whose files and folders the add form lists as checkboxes; ticked ones move into the new note's files folder when it's saved. Blank = no list. See [The inbox](writing-notes.md#the-inbox-file-downloads-into-a-note) | `~/new` |
| **Inbox: leave out (one path per line)** | Paths in the inbox that aren't inbox items, e.g. `~/new/old`. The note folders are always left out | empty |
| **Note folders: leave out (one path per line)** | Files and folders inside a note folder that aren't notes, e.g. `~/knotes/site`: no **needs metadata** card, and not counted as **to settle** | empty |

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
| **Startup** → **Open filtered to today's and yesterday's notes** | Start with today and yesterday selected in the calendar. A folder you switch to for the first time opens the same way, and every "clear" button returns here first (a second click shows everything) | on |
| **Startup** → **Hide non-standard items by default** | Leave the files and folders that aren't notes yet (the **to settle** backlog) out of the default view. The sidebar's **Non-standard** switch still shows them | on |
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

- View: **Kanban**, **Simple list**, **Columns**, **Week calendar**, **Minimal**
- Sort order
- Full or short add form (**Ctrl+N**)
- Light / dark theme (**Ctrl+T**)
- Side editor pane open or closed, and its width (**Ctrl+E**)
- Filter sidebar shown or hidden, its width, which sections are folded, and the calendar's **Created** / **Updated** switch (**Ctrl+B**)
- Quick note and task lists shown or hidden, and their height
- Auto-open the files folder after each save

## The config file

All settings are stored in `config.json` **next to the program**, so a copied program folder takes
its settings with it. Close the app before editing the file by hand.

### Config-file only

A few options have no control in the dialog:

| Key | What it does | Default |
|---|---|---|
| `tag_bank_path` | Path to your `tag-bank.md`. Blank = next to the program. Point it into a synced folder to share the vocabulary across machines | blank |
| `editor_autosave_ms` | Pause (in milliseconds) before the side editor saves | 900 |
