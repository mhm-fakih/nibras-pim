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


def fmt_count(n: int) -> str:
    """A count for display, grouped in threes: ``19123`` → ``"19,123"``.

    Always a comma, never the locale's separator. The app's numbers sit inches from tag codes and
    file-name timestamps that are locale-independent by design, and under a locale that groups with
    a full stop "19.123" reads as a decimal — a folder of nineteen thousand notes would look like
    nineteen. Every user-visible count goes through here so they can't drift apart.
    """
    return f"{n:,}"


def _cap(text: str, max_chars: int, tail_chars: int = 0) -> str:
    """Shorten ``text`` to fit a card. With ``tail_chars`` > 0 the last ``tail_chars`` characters are
    kept too, joined to the head by an ellipsis, so the start *and* the end stay visible — a long
    note's conclusion is often the part worth seeing. Shared by ``Note.title`` and
    ``Note.card_preview`` so the two settings mean exactly the same thing in both."""
    if tail_chars > 0:
        if len(text) > max_chars + tail_chars + 1:
            return text[:max_chars].rstrip() + " … " + text[-tail_chars:].lstrip()
        return text
    if len(text) > max_chars:
        return text[:max_chars].rstrip() + "…"
    return text


# ---------------------------------------------------------------- clause highlighting
#
# A note marks a highlighted clause the way Obsidian and GitHub-flavoured markdown do, ``==like
# this==``, so the marking is just text: it survives Syncthing, reads fine in any editor, and means
# the same thing on the phone. The desktop's reading mode hides the markers and paints the run
# instead — see KnotesWindow._editor_render.
HIGHLIGHT_MARK = "=="

# Companion file holding time marks — one timestamp per line, e.g. "[0:43]" — written beside the
# media it belongs to (``talk.mp3`` → ``talk.tmk``). See Note.time_mark_count.
TIME_MARK_EXT = ".tmk"

# What ends a clause. Sentence punctuation plus its Arabic equivalents (، ؛ ؟ — different code
# points from the Latin ones, and a note is as likely to be in Arabic as in English) and the line
# break, which ends a clause whatever precedes it. Dashes are deliberately absent: "- call Sara" is
# a bullet, and splitting there would make the marker its own clause.
CLAUSE_BREAKS = frozenset(".,;:!?…\n" + "،؛؟")


def split_highlights(text: str) -> tuple[str, list[bool]]:
    """Split ``text`` into its plain reading form and a per-character "is highlighted" flag.

    An unpaired ``==`` is left as literal text — a note is allowed to contain one, and eating it
    would silently change what the file says. This is the exact inverse of :func:`apply_highlights`
    for any text that round-trips through it.
    """
    plain: list[str] = []
    flags: list[bool] = []
    i, n = 0, len(text)
    while i < n:
        if text.startswith(HIGHLIGHT_MARK, i):
            close = text.find(HIGHLIGHT_MARK, i + len(HIGHLIGHT_MARK) + 1)  # +1: never empty
            if close != -1:
                body = text[i + len(HIGHLIGHT_MARK):close]
                plain.append(body)
                flags.extend([True] * len(body))
                i = close + len(HIGHLIGHT_MARK)
                continue
        plain.append(text[i])
        flags.append(False)
        i += 1
    return "".join(plain), flags


def apply_highlights(plain: str, flags: list[bool]) -> str:
    """Put the ``==`` markers back around each highlighted run.

    Whitespace at the edges of a run is pushed OUTSIDE the markers: ``== text ==`` isn't recognised
    as a highlight by the markdown renderers this notation is borrowed from, so a clause that picked
    up a trailing space would come back as literal text next time it was read.
    """
    out: list[str] = []
    i, n = 0, len(plain)
    while i < n:
        if not flags[i]:
            out.append(plain[i])
            i += 1
            continue
        run_end = i
        while run_end < n and flags[run_end]:
            run_end += 1
        start, end = i, run_end
        while start < end and plain[start].isspace():
            out.append(plain[start])
            start += 1
        tail = ""
        while end > start and plain[end - 1].isspace():
            end -= 1
            tail = plain[end] + tail
        if start < end:
            out.append(HIGHLIGHT_MARK + plain[start:end] + HIGHLIGHT_MARK)
        out.append(tail)
        i = run_end
    return "".join(out)


