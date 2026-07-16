"""Amplitude-envelope extraction for the seek bar's spectrum view.

Decodes a media file with a throwaway GStreamer pipeline whose ``level`` element reports the peak
amplitude over fixed time slices, then hands the player a list of per-slice values (0..1, normalised
to the track's loudest moment). The player paints those as vertical bars behind the seek bar so you
can *see* where the loud and quiet stretches are.

Design mirrors ``audio.py``: GStreamer only (no numpy/ffmpeg dependency), and the decode runs on a
worker thread so a long file never blocks the UI. The result is delivered back on the GLib main
thread via ``GLib.idle_add`` so the caller can touch widgets directly. Jobs are cancellable — opening
a new file abandons an in-flight decode of the old one.
"""

from __future__ import annotations

import re
import threading

import gi

gi.require_version("Gst", "1.0")
from gi.repository import Gst, GLib  # noqa: E402

Gst.init(None)

# How many amplitude buckets to sample across the whole track. ~900 gives the seek bar more slices
# than it has pixels on any normal window, so the drawn bars never look blocky.
NUM_BUCKETS = 900
_MIN_INTERVAL_NS = 10 * Gst.MSECOND   # don't ask `level` for slices finer than 10 ms
# Below this dBFS a slice reads as effectively silent (matches `level`'s floor for empty buffers).
_SILENCE_DB = -90.0


class Job:
    """Handle for one in-flight decode. ``cancel()`` makes the result be dropped when it arrives."""

    def __init__(self) -> None:
        self.cancelled = False

    def cancel(self) -> None:
        self.cancelled = True


def compute(path: str, duration_ms: int, on_ready) -> Job:
    """Kick off envelope extraction for ``path``.

    ``on_ready(path, peaks)`` is called on the GLib main thread with ``peaks`` a list of floats in
    ``[0, 1]`` (empty if the file couldn't be decoded). Returns a :class:`Job` you can ``cancel()``.
    """
    job = Job()
    interval_ns = max(int(duration_ms) * Gst.MSECOND // NUM_BUCKETS, _MIN_INTERVAL_NS)
    thread = threading.Thread(
        target=_run, args=(path, interval_ns, on_ready, job), daemon=True)
    thread.start()
    return job


def _run(path: str, interval_ns: int, on_ready, job: Job) -> None:
    peaks = _extract(path, interval_ns, job)
    if job.cancelled:
        return
    GLib.idle_add(lambda: on_ready(path, peaks) if not job.cancelled else None)


def _extract(path: str, interval_ns: int, job: Job) -> list[float]:
    try:
        uri = Gst.filename_to_uri(path)
    except GLib.Error:
        return []
    pipeline = Gst.parse_launch(
        f'uridecodebin uri="{uri}" ! audioconvert ! audioresample ! '
        f"audio/x-raw,channels=1 ! level interval={interval_ns} post-messages=true ! "
        "fakesink sync=false")
    bus = pipeline.get_bus()
    pipeline.set_state(Gst.State.PLAYING)

    peaks: list[float] = []
    try:
        while not job.cancelled:
            msg = bus.timed_pop_filtered(
                Gst.SECOND,
                Gst.MessageType.ELEMENT | Gst.MessageType.EOS | Gst.MessageType.ERROR)
            if msg is None:
                continue  # decode still churning; keep waiting (guarded by job.cancelled)
            if msg.type == Gst.MessageType.ELEMENT:
                amp = _peak_from(msg.get_structure())
                if amp is not None:
                    peaks.append(amp)
            else:
                break  # EOS or ERROR — we're done either way
    finally:
        pipeline.set_state(Gst.State.NULL)

    return _normalise(peaks)


def _peak_from(structure) -> float | None:
    """Turn one ``level`` message into a single 0..1 amplitude (loudest channel, dBFS → linear)."""
    if structure is None or structure.get_name() != "level":
        return None
    # `level` posts "peak" as a per-channel GValueArray; PyGObject marshals it to a plain list of
    # dBFS doubles. Fall back to parsing the serialised structure if that ever changes.
    try:
        peaks = list(structure.get_value("peak") or [])
    except (TypeError, ValueError):
        peaks = _parse_peaks(structure.to_string())
    if not peaks:
        return 0.0
    db = max(peaks)                       # peak dBFS across channels (≤ 0)
    if db <= _SILENCE_DB:
        return 0.0
    return min(1.0, 10.0 ** (db / 20.0))  # dBFS → linear amplitude


def _parse_peaks(text: str) -> list[float]:
    match = re.search(r"peak=\((?:GValueArray|GstValueList)\)[<{]([^>}]*)[>}]", text)
    if not match:
        return []
    out = []
    for token in match.group(1).split(","):
        try:
            out.append(float(token))
        except ValueError:
            pass
    return out


def _normalise(peaks: list[float]) -> list[float]:
    """Scale so the track's loudest slice reaches 1.0 — makes quiet recordings still show shape."""
    if not peaks:
        return []
    top = max(peaks)
    if top <= 0.0:
        return [0.0] * len(peaks)
    return [p / top for p in peaks]
