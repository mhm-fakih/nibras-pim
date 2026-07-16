"""The ``.tmk`` bookmark sidecar — the interop contract shared with the phone.

A media file's bookmarks live in a plain-text sidecar next to it, sharing the media's base name
with the extension swapped to ``.tmk`` (the same "sidecar beside the media" convention the phone's
``.lang.srt`` subtitles use)::

    Dostoevsky … [v20GwJ0JqY8].mp3   ->   Dostoevsky … [v20GwJ0JqY8].tmk

The file is one bookmark per line: a bracketed timestamp, optionally followed by a label::

    [1:09]
    [12:04] p2 key point on refraction
    [1:03:20] auto

Timestamps are human-readable ``M:SS`` (or ``H:MM:SS`` past an hour; a bare ``SS`` or a trailing
``.mmm`` fraction are also accepted on read). This mirrors the phone's ``Bookmark(positionMs,
text)``: the bracket carries ``positionMs``; everything after ``]`` is ``text`` (which may hold a
leading ``pN`` priority token or the literal ``auto`` for auto-bookmarks-on-pause — we preserve it
verbatim and never interpret it here). No dependencies; pure Python so both apps and a unit test can
share it.
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass


@dataclass(order=True)
class Bookmark:
    """One bookmark: a position in the media plus an optional free-text label.

    ``position_ms`` is first so a plain ``sorted()`` orders bookmarks chronologically. ``text`` is
    kept exactly as written (it may embed a ``pN`` priority token or be the sentinel ``"auto"``)."""

    position_ms: int
    text: str = ""


# The suffix a media file's sidecar carries. Kept as a module constant so both the reader and any
# writer (and the Android side, mirrored) agree on the one spelling.
TMK_EXT = ".tmk"

# A line: an optional leading timestamp in [brackets], then optional label text. We tolerate stray
# surrounding whitespace. Everything captured in `ts` is fed to _parse_timestamp.
_LINE_RE = re.compile(r"^\s*\[\s*(?P<ts>[^\]]*?)\s*\]\s*(?P<text>.*?)\s*$")


def sidecar_path(media_path: str) -> str:
    """The ``.tmk`` path for ``media_path`` — its base name with the extension replaced by
    ``.tmk`` (``a/b/song.mp3`` -> ``a/b/song.tmk``). An extension-less media path just gains the
    suffix."""
    root, _ext = os.path.splitext(media_path)
    return root + TMK_EXT


def _parse_timestamp(ts: str) -> int | None:
    """Parse ``H:MM:SS`` / ``M:SS`` / ``SS`` (each part optionally fractional, e.g. ``1:09.5``) to
    milliseconds. Returns ``None`` if it isn't a well-formed timestamp so a junk line is skipped
    rather than crashing the load."""
    ts = ts.strip()
    if not ts:
        return None
    parts = ts.split(":")
    if len(parts) > 3:
        return None
    total = 0.0
    for part in parts:
        part = part.strip()
        if not part:
            return None
        try:
            value = float(part)
        except ValueError:
            return None
        total = total * 60 + value
    return int(round(total * 1000))


def parse_timestamp(ts: str) -> int | None:
    """Public: parse a bracket-less timestamp (``12:04``, ``1:03:20``, ``90``) to milliseconds, or
    ``None`` if it isn't valid — used when editing a bookmark's time in the UI."""
    return _parse_timestamp(ts)


def parse(text: str) -> list[Bookmark]:
    """Parse the full text of a ``.tmk`` file into bookmarks, sorted by position. Blank lines and
    lines that don't start with a ``[timestamp]`` are ignored, so the format stays forgiving."""
    out: list[Bookmark] = []
    for line in text.splitlines():
        if not line.strip():
            continue
        m = _LINE_RE.match(line)
        if not m:
            continue
        ms = _parse_timestamp(m.group("ts"))
        if ms is None:
            continue
        out.append(Bookmark(ms, m.group("text").strip()))
    out.sort()
    return out


def format_timestamp(position_ms: int) -> str:
    """Render milliseconds as the sidecar's bracket body: ``H:MM:SS`` once past an hour, else
    ``M:SS`` (seconds and — past the hour — minutes are always two digits). Sub-second precision is
    dropped: bookmarks are line positions, not frames, and whole seconds keep the file tidy and
    matches what the sample phone file writes (``[1:09]``)."""
    total = max(0, position_ms) // 1000
    hours, rem = divmod(total, 3600)
    minutes, seconds = divmod(rem, 60)
    if hours:
        return f"{hours}:{minutes:02d}:{seconds:02d}"
    return f"{minutes}:{seconds:02d}"


def dump(bookmarks: list[Bookmark]) -> str:
    """Serialize bookmarks back to ``.tmk`` text (sorted, one per line, trailing newline). A label,
    when present, follows the bracket separated by a single space; an empty label writes the bare
    ``[timestamp]`` — round-tripping the phone's sample exactly."""
    lines = []
    for b in sorted(bookmarks):
        stamp = f"[{format_timestamp(b.position_ms)}]"
        lines.append(f"{stamp} {b.text}".rstrip() if b.text.strip() else stamp)
    return "\n".join(lines) + "\n" if lines else ""


def load(media_path: str) -> list[Bookmark]:
    """Load the bookmarks for a media file. A missing or unreadable sidecar yields an empty list —
    a media file simply has no bookmarks yet, never an error."""
    try:
        with open(sidecar_path(media_path), "r", encoding="utf-8") as f:
            return parse(f.read())
    except OSError:
        return []


def save(media_path: str, bookmarks: list[Bookmark]) -> None:
    """Write the bookmarks to the media file's sidecar. An empty list removes the sidecar (rather
    than leaving a zero-byte file) so a cleared media file looks untouched to the phone/Syncthing.
    Written atomically (temp file + replace) so a crash mid-write can't corrupt an existing file."""
    path = sidecar_path(media_path)
    if not bookmarks:
        try:
            os.remove(path)
        except OSError:
            pass
        return
    tmp = path + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(dump(bookmarks))
        os.replace(tmp, path)
    except OSError:
        try:
            os.remove(tmp)
        except OSError:
            pass


if __name__ == "__main__":
    # Quick self-check / round-trip demo. Run: python3 tmk.py
    sample = "[1:09]\n"
    bms = parse(sample)
    assert bms == [Bookmark(69000, "")], bms
    assert dump(bms) == sample, repr(dump(bms))
    mixed = "[0:05] p2 intro\n[1:03:20] auto\n[12:04]\n"
    bms = parse(mixed)
    assert [b.position_ms for b in bms] == [5000, 724000, 3800000], bms
    assert bms[2].text == "auto"  # 1:03:20 sorts after 12:04
    assert dump(bms) == "[0:05] p2 intro\n[12:04]\n[1:03:20] auto\n", repr(dump(bms))
    print("tmk.py self-test OK")
