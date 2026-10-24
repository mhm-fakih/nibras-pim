"""Library scanning + tagging for the folder browser.

Reads genre / artist / album / title / track-number / duration from the audio **and** video files
in a folder using GStreamer's ``Discoverer`` (same stack the player already needs — no extra
dependency for *reading*). The player's 3-pane column browser (Genres -> Artists -> Albums) and the
"untagged" page are both driven by the ``Track`` list this produces.

*Writing* tags (the "tag a whole folder at once" action) uses ``mutagen`` when present — it edits the
files in place across the common audio formats. Video/exotic formats it can't handle are reported
back as skipped rather than failing the batch.
"""

from __future__ import annotations

import os
import threading
from dataclasses import dataclass, field

import gi

gi.require_version("Gst", "1.0")
gi.require_version("GstPbutils", "1.0")
from gi.repository import Gst, GstPbutils, GLib  # noqa: E402

Gst.init(None)

# Media we scan — audio and video containers.
MEDIA_EXTS = {".mp3", ".m4a", ".m4b", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav", ".wma",
              ".mka", ".webm", ".mp4", ".m4v", ".mkv", ".mov", ".avi"}

UNKNOWN = "Unknown"  # display bucket for a missing genre/artist/album


@dataclass
class Track:
    path: str
    title: str = ""
    artist: str = ""
    album: str = ""
    genre: str = ""
    track_no: int = 0
    duration_ms: int = -1

    @property
    def has_tags(self) -> bool:
        """A track is 'tagged' if it carries any of the three browseable fields. Files with none land
        on the untagged page (and are the target of the bulk 'tag this folder' action)."""
        return bool(self.artist or self.album or self.genre)

    def display_title(self) -> str:
        return self.title or os.path.splitext(os.path.basename(self.path))[0]

    def g(self) -> str: return self.genre or UNKNOWN
    def a(self) -> str: return self.artist or UNKNOWN
    def al(self) -> str: return self.album or UNKNOWN


def _tag_str(tags, key: str) -> str:
    if tags is None:
        return ""
    ok, val = tags.get_string(key)
    return val.strip() if ok and val else ""


def _tag_uint(tags, key: str) -> int:
    if tags is None:
        return 0
    ok, val = tags.get_uint(key)
    return val if ok else 0


def list_media(folder: str, recursive: bool = True) -> list[str]:
    """Absolute paths of playable media under ``folder`` (recursively by default), sorted."""
    out: list[str] = []
    if recursive:
        for root, _dirs, names in os.walk(folder):
            for n in names:
                if os.path.splitext(n)[1].lower() in MEDIA_EXTS:
                    out.append(os.path.join(root, n))
    else:
        try:
            for n in os.listdir(folder):
                p = os.path.join(folder, n)
                if os.path.isfile(p) and os.path.splitext(n)[1].lower() in MEDIA_EXTS:
                    out.append(p)
        except OSError:
            pass
    return sorted(out, key=str.lower)


