#!/usr/bin/env python3
"""knotes terminal — a Claude-Code-style TUI: note entry like the desktop app's minimal mode, plus
command-driven backend reports.

Layout: a dashboard on top (repos, note counts), a content log, a status line, and a command/note
bar docked at the bottom.

  - Type "<repo> <type><prio><subject> <body>" + Enter → save a note file (knotes filename scheme);
    the repo letter and the 3-char meta are optional (unset parts use the config defaults).
    e.g. "s t3d some note" → repo s, type t, priority 3, subject d, body "some note".
  - A line starting with '/' → a command (like Claude Code).

Commands: /repos · /login <user> <pass> · /heartbeat · /tasks / /goals / … · /theme · /help · /quit.
Theme: solarized (light by default); '/theme light' / '/theme dark' switches and persists to config.
Config: a local, non-hidden ./config.json (created on first run, seeded from the desktop app's
config.json) holds the repos (code → path + its OWN backend URL), the tag defaults, and the preview
length. Each repo points at its own backend.

'+' prefix on a note line also creates a backend record via the web Import-notes endpoint
(POST /api/import/notes) and archives the file into imported/.

    NIBRAS_URL=http://host:8081 ./run.sh   # env override for every repo's backend (testing)
"""
from __future__ import annotations

import os
import re
import sys
import time

import httpx
from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Horizontal, Vertical
from textual.suggester import SuggestFromList
from textual.widgets import Header, Input, RichLog, Static

import json

# Note storage (filename tag scheme) — the module is shared with the desktop app. A local
# ./notes.py wins (script dir is sys.path[0] — lets a copied-out terminal/ run standalone);
# otherwise fall back to the desktop app's copy next door in the repo layout.
sys.path.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "desktop"))
try:
    from notes import NoteRepository, is_rtl  # noqa: E402
except ImportError:
    sys.stderr.write(
        "knotes_tui: cannot find the 'notes' module.\n"
        "Run from the repo (needs ../desktop/notes.py), or copy desktop/notes.py next to this "
        "script for a standalone setup.\n"
    )
    raise SystemExit(1)

VERSION = "1.2.36"
CODENAME = "Gratitude"

# Local, non-hidden config file, right next to this program — the terminal app owns it.
CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.json")


DEFAULT_BACKEND = "http://127.0.0.1:8081"

DEFAULT_THEME = "solarized-light"
THEME_ALIASES = {
    "light": "solarized-light", "l": "solarized-light", "solarized-light": "solarized-light",
    "dark": "solarized-dark", "d": "solarized-dark", "solarized-dark": "solarized-dark",
}


def _default_config() -> dict:
    """Defaults, seeded once from the desktop app's config.json (if reachable) so existing repos and
    tag defaults carry over; after that this file is authoritative. Each repo has its OWN backend."""
    cfg = {
        "preview_chars": 60,           # chars of the "last note" preview in the dashboard
        "default_repo": "",
        "default_type": "N",
        "default_priority": 2,
        "default_subject": "Z",
        "theme": DEFAULT_THEME,        # solarized-light / solarized-dark (switch with /theme)
        # code -> { path, backend }: every repo points at its own backend.
        "repos": {"A": {"path": "~/knotes", "backend": DEFAULT_BACKEND}},
    }
    try:
        from config import Settings  # desktop config (on sys.path)
        s = Settings.load()
        cfg["repos"] = {c.upper(): {"path": p, "backend": DEFAULT_BACKEND} for c, p in s.repos()}
        cfg["default_repo"] = s.active_repo_code().upper()
        cfg["default_type"] = s.default_type or "N"
        cfg["default_priority"] = s.priority_default
        cfg["default_subject"] = s.default_category or "Z"
        cfg["preview_chars"] = s.terminal_preview_chars
    except Exception:
        pass
    return cfg


def load_config() -> dict:
    """Read the local config.json (creating it with defaults on first run). Env NIBRAS_URL, if set,
    overrides every repo's backend (handy for testing)."""
    cfg = _default_config()
    if os.path.exists(CONFIG_PATH):
        try:
            with open(CONFIG_PATH, encoding="utf-8") as f:
                cfg.update(json.load(f))
        except Exception:
            pass
    else:
        save_config(cfg)
    env_url = os.environ.get("NIBRAS_URL")
    if env_url:
        for r in cfg.get("repos", {}).values():
            if isinstance(r, dict):
                r["backend"] = env_url
    return cfg


