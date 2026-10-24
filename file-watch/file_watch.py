#!/usr/bin/env python3
"""File review tracker — scanner and journal.

    python3 file_watch.py --once     # one scan, print a summary
    python3 file_watch.py            # scan every SCAN_INTERVAL seconds

Why both a snapshot and a journal:

  * Birth time (ext4 crtime, via `stat %W`) gives the creation date of every
    existing file, so "created per day" is exact and RETROACTIVE — the history
    is already there before this ever runs.

  * mtime only records the LATEST edit. A file edited Monday and Wednesday
    shows only Wednesday, so a per-day modification count built from mtime
    alone silently rewrites its own history as you work. That needs a journal:
    each scan diffs against the previous snapshot and appends what changed.

  * Deletions leave no trace at all in the filesystem. Only the journal sees
    them.
"""
import csv
import json
import os
import re
import subprocess
import sys
import time
from datetime import date, datetime

# ── config ────────────────────────────────────────────────────────────────────

WATCH_DIR     = "/home/akil/new/knotes"
DATA_DIR      = "/home/akil/prj/file-watch/data"
SCAN_INTERVAL = 120          # seconds (daemon mode; the timer is what runs in practice)

# Files this small carry no content — an empty note, a stray newline, a
# truncated save. They are debris to be deleted, not backlog to be reviewed,
# and counting them makes the folder look worse than it is.
#
# One consequence worth knowing: if a tracked file is truncated to nothing it
# drops out of view here and the journal records a "deleted" event for it. The
# file is still on disk. That is the honest reading anyway — nothing of it
# remains — but it is not literally gone.
MIN_FILE_BYTES = 3

EXCLUDE_HIDDEN = True        # any path component starting with "."
EXCLUDE_DIRS   = {"node_modules", "__pycache__", "venv", "env"}

# Deliberately empty for the knotes layout: everything that should not count
# towards the backlog is already hidden, and EXCLUDE_HIDDEN skips it — the app's
# own .imported, .trash and .deleted, Syncthing's .stfolder and .stversions, and
# the .notes.sqlite index. The live notes are the visible files.
#
# Add absolute paths here only for a directory that is visible but still not
# yours to review.
EXCLUDE_PATHS: set[str] = set()

SNAPSHOT   = os.path.join(DATA_DIR, "snapshot.json")
JOURNAL    = os.path.join(DATA_DIR, "events.csv")
JOURNAL_COLS = ["ts", "event", "path", "size", "birth", "mtime"]


# ── scanning ──────────────────────────────────────────────────────────────────

def is_excluded(dirpath: str, name: str) -> bool:
    if EXCLUDE_HIDDEN and name.startswith("."):
        return True
    if name in EXCLUDE_DIRS:
        return True
    return os.path.join(dirpath, name) in EXCLUDE_PATHS


def walk_files(root: str) -> list[str]:
    """Every non-hidden regular file under root, pruning excluded dirs."""
    found = []
    for dirpath, dirnames, filenames in os.walk(root):
        # Prune in place so os.walk never descends into them at all — this is
        # what keeps .git and .venv from costing anything.
        dirnames[:] = [d for d in dirnames if not is_excluded(dirpath, d)]
        for fn in filenames:
            if is_excluded(dirpath, fn):
                continue
            found.append(os.path.join(dirpath, fn))
    return found


def stat_all(paths: list[str]) -> dict[str, dict]:
    """birth/mtime/atime/size for each path.

    Python's os.stat() cannot return birth time on Linux — st_birthtime does
    not exist here — so this shells out to coreutils `stat`, batched through
    xargs in one pass rather than one process per file.
    """
    if not paths:
        return {}
    fmt = "%W\t%Y\t%X\t%s\t%n\\0"
    proc = subprocess.run(
        ["xargs", "-0", "stat", f"--printf={fmt}"],
        input=("\0".join(paths) + "\0").encode(),
        capture_output=True,
    )
    out = {}
    for rec in proc.stdout.decode("utf-8", "replace").split("\0"):
        if not rec.strip():
            continue
        parts = rec.split("\t", 4)
        if len(parts) != 5 or not parts[0].lstrip("-").isdigit():
            continue
        birth, mtime, atime, size, path = parts
        out[path] = {
            "birth": int(birth),      # 0 when the fs cannot report it
            "mtime": int(mtime),
            "atime": int(atime),
            "size":  int(size),
        }
    return out


# ── state ─────────────────────────────────────────────────────────────────────

def load_snapshot() -> dict:
    try:
        with open(SNAPSHOT, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError):
        return {}


