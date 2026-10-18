#!/usr/bin/env python3
"""
firefox_bookmarks.py

Export Firefox bookmarks from places.sqlite to Markdown, then optionally
delete those bookmarks from the live Firefox profile database.

Linux-focused.
Tested logic against the standard Firefox Places schema:
- moz_bookmarks
- moz_places

USAGE EXAMPLES

1) Export only (no arguments needed — writes a knotes note, see below):
    python3 firefox_bookmarks.py

   or to a path you choose:
    python3 firefox_bookmarks.py --export-md bookmarks.md

2) Export and delete all normal bookmarks (close Firefox first):
    python3 firefox_bookmarks.py --export-md bookmarks.md --delete

3) Export only bookmarks under a folder:
    python3 firefox_bookmarks.py --export-md dev.md --folder "Development"

4) Export and delete only bookmarks matched by URL substring:
    python3 firefox_bookmarks.py --export-md matched.md --url-contains github.com --delete

Deletion only happens with --delete, only after the export file is written, and
always after copying places.sqlite to places.sqlite.backup-<timestamp>.

5) Use a specific profile directory:
    python3 firefox_bookmarks.py --profile ~/.mozilla/firefox/xxxxxxxx.default-release --export-md bookmarks.md

6) Save the note in another knotes folder, with a chosen priority and category:
    python3 firefox_bookmarks.py --knotes ~/notes --priority 3 --category Z

7) Unattended (systemd timer): skip quietly while Firefox has the profile open:
    python3 firefox_bookmarks.py --profile ~/.mozilla/firefox/xxxxxxxx.default-release --delete --if-closed

Without --export-md the export is saved as a knotes note (type R: a Resource) in the
knotes folder (--knotes, else $NIBRAS_KNOTES_DIR, else ~/new/h-knotes-new):
    <yyyy_mm_dd-hhmmss>~R~p<priority>~<category>.md
The timestamp is the export time. Priority and category default to the
folder's .knotes.json (priority_default, default_category), else p2 and D.
"""

from __future__ import annotations

import argparse
import configparser
import datetime as dt
import os
import shutil
import sqlite3
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from knotes_out import add_note_args, check_note_args, knotes_dir, new_note_path  # noqa: E402


NOTE_TYPE = "R"   # bookmarks are Resources (the Import page asks for a resource type)
NOTE_TITLE = "Firefox bookmarks export"


@dataclass
class Bookmark:
    bookmark_id: int
    place_id: Optional[int]
    title: str
    url: str
    parent_id: int
    parent_title: str
    date_added: Optional[int]
    last_modified: Optional[int]


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def expand(p: str) -> Path:
    return Path(os.path.expanduser(p)).resolve()


def find_firefox_root() -> Path:
    return expand("~/.mozilla/firefox")


def find_default_profile_from_profiles_ini(firefox_root: Path) -> Optional[Path]:
    profiles_ini = firefox_root / "profiles.ini"
    if not profiles_ini.exists():
        return None

    parser = configparser.RawConfigParser()
    parser.read(profiles_ini)

    install_default_path = None

    for section in parser.sections():
        if section.startswith("Install") and parser.has_option(section, "Default"):
            install_default_path = parser.get(section, "Default")
            break

    if install_default_path:
        p = firefox_root / install_default_path
        if p.exists():
            return p

    candidates = []
    for section in parser.sections():
        if section.startswith("Profile"):
            path_value = parser.get(section, "Path", fallback=None)
            is_relative = parser.get(section, "IsRelative", fallback="1")
            is_default = parser.get(section, "Default", fallback="0")

            if not path_value:
                continue

            prof = (firefox_root / path_value) if is_relative == "1" else Path(path_value).expanduser()
            if prof.exists():
                candidates.append((is_default == "1", prof))

    for is_default, prof in candidates:
        if is_default:
            return prof

    if candidates:
        return candidates[0][1]

    return None


