"""Plain-text note storage — the desktop port of the Android app's note layer.

A note is just a text file on disk whose *name* encodes its tags:

    <timestamp>~<type>~p<priority>~<category>.md
    e.g.  2026_06_10-143025~W~p3~A.md

The priority token always carries a ``p`` prefix so it can be found unambiguously
regardless of the timestamp format; the token before it is the type, the token after
it is the category. Empty type/category are allowed (``...~~p3~.md``).

This module is deliberately free of any GUI code so it is easy to read, test, and
reuse. It matches the Kotlin ``NoteFiles``/``NoteRepository`` byte-for-byte, so notes
created here and on the phone live happily in the same (e.g. Syncthing-synced) folder.
"""

from __future__ import annotations

import os
import re
import shutil
import time
from dataclasses import dataclass, field
from datetime import datetime

# Arabic (and its supplements / presentation forms) → treat the note as right-to-left.
_RTL = re.compile(r"[؀-ۿݐ-ݿࢠ-ࣿﭐ-﷿ﹰ-﻿]")
_PRIORITY = re.compile(r"^p(\d+)$")

# Canonical note-file name — the single rule for *counting* notes, shared across desktop/terminal/
# mobile/backend: <timestamp>~<type>~p<priority>~<subject>.md (type/subject may be empty; an optional
# -N de-dup suffix is allowed). Only these .md files count; foreign .md and legacy files are ignored,
# and listing is never recursive.
_NOTE_NAME = re.compile(r"^\d{4}_\d{2}_\d{2}-\d{6}(-\d+)?~[^~]*~[pP]\d+~[^~]*\.md$")


def is_rtl(text: str) -> bool:
    return bool(_RTL.search(text))


def is_note_file(name: str) -> bool:
    """True only for `.md` files whose name matches the canonical note format."""
    return bool(_NOTE_NAME.match(name))


# A note's attached files live in a sibling folder named after the note's stem with this suffix
# (e.g. ``2026_06_10-143025~N~p2~F.md`` → ``2026_06_10-143025~N~p2~F.d/``). It sits next to the .md
# so it's easy to see and sync; listing is by .md files only, so the folder never shows as a note.
COMPANION_SUFFIX = ".d"


def companion_dir(note_path: str) -> str:
    """The folder holding a note's attached files, next to the .md (may not exist yet)."""
    stem, _ext = os.path.splitext(note_path)
    return stem + COMPANION_SUFFIX


def companion_files(note_path: str) -> list[str]:
    """Absolute paths of the files attached to a note (its companion folder's contents), sorted."""
    d = companion_dir(note_path)
    if not os.path.isdir(d):
        return []
    return sorted(os.path.join(d, f) for f in os.listdir(d) if os.path.isfile(os.path.join(d, f)))


def now_token(fmt: str) -> str:
    """Current time formatted with a ``strftime`` pattern, falling back to the default."""
    try:
        return datetime.now().strftime(fmt)
    except (ValueError, TypeError):
        return datetime.now().strftime("%Y_%m_%d-%H%M%S")


def build_name(timestamp: str, type_: str | None, priority: int, category: str | None) -> str:
    # '*' is only a "default" marker in settings — never a real code — so strip it defensively here
    # so it can never leak into a file name regardless of where the code came from.
    t = (type_ or "").replace("*", "").strip()
    c = (category or "").replace("*", "").strip()
    return f"{timestamp}~{t}~p{priority}~{c}.md"


@dataclass
class Parsed:
    timestamp: str
    type: str | None
    priority: int | None
    category: str | None


def parse(file_name: str) -> Parsed:
    """Pull the tags back out of a note's file name (inverse of :func:`build_name`)."""
    base = file_name[:-3] if file_name.lower().endswith(".md") else file_name
    parts = base.split("~")
    timestamp = parts[0] if parts else base

    # Locate the priority token (p<number>) among everything after the timestamp.
    prio_idx = -1
    priority: int | None = None
    for i in range(1, len(parts)):
        m = _PRIORITY.match(parts[i])
        if m:
            prio_idx = i
            priority = int(m.group(1))
            break

    if prio_idx < 0:
        # No recognizable priority (e.g. a foreign .md file dropped in the directory).
        return Parsed(
            timestamp=timestamp,
            type=_blank_to_none(parts[1]) if len(parts) > 1 else None,
            priority=None,
            category=_blank_to_none(parts[2]) if len(parts) > 2 else None,
        )

    type_ = _blank_to_none(parts[1]) if prio_idx > 1 else None
    category = next((p for p in parts[prio_idx + 1:] if p.strip()), None)
    return Parsed(timestamp, type_, priority, category)


