"""Sidecar ``.srt`` subtitles — discovery + parsing, no GUI.

Subtitle files sit next to the media and share its base name, with the language in the middle
(the same convention the phone uses)::

    lecture.mp4
    lecture.en.srt      -> language "en"
    lecture.ar.srt      -> language "ar"
    lecture.srt         -> language "sub"  (no language tag)

The player scans these when a file opens, offers the languages it found as chips, and — once one is
picked — shows the active cue synced to the playhead (under the video, or over the seek bar for
audio-only files). Pure Python; the UI just calls ``find``, ``parse`` and ``cue_at``.
"""

from __future__ import annotations

import bisect
import os
import re
from dataclasses import dataclass


@dataclass
class Cue:
    start_ms: int
    end_ms: int
    text: str


# HH:MM:SS,mmm  (also tolerates '.' as the millisecond separator and 1-2 digit hours).
_TIME = re.compile(r"(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})")
# Inline formatting tags (<i>, <b>, <font …>) — stripped so the label shows clean text.
_TAG = re.compile(r"<[^>]+>")


def find(media_path: str) -> list[tuple[str, str]]:
    """``(language, path)`` for every ``.srt`` sidecar beside ``media_path`` that shares its base
    name, sorted by language. ``lecture.en.srt`` → ``("en", …)``; a plain ``lecture.srt`` →
    ``("sub", …)``."""
    folder = os.path.dirname(media_path)
    stem = os.path.splitext(os.path.basename(media_path))[0]
    out: list[tuple[str, str]] = []
    try:
        names = os.listdir(folder)
    except OSError:
        return out
    for name in names:
        if not name.lower().endswith(".srt"):
            continue
        base = name[:-4]  # drop ".srt"
        if base == stem:
            lang = "sub"
        elif base.startswith(stem + ".") and len(base) > len(stem) + 1:
            lang = base[len(stem) + 1:]
        else:
            continue
        out.append((lang, os.path.join(folder, name)))
    return sorted(out, key=lambda x: x[0].lower())


def _ms(h: str, m: str, s: str, frac: str) -> int:
    return ((int(h) * 60 + int(m)) * 60 + int(s)) * 1000 + int(frac.ljust(3, "0")[:3])


def parse(path: str) -> list[Cue]:
    """Parse an ``.srt`` file into time-sorted cues. Tolerant of BOM, CRLF, blank lines and inline
    tags; a malformed block is skipped rather than aborting the parse."""
    try:
        with open(path, "r", encoding="utf-8-sig", errors="replace") as f:
            content = f.read()
    except OSError:
        return []
    content = content.replace("\r\n", "\n").replace("\r", "\n")
    cues: list[Cue] = []
    for block in re.split(r"\n\s*\n", content):
        lines = block.split("\n")
        timing = next((i for i, ln in enumerate(lines) if "-->" in ln), None)
        if timing is None:
            continue
        stamps = _TIME.findall(lines[timing])
        if len(stamps) < 2:
            continue
        start, end = _ms(*stamps[0]), _ms(*stamps[1])
        text = "\n".join(ln for ln in lines[timing + 1:] if ln.strip())
        text = _TAG.sub("", text).strip()
        if text:
            cues.append(Cue(start, end, text))
    cues.sort(key=lambda c: c.start_ms)
    return cues


def cue_at(cues: list[Cue], position_ms: int) -> str | None:
    """The subtitle text active at ``position_ms``, or ``None``. Cues are start-sorted, so a binary
    search finds the candidate; it shows only if the position is within its window."""
    if not cues:
        return None
    i = bisect.bisect_right([c.start_ms for c in cues], position_ms) - 1
    if 0 <= i < len(cues) and cues[i].start_ms <= position_ms <= cues[i].end_ms:
        return cues[i].text
    return None


if __name__ == "__main__":
    sample = "1\n00:00:01,000 --> 00:00:03,500\n<i>Hello</i> world\n\n2\n00:00:04,000 --> 00:00:05,000\nBye\n"
    import tempfile
    p = os.path.join(tempfile.gettempdir(), "_subtest.srt")
    open(p, "w").write(sample)
    cues = parse(p)
    assert [(c.start_ms, c.end_ms, c.text) for c in cues] == [
        (1000, 3500, "Hello world"), (4000, 5000, "Bye")], cues
    assert cue_at(cues, 2000) == "Hello world"
    assert cue_at(cues, 3600) is None
    assert cue_at(cues, 4500) == "Bye"
    os.remove(p)
    print("subtitles.py self-test OK")


#: Long enough for a spoken sentence, short enough to stay on one row of the bookmark list.
LABEL_MAX = 90

_TAG_RE = re.compile(r"<[^>]*>")
_SPACE_RE = re.compile(r"\s+")


def label_at(cues: list[Cue], position_ms: int) -> str:
    """The cue at ``position_ms`` as a one-line bookmark label, or ``""`` when there is none.

    A bare timestamp says nothing when you come back to a list of bookmarks; the sentence is the
    only thing that records why you marked it, and the subtitle file has already done the typing.

    Cues wrap across two or three lines in the file and carry the odd tag, while a bookmark list
    shows one row — hence the flattening and the clip. Clipped on a word boundary, because half a
    word reads as corruption rather than as an abbreviation.
    """
    raw = cue_at(cues, position_ms)
    if not raw:
        return ""
    flat = _SPACE_RE.sub(" ", _TAG_RE.sub(" ", raw)).strip()
    if len(flat) <= LABEL_MAX:
        return flat
    cut = flat[:LABEL_MAX]
    last_space = cut.rfind(" ")
    return (cut[:last_space] if last_space > LABEL_MAX // 2 else cut).rstrip() + "\u2026"
