#!/usr/bin/env python3
"""
knotes output, shared by the capture tools.

A knotes note is a file named

    <yyyy_mm_dd-hhmmss>~<type>~p<priority>~<category>.md

with an optional companion folder of the same name ending ".d" for its files.
The backend's Import page (NoteImportService) reads exactly this from
NIBRAS_KNOTES_DIR: the type picks the module (N note, R resource, ...), the
companion folder's files become the record's attachments.

Notes start with a plain title line and contain no '#' headings: the Zettlr
manuscript embeds notes under its own ##/### structure.

As a library:   from knotes_out import knotes_dir, new_note_path
As a CLI (for the shell tools) — reserves a free name and prints its path:
    knotes_out.py new [--dir DIR] [--type R] [--priority N] [--category Z]
"""

import argparse
import json
import os
import re
from datetime import datetime, timedelta
from pathlib import Path

# Where notes go unless --dir / --knotes says otherwise. The backend reads the
# same variable, so setting it once points capture and import at one folder.
DEFAULT_DIR = "~/new/h-knotes-new"
FALLBACK_PRIORITY = 2      # used when the folder has no .knotes.json
FALLBACK_CATEGORY = "D"


def knotes_dir(override: str | None = None) -> Path:
    """The knotes folder: override, else $NIBRAS_KNOTES_DIR, else DEFAULT_DIR."""
    return Path(os.path.expanduser(
        override or os.environ.get("NIBRAS_KNOTES_DIR") or DEFAULT_DIR)).resolve()


def knotes_defaults(folder: Path) -> tuple[int, str]:
    """
    The default (priority, category) for new notes in a knotes folder, read
    from its .knotes.json; the fallbacks apply when there is none.
    """
    try:
        cfg = json.loads((folder / ".knotes.json").read_text(encoding="utf-8"))
    except (OSError, ValueError):
        cfg = {}
    return (int(cfg.get("priority_default", FALLBACK_PRIORITY)),
            str(cfg.get("default_category", FALLBACK_CATEGORY)))


def new_note_path(folder: Path, note_type: str = "N", priority: int | None = None,
                  category: str | None = None, when: datetime | None = None) -> Path:
    """
    A free note path in folder for `when` (default now). The name only has
    one-second resolution, so if that second is taken (by a note of any type,
    priority or category, or its .d folder) step forward until it isn't — a
    capture never overwrites an existing note. Priority and category default
    to the folder's .knotes.json.
    """
    default_priority, default_category = knotes_defaults(folder)
    priority = default_priority if priority is None else priority
    category = category or default_category
    when = when or datetime.now()
    while True:
        stamp = when.strftime("%Y_%m_%d-%H%M%S")
        if not any(folder.glob(f"{stamp}~*")):
            return folder / f"{stamp}~{note_type}~p{priority}~{category}.md"
        when += timedelta(seconds=1)


def companion_dir(note: Path) -> Path:
    """The note's ".d" folder (not created here)."""
    return note.with_name(note.name[:-3] + ".d")


def add_note_args(parser: argparse.ArgumentParser, dir_flag: str = "--knotes") -> None:
    """The --knotes/--priority/--category options every capture tool shares."""
    parser.add_argument(
        dir_flag, metavar="DIR", default=None,
        help=f"knotes folder to save the note in (default $NIBRAS_KNOTES_DIR, else {DEFAULT_DIR})",
    )
    parser.add_argument(
        "--priority", type=int, default=None, metavar="N",
        help="note priority (the pN in the name); default from the folder's "
             f".knotes.json, else {FALLBACK_PRIORITY}",
    )
    parser.add_argument(
        "--category", default=None, metavar="LETTER",
        help="note category letter (e.g. Z); default from the folder's "
             f".knotes.json, else {FALLBACK_CATEGORY}",
    )


def check_note_args(parser: argparse.ArgumentParser, args) -> None:
    if args.priority is not None and args.priority < 0:
        parser.error("--priority must not be negative")
    if args.category is not None and not re.fullmatch(r"[A-Z]", args.category):
        parser.error("--category must be a single capital letter, e.g. Z")


def main() -> int:
    p = argparse.ArgumentParser(description="Reserve a new knotes note and print its path.")
    sub = p.add_subparsers(dest="cmd", required=True)
    new = sub.add_parser("new", help="create an empty note with a free name; print its path")
    add_note_args(new, "--dir")
    new.add_argument("--type", default="N", help="note type letter (default N)")
    args = p.parse_args()
    check_note_args(new, args)
    if not re.fullmatch(r"[A-Z]", args.type):
        new.error("--type must be a single capital letter, e.g. R")

    folder = knotes_dir(args.dir)
    folder.mkdir(parents=True, exist_ok=True)
    note = new_note_path(folder, args.type, args.priority, args.category)
    note.touch(exist_ok=False)     # reserve the name before the caller writes it
    print(note)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
