# knotes terminal

**knotes terminal** is a note taker that runs in your terminal. It looks like a chat: a log of what
happened on top, a one-line command bar at the bottom. Type a note and press **Enter** — it's saved
as a plain-text file, using the same file names as [knotes desktop](../desktop/index.md) and the
[mobile app](../mobile/index.md), so all of them can share one folder.

It can also talk to a Nibras server: log in, list your most recent tasks, goals or journals, and
send a note straight to the server as a record.

!!! tip "How the notes are stored"
    Notes are plain Markdown files whose names carry their tags. See
    [Plain-text notes](../concepts/plain-text-notes.md) and
    [File names and tags](../concepts/filenames-and-tags.md).

## What you'll find here

- [Writing notes](writing-notes.md) — the note-line format, sticky defaults and the `+` prefix.
- [Commands](commands.md) — every `/command` and key.
- [Server commands](server.md) — logging in and listing records from a Nibras server.
- [Configuration](configuration.md) — note folders, servers, defaults and environment variables.

## Install and run

You need Python 3. The app uses a few Python packages (Textual for the interface, httpx for the
server, plus two optional helpers for Arabic); `run.sh` installs them into a private environment
for you.

The terminal app reuses the note-storage code of knotes desktop, so keep the `terminal/` folder
next to the `desktop/` folder.

=== "Linux / macOS"

    ```bash
    cd terminal
    ./run.sh
    ```

    The first run creates a `.venv` folder and installs the packages; later runs start straight
    away.

=== "By hand"

    `run.sh` is a shell script. Anywhere else, do the same steps yourself:

    ```bash
    cd terminal
    python3 -m venv .venv
    .venv/bin/pip install -r requirements.txt
    .venv/bin/python knotes_tui.py
    ```

    (On Windows the venv's programs are in `.venv\Scripts\` instead of `.venv/bin/`.)

!!! note "Running it on its own"
    If you copy `terminal/` somewhere without `desktop/`, also copy `desktop/notes.py` next to
    `knotes_tui.py`. Without it the app stops with a message saying so.

## First run

On first start the app creates `config.json` next to the program. If knotes desktop is installed
alongside, its note folders and default tags are copied in, so you start where the desktop left
off. Otherwise you get one folder, **A**, at `~/knotes`.

The screen shows:

- **Dashboard** (top) — your note folders: code, path, server and number of notes. **★** marks the
  current folder.
- **Log** (middle) — saved notes, command output and messages.
- **Command bar** (bottom) — where you type.
- **Status bar** (under it) — the current server with a dot that is **green** when it's reachable
  and **red** when it isn't, the current folder and default tags, how long the app has been running,
  and how many notes you've saved this session.

Try it: type `hello from the terminal` and press **Enter**. The log confirms `✓ saved` with the
file name.

To leave, type `/quit`. (**Ctrl+C** and **Ctrl+Q** are deliberately ignored so a stray keypress
can't close the app.)

## Arabic text

Modern terminals shape and order Arabic correctly on their own, so the app prints text as is. If
yours shows Arabic letters disconnected or backwards, start the app with `NIBRAS_RESHAPE=1`:

```bash
NIBRAS_RESHAPE=1 ./run.sh
```

See [Languages](../concepts/languages.md).
