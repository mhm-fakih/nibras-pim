"""Sidecar ``.ai.txt`` transcriptions ("lyrics") — discovery + reading, no GUI.

AI transcription files sit next to the media and share its base name, with the language in the
middle (same convention as the ``.srt`` subtitles, different suffix)::

    260701_0624.mp3
    260701_0624.ar.ai.txt   -> language "ar"
    260701_0624.en.ai.txt   -> language "en"
    260701_0624.ai.txt      -> language "txt"  (no language tag)

Unlike subtitles they carry NO timestamps, so the player renders the whole text in a scrollable
side panel (like lyrics) instead of syncing cues to the playhead. The panel is editable, so the
text can be corrected in place — ``write`` puts it back — and ``path_for`` names a brand-new one.
Pure Python; the UI calls ``find``, ``read``, ``write`` and ``path_for`` (removal is the player's
job — it moves the file to the Trash rather than unlinking it).

Everything is UTF-8, so the encoding says nothing about the language: a new file's ``ar``/``en`` tag
comes from the button the user pressed, and nothing renames it afterwards.
"""

from __future__ import annotations

import os

_SUFFIX = ".ai.txt"

# Languages offered when starting a transcript from scratch. Files in any other language are still
# found and shown — this is only the set the player can create.
NEW_LANGS = ("ar", "en")


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


def write(path: str, text: str) -> bool:
    """Save edited transcription text. Written to a temp file in the same directory and renamed
    over the original, so an interrupted save can't leave a half-written transcript. True on
    success."""
    text = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    tmp = path + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            f.write(text + "\n" if text else "")
        os.replace(tmp, path)
        return True
    except OSError:
        try:
            os.remove(tmp)
        except OSError:
            pass
        return False


def path_for(media_path: str, lang: str) -> str:
    """Where a new transcription for ``media_path`` in ``lang`` belongs:
    ``260701_0624.mp3`` + ``"ar"`` → ``260701_0624.ar.ai.txt`` beside it."""
    stem = os.path.splitext(os.path.basename(media_path))[0]
    return os.path.join(os.path.dirname(media_path), f"{stem}.{lang}{_SUFFIX}")


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
    assert write(found[1][1], "hello\r\nthere\n\n") and read(found[1][1]) == "hello\nthere"
    assert not os.path.exists(found[1][1] + ".tmp")   # the temp file never survives a good save

    assert os.path.basename(path_for(media, "ar")) == "260701_0624.ar.ai.txt"
    assert path_for(media, "en") == found[1][1]        # names the file find() already reports
    os.remove(found[0][1])
    assert [l for l, _ in find(media)] == ["en"]
    print("lyrics.py self-test OK")