def save_snapshot(data: dict) -> None:
    os.makedirs(DATA_DIR, exist_ok=True)
    tmp = SNAPSHOT + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp, SNAPSHOT)      # atomic: a reader never sees half a file


def append_events(events: list[dict]) -> None:
    if not events:
        return
    os.makedirs(DATA_DIR, exist_ok=True)
    new = not os.path.exists(JOURNAL) or os.path.getsize(JOURNAL) == 0
    with open(JOURNAL, "a", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=JOURNAL_COLS)
        if new:
            w.writeheader()
        for e in events:
            w.writerow(e)


def diff(old: dict, new: dict) -> list[dict]:
    """Events since the previous scan."""
    ts = datetime.now().isoformat(timespec="seconds")
    events = []
    for path, meta in new.items():
        prev = old.get(path)
        if prev is None:
            # Only call it "created" if it was really born since the last scan;
            # otherwise it is a file that moved into scope or the first ever run.
            kind = "created" if meta["birth"] else "seen"
            events.append({"ts": ts, "event": kind, "path": path,
                           "size": meta["size"], "birth": meta["birth"],
                           "mtime": meta["mtime"]})
        elif meta["mtime"] != prev.get("mtime") or meta["size"] != prev.get("size"):
            events.append({"ts": ts, "event": "modified", "path": path,
                           "size": meta["size"], "birth": meta["birth"],
                           "mtime": meta["mtime"]})
    for path, meta in old.items():
        if path not in new:
            events.append({"ts": ts, "event": "deleted", "path": path,
                           "size": meta.get("size", 0),
                           "birth": meta.get("birth", 0),
                           "mtime": meta.get("mtime", 0)})
    return events


# ── derived indicators ────────────────────────────────────────────────────────

IMPORT_GAP_SEC = 86400   # mtime this much older than birth = genuinely imported

# knotes names every note after the moment it was created:
#     2026_07_22-102037~T~p3~Dev.md
# and that is more trustworthy than the filesystem here. An editor that saves
# atomically — xed and knotes both do, writing a temp file and renaming it over
# the original — gives the file a NEW inode on every save, so ext4's birth time
# records the last save rather than the creation. Left to the filesystem, a
# folder edited this afternoon looks like it was all created this afternoon,
# and every age and per-day count built on it is wrong.
NAME_TS_RE = re.compile(r"(\d{4})_(\d{2})_(\d{2})-(\d{2})(\d{2})(\d{2})")


def filename_birth(path: str) -> int:
    """Creation time encoded in the filename, or 0 if it encodes none."""
    m = NAME_TS_RE.search(os.path.basename(path))
    if not m:
        return 0
    try:
        return int(datetime(*(int(g) for g in m.groups())).timestamp())
    except ValueError:
        return 0            # 2026_02_30-… and similar: not a real date


def effective_birth(path: str, meta: dict) -> int:
    """When the file came into being, filename first, filesystem second."""
    return filename_birth(path) or meta.get("birth", 0)


