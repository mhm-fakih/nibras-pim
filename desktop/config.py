"""User settings, stored as JSON under ``$XDG_CONFIG_HOME/knotes/config.json``.

Defaults mirror the Android app so the two can share a notes directory. The timestamp
format is a Python ``strftime`` pattern; the default produces names like
``2026_06_10-143025`` — the same shape the phone uses.
"""

from __future__ import annotations

import json
import os
import sys
from dataclasses import dataclass, field, asdict


def _base_dir() -> str:
    """Where the writable config.json lives — right next to the program, so it's self-contained
    and portable. When frozen by PyInstaller, ``__file__`` is inside the ephemeral _MEIPASS
    extraction dir (wiped on exit), so use the directory of the executable instead — otherwise
    saved settings would be lost on the next launch."""
    if getattr(sys, "frozen", False):
        return os.path.dirname(os.path.abspath(sys.executable))
    return os.path.dirname(os.path.abspath(__file__))


_HERE = _base_dir()


def _config_path() -> str:
    return os.path.join(_HERE, "config.json")


def _legacy_path() -> str:
    """Old location (XDG); read once for a seamless migration to the next-to-program file."""
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config")
    return os.path.join(base, "knotes", "config.json")


def _bundled_path() -> str | None:
    """The config.json packed into the frozen binary (read-only seed for the first launch)."""
    base = getattr(sys, "_MEIPASS", None)
    return os.path.join(base, "config.json") if base else None


def _clean_tags(raw) -> list[tuple[str, str]]:
    """Normalise a stored ``[[code, name], …]`` list: drop any '*' from codes (it's only a default
    marker, never a real code) and skip empties. Guards against legacy data saved before the parser
    stripped mis-placed stars."""
    out: list[tuple[str, str]] = []
    for item in raw:
        item = tuple(item)
        code = str(item[0]).replace("*", "").strip() if item else ""
        name = str(item[1]).strip() if len(item) > 1 else ""
        if code:
            out.append((code, name))
    return out


