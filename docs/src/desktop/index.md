# Nibras Notes (desktop)

**Nibras Notes** is a small, keyboard-first note taker for Linux (and Windows). You type a
thought, pick a type, priority and category, press **Ctrl+Enter** — and it is saved as a plain
Markdown file in a folder you choose. No database, no account, no lock-in.

!!! note "Nibras Notes and knotes"
    The window, the menu entry and this guide say **Nibras Notes**; the program's files still use
    the older name **knotes** (`knotes.py`, the `knotes` launcher, `.knotes.json`). They are the
    same app. Since 1.3.2 "Serenity" it carries the same version number as the rest of the suite
    (it counted 1.7.x on its own before).

The trick is in the file name: every note's tags are written into its name, like

```
2026_06_10-143025~W~p3~A.md
```

so the same folder works unchanged with the [mobile app](../mobile/index.md), the
[terminal app](../terminal/index.md) and the Nibras server. Sync the folder between machines with
any file-sync tool and every app sees the same notes.

!!! tip "New to the ideas behind knotes?"
    Read [Plain-text notes](../concepts/plain-text-notes.md) and
    [File names and tags](../concepts/filenames-and-tags.md) first — they explain the file-name
    scheme this app is built around.

## What you can do

- **Capture fast** — a text field at the bottom, sticky tags, save with one key; a short form
  that skips the tags altogether, quick note and task lists, and an inbox whose files you can
  file into a new note. See [Writing notes](writing-notes.md).
- **Tag with type, priority and category** from the keyboard, and retag one note or a whole set of
  marked ones at once. See [Type, priority & category](tagging.md).
- **Browse and filter** in a kanban, list or column view, with a month calendar, tag cloud and
  live counts — and see your journal and planner notes laid out in a **week calendar**. See
  [Board, sidebar & filters](board-and-filters.md).
- **Attach files** to a note in its own companion folder. See [Attached files](attached-files.md).
- **Merge, delete and settle** notes and loose files safely. See
  [Organizing notes](organizing-notes.md).
- **Stay in step with a Nibras server's** module and department codes, and import finished notes
  into it as records. See [Connecting to a Nibras server](nibras-server.md).

## Install

Nibras Notes is a Python 3 program that uses GTK 3. There is nothing to compile — you only need
Python and the GTK bindings (PyGObject).

=== "Debian / Ubuntu / Mint"

    ```bash
    sudo apt install python3-gi gir1.2-gtk-3.0
    ```

=== "Fedora"

    ```bash
    sudo dnf install python3-gobject gtk3
    ```

=== "Arch"

    ```bash
    sudo pacman -S python-gobject gtk3
    ```

=== "Windows"

    Windows has no system GTK, so install it through [MSYS2](https://www.msys2.org/). In the
    **MSYS2 UCRT64** shell:

    ```bash
    pacman -S mingw-w64-ucrt-x86_64-python \
              mingw-w64-ucrt-x86_64-python-gobject \
              mingw-w64-ucrt-x86_64-gtk3 \
              mingw-w64-ucrt-x86_64-librsvg
    ```

    `librsvg` is only needed for the window icon. (Installing `pygobject` with `pip` also works,
    but only if a GTK 3 runtime is already on your `PATH` — MSYS2 is the easier route.)

Then put the `desktop/` folder wherever you like to keep programs.

## Run

=== "Linux"

    ```bash
    cd desktop
    ./run.sh
    ```

    `run.sh` checks that GTK is available and tells you what to install if it isn't. It then
    starts the app detached from the terminal — closing the terminal doesn't close the app — and
    appends everything the app prints to `desktop.log` next to the program. If the app ever fails
    to appear (for example when started at login), look there first. You can also start it
    directly with `python3 knotes.py`.

    **Menu entry and panel pin.** Run `./install.sh` once. It adds **Nibras Notes** to your
    application menu, with the app's icon — a white note on a navy disc; launch it from there, then
    right-click its panel icon and choose **Pin**. Re-run `install.sh` if you move the folder.

=== "Windows"

    Double-click `run.bat` (or run it from a shell). It prefers `pythonw.exe`, so no console window
    stays open, and prints a hint if Python or GTK is missing.

    For a Start-menu entry, right-click `run.bat` → **Create shortcut**, give the shortcut an icon
    and move it into `%APPDATA%\Microsoft\Windows\Start Menu\Programs`.

## First run

1. **Pick your notes folder.** By default notes go to `~/knotes`. To use another folder, press
   **Ctrl+,** to open **Settings** and edit **Note folders**, e.g. `A:~/Knotes` (on Windows,
   `A:C:\Users\you\Knotes` — `~` works there too). See [Settings](settings.md#note-folders).
2. **Write a note.** Type in the field at the bottom and press **Ctrl+Enter**.
3. **Look at the result.** A card appears on the board, and a file named like
   `2026_06_10-143025~N~p2~F.md` appears in your folder.

The title bar shows the version and how many notes the folder holds, e.g.
`Nibras Notes v1.3.2.b57 · 412 notes`.

!!! note "The board opens on today and yesterday"
    By default the app starts filtered to **today's and yesterday's** notes, so a quiet morning
    shows **No notes entered today or yesterday**. Click **Clear all filters** at the foot of the
    sidebar twice to see everything (the first click returns to this launch view), or turn off
    **Open filtered to today's and yesterday's notes** in [Settings](settings.md).

Press **F1** at any time for the list of [keyboard shortcuts](keyboard-shortcuts.md).

## Where things are stored

| What | Where |
|---|---|
| Your notes | Plain `.md` files in each note folder |
| Attached files | A `<note name>.d/` folder next to each note — see [Attached files](attached-files.md) |
| Deleted and merged notes | Hidden `.deleted/` and `.merged/` folders inside the note folder |
| Notes imported into a Nibras server | An `imported/` folder inside the note folder |
| Quick notes and quick tasks | `quick-notes.md` and `quick-tasks.md` at the top of the note folder |
| Per-folder tag options | A hidden `.knotes.json` inside each note folder |
| App settings | `config.json` next to the program |
| Hashtag vocabulary | `tag-bank.md` next to the program (or a path you choose) |
| What the app printed | `desktop.log` next to the program (Linux, when started with `run.sh`) |

## Windows differences

Everything works the same on Windows — the file names, the hidden housekeeping folders, the
synced notes folder. Only a few system integrations differ:

| | Linux | Windows |
|---|---|---|
| Open a note (**Enter**) | Your configured editor, else the system default | Your configured editor, else the file's default app |
| Open a note's files folder (**Ctrl+O**) | Nemo (as a new tab), else your default file manager | Explorer, one window each time |
| Pinning to the panel | Via `install.sh` | Pin the `run.bat` shortcut |