# A stretch of note text split into ``(text, highlighted)`` runs — what a view needs to render a
# note: the words, and which of them are marked.
Runs = list[tuple[str, bool]]


def _group_runs(chars: list[tuple[str, bool]]) -> Runs:
    """Collapse per-character ``(char, highlighted)`` pairs into runs of the same flag."""
    runs: Runs = []
    for char, hot in chars:
        if runs and runs[-1][1] == hot:
            runs[-1] = (runs[-1][0] + char, hot)
        else:
            runs.append((char, hot))
    return runs


def clause_bounds(plain: str, offset: int) -> tuple[int, int]:
    """The clause containing ``offset``, as a ``[start, end)`` range of ``plain``.

    Empty when there's no clause there (a click on the blank line between two paragraphs) — the
    caller treats that as "nothing to highlight" rather than guessing at a neighbour.
    """
    n = len(plain)
    if n == 0:
        return 0, 0
    offset = min(max(offset, 0), n)
    # A click lands on the character to the RIGHT of the caret, so landing on a break means the user
    # aimed at the clause that just ended — walk back to it. Several breaks can sit together and all
    # have to be crossed: clicking in the empty space past the end of a line (the commonest way to
    # aim at a short line) puts the caret on that line's "\n", right after the sentence's ".".
    #
    # Crossing a SECOND newline is where it stops, and that's what keeps a click on a blank line
    # from reaching back into the paragraph above it and marking a clause the user wasn't pointing
    # at. Empty is the honest answer there.
    if offset >= n:
        offset = n - 1
    newlines = 0
    while offset >= 0 and plain[offset] in CLAUSE_BREAKS:
        if plain[offset] == "\n":
            newlines += 1
            if newlines > 1:
                return 0, 0
        offset -= 1
    if offset < 0:
        return 0, 0
    start = offset
    while start > 0 and plain[start - 1] not in CLAUSE_BREAKS:
        start -= 1
    end = offset
    while end < n and plain[end] not in CLAUSE_BREAKS:
        end += 1
    while start < end and plain[start].isspace():   # hug the words, not the gap before them
        start += 1
    while end > start and plain[end - 1].isspace():
        end -= 1
    return start, end


def is_rtl(text: str) -> bool:
    return bool(_RTL.search(text))


def is_note_file(name: str) -> bool:
    """True only for `.md` files whose name matches the canonical note format."""
    return bool(_NOTE_NAME.match(name))


# Extensions whose CONTENT is prose we can adopt as a note body. Anything else — a .drawio, a PDF,
# an image, a spreadsheet — is an artifact, not text: promoting it attaches the file to a new note
# instead of reinterpreting its bytes as the body (see NoteRepository.promote).
#
# Extension, deliberately, and not "does it decode as UTF-8": a .drawio is valid XML and an .svg or
# a .csv is plain ASCII, so a decode test waves all of them through and lands their markup in a
# note body. The question isn't whether the bytes are text, it's whether they're *the note*.
PLAIN_TEXT_EXTS = (".md", ".txt")


def is_plain_text(name: str) -> bool:
    """Whether a file's content can stand as a note body — judged by extension only."""
    return os.path.splitext(name)[1].lower() in PLAIN_TEXT_EXTS


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


def mtime_token(path: str, fmt: str) -> str:
    """A file/folder's own last-modified time formatted the same way as :func:`now_token` — used
    when promoting a non-conforming item, so it lands at its real chronological position instead
    of jumping to "now"."""
    try:
        return datetime.fromtimestamp(os.path.getmtime(path)).strftime(fmt)
    except (ValueError, TypeError, OSError):
        return datetime.fromtimestamp(os.path.getmtime(path)).strftime("%Y_%m_%d-%H%M%S")