def journal_edited() -> set[str]:
    """Paths the journal has ever recorded a modification for.

    Needed because birth time is not a reliable arrival marker in every
    directory. knotes saves atomically — write a temp file, rename it over the
    original — so every save produces a NEW inode with a fresh birth time and
    mtime equal to it. The `mtime > birth` test can then never see an edit: it
    reported 1 revisited file out of 47 while the journal held modifications
    for 41 of them.

    The journal only knows what has happened since it started watching, so the
    timestamp test below is kept as the fallback for older history.
    """
    edited: set[str] = set()
    try:
        with open(JOURNAL, encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                if row.get("event") == "modified":
                    edited.add(row.get("path", ""))
    except OSError:
        pass
    return edited


def classify(meta: dict, path: str = "", edited: set[str] | None = None,
             birth: int | None = None) -> str:
    """untouched | revisited

    The question is only: has this been edited SINCE IT APPEARED HERE?

    An earlier version treated mtime < birth as its own "imported" category,
    which was wrong. Copying a directory gives every file a fresh birth time
    while preserving content mtimes, so whole trees landed there on gaps of
    45-60 seconds. For reviewing purposes a file copied in and never opened is
    in the same state as one created and never opened — unreviewed. Both are
    backlog.
    """
    if edited and path in edited:
        return "revisited"          # the journal saw it change; timestamps may not
    b = meta["birth"] if birth is None else birth
    m = meta["mtime"]
    if not b:
        return "unknown"
    return "revisited" if m > b + 1 else "untouched"


def is_imported(meta: dict) -> bool:
    """Informational only: content substantially predates arrival (a download,
    or a copy that preserved timestamps). Still counts as backlog."""
    b, m = meta["birth"], meta["mtime"]
    return bool(b) and m < b - IMPORT_GAP_SEC


def summarise(files: dict) -> dict:
    today = date.today()
    edited = journal_edited()
    buckets = {"today": 0, "2-7d": 0, "8-30d": 0, "30d+": 0}
    kinds = {"untouched": 0, "revisited": 0, "unknown": 0}
    imported = 0
    created_per_day: dict[str, int] = {}
    oldest = None

    for path, meta in files.items():
        birth = effective_birth(path, meta)
        kind = classify(meta, path, edited, birth)
        kinds[kind] += 1
        if is_imported(meta):
            imported += 1
        if not birth:
            continue
        bday = date.fromtimestamp(birth)
        created_per_day[bday.isoformat()] = created_per_day.get(bday.isoformat(), 0) + 1
        if kind != "untouched":
            continue
        age = (today - bday).days
        if age <= 0:   buckets["today"] += 1
        elif age <= 7: buckets["2-7d"] += 1
        elif age <= 30: buckets["8-30d"] += 1
        else:          buckets["30d+"] += 1
        if oldest is None or birth < oldest[2]:
            oldest = (path, meta, birth)

    settled = kinds["revisited"]
    authored = kinds["untouched"] + kinds["revisited"]
    return {
        "scanned_at": datetime.now().isoformat(timespec="seconds"),
        "watch_dir": WATCH_DIR,
        "total_files": len(files),
        "kinds": kinds,
        "imported": imported,
        "untouched_by_age": buckets,
        "settle_rate": round(settled / authored * 100) if authored else 0,
        "created_per_day": dict(sorted(created_per_day.items())),
        "oldest_untouched": (
            {"path": os.path.relpath(oldest[0], WATCH_DIR),
             "age_days": (today - date.fromtimestamp(oldest[2])).days}
            if oldest else None
        ),
    }


def scan_once(verbose: bool = False) -> dict:
    paths = walk_files(WATCH_DIR)
    files = {p: m for p, m in stat_all(paths).items()
             if m["size"] >= MIN_FILE_BYTES}
    old = load_snapshot()

    # A snapshot taken of a different directory is not a baseline for this one.
    # Diffing across the change reports every file of the old tree as deleted
    # and every file of the new one as created — none of which happened. That
    # is not a cosmetic problem: the journal is the record of what became of
    # your files, and 465 invented deletions make it worth less than no record.
    prev_dir = (old.get("summary") or {}).get("watch_dir")
    if prev_dir and prev_dir != WATCH_DIR:
        # Record nothing at all, rather than diffing against an empty baseline:
        # that would replace the phantom deletions with an equally false set of
        # "created" events for files that have been sitting there for weeks.
        # The first scan of a new tree is a baseline, and a baseline is not news.
        if verbose:
            print(f"watch dir changed: {prev_dir} -> {WATCH_DIR}")
            print("  recording a baseline; no events for files that merely "
                  "came into view")
        events = []
    else:
        events = diff(old.get("files", {}), files)
    append_events(events)

    summary = summarise(files)
    save_snapshot({"files": files, "summary": summary})

    if verbose:
        k = summary["kinds"]
        b = summary["untouched_by_age"]
        print(f"scanned {summary['total_files']} files in {WATCH_DIR}")
        print(f"  untouched {k['untouched']} · revisited {k['revisited']} "
              f"  (of which arrived as copies/downloads: {summary['imported']})")
        print(f"  backlog:  today {b['today']} · 2-7d {b['2-7d']} "
              f"· 8-30d {b['8-30d']} · 30d+ {b['30d+']}")
        print(f"  settle rate: {summary['settle_rate']}%")
        if summary["oldest_untouched"]:
            o = summary["oldest_untouched"]
            print(f"  oldest untouched: {o['path']}  ({o['age_days']}d)")
        print(f"  journal: +{len(events)} events")
    return summary


if __name__ == "__main__":
    if "--once" in sys.argv:
        scan_once(verbose=True)
    else:
        print(f"Watching {WATCH_DIR} every {SCAN_INTERVAL}s. Ctrl+C to stop.",
              flush=True)
        try:
            while True:
                s = scan_once()
                print(f"[{s['scanned_at']}] {s['total_files']} files, "
                      f"backlog {sum(s['untouched_by_age'].values())}", flush=True)
                time.sleep(SCAN_INTERVAL)
        except KeyboardInterrupt:
            print("\nstopped")
