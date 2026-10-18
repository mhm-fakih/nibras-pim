# Configuration

All settings live in `config.json` **next to the program**. It's created on the first run —
copied from knotes desktop's settings when available — and the app writes back to it when you
switch folders or themes. Edit it with any text editor while the app is closed.

## Example

```json
{
  "preview_chars": 60,
  "default_repo": "P",
  "default_type": "N",
  "default_priority": 2,
  "default_subject": "Z",
  "theme": "solarized-light",
  "repos": {
    "P": { "path": "~/Knotes", "backend": "https://nibras.example.com" },
    "W": { "path": "~/Work-notes", "backend": "https://work.example.com" }
  }
}
```

## Settings

| Key | What it does | Default |
|---|---|---|
| `repos` | Your note folders. Each one has a one-letter code, a `path` and its own `backend` (server address) | one folder **A** at `~/knotes` |
| `default_repo` | Code of the current folder. Updated automatically when you name a folder in a note line | the first folder |
| `default_type` | Type for notes without a tag code | `N` |
| `default_priority` | Priority for notes without a tag code | `2` |
| `default_subject` | Category for notes without a tag code | `Z` |
| `theme` | `solarized-light` or `solarized-dark` — or use `/theme` | `solarized-light` |
| `preview_chars` | Length of note previews | `60` |

Folder codes should be single letters — that's what a note line can address
(see [Writing notes](writing-notes.md)).

!!! tip "One folder, several apps"
    Point a folder's `path` at the same folder knotes desktop or your phone uses. They all read and
    write the same file names, so notes show up everywhere. See
    [Sync](../concepts/sync.md).

## Environment variables

| Variable | Effect |
|---|---|
| `NIBRAS_URL` | Use this server address for **every** folder for this run, e.g. `NIBRAS_URL=https://test.example.com ./run.sh`. Handy for testing |
| `NIBRAS_RESHAPE=1` | Shape and reorder Arabic text yourself, for terminals that don't do it |

!!! warning
    If you switch folders or themes while `NIBRAS_URL` is set, the app saves its config — and with
    it the overridden server address for every folder. Check `config.json` afterwards.

## Other files

| File | Purpose |
|---|---|
| `~/.config/knotes/tokens.json` | Your logins, one per server. Delete it to log out everywhere |
| `.venv/` | The private Python environment `run.sh` creates. Delete it to reinstall the packages |