# The "-2", "-3", … build_name appends when a name is already taken (see _NOTE_NAME's optional
# group). Only ever stripped as a fallback — see parse_created.
_DEDUP_SUFFIX = re.compile(r"-\d+$")


def parse_created(timestamp: str, fmt: str, fallback_epoch: float) -> float:
    """A note's creation time (its filename timestamp token, parsed as ``fmt``) as a Unix epoch —
    for sorting by "date created" independent of the file's own mtime (which a metadata-only edit
    still bumps, since the note file gets rewritten/renamed). Falls back to ``fallback_epoch``
    (the note's mtime) when the token doesn't match ``fmt`` — a foreign note, or one written before
    the format was last changed.

    A ``-N`` de-dup suffix (build_name adds one when two notes would take the same name) is retried
    without the suffix, or every de-duplicated note would fall back to its mtime and report the day
    it was last *written* as the day it was created. The retry happens only after the whole token
    has already failed, so a ``fmt`` whose own final field is numeric — ``%Y-%m-%d-%H-%M-%S`` — is
    never mistaken for a suffixed one.
    """
    for token in (timestamp, _DEDUP_SUFFIX.sub("", timestamp or "")):
        try:
            return datetime.strptime(token, fmt).timestamp()
        except (ValueError, TypeError):
            continue
    return fallback_epoch


def is_companion_dir(dir_path: str) -> bool:
    """True if `dir_path` is *already* a real companion folder — i.e. its name ends in
    :data:`COMPANION_SUFFIX` and a matching ``<stem>.md`` note sits next to it. There's no reverse
    index from folder to note (companion_dir() only goes note → folder), so this re-derives it."""
    name = os.path.basename(dir_path.rstrip(os.sep + "/"))
    if not name.endswith(COMPANION_SUFFIX):
        return False
    sibling = os.path.join(os.path.dirname(dir_path), name[: -len(COMPANION_SUFFIX)] + ".md")
    return os.path.isfile(sibling)


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