def ensure_profile(profile_arg: Optional[str]) -> Path:
    if profile_arg:
        profile = expand(profile_arg)
        if not profile.exists():
            raise FileNotFoundError(f"Profile directory not found: {profile}")
        return profile

    firefox_root = find_firefox_root()
    if not firefox_root.exists():
        raise FileNotFoundError(f"Firefox directory not found: {firefox_root}")

    profile = find_default_profile_from_profiles_ini(firefox_root)
    if profile is None:
        raise FileNotFoundError("Could not locate a Firefox profile from profiles.ini")

    return profile


def copy_places_db(profile_dir: Path) -> Path:
    src = profile_dir / "places.sqlite"
    if not src.exists():
        raise FileNotFoundError(f"places.sqlite not found in profile: {src}")

    tmpdir = Path(tempfile.mkdtemp(prefix="ff_places_copy_"))
    dst = tmpdir / "places.sqlite"
    shutil.copy2(src, dst)
    return dst


def backup_places_db(profile_dir: Path) -> Path:
    src = profile_dir / "places.sqlite"
    if not src.exists():
        raise FileNotFoundError(f"places.sqlite not found in profile: {src}")

    ts = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = profile_dir / f"places.sqlite.backup-{ts}"
    shutil.copy2(src, backup)
    return backup