def _blank_to_none(s: str) -> str | None:
    return s if s.strip() else None


@dataclass
class Note:
    path: str
    timestamp: str
    type: str | None
    priority: int | None
    category: str | None
    content: str
    mtime: float
    is_rtl: bool
    file_names: list[str] = field(default_factory=list)  # basenames in the note's companion folder

    @property
    def n_files(self) -> int:
        return len(self.file_names)

    def preview(self, max_lines: int) -> str:
        lines = self.content.splitlines()[: max(max_lines, 1)]
        text = "\n".join(lines).rstrip()
        return text if text else self.content[:120]

    def title(self, max_chars: int, tail_chars: int = 0) -> str:
        """A one-line, length-capped summary for the compact list (whitespace collapsed). With
        ``tail_chars`` > 0, long notes keep their first ``max_chars`` and last ``tail_chars``
        characters, joined by an ellipsis, so the start *and* the end stay visible."""
        text = " ".join(self.content.split())
        if not text:
            return "(empty)"
        if tail_chars > 0:
            if len(text) > max_chars + tail_chars + 1:
                return text[:max_chars].rstrip() + " … " + text[-tail_chars:].lstrip()
            return text
        if len(text) > max_chars:
            text = text[:max_chars].rstrip() + "…"
        return text

    def word_count(self) -> int:
        """Number of whitespace-separated words in the note body."""
        return len(self.content.split())

    def time_text(self) -> str:
        return time.strftime("%Y-%m-%d %H:%M", time.localtime(self.mtime))