def _with_trailing_newline(text: str) -> str:
    """A file missing its final newline shows as a spurious change on every future line ('No
    newline at end of file') in git diff and similar tools — always write one."""
    return text if text.endswith("\n") else text + "\n"
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
    # Memoised time-mark total (see time_mark_count). Out of equality and repr: it's a cache of
    # what's on disk, not part of what the note IS — two notes don't differ because one has been
    # counted and the other hasn't.
    _time_marks: int | None = field(default=None, repr=False, compare=False)

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
        plain, _flags = split_highlights(self.content)
        return _cap(" ".join(plain.split()), max_chars, tail_chars) or "(empty)"

    def card_preview(self, max_chars: int, tail_chars: int = 0) -> tuple[Runs, Runs]:
        """The body of a board card, as ``(first line, the rest)`` — the caller renders the first
        line bold. Each is a list of ``(text, highlighted)`` runs, so a clause marked ``==like
        this==`` is painted on the card as well as in the reading pane, and the markers themselves
        are never shown: the app writes them, so it owes every view a clean rendering of them.

        A knotes note's line 1 *is* its title in practice: that's how they're written, it's what a
        settled file names itself after, and it's the line the phone side shows. Flattening the
        whole note into one run of text (what ``title`` does) buries it — so the breaks stay and the
        card is read like the note.

        Blank lines are dropped outright and each line is stripped. A card shows about four lines,
        and the usual markdown shape "title, blank, body" would spend one of them on nothing — the card
        is a preview, not the document. Stripping also keeps a stray leading space out of the bold
        title, where it reads as a rendering bug. The length cap is the same as ``title``'s and
        applies to the whole text, so the two settings still mean what they say in both.

        Everything below works on a list of ``(character, highlighted)`` pairs rather than on a
        string: tidying and capping then move the flags with the text automatically, instead of the
        two being kept in step by hand through every slice and strip.
        """
        plain, flags = split_highlights(self.content)
        chars: list[tuple[str, bool]] = []
        at = 0
        for line in plain.split("\n"):
            pairs = list(zip(line, flags[at:at + len(line)]))
            at += len(line) + 1     # the "\n" that split() consumed
            while pairs and pairs[0][0].isspace():
                pairs.pop(0)
            while pairs and pairs[-1][0].isspace():
                pairs.pop()
            if not pairs:
                continue
            if chars:
                chars.append(("\n", False))
            chars.extend(pairs)
        if not chars:
            return [("(empty)", False)], []

        def _trim_end(seq: list[tuple[str, bool]]) -> None:
            while seq and seq[-1][0].isspace():
                seq.pop()

        if tail_chars > 0:
            if len(chars) > max_chars + tail_chars + 1:
                head, tail = chars[:max_chars], chars[-tail_chars:]
                _trim_end(head)
                while tail and tail[0][0].isspace():
                    tail.pop(0)
                chars = head + [(c, False) for c in " … "] + tail
        elif len(chars) > max_chars:
            chars = chars[:max_chars]
            _trim_end(chars)
            chars.append(("…", False))

        split = next((i for i, (c, _f) in enumerate(chars) if c == "\n"), len(chars))
        return _group_runs(chars[:split]), _group_runs(chars[split + 1:])

    def word_count(self) -> int:
        """Number of whitespace-separated words in the note body."""
        return len(self.content.split())

    def highlight_count(self) -> int:
        """How many ``==marked==`` clauses the note has — runs, not characters, so one long
        highlighted sentence counts once."""
        _plain, flags = split_highlights(self.content)
        return sum(1 for i, on in enumerate(flags) if on and (i == 0 or not flags[i - 1]))

    def time_mark_count(self) -> int:
        """How many time marks are recorded against this note: one per non-blank line across every
        ``.tmk`` file in its companion folder.

        A ``.tmk`` sits beside the media it belongs to (``talk.mp3`` → ``talk.tmk``) and holds one
        timestamp per line, ``[0:43]`` — a position marked while listening. Counted, never parsed:
        the count is the whole signal on a card, and being incurious about the line format means a
        hand-edited or differently-stamped file still counts correctly.

        Read lazily and memoised on the instance. The files are tiny, but a board of thousands of
        notes would otherwise open a companion folder for every one of them on every reload — so
        the ``.tmk`` check runs against ``file_names``, which is already in memory, and the disk is
        touched only for the notes that actually have one.
        """
        if self._time_marks is None:
            folder = companion_dir(self.path)
            total = 0
            for name in self.file_names:
                if not name.lower().endswith(TIME_MARK_EXT):
                    continue
                try:
                    with open(os.path.join(folder, name), "r", encoding="utf-8",
                              errors="replace") as f:
                        total += sum(1 for line in f if line.strip())
                except OSError:
                    continue   # deleted or unreadable mid-reload — just don't count it
            self._time_marks = total
        return self._time_marks

    def time_text(self) -> str:
        return time.strftime("%Y-%m-%d %H:%M", time.localtime(self.mtime))