def save_config(cfg: dict) -> None:
    try:
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
    except Exception:
        pass


def _tokens_path() -> str:
    """JWTs live OUTSIDE the app dir/git tree, in the user's config home with 0600 perms."""
    base = os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config")
    return os.path.join(base, "knotes", "tokens.json")


def load_tokens() -> dict:
    try:
        with open(_tokens_path(), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def save_tokens(tokens: dict) -> None:
    try:
        path = _tokens_path()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(tokens, f)
        os.chmod(path, 0o600)  # readable only by the user
    except Exception:
        pass

# A note line may start with:  <repo-letter> <type><priority><subject> <body>
# e.g. "s t3d some note" → repo s, type t, priority 3, subject d, body "some note".
# Both the repo letter and the 3-char meta are optional; each is consumed only if it matches.
META_RE = re.compile(r"[A-Za-z][0-9][A-Za-z]")

# --- Arabic shaping (OFF by default; modern terminals shape+bidi Arabic themselves, so pre-shaping
# would double-process it into garbled text. Set NIBRAS_RESHAPE=1 for terminals that don't shape) ---
_want_reshape = os.environ.get("NIBRAS_RESHAPE", "0") == "1"
try:
    import arabic_reshaper
    try:
        from bidi import get_display          # python-bidi >= 0.5
    except Exception:
        from bidi.algorithm import get_display  # python-bidi < 0.5
    SHAPING = _want_reshape
except Exception:  # pragma: no cover - optional deps
    SHAPING = False


def shape(text: str) -> str:
    if SHAPING and is_rtl(text):
        return get_display(arabic_reshaper.reshape(text))
    return text


TS_FORMAT = "%Y_%m_%d-%H%M%S"
MODULES = ("tasks", "goals", "journals", "planners", "resources", "notes", "writings")

# Inline suggestions for '/' commands (accept the ghost text with the → / End key). Bare note text
# matches nothing here, so it isn't interfered with. Order matters — the first prefix match wins.
COMMAND_SUGGESTIONS = ["/login ", "/heartbeat", *(f"/{m}" for m in MODULES), "/repos", "/theme ", "/help", "/quit"]


class KnotesTUI(App):
    TITLE = "Nibras terminal"
    SUB_TITLE = f'v{VERSION} "{CODENAME}"'
    CSS = """
    #dashboard { dock: top; padding: 0 1; border: round $panel; }
    #log { height: 1fr; border: round $panel; padding: 0 1; }
    #bottombar { dock: bottom; height: auto; }
    /* Claude-Code-style command bar: a single-line field between a top and bottom grey rule. */
    /* Rules and prompt glyph track the theme — hardcoded grey/white vanished on a light background. */
    #promptrow { height: auto; border-top: solid $foreground-muted; border-bottom: solid $foreground-muted;
                 background: transparent; }
    #prompt { width: 2; color: $foreground; background: transparent; }
    /* Flatten Textual's default 3-high bordered Input to a single line; no field background. */
    #cmd { border: none; height: 1; padding: 0 1; background: transparent; }
    #cmd:focus { background: transparent; }
    #cmd.-rtl { text-align: right; }
    /* Solid block cursor with EXPLICIT colours — `text-style: reverse` swaps fg/bg, but this
       field's background is transparent, so the swap rendered an invisible cursor. NOTE:
       $text resolves to the special 'auto' value, which is illegal for background — use the
       concrete $foreground/$background theme colours. (A real terminal beam isn't possible:
       forcing it via escapes raced with Textual's I/O.) */
    #cmd > .input--cursor { background: $foreground; color: $background; text-style: none; }
    /* status bar under the box (backend, repo+triple, uptime, session count) with a little space below. */
    #statusbar { height: auto; color: $text-muted; padding: 0 1 1 1; }
    """
    # Exit ONLY via the /quit command — a stray Ctrl+C/Ctrl+Q shouldn't drop the app (both are easy to
    # hit while typing). The priority bindings override Textual's built-in quit shortcuts.
    BINDINGS = [
        Binding("ctrl+q", "ignore_quit", "", priority=True, show=False),
        Binding("ctrl+c", "ignore_quit", "", priority=True, show=False),
    ]

    def __init__(self) -> None:
        super().__init__()
        self.client = httpx.AsyncClient(timeout=10.0)  # per-request full URLs (backend is per repo)
        self.tokens: dict[str, str] = load_tokens()    # backend URL -> JWT, persisted across launches
        self.config = load_config()
        # repo code -> { path, backend }; tolerate the old {code: path} form too.
        self.repos: dict[str, dict] = {}
        for code, v in self.config.get("repos", {}).items():
            if isinstance(v, dict):
                self.repos[code.upper()] = {"path": os.path.expanduser(v.get("path", "")),
                                            "backend": v.get("backend", DEFAULT_BACKEND)}
            else:
                self.repos[code.upper()] = {"path": os.path.expanduser(v), "backend": DEFAULT_BACKEND}
        if not self.repos:
            self.repos["A"] = {"path": os.path.expanduser("~/knotes"), "backend": DEFAULT_BACKEND}
        self.default_repo = (self.config.get("default_repo") or next(iter(self.repos))).upper()
        if self.default_repo not in self.repos:
            self.default_repo = next(iter(self.repos))
        self.def_type = self.config.get("default_type") or None
        self.def_prio = int(self.config.get("default_priority", 2))
        self.def_cat = self.config.get("default_subject") or None
        self.preview_chars = int(self.config.get("preview_chars", 60))
        self.ts_format = TS_FORMAT
        self._last_rtl = False     # last input direction, so we only re-align on transitions
        self._session_notes = 0    # notes saved this session (shown in the status bar)
        self._online = False       # current repo's backend reachable? (status-bar colour)
        self._history: list[str] = []          # submitted lines, for Up/Down recall
        self._hist_pos = 0
        self._pending_push: list[tuple[str, str, str]] = []  # (backend, repo_path, filename) not yet pushed
        self._start = time.monotonic()

    def compose(self) -> ComposeResult:
        yield Header()
        yield Static(id="dashboard")
        yield RichLog(id="log", markup=True, wrap=True, highlight=False)
        # Claude-Code-style bottom: a context line, the bordered input box, then a one-line status bar.
        with Vertical(id="bottombar"):
            with Horizontal(id="promptrow"):
                yield Static("›", id="prompt")
                yield Input(
                    placeholder="note text + Enter to save · '/' for a command (Tab/→ completes)",
                    id="cmd",
                    # Don't select-all on (re)focus — that's what made a keyboard-layout switch (a brief
                    # blur→refocus) select the text so the next key wiped it.
                    select_on_focus=False,
                    suggester=SuggestFromList(COMMAND_SUGGESTIONS, case_sensitive=False),
                )
            yield Static(id="statusbar")

    def on_mount(self) -> None:
        self.theme = THEME_ALIASES.get(str(self.config.get("theme", "")).lower(), DEFAULT_THEME)
        self.query_one("#log", RichLog).can_focus = False
        cmd = self.query_one("#cmd", Input)
        try:  # non-blinking cursor (attribute name/availability varies by Textual version)
            cmd.cursor_blink = False
        except Exception:
            pass
        cmd.focus()
        self.write("Note line: [b][<repo>] <type><prio><subject> <body>[/b]  \ne.g. [i]s t3d some note[/i] "
                   "or [i]t3d some note[/i] (repo optional → sticky default). '/' for commands (/help).")
        self._refresh_dashboard()
        self._update_statusbar()
        self.set_interval(1.0, self._update_statusbar)          # tick the uptime clock
        self.set_interval(5.0, lambda: self.run_worker(self.check_heartbeat(), exclusive=False))
        self.run_worker(self.check_heartbeat(), exclusive=False)

    def _update_statusbar(self) -> None:
        """One-line status bar under the box: the current backend (green if online), the sticky repo +
        triple, process uptime and notes saved this session."""
        s = int(time.monotonic() - self._start)
        h, m, sec = s // 3600, (s % 3600) // 60, s % 60
        notes = self._session_notes
        meta = f"{self.def_type or '·'}{self.def_prio}{self.def_cat or '·'}"
        dot = "$text-success" if self._online else "$text-error"
        backend = self._backend()
        self.query_one("#statusbar", Static).update(
            f"[{dot}]●[/{dot}] [{'$text-success' if self._online else 'dim'}]{backend}[/]"
            f"   [dim]·[/dim] [b]{self.default_repo} {meta}[/b]"
            f"   [dim]·   up {h}:{m:02d}:{sec:02d}   ·   {notes} note{'' if notes == 1 else 's'} this session[/dim]")

    def _note_preview(self, content: str) -> str:
        """First N chars of the note + word count (never the whole thing). N = terminal_preview_chars
        from the config file (default 60)."""
        text = " ".join(content.split())  # collapse newlines/runs of whitespace
        words = len(content.split())
        n = max(1, self.preview_chars)
        body = text if len(text) <= n else text[:n].rstrip() + " …"
        return f"{shape(body) or '(empty)'}  [dim]({words}w)[/dim]"

    def _refresh_dashboard(self) -> None:
        """Top panel: each repo's code + path + note count + last-entered note (from the config)."""
        lines = [f'[b]Nibras Terminal v{VERSION} "{CODENAME}"[/b]\nRepositories:\n']
        for code in sorted(self.repos):
            path = self.repos[code]["path"]
            backend = self.repos[code]["backend"]
            try:
                notes = NoteRepository(path).list_notes()
            except Exception:
                notes = []
            n = len(notes)
            last_txt = self._note_preview(notes[-1].content) if notes else "[dim]—[/dim]"
            star = "★" if code == self.default_repo else " "
            short = path.replace(os.path.expanduser("~"), "~")
            lines.append(f" {star} [b $text-primary]{code}[/]  [dim]{short}[/dim] → [dim]{backend}[/dim]  "
                         f"[b]{n}[/b] note{'s' if n != 1 else ''}")
        self.query_one("#dashboard", Static).update("\n".join(lines))

    def action_ignore_quit(self) -> None:
        self.write("[yellow]Exit shortcut ignored — type [b]/quit[/b] to exit.[/yellow]")

    # --- output / status ---------------------------------------------------------------------------
    def write(self, text: str) -> None:
        self.query_one("#log", RichLog).write(self._themed(text))

    def _themed(self, text: str) -> str:
        """Re-point the semantic colour tags in log lines at the ACTIVE theme's palette. RichLog is
        Rich markup, not Textual's, so it can't read $variables and resolves bare 'green'/'red' to the
        terminal's own ANSI palette — which fights the app's background (glaring on solarized-light).
        Substituting concrete hexes keeps the log in the same palette as the rest of the UI."""
        t = self.current_theme
        for name, value in (("green", t.success), ("red", t.error),
                            ("yellow", t.warning), ("cyan", t.primary)):
            text = (text.replace(f"[{name}]", f"[{value}]").replace(f"[/{name}]", f"[/{value}]")
                        .replace(f"[b {name}]", f"[b {value}]").replace(f"[/b {name}]", f"[/b {value}]"))
        return text

    def _backend(self) -> str:
        """The current repo's backend URL (each repo has its own)."""
        return self.repos[self.default_repo]["backend"]

    def switch_theme(self, mode: str) -> None:
        """/theme [light|dark] — bare '/theme' just reports the current one. The choice is written back
        to config.json so it survives a relaunch (same treatment as the sticky repo)."""
        if not mode:
            self.write(f"theme: [b]{self.theme}[/b]  (/theme light · /theme dark)")
            return
        name = THEME_ALIASES.get(mode.lower())
        if not name:
            self.write(f"[red]unknown theme:[/red] {mode}  (light · dark)")
            return
        self.theme = name
        self.config["theme"] = name
        save_config(self.config)
        self._refresh_dashboard()
        self._update_statusbar()
        self.write(f"[green]✓ theme[/green] [b]{name}[/b]")

    def _persist_default_repo(self) -> None:
        """Remember the last-used repo as the default across launches (writes the local config)."""
        self.config["default_repo"] = self.default_repo
        save_config(self.config)

    def on_descendant_blur(self, _event) -> None:
        # Keep the command bar always focused.
        self.call_after_refresh(self.query_one("#cmd", Input).focus)

    # --- input -------------------------------------------------------------------------------------
    async def on_input_submitted(self, event: Input.Submitted) -> None:
        text = event.value
        event.input.value = ""
        if not text.strip():
            return
        self._history.append(text)          # recall with Up/Down
        self._hist_pos = len(self._history)
        stripped = text.lstrip()
        if stripped.startswith("/"):
            await self.command(stripped[1:].strip())
        elif stripped.startswith("+"):
            await self.push_note(stripped[1:])  # save locally + create a backend record
        else:
            self.save_note(text)

    def on_key(self, event) -> None:
        """Up/Down recall previously submitted lines (shell-style history)."""
        # Tab accepts the ghost suggestion (same as → / End) — the natural completion key.
        if event.key == "tab" and self.query_one("#cmd", Input).has_focus:
            cmd = self.query_one("#cmd", Input)
            suggestion = getattr(cmd, "_suggestion", "") or ""
            if len(suggestion) > len(cmd.value):
                cmd.value = suggestion
                cmd.cursor_position = len(suggestion)
                event.prevent_default()
                event.stop()
            return
        if event.key not in ("up", "down") or not self.query_one("#cmd", Input).has_focus:
            return
        if not self._history:
            return
        cmd = self.query_one("#cmd", Input)
        if event.key == "up":
            self._hist_pos = max(0, self._hist_pos - 1)
            cmd.value = self._history[self._hist_pos]
        else:
            self._hist_pos = min(len(self._history), self._hist_pos + 1)
            cmd.value = self._history[self._hist_pos] if self._hist_pos < len(self._history) else ""
        cmd.cursor_position = len(cmd.value)
        event.prevent_default()
        event.stop()

    def _note_tags_present(self, text: str) -> tuple[bool, bool]:
        """(has recognised repo letter, has valid <type><prio><subject> token) at the start."""
        parts = text.strip().split()
        i = 0
        has_repo = bool(parts) and len(parts[0]) == 1 and parts[0].upper() in self.repos
        if has_repo:
            i = 1
        has_meta = len(parts) > i and bool(META_RE.fullmatch(parts[i]))
        return has_repo, has_meta

    async def on_input_changed(self, event: Input.Changed) -> None:
        # Right-align for Arabic. Only touch the widget when the direction flips — safe now that
        # select_on_focus=False stops the re-align from selecting all. (Bar colouring is disabled.)
        rtl = is_rtl(event.value)
        if rtl != self._last_rtl:
            self._last_rtl = rtl
            event.input.set_class(rtl, "-rtl")

    def _parse_note(self, text: str):
        """[<repo>] <type><prio><subject> <body>. A valid 3-char meta makes the note conform; the
        leading repo letter is optional (defaults to the sticky default repo). Only then is the prefix
        stripped — otherwise the note silently gets the default repo + default tags and the WHOLE line
        becomes the body, so nothing the user typed is ever dropped."""
        text = text.strip()
        parts = text.split()
        has_repo, has_meta = self._note_tags_present(text)
        if has_meta:
            i = 1 if has_repo else 0
            repo = parts[0].upper() if has_repo else self.default_repo
            tok = parts[i]
            return repo, tok[0].upper(), int(tok[1]), tok[2].upper(), " ".join(parts[i + 1:])
        return self.default_repo, self.def_type, self.def_prio, self.def_cat, text

    def _write_note(self, text: str):
        """Parse a note line, apply the sticky repo/triple defaults, and save the file. Returns
        (repo, type_, prio, cat, body, path) or None if there was nothing to save / a save error."""
        repo, type_, prio, cat, body = self._parse_note(text)
        if not body.strip():
            self.write("[yellow]nothing to save (empty note body)[/yellow]")
            return None
        # Sticky defaults: an explicit repo / meta becomes the default for subsequent plain notes.
        has_repo, has_meta = self._note_tags_present(text)
        if has_repo and repo != self.default_repo:
            self.default_repo = repo
            self._persist_default_repo()
            self.run_worker(self.check_heartbeat(), exclusive=False)  # new repo → its own backend
        if has_meta:
            self.def_type, self.def_prio, self.def_cat = type_, prio, cat
        if has_repo or has_meta:
            self._update_statusbar()
        try:
            path = NoteRepository(self.repos[repo]["path"]).save_new(body, type_, prio, cat, self.ts_format)
        except Exception as exc:
            self.write(f"[red]save failed:[/red] {exc}")
            return None
        self._session_notes += 1
        return repo, type_, prio, cat, body, path

    def save_note(self, text: str) -> None:
        r = self._write_note(text)
        if r is None:
            return
        repo, type_, prio, cat, body, path = r
        words = body.split()
        n = len(words)
        preview = " ".join(words[:2]) + (" … " + " ".join(words[-2:]) if n > 4 else "")
        tag = f"{repo}:{type_ or '·'} p{prio}" + (f" {cat}" if cat else "")
        self.write(f"[green]✓ saved[/green] [dim]{tag}[/dim]  {shape(preview)}  [dim]({n}w · {os.path.basename(path)})[/dim]")
        self._refresh_dashboard()
        self._update_statusbar()

    async def push_note(self, text: str) -> None:
        """'+' prefix: ALWAYS save the note in the repo first (so it's never lost), then create a
        backend record via the SAME endpoint the web Import-notes page uses (POST /api/import/notes).
        If not logged in, the note is queued and auto-pushed after /login."""
        r = self._write_note(text)   # save locally first — never lose the note
        if r is None:
            return
        repo, type_, prio, cat, body, path = r
        backend = self._backend()
        repo_path = self.repos[repo]["path"]
        filename = os.path.basename(path)
        if not self.tokens.get(backend):
            self._pending_push.append((backend, repo_path, filename))
            self.write(f"[yellow]saved {filename} locally — queued to push to {backend} after /login[/yellow]")
        else:
            await self._import_one(backend, repo_path, filename)
        self._refresh_dashboard()
        self._update_statusbar()

    async def _import_one(self, backend: str, repo_path: str, filename: str) -> None:
        """Push one already-saved note file: point the backend's importer at this repo's folder, then
        import the file (creates the record + archives it to imported/)."""
        token = self.tokens.get(backend)
        if not token:
            self._pending_push.append((backend, repo_path, filename))
            return
        auth = {"Authorization": f"Bearer {token}"}
        try:
            # The repo's folder rides along per request — no global import-dir flip (the old
            # PUT /dir dance failed silently and the import then scanned the wrong folder).
            resp = await self.client.post(f"{backend}/api/import/notes", headers=auth,
                                          json={"filename": filename, "dir": repo_path})
            if resp.status_code == 200:
                rec = resp.json()
                self.write(f"[green]✓ pushed[/green] {filename} → "
                           f"[cyan]{rec.get('module')} #{rec.get('id')}[/cyan]  [dim](→ imported/)[/dim]")
            elif resp.status_code in (401, 403):
                self.tokens.pop(backend, None)
                save_tokens(self.tokens)
                self._pending_push.append((backend, repo_path, filename))
                self.write("[yellow]session expired — note re-queued; /login again[/yellow]")
            else:
                self.write(f"[red]push failed[/red] {filename} (HTTP {resp.status_code}): "
                           f"{self._err(resp)} — kept in the repo")
        except Exception as exc:
            self.write(f"[red]push error:[/red] {exc} — {filename} kept in the repo")

    @staticmethod
    def _err(resp) -> str:
        """The backend's error text from a failed response (detail/message/error, else raw body)."""
        try:
            data = resp.json()
            if isinstance(data, dict):
                return str(data.get("detail") or data.get("message") or data.get("error") or data)
            return str(data)
        except Exception:
            return (resp.text or "").strip()[:300] or "(no body)"

    async def _drain_pending(self, backend: str) -> None:
        """After a login, push any notes that were queued for that backend."""
        queued = [(rp, f) for (b, rp, f) in self._pending_push if b == backend]
        if not queued:
            return
        self._pending_push = [(b, rp, f) for (b, rp, f) in self._pending_push if b != backend]
        self.write(f"[dim]pushing {len(queued)} queued note(s)…[/dim]")
        for repo_path, filename in queued:
            await self._import_one(backend, repo_path, filename)

    # --- commands ----------------------------------------------------------------------------------
    async def command(self, cmd: str) -> None:
        parts = cmd.split()
        if not parts:
            return
        name, args = parts[0].lower(), parts[1:]
        if name in ("quit", "exit", "q"):
            await self.client.aclose()
            self.exit()
        elif name in ("help", "h", "?"):
            self.write("[b]note[/b]: [<repo>] <type><prio><subject> <body>  e.g. [i]s t3d some note[/i]  "
                       "(repo optional; an explicit repo becomes the sticky default)")
            self.write("[b]+[/b] prefix: save the note AND push it to the repo's backend as a record (→ imported/)")
            self.write("/repos · /login <u> <p> · /heartbeat · " + " · ".join(f"/{m}" for m in MODULES)
                       + " · /theme <light|dark> · /quit")
        elif name in ("repos", "repo"):
            self._refresh_dashboard()
        elif name == "theme":
            self.switch_theme(args[0] if args else "")
        elif name == "heartbeat":
            await self.check_heartbeat()
            self.write(f"[{'green' if self._online else 'red'}]● {self._backend()} "
                       f"{'up' if self._online else 'unreachable'}[/]")
        elif name == "login":
            if len(args) < 2:
                self.write("[red]usage: /login <user> <pass>[/red]")
            else:
                await self.login(args[0], args[1])
        elif name in MODULES:
            await self.report(name)
        else:
            self.write(f"[red]unknown command:[/red] {name}  (/help)")

    # --- backend -----------------------------------------------------------------------------------
    async def check_heartbeat(self) -> None:
        """Silently probe the current repo's backend and drive the status-bar online flag (no log line)."""
        try:
            r = await self.client.get(f"{self._backend()}/page/heartbeatJson")
            self._online = r.status_code == 200 and r.json().get("result") == "ok"
        except Exception:
            self._online = False
        self._update_statusbar()

    async def login(self, user: str, password: str) -> None:
        try:
            r = await self.client.post(f"{self._backend()}/api/auth/login",
                                       json={"username": user, "password": password})
            if r.status_code == 200:
                body = r.json()
                self.tokens[self._backend()] = body["token"]
                save_tokens(self.tokens)  # survive relaunch (until it expires)
                self.write(f"[green]logged in as {body['user']['username']} ({body['user']['role']}) "
                           f"@ {self._backend()}[/green]")
                await self._drain_pending(self._backend())  # push any notes queued while logged out
            else:
                self.write(f"[red]login failed[/red] (HTTP {r.status_code}): {self._err(r)}")
        except Exception as exc:
            self.write(f"[red]login error:[/red] {exc}")

    async def report(self, module: str) -> None:
        token = self.tokens.get(self._backend())
        if not token:
            self.write("[yellow]log in first: /login <user> <pass>[/yellow]")
            return
        try:
            r = await self.client.get(f"{self._backend()}/api/{module}",
                                      headers={"Authorization": f"Bearer {token}"},
                                      params={"size": 20, "sort": "lastUpdated,desc"})
            if r.status_code in (401, 403):
                self.tokens.pop(self._backend(), None)
                save_tokens(self.tokens)
                self.write("[yellow]session expired — /login again[/yellow]")
                return
            if r.status_code != 200:
                self.write(f"[red]{module}: HTTP {r.status_code}[/red] {self._err(r)}")
                return
            data = r.json()
            rows = data.get("content", data if isinstance(data, list) else [])
            self.write(f"[b]{module}[/b] — {data.get('totalElements', len(rows))} total, showing {len(rows)}")
            for rec in rows:
                title = rec.get("displayTitle") or rec.get("summary") or "(untitled)"
                status = (rec.get("status") or {}).get("name", "")
                self.write(f"  [cyan]#{rec.get('id')}[/cyan]  {shape(str(title))}"
                           + (f"  [dim]{shape(status)}[/dim]" if status else ""))
        except Exception as exc:
            self.write(f"[red]{module} error:[/red] {exc}")


if __name__ == "__main__":
    KnotesTUI().run()
