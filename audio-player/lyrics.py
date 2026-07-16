"""Sidecar ``.ai.txt`` transcriptions ("lyrics") — discovery + reading, no GUI.

AI transcription files sit next to the media and share its base name, with the language in the
middle (same convention as the ``.srt`` subtitles, different suffix)::

    260701_0624.mp3
    260701_0624.ar.ai.txt   -> language "ar"
    260701_0624.en.ai.txt   -> language "en"
    260701_0624.ai.txt      -> language "txt"  (no language tag)

Unlike subtitles they carry NO timestamps, so the player renders the whole text in a scrollable
side panel (like lyrics) instead of syncing cues to the playhead. Pure Python; the UI calls
``find``, ``read`` and ``delete``.
"""

from __future__ import annotations

import os

_SUFFIX = ".ai.txt"


def find(media_path: str) -> list[tuple[str, str]]:
    """``(language, path)`` for every ``.ai.txt`` sidecar beside ``media_path`` that shares its
    base name, sorted by language. ``x.ar.ai.txt`` → ``("ar", …)``; a plain ``x.ai.txt`` →
    ``("txt", …)``."""
    folder = os.path.dirname(media_path)
    stem = os.path.splitext(os.path.basename(media_path))[0]
    out: list[tuple[str, str]] = []
    try:
        names = os.listdir(folder)
    except OSError:
        return out
    for name in names:
        if not name.lower().endswith(_SUFFIX):
            continue
        base = name[:-len(_SUFFIX)]  # drop ".ai.txt"
        if base == stem:
            lang = "txt"
        elif base.startswith(stem + ".") and len(base) > len(stem) + 1:
            lang = base[len(stem) + 1:]
        else:
            continue
        out.append((lang, os.path.join(folder, name)))
    return sorted(out, key=lambda x: x[0].lower())


def read(path: str) -> str:
    """The transcription text (tolerant of BOM/CRLF); empty string when unreadable."""
    try:
        with open(path, "r", encoding="utf-8-sig", errors="replace") as f:
            return f.read().replace("\r\n", "\n").replace("\r", "\n").strip()
    except OSError:
        return ""


def delete(path: str) -> bool:
    """Remove a transcription file; True on success."""
    try:
        os.remove(path)
        return True
    except OSError:
        return False


if __name__ == "__main__":
    import tempfile
    d = tempfile.mkdtemp()
    media = os.path.join(d, "260701_0624.mp3")
    open(media, "w").close()
    open(os.path.join(d, "260701_0624.ar.ai.txt"), "w").write("مرحبا\n")
    open(os.path.join(d, "260701_0624.en.ai.txt"), "w").write("hello\r\nworld\n")
    open(os.path.join(d, "unrelated.en.ai.txt"), "w").write("x")
    found = find(media)
    assert [l for l, _ in found] == ["ar", "en"], found
    assert read(found[1][1]) == "hello\nworld"
    assert delete(found[0][1]) and [l for l, _ in find(media)] == ["en"]
    print("lyrics.py self-test OK")
