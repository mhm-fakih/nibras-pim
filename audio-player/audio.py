"""GStreamer ``playbin`` wrapper — the audio engine, with no GUI.

Everything the player UI needs and nothing it doesn't: load / play / pause, seek (absolute and
relative — the skip buttons and bookmark ticks both land here), position & duration polling,
pitch-preserving playback rate (via ``scaletempo``), and volume/gain. End-of-stream, errors and the
"duration is now known" moment are delivered as callbacks so the UI can auto-advance, show an error,
and place the bookmark ticks the instant the length is available.

Integrates with the GLib main loop GTK already runs (a bus signal watch), so there's no second
thread. Position is *pulled* by the UI on a timer rather than pushed, which is how media players
normally drive a seek bar.
"""

from __future__ import annotations

import os

import gi

gi.require_version("Gst", "1.0")
from gi.repository import Gst, GLib  # noqa: E402

Gst.init(None)

# Rate limits mirror the phone's speed slider (0.75×–1.25×). Kept here so the UI and the engine
# can't disagree on what's clampable.
MIN_RATE = 0.75
MAX_RATE = 1.25

_NS_PER_MS = Gst.MSECOND  # 1_000_000 — GStreamer works in nanoseconds; we speak milliseconds.


class AudioPlayer:
    """A single-track audio player. Create one, wire the callbacks, then ``load()`` files into it.

    Callbacks (all optional, all fired on the GLib main thread):
      * ``on_eos()``            — the current track played to its end (drive auto-advance here).
      * ``on_error(message)``   — a decode/IO error; the track won't play.
      * ``on_duration(ms)``     — the media duration became known (place the seek-bar ticks).
      * ``on_state(playing)``   — play/pause state flipped (update the transport button).
    """

    def __init__(self, on_eos=None, on_error=None, on_duration=None, on_state=None):
        self.on_eos = on_eos
        self.on_error = on_error
        self.on_duration = on_duration
        self.on_state = on_state

        self._playbin = Gst.ElementFactory.make("playbin", "player")
        if self._playbin is None:
            raise RuntimeError("GStreamer 'playbin' is unavailable — install gstreamer plugins.")

        # scaletempo keeps the pitch natural when the rate isn't 1.0 (so speech doesn't chipmunk at
        # 1.25×). Inserted into playbin's audio path via the audio-filter property; if the plugin is
        # missing we simply run without it (rate changes then affect pitch, but still work).
        scaletempo = Gst.ElementFactory.make("scaletempo", "scaletempo")
        if scaletempo is not None:
            self._playbin.set_property("audio-filter", scaletempo)

        # Video output rendered into an embeddable GTK widget (so video plays *inside* our window,
        # not a separate one). ``video_widget`` is a GtkWidget the UI packs into its video area.
        #
        # We default to the SOFTWARE ``gtksink``, not the GL ``gtkglsink``: on some Mesa/driver
        # stacks the GL sink's DMABuf texture path produces green flashes and an "interlaced" look
        # that can bleed into the whole window (its GL context is created even for audio-only files).
        # ``gtksink`` avoids that entirely. Set AUDIO_PLAYER_GL=1 to opt back into the GL sink.
        self.video_widget = None
        use_gl = os.environ.get("AUDIO_PLAYER_GL") == "1"
        gtksink = None if use_gl else Gst.ElementFactory.make("gtksink", "gtksink")
        if gtksink is not None:
            self._playbin.set_property("video-sink", gtksink)
            self.video_widget = gtksink.get_property("widget")
        else:
            glsink = Gst.ElementFactory.make("gtkglsink", "glsink")
            if glsink is not None:
                sinkbin = Gst.ElementFactory.make("glsinkbin", "glsinkbin")
                sinkbin.set_property("sink", glsink)
                self._playbin.set_property("video-sink", sinkbin)
                self.video_widget = glsink.get_property("widget")

        self._rate = 1.0
        self._duration_ns = -1
        self._pending_seek_ns: int | None = None  # a seek requested before the pipeline could serve it
        self._current_path: str | None = None

        bus = self._playbin.get_bus()
        bus.add_signal_watch()
        bus.connect("message::eos", self._on_bus_eos)
        bus.connect("message::error", self._on_bus_error)
        bus.connect("message::state-changed", self._on_bus_state)
        bus.connect("message::duration-changed", self._on_bus_duration_changed)
        bus.connect("message::async-done", self._on_bus_async_done)

    # ------------------------------------------------------------------ load / transport

    @property
    def current_path(self) -> str | None:
        return self._current_path

    def load(self, path: str, autoplay: bool = False) -> None:
        """Point the player at a new file. Rate is preserved across tracks; duration resets until the
        new media reports it. ``autoplay`` starts it immediately, otherwise it loads paused."""
        self.stop()
        self._current_path = path
        self._duration_ns = -1
        self._pending_seek_ns = None
        self._playbin.set_property("uri", Gst.filename_to_uri(path))
        # PAUSED pre-rolls the pipeline so duration/seeking are available even before play.
        self._playbin.set_state(Gst.State.PLAYING if autoplay else Gst.State.PAUSED)

    def play(self) -> None:
        if self._current_path is not None:
            self._playbin.set_state(Gst.State.PLAYING)

    def pause(self) -> None:
        self._playbin.set_state(Gst.State.PAUSED)

    def toggle(self) -> None:
        self.pause() if self.is_playing else self.play()

    def stop(self) -> None:
        self._playbin.set_state(Gst.State.NULL)

    def dispose(self) -> None:
        """Tear the pipeline down (call on window close) so GStreamer threads exit cleanly."""
        self._playbin.set_state(Gst.State.NULL)

    @property
    def is_playing(self) -> bool:
        _ok, state, _pending = self._playbin.get_state(0)
        return state == Gst.State.PLAYING

    def has_video(self) -> bool:
        """True once the current media is known to carry a video stream (valid after pre-roll). The
        UI shows/hides its video area on this."""
        try:
            return self._playbin.get_property("n-video") > 0
        except Exception:
            return False

    # ------------------------------------------------------------------ position / duration

    def position_ms(self) -> int:
        ok, pos = self._playbin.query_position(Gst.Format.TIME)
        return pos // _NS_PER_MS if ok and pos >= 0 else 0

    def duration_ms(self) -> int:
        """Media length in ms, or ``-1`` until known. Cached from the pipeline; re-queried lazily so
        a track that reports its duration late still resolves."""
        if self._duration_ns < 0:
            ok, dur = self._playbin.query_duration(Gst.Format.TIME)
            if ok and dur > 0:
                self._duration_ns = dur
        return self._duration_ns // _NS_PER_MS if self._duration_ns > 0 else -1

    def seek(self, position_ms: int) -> None:
        """Seek to an absolute position (ms), clamped to the media. Used by the skip buttons, the
        seek bar, and tapping a bookmark tick. If the pipeline isn't ready to seek yet the request is
        stored and replayed once it pre-rolls (so seeking a just-loaded file still works)."""
        target_ns = max(0, position_ms) * _NS_PER_MS
        if self._duration_ns > 0:
            target_ns = min(target_ns, self._duration_ns)
        if not self._do_seek(target_ns):
            self._pending_seek_ns = target_ns

    def seek_by(self, delta_ms: int) -> None:
        self.seek(self.position_ms() + delta_ms)

    def _do_seek(self, target_ns: int) -> bool:
        """Issue the actual rate-aware seek. Returns False if the pipeline rejected it (not yet
        pre-rolled), so the caller can defer."""
        return self._playbin.seek(
            self._rate, Gst.Format.TIME,
            Gst.SeekFlags.FLUSH | Gst.SeekFlags.KEY_UNIT,
            Gst.SeekType.SET, target_ns,
            Gst.SeekType.NONE, 0)

    # ------------------------------------------------------------------ rate / volume

    @property
    def rate(self) -> float:
        return self._rate

    def set_rate(self, rate: float) -> None:
        """Set playback speed (clamped to 0.75×–1.25×), preserving pitch. Applied as an in-place
        rate seek from the current position so it takes effect immediately mid-playback."""
        self._rate = max(MIN_RATE, min(MAX_RATE, rate))
        pos_ns = self.position_ms() * _NS_PER_MS
        self._playbin.seek(
            self._rate, Gst.Format.TIME,
            Gst.SeekFlags.FLUSH | Gst.SeekFlags.KEY_UNIT,
            Gst.SeekType.SET, pos_ns,
            Gst.SeekType.NONE, 0)

    def set_volume(self, linear: float) -> None:
        """Set output volume. ``1.0`` is unity; playbin allows up to ``10.0`` for a gain boost (the
        phone's dB boost), and ``0`` mutes. Clamped to a safe ceiling."""
        self._playbin.set_property("volume", max(0.0, min(10.0, linear)))

    def get_volume(self) -> float:
        return self._playbin.get_property("volume")

    # ------------------------------------------------------------------ bus callbacks

    def _on_bus_eos(self, _bus, _msg) -> None:
        if self.on_eos:
            self.on_eos()

    def _on_bus_error(self, _bus, msg) -> None:
        err, _debug = msg.parse_error()
        if self.on_error:
            self.on_error(err.message if err else "playback error")

    def _on_bus_state(self, _bus, msg) -> None:
        # Only the playbin's own state transitions matter (child elements post their own).
        if msg.src is self._playbin and self.on_state:
            _old, new, _pending = msg.parse_state_changed()
            self.on_state(new == Gst.State.PLAYING)

    def _on_bus_duration_changed(self, _bus, _msg) -> None:
        self._duration_ns = -1  # invalidate; duration_ms() re-queries and caches
        ms = self.duration_ms()
        if ms > 0 and self.on_duration:
            self.on_duration(ms)

    def _on_bus_async_done(self, _bus, _msg) -> None:
        # The pipeline finished pre-rolling: a deferred seek can now be served, and duration is ready.
        if self._pending_seek_ns is not None and self._do_seek(self._pending_seek_ns):
            self._pending_seek_ns = None
        ms = self.duration_ms()
        if ms > 0 and self.on_duration:
            self.on_duration(ms)