@dataclass
class Settings:
    notes_dir: str = os.path.expanduser("~/knotes")
    timestamp_format: str = "%Y_%m_%d-%H%M%S"
    # Characters of the note shown as its one-line "title" in the compact list. A card shows the
    # first ``preview_chars`` and (for longer notes) the last ``preview_tail_chars`` of the body.
    preview_chars: int = 120
    preview_tail_chars: int = 40
    # On a card, a note's attached files are listed (up to 5, then "…") with each name truncated to
    # this many leading characters plus its extension — a quick overview of what the note contains.
    card_file_chars: int = 12
    # The "just added" card preview shows the first N + last M WORDS of the note (with a word count).
    preview_head_words: int = 2
    preview_tail_words: int = 2
    # Characters of the note shown in the terminal dashboard's per-repo "last note" preview.
    terminal_preview_chars: int = 60
    # Command used to open a note on click; blank means the system default (xdg-open). e.g. "xed".
    opener: str = ""
    priority_min: int = 1
    priority_max: int = 5
    # The tag/priority a fresh note starts with. In the settings text the default type/category is
    # the one marked with a trailing '*' (e.g. "N*"); the '*' is only a marker — never stored in
    # the code, shown in the UI, or written to a file name.
    priority_default: int = 2
    default_type: str = "N"
    default_category: str = "F"
    # UI theme: "dark" (Solarized-style slate, the default) or "light".
    theme: str = "dark"
    # When True a plain Enter in the add field saves the note (Shift+Enter inserts a newline);
    # when False (the default) only Ctrl+Enter saves and Enter inserts a newline.
    save_on_enter: bool = False
    # How the notes are displayed: "kanban" (category columns, the default) or "list" (a flat list).
    view_mode: str = "kanban"
    # Minimal side-note mode (toggle with Ctrl+L): hides the board and the tag chip rows, showing only
    # the just-added card + the compact selected tags + the text field. Overlays view_mode, so turning
    # it off returns to whatever list/kanban view was active.
    minimal_mode: bool = False
    # Card ordering: "datetime" (newest first), "category", "type" or "priority" (highest first).
    sort_by: str = "datetime"
    # Number of rows the kanban category columns are spread across (1–3); the columns fill each row.
    kanban_rows: int = 2
    # Optional nibras backend, used ONLY to refresh the type/category codes so captures map exactly
    # to backend modules/departments on import. Purely additive: blank URL → fully offline, defaults
    # apply, and note-taking never depends on the network. Mirrors the mobile app's config.
    backend_url: str = ""          # e.g. http://192.168.0.47:8081 (or a Tomcat context path)
    backend_user: str = ""
    backend_password: str = ""
    # Note folders the user can switch between, each labelled by a short code, e.g.
    # [("A", "~/personal"), ("B", "~/work")]. Empty → a single repo derived from ``notes_dir``.
    repositories: list[tuple[str, str]] = field(default_factory=list)
    # Code of the folder new notes are currently written to / displayed (blank → the first one).
    active_repo: str = ""
    # Each tag is a (code, name) pair, e.g. ("W", "Work").
    types: list[tuple[str, str]] = field(
        default_factory=lambda: [("N", "Note"), ("W", "Work"), ("H", "Home"), ("I", "Ideas"), ("T", "Todo")])
    categories: list[tuple[str, str]] = field(
        default_factory=lambda: [("F", "Family"), ("P", "Personal"), ("W", "Work"), ("M", "Misc")])

    def priorities(self) -> range:
        return range(self.priority_min, max(self.priority_max, self.priority_min) + 1)

    # --- repositories ----------------------------------------------------

    def repos(self) -> list[tuple[str, str]]:
        """The configured (code, path) note folders — always at least one (derived from notes_dir
        when none are configured)."""
        return self.repositories if self.repositories else [("A", self.notes_dir)]

    def active_repo_code(self) -> str:
        codes = [c for c, _ in self.repos()]
        return self.active_repo if self.active_repo in codes else codes[0]

    def active_repo_path(self) -> str:
        code = self.active_repo_code()
        path = next((p for c, p in self.repos() if c == code), self.repos()[0][1])
        return os.path.expanduser(path)

    # --- persistence -----------------------------------------------------

    @classmethod
    def load(cls) -> "Settings":
        raw = None
        # Prefer the persisted next-to-program file, then migrate the old XDG one, then (for a frozen
        # binary's first run) the packaged seed. Writes always go to the next-to-program file.
        for path in filter(None, (_config_path(), _legacy_path(), _bundled_path())):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    raw = json.load(f)
                break
            except (OSError, ValueError):
                continue
        if raw is None:
            return cls()
        s = cls()
        s.notes_dir = raw.get("notes_dir", s.notes_dir)
        s.timestamp_format = raw.get("timestamp_format", s.timestamp_format)
        s.preview_chars = int(raw.get("preview_chars", s.preview_chars))
        s.preview_tail_chars = int(raw.get("preview_tail_chars", s.preview_tail_chars))
        s.card_file_chars = int(raw.get("card_file_chars", s.card_file_chars))
        s.preview_head_words = int(raw.get("preview_head_words", s.preview_head_words))
        s.preview_tail_words = int(raw.get("preview_tail_words", s.preview_tail_words))
        s.terminal_preview_chars = int(raw.get("terminal_preview_chars", s.terminal_preview_chars))
        s.opener = raw.get("opener", s.opener)
        s.priority_min = int(raw.get("priority_min", s.priority_min))
        s.priority_max = int(raw.get("priority_max", s.priority_max))
        s.priority_default = int(raw.get("priority_default", s.priority_default))
        s.default_type = str(raw.get("default_type", s.default_type)).replace("*", "").strip()
        s.default_category = str(raw.get("default_category", s.default_category)).replace("*", "").strip()
        s.theme = raw.get("theme", s.theme)
        s.save_on_enter = bool(raw.get("save_on_enter", s.save_on_enter))
        s.view_mode = raw.get("view_mode", s.view_mode)
        s.minimal_mode = bool(raw.get("minimal_mode", s.minimal_mode))
        s.sort_by = raw.get("sort_by", s.sort_by)
        s.kanban_rows = int(raw.get("kanban_rows", s.kanban_rows))
        s.backend_url = str(raw.get("backend_url", s.backend_url)).strip()
        s.backend_user = raw.get("backend_user", s.backend_user)
        s.backend_password = raw.get("backend_password", s.backend_password)
        s.repositories = [tuple(r) for r in raw.get("repositories", s.repositories)]
        s.active_repo = raw.get("active_repo", s.active_repo)
        s.types = _clean_tags(raw.get("types", s.types))
        s.categories = _clean_tags(raw.get("categories", s.categories))
        return s

    def save(self) -> None:
        data = asdict(self)
        # JSON has no tuples; store tag/repo pairs as lists.
        data["types"] = [list(t) for t in self.types]
        data["categories"] = [list(c) for c in self.categories]
        data["repositories"] = [list(r) for r in self.repositories]
        with open(_config_path(), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)


