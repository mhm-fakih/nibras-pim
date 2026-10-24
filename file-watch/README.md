# file-watch

Tracks which files under a watched folder have actually been reviewed, and
which have only ever accumulated. Right half of a two-column terminal
dashboard; the left half is [`window-tracker`](../window-tracker/).

The framing is an **inbox**, not a file count: anything never edited since it
appeared is unprocessed.

## Running

```bash
python3 file_watch.py --once     # one scan, print a summary
python3 file_watch.py            # rescan every SCAN_INTERVAL seconds
```

A scan of ~180 files takes about 25 ms.

Deployed as a **systemd user timer** (every 2 minutes), version-controlled in
`systemd/` — that copy is the source of truth:

```bash
./systemd/install.sh                            # install / refresh
systemctl --user list-timers file-watch.timer   # when it next fires
systemctl --user start file-watch.service       # force a scan now
journalctl --user -u file-watch -f              # watch scans
```

A timer rather than a resident service: the work is 26 ms every 2 minutes, so a
long-lived process buys nothing and adds a failure mode — a wedged loop stops
scanning silently, whereas a oneshot that dies simply runs again next interval.
`Persistent=true` catches up one run missed across a suspend or reboot; without
it the journal gains a silent gap every overnight, and that journal is the only
record of modification history.

Unlike `window-tracker` this needs no X session, so it keeps working after a
logout.

## Output

Both in `data/`, outside the watched tree so the scanner never sees its own writes:

| File | Contents |
|---|---|
| `snapshot.json` | current state of every file, plus the derived summary; written atomically |
| `events.csv` | append-only journal — `ts,event,path,size,birth,mtime` |

## Why a snapshot *and* a journal

**Birth time gives history for free.** ext4 records a creation time, readable
via `stat %W`, so "created per day" is exact and retroactive — the history is
already there before this ever runs. Python cannot read it directly:
`os.stat()` has no `st_birthtime` on Linux, so this shells out to coreutils
`stat`, batched through `xargs` in one pass.

**mtime alone cannot give a modification history.** It records only the *latest*
edit. A file edited Monday and Wednesday shows only Wednesday — so a per-day
modification chart built from mtime silently rewrites its own past as you work,
with old days shrinking. That needs the journal: each scan diffs against the
previous snapshot and appends what changed.

**Deletions leave no filesystem trace at all.** Only the journal sees them.

## Classification

The question is only: has this been edited since it appeared here?

**The answer comes from the journal, not from timestamps.** A file is
**revisited** if the journal has ever recorded a modification for it; the
`mtime > birth` test survives only as a fallback for history predating the
journal.

That is not defensive coding. Editors that save *atomically* — write a temp
file, rename it over the original — give the file a **new inode on every save**,
so its birth time is reset to equal its mtime and `mtime > birth` can never
become true. xed and knotes both do this. On the notes folder the timestamp
test reported **1 revisited file out of 47** while the journal held
modifications for **41 of them**: a settle rate of 2% for a folder that had
just been almost entirely rewritten.

Any folder edited with such a tool will show the same, so this is less a
knotes quirk than something knotes made visible.

An earlier version had a third category, "imported", for `mtime < birth`. It
was wrong — it captured 76% of the folder. Copying a directory gives every file
a fresh birth time while preserving content mtimes, so whole trees landed there
on gaps of 45–60 seconds. A file copied in and never opened is unreviewed in
exactly the same way as one created and never opened; both are backlog.
`is_imported()` survives as an informational flag for gaps over a day (real
downloads), which still count as backlog.

## Indicators

- **Unreviewed backlog**, bucketed by age: today / 2–7d / 8–30d / 30d+
- **The oldest untouched file, named** — a count does not make you act, a name does
- **Settle rate** — share of files ever edited after arriving
- **Created per day** — from the filename when it encodes a timestamp
  (`2026_07_22-102037~T~p3~Dev.md`), else from birth time

  Same cause as above: atomic saves reset birth time, so the filesystem
  reported every note in the folder as created today. Filename and inode
  disagreed by up to **20 days**.

## Configuration

At the top of `file_watch.py`:

| Setting | Default |
|---|---|
| `WATCH_DIR` | `~/new/knotes` |
| `SCAN_INTERVAL` | `120` seconds (daemon mode; the timer is what runs) |
| `MIN_FILE_BYTES` | `3` — 0/1/2-byte files are debris, not backlog |
| `EXCLUDE_HIDDEN` | `True` — any path component starting with `.` |
| `EXCLUDE_DIRS` | `node_modules`, `__pycache__`, `venv`, `env` |
| `EXCLUDE_PATHS` | empty for knotes — see below |
| `MAX_FILE_MB` | 5 |

`EXCLUDE_PATHS` is empty for knotes because everything that should not count is
already hidden — the app's own `.imported`, `.trash` and `.deleted`, Syncthing's
`.stfolder` and `.stversions` — and `EXCLUDE_HIDDEN` skips those without being
told. It earns its keep on a tree where tooling output is *visible*: pointed at
`~/shr/dev-test` it excluded `scr-logs`, rewritten by the window tracker
every few seconds, and `bkp-prj`, which gains a ~35 MB zip per backup. Unexcluded,
either would dominate every "most active file" and "new files today" figure with
the system's own noise.

## Known limitation

`atime` is mounted `relatime`, so "have I read this?" is only partly
answerable — `atime > mtime` means opened since last written, but `grep -r`,
backups and indexers all trip it. Too noisy for a headline number.