def discover(path: str, discoverer: "GstPbutils.Discoverer") -> Track | None:
    """Read one file's tags into a ``Track``. Returns ``None`` if the file isn't decodable media
    (so junk in the folder is skipped); a valid media file with no tags still returns a Track (it
    just lands on the untagged page)."""
    try:
        info = discoverer.discover_uri(Gst.filename_to_uri(path))
    except GLib.Error:
        return None
    tags = info.get_tags()
    dur = info.get_duration()
    return Track(
        path=path,
        title=_tag_str(tags, Gst.TAG_TITLE),
        # Prefer the album-artist for grouping; fall back to the track artist.
        artist=_tag_str(tags, Gst.TAG_ALBUM_ARTIST) or _tag_str(tags, Gst.TAG_ARTIST),
        album=_tag_str(tags, Gst.TAG_ALBUM),
        genre=_tag_str(tags, Gst.TAG_GENRE),
        track_no=_tag_uint(tags, Gst.TAG_TRACK_NUMBER),
        duration_ms=(dur // Gst.MSECOND) if dur and dur > 0 else -1,
    )


def _discover_all(paths: list[str], on_track, on_done, timeout_s: int) -> None:
    """Read every path's tags in order, reporting progress. Runs on the scan thread."""
    discoverer = GstPbutils.Discoverer.new(timeout_s * Gst.SECOND)
    tracks: list[Track] = []
    total = len(paths)
    for i, path in enumerate(paths, 1):
        t = discover(path, discoverer)
        if t is not None:
            tracks.append(t)
            if on_track:
                GLib.idle_add(on_track, t, i, total)
    if on_done:
        GLib.idle_add(on_done, tracks)


def scan_folder(folder: str, on_track=None, on_done=None, timeout_s: int = 10) -> None:
    """Scan ``folder`` in a background thread. ``on_track(track, done, total)`` fires per file and
    ``on_done(tracks)`` at the end — both marshalled onto the GLib main loop, so callers can touch
    GTK widgets directly. Returns immediately; the scan runs off the UI thread."""
    threading.Thread(
        target=lambda: _discover_all(list_media(folder), on_track, on_done, timeout_s),
        daemon=True).start()


def scan_paths(paths: list[str], on_track=None, on_done=None, timeout_s: int = 10) -> None:
    """Like :func:`scan_folder`, but for an explicit list of files that need not share a folder —
    what a *virtual* playlist (the files you bookmarked today, gathered from all over the tree) is
    built from. The given order is preserved in the resulting track list."""
    ordered = list(paths)
    threading.Thread(target=lambda: _discover_all(ordered, on_track, on_done, timeout_s),
                     daemon=True).start()


# ----------------------------------------------------------------- aggregation for the 3 panes

def counts(tracks: list[Track], key) -> list[tuple[str, int]]:
    """(value, count) pairs for a display key (``Track.g``/``a``/``al``), Unknown last, else A→Z."""
    tally: dict[str, int] = {}
    for t in tracks:
        tally[key(t)] = tally.get(key(t), 0) + 1
    return sorted(tally.items(), key=lambda kv: (kv[0] == UNKNOWN, kv[0].lower()))


# ----------------------------------------------------------------- writing tags (bulk folder tag)

# The four browseable/editable fields we support, in the "easy" (interoperable) mutagen vocabulary.
# These map to the most widely-supported cross-format tags: ID3v2 TIT2/TPE1/TALB/TCON, Vorbis
# comments TITLE/ARTIST/ALBUM/GENRE, and MP4 ©nam/©ART/©alb/©gen — so tags written here are read
# back by every common player (and by our own Discoverer scan).
EASY_KEYS = ("title", "artist", "album", "genre")


def mutagen_available() -> bool:
    try:
        import mutagen  # noqa: F401
        return True
    except ImportError:
        return False


def write_file_tags(path: str, fields: dict[str, str]) -> bool:
    """Write standard tags onto one file. ``fields`` may contain any of ``title/artist/album/genre``;
    only non-blank values are applied (so you can set just the title). Returns True on success, False
    if the format can't be tagged or mutagen is missing. Edits the file in place."""
    try:
        import mutagen
    except ImportError:
        return False
    clean = {k: v.strip() for k, v in fields.items() if k in EASY_KEYS and v.strip()}
    if not clean:
        return False
    try:
        f = mutagen.File(path, easy=True)
        if f is None:
            return False
        if f.tags is None:
            f.add_tags()
        for k, v in clean.items():
            f[k] = [v]
        f.save()
        return True
    except Exception:
        return False


def write_tags(paths: list[str], genre: str, artist: str, album: str) -> tuple[int, list[str]]:
    """Bulk-apply genre/artist/album to every path (only non-blank fields are written). Returns
    ``(written, skipped_paths)`` — formats mutagen can't tag land in ``skipped_paths`` rather than
    raising. The per-folder 'tag everything' action calls this."""
    fields = {"genre": genre, "artist": artist, "album": album}
    if not any(v.strip() for v in fields.values()):
        return 0, []
    if not mutagen_available():
        return 0, list(paths)
    written, skipped = 0, []
    for path in paths:
        (skipped, written) = (skipped, written + 1) if write_file_tags(path, fields) \
            else (skipped + [path], written)
    return written, skipped