def open_db(db_path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    return conn


def folder_name_to_id(conn: sqlite3.Connection, folder_name: str) -> list[int]:
    rows = conn.execute(
        """
        SELECT id
        FROM moz_bookmarks
        WHERE type = 2 AND title = ?
        """,
        (folder_name,),
    ).fetchall()
    return [int(r["id"]) for r in rows]


def fetch_bookmarks(
    conn: sqlite3.Connection,
    folder: Optional[str] = None,
    url_contains: Optional[str] = None,
    title_contains: Optional[str] = None,
) -> list[Bookmark]:
    params = []
    where = [
        "b.type = 1",
        "p.url IS NOT NULL",
    ]

    if folder:
        folder_ids = folder_name_to_id(conn, folder)
        if not folder_ids:
            return []
        placeholders = ",".join("?" for _ in folder_ids)
        where.append(f"b.parent IN ({placeholders})")
        params.extend(folder_ids)

    if url_contains:
        where.append("p.url LIKE ?")
        params.append(f"%{url_contains}%")

    if title_contains:
        where.append("COALESCE(b.title, '') LIKE ?")
        params.append(f"%{title_contains}%")

    sql = f"""
        SELECT
            b.id AS bookmark_id,
            b.fk AS place_id,
            COALESCE(b.title, '(untitled)') AS title,
            p.url AS url,
            b.parent AS parent_id,
            COALESCE(parent.title, '(root)') AS parent_title,
            b.dateAdded AS date_added,
            b.lastModified AS last_modified
        FROM moz_bookmarks b
        JOIN moz_places p
            ON b.fk = p.id
        LEFT JOIN moz_bookmarks parent
            ON b.parent = parent.id
        WHERE {" AND ".join(where)}
        ORDER BY b.dateAdded DESC, title COLLATE NOCASE, p.url COLLATE NOCASE
    """

    rows = conn.execute(sql, params).fetchall()

    return [
        Bookmark(
            bookmark_id=int(r["bookmark_id"]),
            place_id=int(r["place_id"]) if r["place_id"] is not None else None,
            title=str(r["title"]),
            url=str(r["url"]),
            parent_id=int(r["parent_id"]),
            parent_title=str(r["parent_title"]),
            date_added=r["date_added"],
            last_modified=r["last_modified"],
        )
        for r in rows
    ]


def firefox_time_to_iso(us: Optional[int]) -> str:
    if not us:
        return ""
    try:
        return dt.datetime.utcfromtimestamp(us / 1_000_000).isoformat() + "Z"
    except Exception:
        return ""


NO_DATE = "(no date)"


def firefox_time_to_date(us: Optional[int]) -> str:
    """Local calendar date (YYYY-MM-DD) of a Firefox microsecond timestamp."""
    if not us:
        return NO_DATE
    try:
        return dt.datetime.fromtimestamp(us / 1_000_000).strftime("%Y-%m-%d")
    except Exception:
        return NO_DATE


def escape_md(text: str) -> str:
    return (
        text.replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
    )


def write_markdown(bookmarks: Iterable[Bookmark], output_file: Path, source_profile: Path) -> None:
    """
    Write the bookmarks grouped under a bold line per date added (newest date
    first). Each entry is just a Markdown link — no bullets, no metadata — and
    entries are separated by a blank line.

    The file is a knotes note: it starts with a plain title line, and has no
    '#' headings, since the manuscript embeds notes under its own ##/###
    structure.
    """
    bookmarks = list(bookmarks)

    # Undated bookmarks sort last; everything else is newest-first, matching the
    # query's ORDER BY (descending timestamps => descending dates).
    groups: dict[str, list[Bookmark]] = {}
    for bm in bookmarks:
        groups.setdefault(firefox_time_to_date(bm.date_added), []).append(bm)
    dates = sorted((d for d in groups if d != NO_DATE), reverse=True)
    if NO_DATE in groups:
        dates.append(NO_DATE)

    with output_file.open("w", encoding="utf-8") as f:
        f.write(f"{NOTE_TITLE}\n\n")
        f.write(f"_{len(bookmarks)} bookmarks · exported "
                f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}_\n\n")
        for date in dates:
            f.write(f"**{date}**\n\n")
            for bm in groups[date]:
                title = escape_md(bm.title or "(untitled)")
                f.write(f"[{title}]({bm.url})\n\n")


SYNC_STATUS_NORMAL = 2    # moz_bookmarks.syncStatus of an item already on the Sync server


def delete_bookmarks(conn: sqlite3.Connection, bookmark_ids: list[int]) -> int:
    """
    Delete bookmarks the way Firefox's own Bookmarks.remove() does, so the
    deletion survives Firefox Sync.

    A bare DELETE is not enough with Sync on: Firefox only uploads a deletion
    when it finds a tombstone (guid in moz_bookmarks_deleted). Without one,
    the next sync sees the bookmarks still on the server, takes them for new
    remote items, and restores them. The triggers that would keep the rest in
    step live only on Firefox's own connection (TEMP triggers), so it is all
    done by hand here:
      - a tombstone for every item already synced (syncStatus NORMAL);
      - the parent folders' syncChangeCounter bumped, so their new child
        lists are uploaded too, and their lastModified updated;
      - the remaining siblings renumbered so positions stay contiguous;
      - moz_places.foreign_count decremented for the removed bookmarks.
    """
    if not bookmark_ids:
        return 0

    placeholders = ",".join("?" for _ in bookmark_ids)
    cols = {r["name"] for r in conn.execute("PRAGMA table_info(moz_bookmarks)")}
    has_sync = {"guid", "syncStatus", "syncChangeCounter"} <= cols and conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'moz_bookmarks_deleted'"
    ).fetchone() is not None
    now_us = int(dt.datetime.now().timestamp() * 1_000_000)

    cur = conn.cursor()
    cur.execute("BEGIN IMMEDIATE")
    rows = cur.execute(
        f"SELECT id, fk, parent{', guid, syncStatus' if has_sync else ''} "
        f"FROM moz_bookmarks WHERE id IN ({placeholders})",
        bookmark_ids,
    ).fetchall()
    parents = {r["parent"] for r in rows}

    if has_sync:
        cur.executemany(
            "INSERT OR REPLACE INTO moz_bookmarks_deleted (guid, dateRemoved) VALUES (?, ?)",
            [(r["guid"], now_us) for r in rows
             if r["guid"] and r["syncStatus"] == SYNC_STATUS_NORMAL],
        )

    cur.execute(
        f"DELETE FROM moz_bookmarks WHERE id IN ({placeholders})",
        bookmark_ids,
    )
    deleted = cur.rowcount

    for parent in parents:
        children = cur.execute(
            "SELECT id FROM moz_bookmarks WHERE parent = ? ORDER BY position, id", (parent,)
        ).fetchall()
        cur.executemany(
            "UPDATE moz_bookmarks SET position = ? WHERE id = ?",
            [(i, c["id"]) for i, c in enumerate(children)],
        )
        if has_sync:
            cur.execute(
                "UPDATE moz_bookmarks SET syncChangeCounter = syncChangeCounter + 1, "
                "lastModified = ? WHERE id = ?",
                (now_us, parent),
            )
        else:
            cur.execute("UPDATE moz_bookmarks SET lastModified = ? WHERE id = ?", (now_us, parent))

    if "foreign_count" in {r["name"] for r in conn.execute("PRAGMA table_info(moz_places)")}:
        cur.executemany(
            "UPDATE moz_places SET foreign_count = MAX(foreign_count - 1, 0) WHERE id = ?",
            [(r["fk"],) for r in rows if r["fk"] is not None],
        )

    conn.commit()
    return deleted


