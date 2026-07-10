# knotes terminal (TUI)

A Claude-Code-style terminal interface: **note entry** like the desktop app's minimal mode, plus
command-driven backend reports. A scrolling content log on top, a status line, and a command/note
bar docked at the bottom. Note storage reuses the desktop app's `notes.py` (same filename scheme, so
the same folder works from phone, desktop and terminal).

## Run

```bash
./run.sh                                 # .venv + deps
NIBRAS_URL=http://host:8081 ./run.sh     # env override for every repo's backend (testing)
```

## Config

A local, **non-hidden `config.json`** (next to the program, created on first run and seeded from the
desktop app's config) holds everything:

```json
{
  "preview_chars": 60,
  "default_repo": "S",
  "default_type": "N", "default_priority": 2, "default_subject": "Z",
  "repos": {
    "H": { "path": "~/h/knotes", "backend": "http://host-a:8081" },
    "S": { "path": "~/new/knotes", "backend": "http://127.0.0.1:8081" }
  }
}
```

**Each repo has its own backend.** The current (sticky) repo's backend is what `/login`, `/tasks`,
`+`, etc. talk to; the **status bar** shows that backend URL with a **green ●** when it's online (red
when unreachable). `config.json` is git-ignored (it's your local settings).

## Use

The top **dashboard** lists each repo's code, path → backend, note count and last-entered note
(★ = default repo).

**Note line** — type + Enter to save:

```
<repo> <type><priority><subject> <body>
```

A valid **3-char meta** (letter-digit-letter) makes the line conform; the leading **repo letter is
optional** and defaults to the **sticky default repo** (the last repo you used explicitly, shown in
the status line and remembered across launches). Only a conforming line has its prefix stripped —
otherwise the note silently gets the default repo + default tags and the **whole line is kept as the
body**, so nothing you type is ever dropped. The command bar turns green when a line conforms.

| You type | Saved as |
|---|---|
| `s t3d some note` | repo **s** (now the default), type **t** / p**3** / subject **d**, body "some note" |
| `t3d some note` | **default** repo, type t / p3 / subject d, body "some note" |
| `b x3 note` | no repo `b` + incomplete meta → default repo + tags, body "b x3 note" |
| `just a note` | default repo + default tags, body "just a note" |

**`+` prefix** — start the line with `+` to *also* push the note to the repo's backend as a record,
using the same endpoint the web **Import notes** page uses (`POST /api/import/notes`): the backend
creates the record and moves the file into the repo's `imported/` subfolder. Requires being logged in
(`/login`) and the repo's `path` to be that backend's import folder. e.g. `+ s t3d some note`.

**Commands** — a line starting with `/`:

| Command | Action |
|---|---|
| `/repos` | reprint the dashboard |
| `/login <user> <pass>` | authenticate (stores the JWT) |
| `/heartbeat` | check the backend (`/page/heartbeatJson`, no auth) |
| `/tasks` / `/goals` / `/journals` / … | list the 20 most-recent records |
| `/help` · `/quit` | help · exit |

The command bar **turns green** once the note carries a recognized repo letter + a valid
`<type><prio><subject>` tag (e.g. `h g3d …`), so you can see at a glance that it's fully tagged.

The cursor is non-blinking and the command bar keeps focus. Typing `/` shows an inline suggestion
of the matching command (ghost text) — press **→** (or End) to accept it.

## Arabic / RTL

- Modern terminals shape + bidi Arabic themselves, so by default the app outputs **raw** text and
  lets the terminal render it (pre-shaping would double-process it into garbled text). If your
  terminal does **not** shape Arabic, set `NIBRAS_RESHAPE=1` to reshape (`arabic-reshaper`) +
  bidi-order (`python-bidi`) the log content.
- The command bar stays left-aligned even for Arabic: Textual's Input select-all's its content on any
  text-align change mid-entry (which deletes what you typed), so dynamic RTL alignment isn't safe.
  Arabic still reads correctly (the terminal shapes + bidi-orders it).

## Next steps

- Keyboard shortcuts (not just `:` commands) to cycle type/priority.
- Tables (`DataTable`) for reports; richer report grammar; write commands.

## Next steps

- Richer report grammar (filters, sorts, date ranges) reusing the backend's command grammar.
- Tables (Textual `DataTable`) instead of log lines for reports.
- Write commands (create/complete records).
- Token persistence + a config file for the base URL and default account.