# The per-repository tag config file, stored inside the note folder itself.
REPO_CONFIG_NAME = ".knotes.json"


@dataclass
class RepoConfig:
    """Per-repository tag configuration (subjects/types, categories and priorities).

    Stored as ``.knotes.json`` inside each note folder, so every repository has its OWN set of
    types/categories/priorities instead of one global set. Missing on first use → seeded from the
    global :class:`Settings` (preserving prior behaviour) and written into the folder.
    """
    priority_min: int = 1
    priority_max: int = 5
    priority_default: int = 2
    default_type: str = "N"
    default_category: str = "F"
    types: list[tuple[str, str]] = field(
        default_factory=lambda: [("N", "Note"), ("W", "Work"), ("H", "Home"), ("I", "Ideas"), ("T", "Todo")])
    categories: list[tuple[str, str]] = field(
        default_factory=lambda: [("F", "Family"), ("P", "Personal"), ("W", "Work"), ("M", "Misc")])

    def priorities(self) -> range:
        return range(self.priority_min, max(self.priority_max, self.priority_min) + 1)

    @staticmethod
    def path_for(repo_path: str) -> str:
        return os.path.join(os.path.expanduser(repo_path), REPO_CONFIG_NAME)

    @classmethod
    def _from_seed(cls, s: "Settings") -> "RepoConfig":
        return cls(priority_min=s.priority_min, priority_max=s.priority_max,
                   priority_default=s.priority_default, default_type=s.default_type,
                   default_category=s.default_category,
                   types=[tuple(t) for t in s.types], categories=[tuple(c) for c in s.categories])

    @classmethod
    def load(cls, repo_path: str, seed: "Settings | None" = None) -> "RepoConfig":
        try:
            with open(cls.path_for(repo_path), "r", encoding="utf-8") as f:
                raw = json.load(f)
        except (OSError, ValueError):
            raw = None
        if raw is None:
            # No per-repo file yet: seed from the global settings and write it so the folder
            # gets its own config going forward (best-effort — ignore an unwritable folder).
            cfg = cls._from_seed(seed) if seed is not None else cls()
            cfg.save(repo_path)
            return cfg
        cfg = cls()
        cfg.priority_min = int(raw.get("priority_min", cfg.priority_min))
        cfg.priority_max = int(raw.get("priority_max", cfg.priority_max))
        cfg.priority_default = int(raw.get("priority_default", cfg.priority_default))
        cfg.default_type = str(raw.get("default_type", cfg.default_type)).replace("*", "").strip()
        cfg.default_category = str(raw.get("default_category", cfg.default_category)).replace("*", "").strip()
        cfg.types = _clean_tags(raw.get("types", cfg.types))
        cfg.categories = _clean_tags(raw.get("categories", cfg.categories))
        return cfg

    def save(self, repo_path: str) -> None:
        data = {
            "priority_min": self.priority_min,
            "priority_max": self.priority_max,
            "priority_default": self.priority_default,
            "default_type": self.default_type,
            "default_category": self.default_category,
            "types": [list(t) for t in self.types],
            "categories": [list(c) for c in self.categories],
        }
        try:
            os.makedirs(os.path.expanduser(repo_path), exist_ok=True)
            with open(self.path_for(repo_path), "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except OSError:
            pass


def parse_tag_block(text: str) -> tuple[list[tuple[str, str]], str]:
    """Parse a ``code:name`` per-line block. A trailing ``*`` on a code marks the default
    selection; it is stripped from the stored code. Returns (tags, default_code)."""
    tags: list[tuple[str, str]] = []
    default_code = ""
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        code, _, name = line.partition(":")
        code = code.strip()
        # A '*' ANYWHERE on the code marks it the default (leading, trailing, wherever); every '*' is
        # then stripped so it never becomes part of the real code — which flows into the chip label,
        # the tag and the file name. If several codes are starred, the first one (in order) wins.
        is_default = "*" in code
        code = code.replace("*", "").strip()
        if not code:
            continue
        if is_default and not default_code:
            default_code = code
        tags.append((code, name.strip()))
    return tags, default_code


def format_tag_block(tags: list[tuple[str, str]], default_code: str) -> str:
    """Render tags one per line, appending ``*`` to the default one (inverse of parse_tag_block)."""
    lines = []
    for code, name in tags:
        mark = "*" if code == default_code else ""
        lines.append(f"{code}{mark}:{name}" if name else f"{code}{mark}")
    return "\n".join(lines)