class NoteRepository:
    """Reads and writes notes as plain-text files in one directory."""

    def __init__(self, directory: str):
        self.directory = directory

    def _dir(self) -> str:
        os.makedirs(self.directory, exist_ok=True)
        return self.directory

    def list_notes(self) -> list[Note]:
        """Canonical-format ``.md`` notes directly in the directory (non-recursive), oldest first."""
        d = self._dir()
        notes = []
        for name in os.listdir(d):
            path = os.path.join(d, name)
            if os.path.isfile(path) and is_note_file(name):
                notes.append(self._to_note(path))
        notes.sort(key=lambda n: n.mtime)
        return notes

    def _to_note(self, path: str) -> Note:
        p = parse(os.path.basename(path))
        try:
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
        except OSError:
            content = ""
        return Note(
            path=path,
            timestamp=p.timestamp,
            type=p.type,
            priority=p.priority,
            category=p.category,
            content=content,
            mtime=os.path.getmtime(path),
            is_rtl=is_rtl(content),
            file_names=[os.path.basename(f) for f in companion_files(path)],
        )

    def save_new(self, text: str, type_: str | None, priority: int, category: str | None,
                 fmt: str) -> str:
        token = now_token(fmt)
        path = os.path.join(self._dir(), build_name(token, type_, priority, category))
        counter = 2
        while os.path.exists(path):
            path = os.path.join(self._dir(), build_name(f"{token}-{counter}", type_, priority, category))
            counter += 1
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        return path

    def update(self, note: Note, text: str, type_: str | None, priority: int,
               category: str | None) -> str:
        """Rewrite a note's body, renaming the file if its tags changed."""
        parent = os.path.dirname(note.path)
        target = os.path.join(parent, build_name(note.timestamp, type_, priority, category))
        if os.path.abspath(target) != os.path.abspath(note.path):
            counter = 2
            while os.path.exists(target):
                target = os.path.join(
                    parent, build_name(f"{note.timestamp}-{counter}", type_, priority, category))
                counter += 1
            os.rename(note.path, target)
            self._relocate_companion(note.path, target)  # keep the files folder next to the note
        with open(target, "w", encoding="utf-8") as f:
            f.write(text)
        return target

    def _relocate_companion(self, old_note_path: str, new_note_path: str) -> None:
        """Move a note's companion folder so it keeps following the note to a new name/location.
        If a folder already exists at the destination, merge the files in (de-duplicating names)."""
        old = companion_dir(old_note_path)
        if not os.path.isdir(old):
            return
        new = companion_dir(new_note_path)
        if not os.path.exists(new):
            try:
                os.rename(old, new)
                return
            except OSError:
                pass
        os.makedirs(new, exist_ok=True)
        for fname in os.listdir(old):
            src = os.path.join(old, fname)
            if not os.path.isfile(src):
                continue
            dst = os.path.join(new, fname)
            stem, ext = os.path.splitext(fname)
            counter = 2
            while os.path.exists(dst):
                dst = os.path.join(new, f"{stem}-{counter}{ext}")
                counter += 1
            try:
                os.rename(src, dst)
            except OSError:
                pass
        try:
            os.rmdir(old)  # remove the now-empty source folder (ignored if not empty)
        except OSError:
            pass

    def attach(self, note: Note, src_paths: list[str]) -> int:
        """Copy files into the note's companion folder (created on demand), de-duplicating names on
        collision. Returns the number of files added."""
        dest = companion_dir(note.path)
        os.makedirs(dest, exist_ok=True)
        added = 0
        for src in src_paths:
            if not os.path.isfile(src):
                continue
            base = os.path.basename(src)
            target = os.path.join(dest, base)
            stem, ext = os.path.splitext(base)
            counter = 2
            while os.path.exists(target):
                target = os.path.join(dest, f"{stem}-{counter}{ext}")
                counter += 1
            try:
                shutil.copy2(src, target)
                added += 1
            except OSError:
                pass
        return added

    # Notes are never physically erased — they're moved here, inside the repo folder, so a mistaken
    # delete is always recoverable. list_notes() only reads root-level .md files, so this subfolder's
    # contents never show on the board.
    TRASH_DIRNAME = "Deleted"

    def delete(self, note: Note) -> bool:
        """Soft-delete: move the note into the repo's ``Deleted`` subfolder (created on demand),
        de-duplicating the name on collision. Returns True on success."""
        try:
            trash = os.path.join(os.path.dirname(note.path), self.TRASH_DIRNAME)
            os.makedirs(trash, exist_ok=True)
            base = os.path.basename(note.path)
            target = os.path.join(trash, base)
            stem, ext = os.path.splitext(base)
            counter = 2
            while os.path.exists(target):
                target = os.path.join(trash, f"{stem}-{counter}{ext}")
                counter += 1
            os.rename(note.path, target)
            self._relocate_companion(note.path, target)  # the files go to Deleted/ with the note
            return True
        except OSError:
            return False

    # Absorbed notes are moved here (a sibling of Deleted) rather than erased, so a merge stays
    # recoverable. Like Deleted, list_notes() never reads this subfolder, so they drop off the board.
    MERGED_DIRNAME = "merged"

    def merge(self, survivor: Note, others: list[Note]) -> str | None:
        """Fold every note in ``others`` into ``survivor``: append each one's body under the
        survivor's, separated by a ``---`` line carrying the source filename (which preserves the
        tags/timestamp the absorbed note is about to lose), then move the absorbed files into the
        ``merged`` subfolder. The survivor keeps its filename/metadata and original mtime (so it
        stays put in the list). Returns the survivor's path, or None on failure."""
        try:
            parts = [survivor.content.rstrip()]
            for note in others:
                parts.append(f"---\n{os.path.basename(note.path)}\n{note.content.rstrip()}")
            with open(survivor.path, "w", encoding="utf-8") as f:
                f.write("\n\n".join(parts) + "\n")
            os.utime(survivor.path, (survivor.mtime, survivor.mtime))  # keep its list position

            # Fold each absorbed note's files into the survivor's companion folder, so a merged
            # record carries all the attachments (the emptied .md then moves to merged/ on its own).
            for note in others:
                self._relocate_companion(note.path, survivor.path)

            merged_dir = os.path.join(os.path.dirname(survivor.path), self.MERGED_DIRNAME)
            os.makedirs(merged_dir, exist_ok=True)
            for note in others:
                base = os.path.basename(note.path)
                target = os.path.join(merged_dir, base)
                stem, ext = os.path.splitext(base)
                counter = 2
                while os.path.exists(target):
                    target = os.path.join(merged_dir, f"{stem}-{counter}{ext}")
                    counter += 1
                os.rename(note.path, target)
            return survivor.path
        except OSError:
            return None
