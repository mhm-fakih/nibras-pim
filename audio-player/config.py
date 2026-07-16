"""Player settings + per-file resume positions, stored as JSON next to the program.

Mirrors the sibling ``desktop/knotes`` config style: a ``Settings`` dataclass persisted to
``config.json`` beside the script (portable, PyInstaller-safe). Playback knobs default to the
phone's values so the two players feel the same. Resume positions and the recent-files list live in
the same file, keyed by absolute path.
"""

from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass, field, asdict


def _base_dir() -> str:
    if getattr(sys, "frozen", False):
        return os.path.dirname(os.path.abspath(sys.executable))
    return os.path.dirname(os.path.abspath(__file__))


_HERE = _base_dir()


def _config_path() -> str:
    return os.path.join(_HERE, "config.json")


@dataclass
class Settings:
    # Playback knobs — defaults match the phone's audio player.
    rate: float = 1.0                 # 0.75–1.25 (clamped by the engine)
    skip_primary_secs: int = 10       # the big skip buttons
    skip_secondary_secs: int = 5      # the small skip buttons
    bookmark_rewind_secs: int = 5     # a bookmark lands N seconds before "now" (the line you just heard),
                                      # and playback rewinds there so you re-hear it
    volume: float = 1.0               # 1.0 = unity; up to 10.0 boosts (the phone's gain)
    sleep_enabled: bool = False       # the sleep timer is hidden until enabled in Settings
    sleep_presets_min: list[int] = field(default_factory=lambda: [15, 30, 45, 60])
    theme: str = "light"              # "light" (Solarized parchment, the default) or "dark" (slate)
    library_root: str = ""            # root folder of the browser tree; blank = home (~)
    track_columns: list[str] = field(default_factory=list)  # saved playlist column order (by title)
    # Saved layout — 0 means "use the built-in default". Restored on relaunch.
    pane_sidebar: int = 0             # sidebar width (outer split)
    pane_left: int = 0               # folder-tree / recently-bookmarked split
    pane_split: int = 0              # playlist / player split
    win_w: int = 0
    win_h: int = 0

    # Session state.
    last_media: str = ""              # reopened (paused) on next launch
    last_folder: str = ""             # the folder the library browser last scanned
    recent: list[str] = field(default_factory=list)     # most-recent-first, capped
    recent_bookmarked: list[str] = field(default_factory=list)  # files recently bookmarked, newest first
    resume: dict[str, int] = field(default_factory=dict)  # abs path -> last position (ms)

    recent_cap: int = 20

    # --- resume / recents --------------------------------------------------

    def remember_position(self, path: str, position_ms: int) -> None:
        """Store where a file was left off (rounded to whole seconds — enough to resume, and keeps
        the JSON tidy). Near-start positions are dropped so a barely-played file isn't 'resumed'."""
        if position_ms > 3000:
            self.resume[os.path.abspath(path)] = (position_ms // 1000) * 1000
        else:
            self.resume.pop(os.path.abspath(path), None)

    def saved_position(self, path: str) -> int:
        return self.resume.get(os.path.abspath(path), 0)

    def push_recent(self, path: str) -> None:
        path = os.path.abspath(path)
        self.recent = [path] + [p for p in self.recent if p != path]
        del self.recent[self.recent_cap:]
        self.last_media = path

    def push_bookmarked(self, path: str) -> None:
        """Record a file as recently bookmarked (newest first), for the sidebar list."""
        path = os.path.abspath(path)
        self.recent_bookmarked = [path] + [p for p in self.recent_bookmarked if p != path]
        del self.recent_bookmarked[self.recent_cap:]

    # --- persistence -------------------------------------------------------

    @classmethod
    def load(cls) -> "Settings":
        try:
            with open(_config_path(), "r", encoding="utf-8") as f:
                raw = json.load(f)
        except (OSError, ValueError):
            return cls()
        s = cls()
        s.rate = float(raw.get("rate", s.rate))
        s.skip_primary_secs = int(raw.get("skip_primary_secs", s.skip_primary_secs))
        s.skip_secondary_secs = int(raw.get("skip_secondary_secs", s.skip_secondary_secs))
        s.bookmark_rewind_secs = int(raw.get("bookmark_rewind_secs", s.bookmark_rewind_secs))
        s.volume = float(raw.get("volume", s.volume))
        s.sleep_enabled = bool(raw.get("sleep_enabled", s.sleep_enabled))
        s.sleep_presets_min = [int(x) for x in raw.get("sleep_presets_min", s.sleep_presets_min)]
        s.theme = raw.get("theme", s.theme)
        s.library_root = raw.get("library_root", s.library_root)
        s.track_columns = [str(c) for c in raw.get("track_columns", s.track_columns)]
        s.pane_sidebar = int(raw.get("pane_sidebar", s.pane_sidebar))
        s.pane_left = int(raw.get("pane_left", s.pane_left))
        s.pane_split = int(raw.get("pane_split", s.pane_split))
        s.win_w = int(raw.get("win_w", s.win_w))
        s.win_h = int(raw.get("win_h", s.win_h))
        s.last_media = raw.get("last_media", s.last_media)
        s.last_folder = raw.get("last_folder", s.last_folder)
        s.recent = [str(p) for p in raw.get("recent", s.recent)]
        s.recent_bookmarked = [str(p) for p in raw.get("recent_bookmarked", s.recent_bookmarked)]
        s.resume = {str(k): int(v) for k, v in raw.get("resume", s.resume).items()}
        s.recent_cap = int(raw.get("recent_cap", s.recent_cap))
        return s

    def save(self) -> None:
        try:
            with open(_config_path(), "w", encoding="utf-8") as f:
                json.dump(asdict(self), f, indent=2, ensure_ascii=False)
        except OSError:
            pass