class NoteRepository:
    """Reads and writes notes as plain-text files in one directory."""

    # The app's own housekeeping folders (soft-deletes → .deleted, merges → .merged) plus ones that
    # arrive from the phone / import side (.imported). The app always creates them hidden + lowercase
    # (a leading dot), so they never show on the board. AUX_DIR_STEMS also lets is_aux_dir() catch
    # legacy or phone-synced variants in ANY case, dotted or not — so a stray ``Imported``, ``Trash``
    # or ``Deleted`` can't leak in as a "needs metadata" card.
    AUX_DIR_STEMS = frozenset({"deleted", "merged", "imported", "trash", "trashed"})

    @classmethod
    def is_aux_dir(cls, name: str) -> bool:
        """True for any auxiliary/housekeeping folder — its bare stem (leading dots stripped, case
        folded) is one of AUX_DIR_STEMS. Catches ``.deleted``, ``Deleted``, ``Trash``, ``.imported`` …"""
        return name.lstrip(".").casefold() in cls.AUX_DIR_STEMS

    # Sidecar files that live among the notes but aren't notes: an index/database the sync or import
    # side keeps next to them. Not dotted, so nothing else would hide them — without this each one
    # lands on the board as a "needs metadata" card dated today, asking to be settled, which is
    # exactly what it must never be (promoting it would rename the live database).
    # ``claude.md`` is here for the same reason as the database: it's an instruction file for a
    # tool that reads the folder, not a note, and it isn't dotted, so nothing else would hide it.
    IGNORED_FILE_STEMS = frozenset({"notes.sqlite", "claude.md"})

    # What SQLite writes alongside an open database. They appear and vanish as the DB is used, so
    # ignoring only the .sqlite itself would still let two mystery cards blink onto the board.
    _DB_SIDECAR_SUFFIXES = ("-wal", "-shm", "-journal")

    @classmethod
    def is_ignored_file(cls, name: str) -> bool:
        """True for a sidecar file to hide as if it were dotted — no card, nothing to promote.
        Case-folded, so ``Notes.sqlite`` from another platform is caught too."""
        lower = name.casefold()
        return any(lower == stem or lower.startswith(stem) and lower[len(stem):] in cls._DB_SIDECAR_SUFFIXES
                   for stem in cls.IGNORED_FILE_STEMS)

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

    def list_unrecognized(self) -> list[Note]:
        """Files and folders sitting in the directory that aren't a canonical note, a real
        companion folder, a dotfile (``.knotes.json``, Syncthing's ``.stfolder``/``.stversions``,
        …), an ignored sidecar (``notes.sqlite`` and friends, see is_ignored_file), or one of the
        app's own special folders (see is_aux_dir) — shown to the user as "needs metadata"
        placeholders (type/priority/category all ``None``) rather than silently ignored."""
        d = self._dir()
        items = []
        for name in os.listdir(d):
            if name.startswith(".") or self.is_aux_dir(name) or self.is_ignored_file(name):
                continue
            path = os.path.join(d, name)
            if os.path.isfile(path):
                if not is_note_file(name):
                    items.append(self._to_placeholder(path))
            elif os.path.isdir(path):
                if not is_companion_dir(path):
                    items.append(self._to_placeholder(path))
        items.sort(key=lambda n: n.mtime)
        return items

    def _to_placeholder(self, path: str) -> Note:
        """A non-conforming file/folder, wrapped as a `Note` with blank tags so it slots into the
        existing card rendering, kanban/list grouping ("—"/Uncategorized bucket), and navigation
        with no further changes there. `os.path.isdir(note.path)` distinguishes the two cases when
        it's later promoted (see `NoteRepository.promote`)."""
        if os.path.isdir(path):
            content = ""
            file_names = sorted(os.listdir(path))
        elif is_plain_text(os.path.basename(path)):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
            except (OSError, UnicodeDecodeError):
                content = ""
            file_names = []
        else:
            # Not a note body (see is_plain_text), so don't pull it in: the card shows the file's
            # NAME for a placeholder anyway, and reading e.g. a 20 MB .drawio only to scan it for
            # #hashtags and word-count it would be work spent on markup nobody asked to see.
            content = ""
            file_names = []
        return Note(
            path=path,
            timestamp=mtime_token(path, "%Y_%m_%d-%H%M%S"),
            type=None,
            priority=None,
            category=None,
            content=content,
            mtime=os.path.getmtime(path),
            is_rtl=is_rtl(content),
            file_names=file_names,
        )

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
            f.write(_with_trailing_newline(text))
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
            f.write(_with_trailing_newline(text))
        return target

    def _free_path(self, parent: str, timestamp: str, type_: str | None, priority: int,
                   category: str | None) -> str:
        """A canonical note path in [parent] that nothing occupies yet, de-duplicated with a ``-N``
        suffix on the timestamp."""
        target = os.path.join(parent, build_name(timestamp, type_, priority, category))
        counter = 2
        while os.path.exists(target):
            target = os.path.join(parent, build_name(f"{timestamp}-{counter}", type_, priority, category))
            counter += 1
        return target

    def promote(self, note: Note, type_: str | None, priority: int, category: str | None,
                fmt: str) -> str:
        """Turn a `list_unrecognized()` placeholder into a real note, once the user has supplied
        at least one tag. Three cases:

        - **Folder**: a brand-new note is created (via `save_new`, timestamped "now" — there's no
          prior content to anchor it to), with the folder's original name as its first line; the
          folder itself is then renamed to become that new note's companion folder.
        - **Plain-text file** (`.md`/`.txt`, see :func:`is_plain_text`): renamed into the canonical
          format, timestamped from the file's own mtime (its real chronological position, not
          "now"); its original filename becomes the first line of the body so that info isn't lost
          once the name turns opaque.
        - **Any other file**: its bytes are an artifact, not a note body, so nothing is
          reinterpreted — a NEW note is written whose only line is the original file name, and the
          file is moved into that note's companion folder as an **attachment**, keeping its own
          name. A `diagram.drawio` becomes a note reading "diagram.drawio" with the .drawio
          attached, not a note whose body is a screenful of XML.

        Both file cases take their timestamp from the file's mtime, so a promoted item lands at its
        real chronological position rather than jumping to the head of the board.
        """
        if os.path.isdir(note.path):
            original_name = os.path.basename(note.path.rstrip(os.sep + "/"))
            new_path = self.save_new(original_name + "\n", type_, priority, category, fmt)
            target_dir = companion_dir(new_path)
            os.rename(note.path, target_dir)  # target_dir can't already exist: new_path is fresh
            return new_path

        original_name = os.path.basename(note.path)
        target = self._free_path(os.path.dirname(note.path), mtime_token(note.path, fmt),
                                 type_, priority, category)

        if not is_plain_text(original_name):
            # The note is written FIRST: its path is what names the companion folder the file moves
            # into. Both the folder and the name inside it are fresh (target is), so neither move
            # can collide with something already there.
            with open(target, "w", encoding="utf-8") as f:
                f.write(_with_trailing_newline(original_name))
            folder = companion_dir(target)
            os.makedirs(folder, exist_ok=True)
            os.rename(note.path, os.path.join(folder, original_name))
            return target

        new_body = original_name + "\n" + note.content
        os.rename(note.path, target)
        with open(target, "w", encoding="utf-8") as f:
            f.write(_with_trailing_newline(new_body))
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
    TRASH_DIRNAME = ".deleted"

    def delete(self, note: Note) -> bool:
        """Soft-delete: move the note into the repo's ``.deleted`` subfolder (created on demand),
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

    MERGED_DIRNAME = ".merged"
    # Notes brought in from the phone / an import run land here (hidden + lowercase, like the rest);
    # the desktop app doesn't create it, but names it so the constant is the single source of truth.
    IMPORTED_DIRNAME = ".imported"

    # Absorbed notes are moved here (a sibling of .deleted) rather than erased, so a merge stays
    # recoverable. Like .deleted, list_notes() never reads this subfolder, so they drop off the board.

    def merge(self, survivor: Note, others: list[Note]) -> str | None:
        """Fold every note in ``others`` into ``survivor``: append each one's body under the
        survivor's, separated by a ``---`` line carrying the source filename (which preserves the
        tags/timestamp the absorbed note is about to lose), then move the absorbed files into the
        ``.merged`` subfolder. The survivor keeps its filename/metadata and original mtime (so it
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