def count_bookmarks(profile_dir: Path) -> str:
    """Bookmark count for a profile, via a temp copy so a live Firefox can't block it."""
    try:
        db = copy_places_db(profile_dir)
    except Exception as ex:
        return f"unreadable ({ex})"
    try:
        with open_db(db) as conn:
            return str(conn.execute(
                "SELECT COUNT(*) FROM moz_bookmarks WHERE type = 1"
            ).fetchone()[0])
    except Exception as ex:
        return f"unreadable ({ex})"
    finally:
        try:
            db.unlink(missing_ok=True)
            db.parent.rmdir()
        except Exception:
            pass


def list_profiles(firefox_root: Path, auto: Optional[Path]) -> None:
    """
    Show every profile holding a places.sqlite, with its bookmark count, marking
    the one auto-detection would pick.

    Worth checking before trusting the default: profiles.ini's install-default
    entry is rewritten whenever Firefox switches profiles, so the profile this
    script picks can change between runs without anything in the script changing.
    """
    profiles = sorted(p for p in firefox_root.iterdir()
                      if p.is_dir() and (p / "places.sqlite").exists())
    if not profiles:
        print(f"No profiles with a places.sqlite under {firefox_root}")
        return

    print(f"Profiles under {firefox_root}:\n")
    for p in profiles:
        mark = "->" if auto is not None and p == auto else "  "
        running = " [firefox running]" if profile_in_use(p) else ""
        print(f" {mark} {p.name}    bookmarks: {count_bookmarks(p)}{running}")
    print("\n '->' is the profile used when --profile is omitted.")
    print(" Pin it explicitly with --profile for cron jobs.")


def profile_in_use(profile_dir: Path) -> bool:
    """
    True while Firefox has the profile open.

    On Linux it points the profile's `lock` symlink at "<ip>:+<pid>" while
    running. An unclean exit leaves that symlink behind, so its presence alone
    means nothing — the pid it names has to still be alive. Anything we can't
    parse is treated as locked, which errs toward refusing to delete.
    """
    lock = profile_dir / "lock"
    if not os.path.lexists(lock):
        return False

    try:
        pid = int(os.readlink(lock).rsplit(":+", 1)[-1])
    except (OSError, ValueError):
        return True

    try:
        os.kill(pid, 0)            # signal 0 only probes for existence
    except ProcessLookupError:
        return False               # stale lock from a crashed session
    except PermissionError:
        return True                # alive, just owned by another user
    return True


def maybe_vacuum(conn: sqlite3.Connection) -> None:
    try:
        conn.execute("VACUUM")
    except sqlite3.DatabaseError:
        pass


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Export Firefox bookmarks to Markdown and optionally delete them."
    )
    p.add_argument("--profile", help="Firefox profile directory")
    p.add_argument(
        "--export-md",
        help="Markdown output file path (default: a new knotes note in --knotes)",
    )
    add_note_args(p)
    p.add_argument(
        "--if-closed",
        action="store_true",
        help="Do nothing (exit 0) while Firefox has the profile open — for timers, so a "
             "run never exports bookmarks it then can't delete",
    )
    p.add_argument("--folder", help="Only include bookmarks directly inside a folder with this title")
    p.add_argument("--url-contains", help="Only include bookmarks whose URL contains this text")
    p.add_argument("--title-contains", help="Only include bookmarks whose title contains this text")
    p.add_argument(
        "--delete",
        action="store_true",
        help="After exporting, delete those bookmarks from the live Firefox database "
             "(a timestamped backup of places.sqlite is made first). Refuses while "
             "Firefox is running.",
    )
    p.add_argument(
        "--yes-close-firefox",
        action="store_true",
        help=argparse.SUPPRESS,        # kept so older commands still run; no longer needed
    )
    p.add_argument(
        "--no-vacuum",
        action="store_true",
        help="Skip VACUUM after deletion",
    )
    p.add_argument(
        "--list-profiles",
        action="store_true",
        help="List every Firefox profile with a bookmark count, then exit. Use it to "
             "find the right --profile value; auto-detection follows profiles.ini, "
             "which Firefox rewrites when you switch profiles.",
    )
    p.add_argument(
        "--force",
        action="store_true",
        help="Delete even if Firefox appears to be running (it may undo the deletion "
             "from its in-memory copy, or corrupt the database)",
    )
    args = p.parse_args()
    check_note_args(p, args)
    return args


def main() -> int:
    args = parse_args()

    try:
        profile_dir = ensure_profile(args.profile)
    except Exception as ex:
        if args.list_profiles:
            profile_dir = None                     # still worth listing what exists
        else:
            eprint(f"ERROR: {ex}")
            return 1

    if args.list_profiles:
        list_profiles(find_firefox_root(), profile_dir)
        return 0

    # Printed every run: which profile was used is not obvious when it is
    # auto-detected, and exporting the wrong one looks like data loss.
    print(f"Profile: {profile_dir}{'' if args.profile else '  (auto-detected)'}")

    if args.if_closed and profile_in_use(profile_dir):
        print("Firefox has this profile open — skipped (--if-closed).")
        return 0

    if args.export_md:
        export_path = expand(args.export_md)
    else:
        folder = knotes_dir(args.knotes)
        folder.mkdir(parents=True, exist_ok=True)
        export_path = new_note_path(folder, NOTE_TYPE, args.priority, args.category)

    try:
        copied_db = copy_places_db(profile_dir)
    except Exception as ex:
        eprint(f"ERROR copying places.sqlite for export: {ex}")
        return 1

    try:
        with open_db(copied_db) as conn:
            bookmarks = fetch_bookmarks(
                conn,
                folder=args.folder,
                url_contains=args.url_contains,
                title_contains=args.title_contains,
            )
    except Exception as ex:
        eprint(f"ERROR reading copied database: {ex}")
        return 1
    finally:
        try:
            copied_db.unlink(missing_ok=True)
            copied_db.parent.rmdir()
        except Exception:
            pass

    if not bookmarks:
        print("No matching bookmarks found.")
        return 0

    try:
        export_path.parent.mkdir(parents=True, exist_ok=True)
        write_markdown(bookmarks, export_path, profile_dir)
    except Exception as ex:
        eprint(f"ERROR writing Markdown: {ex}")
        return 1

    print(f"Exported {len(bookmarks)} bookmarks to {export_path}")

    if not args.delete:
        return 0

    if profile_in_use(profile_dir) and not args.force:
        eprint("Refusing deletion: Firefox is running with this profile.")
        eprint("It keeps its own copy of the bookmarks in memory and would write it back,")
        eprint("undoing the deletion. Close Firefox and rerun, or pass --force.")
        return 2

    try:
        backup = backup_places_db(profile_dir)
        print(f"Backup created: {backup}")
    except Exception as ex:
        eprint(f"ERROR creating backup: {ex}")
        return 1

    live_db = profile_dir / "places.sqlite"
    try:
        with open_db(live_db) as conn:
            bookmark_ids = [b.bookmark_id for b in bookmarks]
            deleted = delete_bookmarks(conn, bookmark_ids)
            if not args.no_vacuum:
                maybe_vacuum(conn)
    except Exception as ex:
        eprint(f"ERROR deleting bookmarks from live database: {ex}")
        eprint("Your backup remains available.")
        return 1

    print(f"Deleted {deleted} bookmarks from Firefox.")
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
