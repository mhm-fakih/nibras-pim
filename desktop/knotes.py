#!/usr/bin/env python3
"""knotes — a small GTK3 note taker (Linux desktop port of the knotes Android homepage).

Notes are plain-text files whose names encode their tags (see ``notes.py``). The UI is a
chronological list of note cards with a tagging input bar at the bottom; clicking a card
loads it for editing. Everything is reachable from the keyboard — see the Help dialog (F1).

Run it with:  python3 knotes.py
Needs:        PyGObject + GTK 3  (Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-3.0)
"""

from __future__ import annotations

import math
import os
import re
import subprocess
import sys
import threading
import traceback
import unicodedata
import warnings
from datetime import datetime, timedelta

import gi

gi.require_version("Gtk", "3.0")
import cairo  # noqa: E402  (pycairo — ships with PyGObject; used to draw the highlighter cursor)
from gi.repository import Gtk, Gdk, GLib, Pango, Gio  # noqa: E402

from config import Settings, RepoConfig, parse_tag_block, format_tag_block  # noqa: E402
from notes import (NoteRepository, Note, is_rtl, companion_dir, companion_files,  # noqa: E402
                   companion_has_content, parse_created, QuickFile, QuickEntry,
                   QUICK_NOTES_FILE, QUICK_TASKS_FILE, markdown_spans,
                   fmt_count, split_highlights, apply_highlights, clause_bounds,
                   parse as parse_name)
from backend import fetch_taxonomy, import_note  # noqa: E402
from filters import FilterState, Taxonomy, default_filter, BY_CREATED, NS_INCLUDE, NS_ONLY, TX_INCLUDE, TX_ONLY  # noqa: E402
import tagbank  # noqa: E402
from sidebar import FilterSidebar  # noqa: E402

# A development copy (run-dev.sh sets KNOTES_DEV=1) registers its own application ID. GTK apps are
# single-instance per ID, so with a shared one, launching the dev copy while the installed one is
# open would just raise the installed window — and you'd be testing the old code without noticing.
DEV_MODE = os.environ.get("KNOTES_DEV") == "1"
APP_ID = "org.knotes.desktop.dev" if DEV_MODE else "org.knotes.desktop"
HERE = os.path.dirname(os.path.abspath(__file__))

# Platform switches for the handful of places that shell out (open a file / reveal a folder).
IS_WINDOWS = os.name == "nt"
IS_MACOS = sys.platform == "darwin"
DEFAULT_OPENER = "start" if IS_WINDOWS else ("open" if IS_MACOS else "xdg-open")

# The name the app calls itself, everywhere a person reads it. The *identifiers* stay "knotes" on
# purpose — the module names, knotes.svg, the wmclass, GLib.set_prgname, the config file's location
# next to the program — because renaming those would orphan an installed launcher and the settings
# file beside it. Display name and identity are allowed to differ; that's what this constant is for.
APP_NAME = "Nibras Notes (dev)" if DEV_MODE else "Nibras Notes"

VERSION = "1.7.0"


def _data_dir() -> str:
    """Directory for writable runtime files that must persist across launches. When frozen by
    PyInstaller, HERE points into the ephemeral _MEIPASS extraction dir (wiped on exit), so use the
    executable's directory instead; otherwise it's the script directory. (Read-only bundled
    resources like style.css / knotes.svg are still loaded from HERE / _MEIPASS.)"""
    if getattr(sys, "frozen", False):
        return os.path.dirname(os.path.abspath(sys.executable))
    return HERE


def _bump_build_number() -> int:
    """Increment and return the launch/build counter, persisted next to the program (gitignored)."""
    path = os.path.join(_data_dir(), "build-number.txt")
    try:
        n = int(open(path, encoding="utf-8").read().strip()) + 1
    except (OSError, ValueError):
        n = 1
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(str(n))
    except OSError:
        pass
    return n


BUILD_NUMBER = _bump_build_number()

# Tag colours for the inline card chips (Pango markup). These match the .badge.* CSS and are the
# same in both themes (the accent/violet/cyan are theme-independent).
TAG_COLOR_PRIO = "#6c71c5"  # violet
TAG_COLOR_TYPE = "#268bd2"  # accent

# The week calendar view. Its notes are the journal and planner types — matched by code, so a
# folder that names them differently still gets them; the names here are only the fallback for a
# folder whose tag set doesn't define the code at all.
CALENDAR_TYPES = {"J": "Journal", "P": "Planner"}
WEEK_SLOT_MINUTES = 30     # one row per half hour, as Outlook's default
WEEK_SCROLL_HOUR = 7       # the hour a freshly opened week is scrolled to
WEEK_GUTTER = 52           # width of the time column, px
WEEK_TOOLTIP_LINES = 40    # an entry's hover shows its note up to this many lines
TAG_COLOR_CAT = "#2aa198"   # cyan
TAG_COLOR_HASH = "#859900"  # green — inline #hashtags pulled from the note body

# A #hashtag: a '#' (not mid-word) followed by word characters (Unicode, so Arabic tags work too),
# then any run of word characters, dots and hyphens — the tag bank's vocabulary is dotted
# (`A.quran`) and its aliases hyphenated (`آية-اليوم`), and a tag the completer inserts has to be
# one the board can see. Kept in step with filters.TAG_RE, which does the same job for the sidebar.
_HASHTAG_RE = re.compile(r"(?<!\w)#(\w[\w.\-]*)", re.UNICODE)


def _hashtags(text: str) -> list[str]:
    """Distinct hashtags in [text], in first-seen order, compared case-insensitively."""
    out: list[str] = []
    seen: set[str] = set()
    for m in _HASHTAG_RE.finditer(text):
        # Trailing dots/hyphens belong to the sentence, not the tag: "…see #A.quran." ends in a
        # full stop, and "#A.quran" and "#A.quran." must not become two different tags.
        tag = m.group(1).rstrip(".-")
        if tag and tag.lower() not in seen:
            seen.add(tag.lower())
            out.append(tag)
    return out


# Gap (px) between the cards of the "columns" view, in both directions. It's part of the width a
# column occupies, so _column_count has to account for it or the last column won't fit. BOARD_BORDER
# is the board's own inset, which _column_count subtracts for the same reason.
COLUMN_GAP = 8
BOARD_BORDER = 8
# Width held back for the board's vertical scrollbar when working out how many columns fit. A
# constant, not the scrollbar's measured width: the scrollbar only exists once the content
# overflows, which depends on the very column count being computed.
SCROLLBAR_ALLOWANCE = 16

# The note editor grows with its content between these heights (px), then scrolls vertically.
EDITOR_MIN_HEIGHT = 36
EDITOR_MAX_HEIGHT = 180

class TagCompleter:
    """Hashtag autocomplete for one Gtk.TextView, offering the tag bank after a ``#``.

    A borderless popup window under the caret, listing matches from ``tagbank``. It never takes the
    keyboard (``set_accept_focus(False)``): the keystrokes must keep reaching the text view, so the
    list follows along beside the typing rather than taking the input for itself.

    A GtkPopover would be the modern spelling and was tried first — it reports itself visible and
    mapped, with a sensible allocation, and then draws nothing at all when its ``relative_to`` is a
    GtkTextView nested this deep. A POPUP window is its own X window: it is either on the screen or
    it is not, which is also what makes it verifiable from a screenshot.

    Picking a match replaces what was typed with the **canonical** tag — an alias is a way to find
    a tag, never a tag to write. That, and not the saved keystrokes, is what stops a vocabulary
    fraying into three spellings of the same idea.

    The keys are not handled here: this window's own key handler runs before the focused widget's
    (see _on_key_press), so it drives the list and calls in here. One class per view, so the pane's
    editor and the add field each get their own popup and their own state.
    """

    ROW_LIMIT = 12

    def __init__(self, window: "KnotesWindow", view: Gtk.TextView):
        self.window = window
        self.view = view
        self.matches: list[tagbank.Match] = []
        self.start_offset: int | None = None   # offset of the '#', while a completion is open

        self.list = Gtk.ListBox()
        self.list.set_selection_mode(Gtk.SelectionMode.BROWSE)
        self.list.get_style_context().add_class("tag-complete")
        self.list.connect("row-activated", lambda _l, row: self.accept(row.get_index()))
        self.scroller = scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.set_max_content_height(240)
        scroller.set_propagate_natural_height(True)
        scroller.add(self.list)
        frame = Gtk.Frame()
        frame.get_style_context().add_class("tag-complete-frame")
        frame.add(scroller)
        self.popup = Gtk.Window(type=Gtk.WindowType.POPUP)
        self.popup.set_type_hint(Gdk.WindowTypeHint.COMBO)
        self.popup.set_accept_focus(False)   # the caret stays in the note being written
        self.popup.set_transient_for(window)
        self.popup.set_default_size(POPUP_WIDTH, -1)
        self.popup.add(frame)

        view.get_buffer().connect("changed", lambda *_a: self.refresh())
        view.connect("focus-out-event", lambda *_a: (self.hide(), False)[1])

    # ------------------------------------------------------------ state

    @property
    def active(self) -> bool:
        return self.start_offset is not None

    def hide(self) -> None:
        self.start_offset = None
        self.matches = []
        self.popup.hide()

    def refresh(self) -> None:
        """Re-read what's being typed and show, update, or hide the list. Driven by the buffer's
        own "changed", so it tracks deletions and pastes as well as typing."""
        typed = self._typed()
        if typed is None:
            self.hide()
            return
        offset, prefix = typed
        self.matches = tagbank.suggest(self.window.tag_bank(), prefix, self.ROW_LIMIT)
        if not self.matches:
            self.hide()
            return
        self.start_offset = offset
        self._fill()
        self.popup.show_all()
        # Shrink back to what the rows actually need. A GtkWindow keeps whatever size it was last
        # given, so without this the list stays as tall as the longest match set it has ever shown
        # — twelve rows' worth of empty box under the two that are left as the query narrows.
        self.popup.resize(POPUP_WIDTH, 1)
        self._place()

    def _typed(self) -> tuple[int, str] | None:
        """``(offset of the '#', text typed after it)`` when the caret sits in a hashtag being
        written, else None.

        Only the current line is examined, and only back to the last ``#``. A ``#`` in the middle
        of a word (``C#``, ``rgb#fff``) doesn't start a tag, and neither does one with whitespace
        after it — that's a markdown heading, or a tag already finished.
        """
        buffer = self.view.get_buffer()
        caret = buffer.get_iter_at_mark(buffer.get_insert())
        line_start = caret.copy()
        line_start.set_line_offset(0)
        text = buffer.get_text(line_start, caret, False)
        hash_at = text.rfind("#")
        if hash_at == -1:
            return None
        if hash_at > 0 and (text[hash_at - 1].isalnum() or text[hash_at - 1] == "#"):
            return None
        prefix = text[hash_at + 1:]
        if len(prefix) > 40 or any(c.isspace() for c in prefix):
            return None
        return caret.get_offset() - len(prefix) - 1, prefix

    # ------------------------------------------------------------ view

    def _fill(self) -> None:
        for child in self.list.get_children():
            self.list.remove(child)
        for match in self.matches:
            label = Gtk.Label(xalign=0)
            shown = GLib.markup_escape_text(match.canonical)
            if match.via:
                # The alias is what the user typed; showing it is what explains why a Latin
                # canonical tag turned up under an Arabic query.
                shown += f'   <span alpha="55%">{GLib.markup_escape_text(match.via)}</span>'
            label.set_markup(shown)
            row = Gtk.ListBoxRow()
            row.add(label)
            self.list.add(row)
        self.list.show_all()
        first = self.list.get_row_at_index(0)
        if first is not None:
            self.list.select_row(first)

    def _place(self) -> None:
        """Move the popup just under the caret, in root coordinates.

        Three spaces are involved and they are easy to confuse: the caret's position is in BUFFER
        coordinates, which scroll; converting to TEXT-window coordinates undoes the scrolling; and
        the window's own origin turns that into the screen position the popup needs. Flipped above
        the caret when it would otherwise run off the bottom of the screen.
        """
        text_window = self.view.get_window(Gtk.TextWindowType.TEXT)
        if text_window is None:
            return
        buffer = self.view.get_buffer()
        caret = self.view.get_iter_location(buffer.get_iter_at_mark(buffer.get_insert()))
        wx, wy = self.view.buffer_to_window_coords(Gtk.TextWindowType.TEXT, caret.x, caret.y)
        _ok, origin_x, origin_y = text_window.get_origin()
        x, y = origin_x + wx, origin_y + wy + max(caret.height, 16)

        _min, natural = self.popup.get_preferred_height()
        screen = self.view.get_display().get_monitor_at_window(text_window).get_workarea()
        if y + natural > screen.y + screen.height:
            y = max(screen.y, origin_y + wy - natural)     # no room below — sit above the caret
        x = min(x, screen.x + screen.width - POPUP_WIDTH)
        self.popup.move(x, y)
        # Raised explicitly. An override-redirect window is normally stacked on top when it maps,
        # but this one maps while the main window is being clicked and typed into, and the window
        # manager raises *that* — leaving the list mapped, correctly sized, correctly placed, and
        # entirely behind the note it belongs to. It looks exactly like a list that never appeared.
        gdk_window = self.popup.get_window()
        if gdk_window is not None:
            gdk_window.raise_()

    # ------------------------------------------------------------ keys

    def move(self, delta: int) -> None:
        row = self.list.get_selected_row()
        index = (row.get_index() if row is not None else 0) + delta
        index = max(0, min(index, len(self.matches) - 1))
        target = self.list.get_row_at_index(index)
        if target is None:
            return
        self.list.select_row(target)
        # Scrolled by hand rather than with grab_focus: focus has to stay in the text view, or the
        # next character typed goes into the list instead of the note.
        adjustment = self.scroller.get_vadjustment()
        if adjustment is None:
            return
        box = target.get_allocation()
        if box.y < adjustment.get_value():
            adjustment.set_value(box.y)
        elif box.y + box.height > adjustment.get_value() + adjustment.get_page_size():
            adjustment.set_value(box.y + box.height - adjustment.get_page_size())

    def accept(self, index: int | None = None) -> None:
        """Replace the typed ``#prefix`` with the canonical tag, and a space to carry on after."""
        if not self.active:
            return
        if index is None:
            row = self.list.get_selected_row()
            index = row.get_index() if row is not None else 0
        if not 0 <= index < len(self.matches):
            self.hide()
            return
        canonical = self.matches[index].canonical
        buffer = self.view.get_buffer()
        start = buffer.get_iter_at_offset(self.start_offset)
        self.hide()          # before the edit: the resulting "changed" must not reopen the list
        buffer.delete(start, buffer.get_iter_at_mark(buffer.get_insert()))
        buffer.insert_at_cursor(f"#{canonical} ")


# The highlighter-pen cursor used in the pane's reading mode: canvas size (px, logical) and the
# hotspot, which is the nib tip — the point that will be "written" with, not the middle of the icon.
# Width (px) of the '#' completer's popup list.
POPUP_WIDTH = 280

# A card shows when the note was created and when it was last touched. When those drift apart the
# modification date is coloured: a note still being edited a week after it was captured is either
# a live document or something that quietly outgrew being a note, and either way it's worth seeing
# from across the board. Solarized orange/red, matching the tag palette above.
STALE_WARN_DAYS = 3    # more than this many days apart → orange
STALE_ALERT_DAYS = 7   # more than this → red
STALE_WARN_COLOR = "#cb4b16"
STALE_ALERT_COLOR = "#dc322f"

PEN_SIZE = 28
PEN_HOTSPOT = (5, 23)


def _draw_highlighter(cr: cairo.Context, nib: Gdk.RGBA) -> None:
    """Draw a marker pen leaning up-and-right from its nib at PEN_HOTSPOT.

    The nib (and a band on the barrel) is painted in the colour the click will apply, so the pointer
    answers "what will this do, and in what colour" without anything having to be clicked. Every
    shape is laid down over a heavy dark outline first, which is what keeps the pen visible against
    a light page and a dark one alike — a cursor has no control over what it will be seen on.
    """
    cr.translate(*PEN_HOTSPOT)
    cr.rotate(math.pi / 4)

    def nib_path() -> None:
        cr.move_to(0, 0)
        cr.line_to(-3.2, -4.5)
        cr.line_to(3.2, -4.5)
        cr.close_path()

    def body_path() -> None:
        cr.rectangle(-3.6, -17.5, 7.2, 13.0)

    cr.set_source_rgba(0, 0, 0, 0.85)
    cr.set_line_width(2.6)
    nib_path()
    cr.stroke()
    body_path()
    cr.stroke()

    cr.set_line_width(1.0)
    cr.set_source_rgb(nib.red, nib.green, nib.blue)
    nib_path()
    cr.fill()
    cr.set_source_rgb(0.93, 0.93, 0.95)
    body_path()
    cr.fill()
    cr.set_source_rgba(0, 0, 0, 0.55)
    body_path()
    cr.stroke()
    cr.set_source_rgb(nib.red, nib.green, nib.blue)
    cr.rectangle(-3.6, -8.5, 7.2, 3.0)
    cr.fill()
    cr.set_source_rgba(0, 0, 0, 0.55)
    cr.rectangle(-3.6, -8.5, 7.2, 3.0)
    cr.stroke()


def _rgba_hex(colour: Gdk.RGBA) -> str:
    """A Gdk.RGBA as ``#rrggbb`` — the one colour spelling both Pango markup and Gdk accept."""
    return "#{:02x}{:02x}{:02x}".format(
        round(colour.red * 255), round(colour.green * 255), round(colour.blue * 255))


# Narrowest the side editor pane may be dragged (px) — below this a wrapped note is unreadable.
EDITOR_PANE_MIN = 240
# Quick-capture strip under the pane: a floor low enough that it can be dragged down to a couple of
# rows, and the same settle delay the editor width uses (see _relax_editor_width for the why).
QUICK_PANELS_MIN_H = 84
# How long (ms) the pane's remembered width is enforced as a size request before being relaxed back
# to EDITOR_PANE_MIN. Only has to outlast the layout pass that follows showing the pane.
EDITOR_PANE_SETTLE_MS = 300

# How long (ms) a restored scroll offset is held against the layout that follows a board rebuild
# (see _pin_scroll). Long enough to outlast measuring + the focus scroll, short enough that it can
# never fight the user's own next scroll.
SCROLL_PIN_MS = 350


def _without_hashtags(runs: list[tuple[str, bool]]) -> list[tuple[str, bool]]:
    """[runs] with every #hashtag removed, spaces collapsed and lines left empty dropped.

    Works on (character, highlighted) pairs, like Note.card_preview, so a highlight survives the
    cut. The tag itself is matched by _HASHTAG_RE, the same rule _hashtags uses to list them."""
    text = "".join(t for t, _h in runs)
    flags = [h for t, h in runs for _c in t]
    cut = [False] * len(text)
    for m in _HASHTAG_RE.finditer(text):
        for i in range(*m.span()):
            cut[i] = True
    lines: list[list[tuple[str, bool]]] = [[]]
    for c, f, x in zip(text, flags, cut):
        if x:
            continue
        if c == "\n":
            lines.append([])
        elif not (c.isspace() and (not lines[-1] or lines[-1][-1][0].isspace())):
            lines[-1].append((c, f))
    pairs: list[tuple[str, bool]] = []
    for line in lines:
        while line and line[-1][0].isspace():
            line.pop()
        if line:
            if pairs:
                pairs.append(("\n", False))
            pairs.extend(line)
    runs_out: list[tuple[str, bool]] = []
    for c, f in pairs:
        if runs_out and runs_out[-1][1] == f:
            runs_out[-1] = (runs_out[-1][0] + c, f)
        else:
            runs_out.append((c, f))
    return runs_out


def _split_first_line(runs: list[tuple[str, bool]]):
    """(first line, the rest) of [runs], the shape Note.card_preview returns."""
    for i, (t, f) in enumerate(runs):
        if "\n" in t:
            a, b = t.split("\n", 1)
            head = runs[:i] + ([(a, f)] if a else [])
            rest = ([(b, f)] if b else []) + runs[i + 1:]
            return head, rest
    return runs, []


def _opens_rtl(markup: str) -> bool:
    """True when the first strong letter of [markup]'s visible text is right-to-left."""
    for c in re.sub(r"<[^>]*>", "", markup):
        kind = unicodedata.bidirectional(c)
        if kind in ("R", "AL"):
            return True
        if kind == "L":
            return False
    return False


def _tag_span(text: str, color: str) -> str:
    """An inline, badge-like Pango span: bold coloured text on a faint tint of the same colour, with
    surrounding spaces for a little horizontal padding.

    Still used where the chips have to sit INSIDE a run of text (tooltips, the one-line hashtag
    index). Where they stand on their own, _tag_chip gives a real widget instead — Pango paints a
    background run as a hard rectangle and has no corner radius at all."""
    t = GLib.markup_escape_text(text)
    return f'<span fgcolor="{color}" bgcolor="{color}" bgalpha="18%" weight="bold">  {t}  </span>'


def _tag_chip(text: str, color: str) -> Gtk.Label:
    """One tag chip as a widget: same bold-on-a-tint look as _tag_span, but with rounded corners,
    which only CSS can give it. The colour varies per chip (type / priority / category / hashtag)
    and CSS can't be parameterised, so the two colour properties are set inline through a provider
    and the shape comes from the .tag-pill class."""
    label = Gtk.Label(label=text)
    label.get_style_context().add_class("tag-pill")
    provider = Gtk.CssProvider()
    provider.load_from_data(
        f"label {{ color: {color}; background-color: alpha({color}, 0.18); }}".encode())
    label.get_style_context().add_provider(provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
    return label


# Colour blocks prepended to style.css at runtime. Only these named colours change between themes;
# the structural rules in style.css reference them and never name a literal colour.
#
# TWO FOREGROUND FAMILIES, deliberately. The plain tokens (@bg/@fg/@card_bg…) dress the WORK AREA —
# board, cards, editor pane, add form — and the @chrome_* ones dress the surrounding furniture:
# header bar, toolbar, status bar and the whole filter sidebar. They are separate so a theme can
# make the two halves different, which "light" now does: brand-navy chrome around a light board.
# A theme that wants one flat surface just sets @chrome_bg to @bg_alt's value, as "dark" does.
THEMES = {
    "dark": """
        @define-color bg        #002b36;
        @define-color bg_alt    #073642;
        @define-color card_bg   #073642;
        @define-color fg        #93a1a1;
        @define-color fg_strong #fdf6e3;
        @define-color fg_muted  #657b83;
        @define-color accent    #268bd2;
        @define-color accent_fg #fdf6e3;
        @define-color chip_bg   #0a4a59;
        @define-color chip_fg   #93a1a1;
        @define-color chip_hover #0e5b6e;
        @define-color violet    #6c71c5;
        @define-color cyan      #2aa198;
        /* Chrome == work area here: one flat slate, exactly as before. */
        @define-color chrome_bg        #073642;
        @define-color chrome_fg        #93a1a1;
        @define-color chrome_fg_strong #fdf6e3;
        @define-color chrome_fg_muted  #657b83;
    """,
    "light": """
        /* Solarized parchment, shared with the sibling audio-player app
           so the two apps read as one set: light beige under the notes, the DARK beige on the
           furniture around them. The two are deliberately close — Solarized separates base3 from
           base2 by a hair, and the borders do the rest of the work. */
        @define-color bg        #fdf6e3;
        @define-color bg_alt    #eee8d5;
        @define-color card_bg   #fdf6e3;
        @define-color fg        #586e75;
        @define-color fg_strong #002b36;
        @define-color fg_muted  #93a1a1;
        @define-color accent    #268bd2;
        @define-color accent_fg #fdf6e3;
        @define-color chip_bg   #eee8d5;
        @define-color chip_fg   #586e75;
        @define-color chip_hover #e3dcc4;
        @define-color violet    #6c71c5;
        @define-color cyan      #2aa198;
        @define-color chrome_bg        #eee8d5;
        @define-color chrome_fg        #586e75;
        @define-color chrome_fg_strong #002b36;
        @define-color chrome_fg_muted  #93a1a1;
    """,
}

# Per-folder chrome tint, so a Ctrl+1/2/3 switch is visible at a glance: the header bar, toolbar,
# sidebar and status bar take the folder's colour while the board keeps the theme's own (readable)
# surface. Set per folder in Settings ("Folder colours", stored as Settings.repo_colors by code).
# A folder with no colour set gets one of these light defaults by its position in the picker; the
# first folder wears the theme unchanged, and folders past the end of the list cycle.
REPO_CHROME_DEFAULTS = ["", "#d6e4f5", "#d8eedd", "#eadcf0", "#f5e6cc"]


def repo_color(settings: Settings, code: str) -> str:
    """The chrome colour for folder [code]: its own setting, else the positional default ("" = none)."""
    if code in settings.repo_colors:
        return settings.repo_colors[code]
    codes = [c for c, _ in settings.repos()]
    index = codes.index(code) if code in codes else 0
    return REPO_CHROME_DEFAULTS[index % len(REPO_CHROME_DEFAULTS)] if index > 0 else ""


def repo_chrome_css(colour: str) -> str:
    """@chrome_* overrides for a chrome painted [colour], or "" for none. The text colours follow
    the background's lightness, so a light pick gets dark text and a dark pick light text."""
    rgba = Gdk.RGBA()
    if not colour or not rgba.parse(colour):
        return ""
    luminance = 0.2126 * rgba.red + 0.7152 * rgba.green + 0.0722 * rgba.blue
    if luminance > 0.5:
        fg, strong, muted = "#3d4f56", "#002b36", "#6c7d83"
    else:
        fg, strong, muted = "#d4dde2", "#ffffff", "#9aaab1"
    return (f"@define-color chrome_bg {_rgba_hex(rgba)};\n@define-color chrome_fg {fg};\n"
            f"@define-color chrome_fg_strong {strong};\n@define-color chrome_fg_muted {muted};\n")


class MonthPicker(Gtk.Box):
    """A month of day buttons, Monday first — the entry form's date picker.

    Not Gtk.Calendar: that takes its first weekday from the system locale (en_US starts on Sunday)
    and GTK3 offers no per-widget override, while the week view, the sidebar calendar and the user
    all count Monday to Sunday. ``selected`` is the picked day (midnight); ``on_pick`` is called
    with it on every click.
    """

    def __init__(self, initial: datetime, on_pick=None):
        super().__init__(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        self.set_border_width(8)
        self.selected = initial.replace(hour=0, minute=0, second=0, microsecond=0)
        self._month = self.selected.replace(day=1)
        self._on_pick = on_pick
        nav = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        for icon, step, tip in (("go-previous-symbolic", -1, "Previous month"),
                                ("go-next-symbolic", 1, "Next month")):
            btn = Gtk.Button()
            btn.add(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.BUTTON))
            btn.set_relief(Gtk.ReliefStyle.NONE)
            btn.set_tooltip_text(tip)
            btn.connect("clicked", lambda _b, k=step: self._shift(k))
            (nav.pack_start if step < 0 else nav.pack_end)(btn, False, False, 0)
        self._title = Gtk.Label()
        nav.set_center_widget(self._title)
        self.pack_start(nav, False, False, 0)
        self._grid = Gtk.Grid(row_spacing=2, column_spacing=2, column_homogeneous=True)
        self.pack_start(self._grid, False, False, 0)
        self._fill()

    def _shift(self, months: int) -> None:
        y, m = divmod(self._month.year * 12 + self._month.month - 1 + months, 12)
        self._month = self._month.replace(year=y, month=m + 1)
        self._fill()

    def _fill(self) -> None:
        for child in self._grid.get_children():
            self._grid.remove(child)
        self._title.set_markup(f"<b>{self._month:%B %Y}</b>")
        for col, name in enumerate(("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")):
            head = Gtk.Label(label=name)
            head.get_style_context().add_class("dim-label")
            self._grid.attach(head, col, 0, 1, 1)
        today = datetime.now().date()
        day = self._month - timedelta(days=self._month.weekday())   # the Monday on or before the 1st
        for row in range(1, 7):
            for col in range(7):
                btn = Gtk.ToggleButton(label=str(day.day))
                btn.set_relief(Gtk.ReliefStyle.NONE)
                btn.set_active(day == self.selected)
                if day.month != self._month.month:
                    btn.get_child().get_style_context().add_class("dim-label")
                if day.date() == today:
                    btn.get_child().set_markup(f"<u><b>{day.day}</b></u>")
                btn.connect("clicked", lambda _b, d=day: self._pick(d))
                self._grid.attach(btn, col, row, 1, 1)
                day += timedelta(days=1)
        self._grid.show_all()

    def _pick(self, day: datetime) -> None:
        self.selected = day
        if day.month != self._month.month:
            self._month = day.replace(day=1)
        GLib.idle_add(lambda: self._fill() and False)   # not from inside the clicked button's own signal
        if self._on_pick is not None:
            self._on_pick(day)


class KnotesWindow(Gtk.ApplicationWindow):
    def __init__(self, app: "KnotesApp"):
        super().__init__(application=app, title=APP_NAME)
        # Start at the minimal width the layout needs (compact, focused note-taking) rather than
        # maximized: a width of 1 makes GTK fall back to the natural minimum; the height stays tall.
        self.set_default_size(1, 780)
        self.set_position(Gtk.WindowPosition.CENTER)

        # A stable WM_CLASS so a Cinnamon panel launcher (StartupWMClass=knotes) groups this
        # window under the pinned icon. Must be set before the window is realized. The call is
        # deprecated but still honoured on X11; silence just its deprecation warning.
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", DeprecationWarning)
                self.set_wmclass("knotes", "knotes")
        except Exception:
            pass
        icon_path = os.path.join(HERE, "knotes.svg")
        if os.path.exists(icon_path):
            self.set_icon_from_file(icon_path)

        self.settings = app.settings
        self.repo = NoteRepository(self.settings.active_repo_path())
        # Starter: give every configured folder its own tag config seeded from the current global
        # settings (once), so each repo begins with the same options and can then diverge.
        self._seed_repo_configs()
        # Tag options (types/categories/priorities) are per-repository, loaded from a .knotes.json
        # inside the active folder (seeded from the global settings the first time).
        self.repo_cfg = RepoConfig.load(self.settings.active_repo_path(), self.settings)
        self._suppress_repo = False  # guards programmatic repo-combo updates

        # Tagging state for the new-note input bar (sticky between notes).
        self.selected_type: str | None = None
        self.selected_category: str | None = None
        self.priority: int = self.repo_cfg.priority_min
        self._suppress_toggle = False  # guards programmatic chip updates

        # Chip widgets, kept so shortcuts can drive them. Ordered lists give the 1..9 indices.
        self.type_buttons: dict[str, Gtk.ToggleButton] = {}
        self.category_buttons: dict[str, Gtk.ToggleButton] = {}
        self.priority_buttons: dict[int, Gtk.ToggleButton] = {}
        self.type_codes: list[str] = []
        self.category_codes: list[str] = []
        # Collapsed kanban subgroups, keyed by (category_code, type_code).
        self._collapsed_groups: set[tuple[str, str]] = set()

        # Card selection / board-navigation state.
        # _grid[grid_row][grid_col] = list of (Note, card_widget) for that column, in visual order.
        self._grid: list[list[list[tuple[Note, Gtk.Widget]]]] = []
        self.selected_note: Note | None = None
        self._sel: tuple[int, int, int] | None = None       # (grid_row, grid_col, card_index)
        self._sel_path: str | None = None                   # path of the selected note (survives reload)
        self._selected_widget: Gtk.Widget | None = None
        self._board_active = False                          # True while navigating cards (not typing)
        self._scroll_pin: list[tuple[object, int]] = []     # (object, handler_id) held by _pin_scroll
        self._scroll_pin_timeout: int | None = None         # its release timer
        # Week calendar view: the Monday (midnight) of the week on screen, and the hour grid's
        # scroll offset — kept across the reloads a save or a folder change triggers, so the grid
        # doesn't jump back to the morning under the pointer. None = scroll to WEEK_SCROLL_HOUR.
        self._week_start = self._monday_of(datetime.now())
        self._week_scroll: float | None = None
        self.minimal_mode = self.settings.minimal_mode      # side-note mode (Ctrl+L), overlays view_mode
        self._pre_minimal_size: tuple[int, int] | None = None   # window size to restore on exit
        self._suppress_minimal_toggle = False                   # guards the header toggle button
        self._minimal_card_selected = False                     # the lone minimal-mode card is selected
        self._last_note: Note | None = None                     # the "just added" note (minimal card)
        self._last_note_card: Gtk.Widget | None = None          # its card widget
        self._merge_marks: list[str] = []                       # paths marked to merge, in mark order
        self._unrecognized_paths: set[str] = set()               # non-conforming items, "needs metadata"
        self._append_entry: Gtk.Entry | None = None             # inline "quick append" field (Space)
        self._append_note: Note | None = None                   # note the append field is editing

        # Left filter sidebar (Ctrl+B). The state lives here because reload_notes filters with it;
        # the sidebar widget only drives it. Which options are picked is per-session (see
        # Settings.sidebar_visible for what IS persisted) — only the heat-map metric is restored.
        self.filters = FilterState(date_field=self.settings.heatmap_metric)
        # Open on today's and yesterday's captures (Settings.start_filter_today) — the same state a
        # shift-click range in the calendar produces, so the days read as selected in the sidebar
        # and the sidebar's "Clear all filters" drops back to the whole board.
        self._apply_start_filter()
        # Each folder's sidebar picks, stashed when it is switched away from and restored on return
        # (per-session, keyed by repo code). See _on_repo_changed.
        self._repo_filters: dict[str, FilterState] = {}
        self.tax = Taxonomy()                    # the active folder's options, see _refresh_taxonomy
        # "columns" view: how many columns the last render laid out, so a resize can re-flow.
        self._columns_ncols = 0
        self._reflow_pending = False

        self._build_ui()
        self._refresh_taxonomy()
        self.connect("key-press-event", self._on_key_press)
        # When the window gains focus, drop the cursor into the add field so a paste is ready.
        self.connect("focus-in-event", self._on_focus_in)
        # Save any unsaved draft in the text field when the window is closed (no lost notes).
        self.connect("delete-event", self._on_delete_event)
        self.reload_notes()
        self._apply_defaults()
        self._apply_minimal_mode()
        # Restore the pane's open/closed state without re-saving it or taking the cursor — the add
        # field keeps the focus on launch whether the pane is up or not.
        self._set_editor_pane(self.settings.editor_pane_visible, persist=False, focus=False)
        # …and the quick strip inside it, likewise without re-saving what was restored.
        self._set_quick_panels(self.settings.quick_panels_visible, persist=False)
        self._setup_layout_watch()
        self._setup_note_dir_watch()
        self._sync_backend_taxonomy()  # best-effort; offline/misconfigured → keeps current codes

    # ------------------------------------------------------------------ UI

    def _build_ui(self) -> None:
        # Row 1 — the native title bar: app name + build + live stats (note count / merge hint)
        # only. All the actual controls live in their own toolbar row below it (see `self.toolbar`)
        # instead of crowding into the title bar, so each row has one job.
        self.header = Gtk.HeaderBar(
            title=f'{APP_NAME} v{VERSION}.b{BUILD_NUMBER}', show_close_button=True)
        self.set_titlebar(self.header)

        root = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.add(root)

        # Row 2 — the toolbar: every view/action control, left-to-right in the same order they used
        # to appear (right-to-left) packed into the title bar.
        self.toolbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        # Spacing comes from CSS padding, NOT set_border_width: a GtkBox insets its CSS background
        # by its border width, so a bar with one paints short of its own allocation and the window
        # colour shows through as a frame around it — invisible while both were parchment, glaring
        # the moment the chrome stopped matching the board. Same for the status bar below.
        self.toolbar.get_style_context().add_class("toolbar")
        root.pack_start(self.toolbar, False, False, 0)

        self.search_button = Gtk.ToggleButton()
        self.search_button.add(Gtk.Image.new_from_icon_name("system-search-symbolic", Gtk.IconSize.BUTTON))
        self.search_button.set_tooltip_text("Search notes (Ctrl+F)")
        self.search_button.connect("toggled", lambda b: self._set_search(b.get_active()))
        self.toolbar.pack_start(self.search_button, False, False, 0)

        settings_button = Gtk.Button()
        settings_button.add(Gtk.Image.new_from_icon_name("emblem-system-symbolic", Gtk.IconSize.BUTTON))
        settings_button.set_tooltip_text("Settings (Ctrl+,)")
        settings_button.connect("clicked", lambda b: self._open_settings())
        self.toolbar.pack_start(settings_button, False, False, 0)

        help_button = Gtk.Button()
        help_button.add(Gtk.Image.new_from_icon_name("help-about-symbolic", Gtk.IconSize.BUTTON))
        help_button.set_tooltip_text("Keyboard shortcuts (F1)")
        help_button.connect("clicked", lambda b: self._show_help())
        self.toolbar.pack_start(help_button, False, False, 0)

        self.theme_button = Gtk.Button()
        self.theme_button.set_tooltip_text("Toggle light / dark theme (Ctrl+T)")
        self.theme_button.connect("clicked", lambda b: self._toggle_theme())
        self.toolbar.pack_start(self.theme_button, False, False, 0)
        self._refresh_theme_icon()

        # View-mode group (kanban / list / minimal) — switched often, so it lives on the toolbar
        # (not the settings dialog). Behaves like a radio: exactly one is active.
        mode_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        mode_box.get_style_context().add_class("linked")
        self.kanban_button = Gtk.ToggleButton()
        self.kanban_button.add(Gtk.Image.new_from_icon_name("view-grid-symbolic", Gtk.IconSize.BUTTON))
        self.kanban_button.set_tooltip_text("Kanban (category columns)")
        self.list_button = Gtk.ToggleButton()
        self.list_button.add(Gtk.Image.new_from_icon_name("view-list-symbolic", Gtk.IconSize.BUTTON))
        self.list_button.set_tooltip_text("Simple list")
        self.columns_button = Gtk.ToggleButton()
        self.columns_button.add(
            Gtk.Image.new_from_icon_name("view-app-grid-symbolic", Gtk.IconSize.BUTTON))
        self.columns_button.set_tooltip_text(
            "Columns — the same flat list with no headings, wrapped into as many columns as fit "
            "(card width in Settings)")
        self.week_button = Gtk.ToggleButton()
        self.week_button.add(Gtk.Image.new_from_icon_name("x-office-calendar-symbolic", Gtk.IconSize.BUTTON))
        self.week_button.set_tooltip_text(
            "Week calendar — journal (J) and planner (P) notes in their time slots; click an empty "
            "slot to add one dated then (Ctrl+PgUp / Ctrl+PgDn change week)")
        self.minimal_button = Gtk.ToggleButton()
        self.minimal_button.add(Gtk.Image.new_from_icon_name("view-restore-symbolic", Gtk.IconSize.BUTTON))
        self.minimal_button.set_tooltip_text("Minimal side-note mode (Ctrl+L)")
        for btn, mode in ((self.kanban_button, "kanban"), (self.list_button, "list"),
                          (self.columns_button, "columns"), (self.week_button, "week"),
                          (self.minimal_button, "minimal")):
            btn.connect("toggled", lambda b, m=mode: self._on_mode_button(b, m))
            mode_box.pack_start(btn, False, False, 0)
        self.toolbar.pack_start(mode_box, False, False, 0)

        # Right-hand editor pane. Its own toggle rather than a member of the view-mode group above:
        # it's orthogonal to how the board is laid out, and stays put across kanban/list/columns.
        self.editor_button = Gtk.ToggleButton()
        self.editor_button.add(Gtk.Image.new_from_icon_name("document-edit-symbolic", Gtk.IconSize.BUTTON))
        self.editor_button.set_tooltip_text(
            "Edit the selected note in the side pane (Ctrl+E) — saved as you type")
        self.editor_button.connect("toggled", self._on_editor_button)
        self.toolbar.pack_start(self.editor_button, False, False, 0)

        # Manual refresh — re-reads the folder from disk, for changes another machine wrote via
        # Syncthing (also auto-detected; see _setup_note_dir_watch). A spinning-icon click target
        # rather than text keeps the toolbar compact.
        self.refresh_button = Gtk.Button()
        self.refresh_button.add(Gtk.Image.new_from_icon_name("view-refresh-symbolic", Gtk.IconSize.BUTTON))
        self.refresh_button.set_tooltip_text("Refresh from disk (Ctrl+R) — pick up notes synced from another machine")
        self.refresh_button.connect("clicked", lambda b: self.reload_notes())
        self.toolbar.pack_start(self.refresh_button, False, False, 0)

        # Card ordering — also switched often, so it's a toolbar dropdown. "Date created" is stable
        # across metadata-only edits (its sort key never changes), unlike "Last updated" — pick it
        # for a mass-tagging pass so cards don't relocate as you go.
        self.sort_selector = Gtk.ComboBoxText()
        for code, label in (("datetime", "Last updated"), ("created", "Date created"),
                            ("category", "Category"), ("type", "Type"), ("priority", "Priority"),
                            ("length", "Note length")):
            self.sort_selector.append(code, label)
        SORT_CODES = ("datetime", "created", "category", "type", "priority", "length")
        self.sort_selector.set_active_id(
            self.settings.sort_by if self.settings.sort_by in SORT_CODES else "datetime")
        self.sort_selector.set_tooltip_text("Sort cards by")
        self.sort_selector.connect("changed", self._on_sort_selected)
        self.toolbar.pack_start(self.sort_selector, False, False, 0)

        # Auto-open the just-saved note's files folder (the Ctrl+O action) — for capture sessions
        # where every record gets files dropped in right after it's written.
        self.auto_folder_button = Gtk.ToggleButton()
        self.auto_folder_button.add(Gtk.Image.new_from_icon_name("folder-open-symbolic", Gtk.IconSize.BUTTON))
        self.auto_folder_button.set_tooltip_text(
            "Auto-open the note's files folder after each save (to drop its files in)")
        self.auto_folder_button.set_active(self.settings.auto_open_folder)
        self.auto_folder_button.connect("toggled", self._on_auto_folder_toggled)
        self.toolbar.pack_start(self.auto_folder_button, False, False, 0)

        # The main split, and the only part of `root` that expands. The filter sidebar runs the full
        # height of it on the left; everything that acts on notes — search, board, "just added", the
        # add form — is stacked in the right pane, so the sidebar sits beside the form rather than
        # stopping above it. Only the toolbar (global actions) and the status bar (which folder
        # you're in) stay full-width, above and below.
        content = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        self.sidebar = FilterSidebar(self.filters, self.settings, self._on_filters_changed)
        content.pack_start(self.sidebar, False, False, 0)
        right = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        # A GtkPaned so the editor pane can be dragged wider — it's a writing surface, and how much
        # room a note needs is the user's call, not a number picked here. pack2(resize=False) keeps
        # the split where they put it when the window is resized: the board absorbs the change.
        self.main_paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        self.main_paned.pack1(right, True, False)
        self.main_paned.pack2(self._build_editor_pane(), False, False)
        content.pack_start(self.main_paned, True, True, 0)
        root.pack_start(content, True, True, 0)

        # Search bar (hidden until toggled).
        self.search_bar = Gtk.SearchBar()
        self.search_entry = Gtk.SearchEntry()
        self.search_entry.set_width_chars(30)
        # Says what it searches, because "filenames too" is not a guessable feature and the tag
        # block only lives in the name — nothing on the card would hint that "n~p2" is a valid query.
        self.search_entry.set_placeholder_text("Search text or file name, then Enter…")
        self.search_entry.set_tooltip_text(
            "Press Enter to search. Matches note text, the type/category codes, and the file name —\n"
            "any part of it. So a whole name (2026_08_17-101705~N~p2~I) finds one note, and a\n"
            "fragment (N~p2) lists every note tagged that way. Clearing the box shows everything again.")
        # The query is applied on Enter, not per keystroke: every reload re-reads the folder and
        # rebuilds every card widget, and doing that for each typed letter — most of which match
        # hundreds of notes — made the entry lag and pinned a core. The one live reaction kept is
        # emptying the box (Escape, the clear icon, backspacing to nothing) restoring the board.
        self._search_query = ""   # the SUBMITTED query, which is what the board reflects
        # The sidebar picks a search cleared, so ending the search can put them back (see
        # _on_search_activate). None when the search cleared nothing.
        self._filters_before_search: FilterState | None = None
        self.search_entry.connect("activate", self._on_search_activate)
        self.search_entry.connect("search-changed", self._on_search_changed)
        # …and again beside the box, because GTK3 hides an entry's placeholder while it has focus,
        # which — since opening the bar focuses it — is exactly whenever anyone is looking. A dim
        # label costs one line of height only while searching and states the non-obvious half.
        search_hint = Gtk.Label(label="Enter to search · text or file name — try  N~p2", xalign=0)
        search_hint.get_style_context().add_class("search-hint")
        search_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        search_row.pack_start(self.search_entry, False, False, 0)
        search_row.pack_start(search_hint, False, False, 0)
        self.search_bar.add(search_row)
        self.search_bar.connect_entry(self.search_entry)
        right.pack_start(self.search_bar, False, False, 0)

        # A one-line answer to "why am I not seeing everything?", directly above the cards.
        # The sidebar already shows what's picked, but it is collapsible, scrollable and often
        # scrolled away from the section doing the filtering — so a narrowed board could look like
        # an empty folder. This states the whole state in one place, at the top of what it filters.
        self.filter_summary_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.filter_summary_row.get_style_context().add_class("filter-summary")
        self.filter_summary = Gtk.Label(xalign=0)
        self.filter_summary.set_ellipsize(3)   # END — a dozen picked tags must not widen the board
        self.filter_summary.set_use_markup(True)
        self.filter_summary_row.pack_start(self.filter_summary, True, True, 0)
        clear_all = Gtk.Button(label="clear")
        clear_all.get_style_context().add_class("filter-summary-clear")
        clear_all.set_tooltip_text(
            "Clear the search box and go back to the launch view (today and yesterday) — "
            "click again for the whole folder")
        clear_all.connect("clicked", lambda _b: self._clear_everything())
        self.filter_summary_row.pack_end(clear_all, False, False, 0)
        self.filter_summary_row.set_no_show_all(True)
        for child in self.filter_summary_row.get_children():
            child.show_all()
        right.pack_start(self.filter_summary_row, False, False, 0)

        # Display area. In kanban mode it holds category columns spread across a configurable number
        # of equal-height rows, the columns stretching to fill each row (so they're screen-size
        # sensitive); in list mode it holds a single, vertically scrolling list; in columns mode a
        # heading-free grid of width-capped cards. The rows and the scroll policy are (re)built per
        # reload by _render_kanban / _render_list / _render_columns.
        self.board_scroller = Gtk.ScrolledWindow()
        self.board = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        self.board.set_border_width(BOARD_BORDER)
        self.board_scroller.add(self.board)
        # The columns view fits whole cards to the available width, so it has to re-flow when that
        # width changes; the handler is a no-op in every other mode.
        self.board_scroller.connect("size-allocate", self._on_board_allocate)
        right.pack_start(self.board_scroller, True, True, 0)

        # "Just added" strip: the most recently entered note, sitting right above the add form.
        self.last_note_bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        self.last_note_bar.set_border_width(10)
        right.pack_start(self.last_note_bar, False, False, 0)

        right.pack_start(self._build_input_bar(), False, False, 0)

        # Status bar: the full path of the active note folder (left) and the current save key (right).
        self.statusbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        self.statusbar.get_style_context().add_class("statusbar")
        # No border width — see the toolbar's note; the padding is in .statusbar.
        self.status_path = Gtk.Label(xalign=0)
        self.status_path.set_ellipsize(1)  # MIDDLE — keep both ends of a long path readable
        self.status_key = Gtk.Label(xalign=1)
        self.statusbar.pack_start(self.status_path, True, True, 0)
        self.statusbar.pack_end(self.status_key, False, False, 0)
        root.pack_start(self.statusbar, False, False, 0)
        self._refresh_status()

        # Hashtag autocomplete on both writing surfaces — the note editor and the add field. They
        # take the same kind of text and a '#' means the same thing in each, so it would be odd for
        # one to offer the vocabulary and the other not.
        self._completers = [TagCompleter(self, self.editor_view), TagCompleter(self, self.textview)]

    # ------------------------------------------------------- editor pane

    def _build_editor_pane(self) -> Gtk.Widget:
        """The right-hand editing pane: the selected note's body, full height, saving itself.

        It runs the height of the main split (beside the board, the "just added" strip *and* the add
        form) rather than sitting above the form, so a long note gets the whole window to be read in
        — which is the only reason to edit here instead of in the card's quick-append field.

        Deliberately not a second way to create notes: it edits the note the board has selected and
        nothing else. The add form below still owns "new", so there's never a question about where
        what you're typing will end up.
        """
        pane = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        pane.get_style_context().add_class("editor-pane")
        pane.set_size_request(EDITOR_PANE_MIN, -1)  # a floor; _apply_editor_width sets the width

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        header.set_border_width(8)
        self.editor_title = Gtk.Label(xalign=0)
        self.editor_title.set_ellipsize(1)  # MIDDLE — a note file name's ends both identify it
        self.editor_title.get_style_context().add_class("editor-pane-title")
        header.pack_start(self.editor_title, True, True, 0)

        # Read / Edit, as a linked pair: which one you're in has to be readable at a glance, because
        # in one a click highlights a clause and in the other it moves a cursor.
        mode_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        mode_box.get_style_context().add_class("linked")
        self.read_button = Gtk.ToggleButton(label="read")
        self.read_button.set_tooltip_text(
            "Reading mode — click a clause to highlight it (==like this== in the file)")
        self.edit_button = Gtk.ToggleButton(label="edit")
        self.edit_button.set_tooltip_text("Editing mode — type into the note, saved as you go")
        for button, mode in ((self.read_button, "read"), (self.edit_button, "edit")):
            button.get_style_context().add_class("pane-mode")
            button.connect("toggled", self._on_pane_mode_button, mode)
            mode_box.pack_start(button, False, False, 0)
        header.pack_start(mode_box, False, False, 0)

        self.quick_toggle = Gtk.ToggleButton()
        self.quick_toggle.add(Gtk.Image.new_from_icon_name("view-list-symbolic", Gtk.IconSize.MENU))
        self.quick_toggle.get_style_context().add_class("iconbtn")
        self.quick_toggle.set_tooltip_text(
            "Show / hide the quick note + task lists under the pane  (Ctrl+J / Ctrl+K jump straight "
            "into one)")
        self.quick_toggle.connect("toggled", self._on_quick_toggle_button)
        header.pack_end(self.quick_toggle, False, False, 0)

        close = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        close.add(Gtk.Image.new_from_icon_name("window-close-symbolic", Gtk.IconSize.MENU))
        close.get_style_context().add_class("iconbtn")
        close.set_tooltip_text("Close the editor pane  (Ctrl+E)")
        close.connect("clicked", lambda _b: self._set_editor_pane(False))
        header.pack_end(close, False, False, 0)
        pane.pack_start(header, False, False, 0)

        self.editor_scroller = scroller = Gtk.ScrolledWindow()
        # EXTERNAL, not NEVER, horizontally — see the add field's scroller for why.
        scroller.set_policy(Gtk.PolicyType.EXTERNAL, Gtk.PolicyType.AUTOMATIC)
        # Overlay scrolling off, as in the add field: an overlay scrollbar floats ON TOP of the text
        # and covers the last few characters of every wrapped line it crosses. Off, it gets its own
        # gutter and the text keeps its full width.
        scroller.set_overlay_scrolling(False)
        self.editor_view = Gtk.TextView()
        self.editor_view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        # Per-paragraph automatic direction, as in the add field: each line flips for Arabic vs.
        # English on its own. The pane's *widget* direction is set per note in _editor_load — that's
        # what decides lines with no strong character of their own (a bare number, a date, a blank
        # line being typed into), which should follow the note, not the locale.
        self.editor_view.set_left_margin(12)
        self.editor_view.set_right_margin(12)
        self.editor_view.set_top_margin(8)
        self.editor_view.set_bottom_margin(8)
        self.editor_buffer = self.editor_view.get_buffer()
        self.editor_buffer.connect("changed", self._on_editor_changed)
        self.editor_view.connect("button-release-event", self._on_editor_click)
        # The text window doesn't exist until the view is realised, and GtkTextView sets its own
        # cursor there when it does — so ours has to be (re)applied after that, not just on render.
        self.editor_view.connect("realize", self._apply_editor_cursor)
        # connect_AFTER: GtkTextView's own motion handler sets the cursor back to "text", so ours
        # has to be re-asserted once that has run. See _apply_editor_cursor.
        self.editor_view.connect_after("motion-notify-event", self._apply_editor_cursor)
        scroller.add(self.editor_view)

        self.editor_status = Gtk.Label(xalign=0)
        self.editor_status.set_ellipsize(3)  # END
        self.editor_status.get_style_context().add_class("editor-pane-status")
        self.editor_status.set_margin_start(10)
        self.editor_status.set_margin_end(10)
        self.editor_status.set_margin_bottom(6)

        # The note being read/edited on top, the two quick lists underneath, splitter between them.
        # pack2 takes resize=False so the strip keeps its height when the WINDOW grows — the note is
        # what should get the new pixels — while the splitter still lets it be dragged either way.
        note_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        note_box.pack_start(scroller, True, True, 0)
        note_box.pack_start(self.editor_status, False, False, 0)
        note_box.pack_start(self._build_files_panel(), False, False, 0)
        self.right_split = Gtk.Paned(orientation=Gtk.Orientation.VERTICAL)
        self.right_split.pack1(note_box, True, False)
        self.right_split.pack2(self._build_quick_panels(), False, False)
        pane.pack_start(self.right_split, True, True, 0)

        self.editor_pane = pane
        # The window's show_all() must not reveal a pane the user left closed. no_show_all stops it
        # — but it also makes show_all() on the pane itself a no-op, so the children are shown here,
        # once, and the pane alone is what gets shown and hidden from then on.
        pane.set_no_show_all(True)
        for child in pane.get_children():
            child.show_all()
        # State: which note the buffer belongs to, the text as last written (so an edit that undoes
        # itself doesn't cause a write), and the pending autosave timer.
        self._editor_note: Note | None = None
        self._editor_path: str | None = None
        self._editor_saved_text = ""
        self._editor_loading = False
        self._editor_timer = 0
        self._editor_mode = self.settings.editor_pane_mode
        self._cursors: dict[str, Gdk.Cursor | None] = {}   # see _apply_editor_cursor
        # One tag, recoloured in place whenever the setting changes — a buffer tag is looked up by
        # name, so re-creating it on every render would throw on the second one.
        self._build_markup_tags()
        # Created LAST on purpose: GtkTextTag priority follows creation order, so the highlight's
        # background wins over a #tag chip's where a marked clause contains one.
        self._hl_tag = self.editor_buffer.create_tag("clause-highlight")
        self._refresh_highlight_tag()
        self._sync_pane_mode_buttons()
        self._editor_load(None)
        return pane

    def _theme_color(self, token: str, fallback: str = "#808080") -> str:
        """One `@define-color` value out of the ACTIVE theme block. The pane's styling has to be
        given real colours — a GtkTextTag takes a colour, not a CSS token — and reading them back
        out of THEMES is what keeps those colours in step with the stylesheet instead of being a
        second palette that drifts."""
        block = THEMES.get(self.settings.theme) or THEMES["dark"]
        m = re.search(rf"@define-color\s+{re.escape(token)}\s+([^;]+);", block)
        return m.group(1).strip() if m else fallback

    @staticmethod
    def _blend(colour: str, onto: str, alpha: float) -> str:
        """[colour] at [alpha] over [onto], as a solid hex. The card chips get their tint from
        `alpha()` in CSS, but a GtkTextTag background has no alpha at all — so the same tint has to
        be mixed by hand against the surface it will sit on."""
        a, b = Gdk.RGBA(), Gdk.RGBA()
        if not (a.parse(colour) and b.parse(onto)):
            return onto
        mix = tuple(round(255 * (getattr(a, c) * alpha + getattr(b, c) * (1 - alpha)))
                    for c in ("red", "green", "blue"))
        return "#{:02x}{:02x}{:02x}".format(*mix)

    def _build_markup_tags(self) -> None:
        """Create the reading-mode markdown tags once, then keep them pointed at the live theme.

        Styling is applied IN PLACE: the markers stay in the buffer and are merely dimmed, so the
        buffer still matches the file character for character. That is what lets clause-clicking and
        the write-back in _on_editor_click stay exactly as they were — see notes.markdown_spans.
        """
        buffer = self.editor_buffer
        table = buffer.get_tag_table()
        if table.lookup("md-bold") is None:
            for kind in ("marker", "bold", "italic", "bolditalic", "code",
                         "heading", "link", "url", "tag"):
                buffer.create_tag(f"md-{kind}")
        self._md_tags = {k: table.lookup(f"md-{k}") for k in
                         ("marker", "bold", "italic", "bolditalic", "code",
                          "heading", "link", "url", "tag")}

        surface = self._theme_color("card_bg", "#ffffff")
        muted = self._theme_color("fg_muted", "#808080")
        accent = self._theme_color("accent", "#268bd2")

        def style(kind: str, **props) -> None:
            for name, value in props.items():
                self._md_tags[kind].set_property(name, value)

        # The markers themselves: still there to be read (and to be edited in the other mode), but
        # pushed back far enough that the text reads as emphasised rather than as punctuation.
        style("marker", foreground=muted, scale=0.85)
        style("bold", weight=Pango.Weight.BOLD)
        style("italic", style=Pango.Style.ITALIC)
        style("bolditalic", weight=Pango.Weight.BOLD, style=Pango.Style.ITALIC)
        style("code", family="monospace",
                background=self._blend(muted, surface, 0.18))
        style("heading", weight=Pango.Weight.BOLD, scale=1.15)
        style("link", foreground=accent, underline=Pango.Underline.SINGLE)
        style("url", foreground=muted, scale=0.9)
        # Same green the cards use for a #hashtag chip, tinted onto the pane the way CSS alpha()
        # tints it there, so a tag looks like the same object in both places.
        style("tag", foreground=TAG_COLOR_HASH,
                background=self._blend(TAG_COLOR_HASH, surface, 0.18))

    def _paint_markup(self, text: str) -> None:
        """Apply the markdown tags over the reading buffer. Offsets come straight from
        markdown_spans, which reads the same string the buffer was filled with."""
        buffer = self.editor_buffer
        for start, end, kind in markdown_spans(text):
            # "highlight" comes back only for the editing buffer (reading mode strips the markers
            # before scanning) and maps to the real highlight tag, so a marked clause looks the
            # same in both modes instead of only being visible in one.
            tag = self._hl_tag if kind == "highlight" else self._md_tags.get(kind)
            if tag is not None:
                buffer.apply_tag(tag, buffer.get_iter_at_offset(start),
                                 buffer.get_iter_at_offset(end))

    def _refresh_highlight_tag(self) -> None:
        """Point the highlight tag at the configured colour, with a foreground that survives it.

        The note's own text colour is the theme's, which is near-white in dark mode — on a yellow
        highlight that's unreadable. Rec. 601 luminance picks black or white per colour, so any pick
        in the colour dialog stays legible instead of only the ones that happen to suit the theme.
        """
        background, foreground = self._highlight_colors()
        self._hl_tag.set_property("background", background)
        self._hl_tag.set_property("foreground", foreground)
        self._cursors.pop("pen", None)   # its nib is this colour; redrawn on the next use

    def _apply_editor_cursor(self, *_args) -> bool:
        """Put the right pointer over the pane's text.

        Reading mode gets a highlighter pen: a click there paints a clause, and that should be
        legible from the pointer before anything is clicked. Editing mode gets the usual I-beam, and
        a note that can't be written (an unsettled item) gets the plain arrow — a pen over text that
        won't take a mark would be a promise the pane can't keep.

        Also wired to motion-notify with connect_AFTER, because GtkTextView resets the cursor to
        "text" from its own motion handler: set once and the pen survives exactly until the pointer
        moves. Re-asserting is nearly free — the cursors are cached, and gdk_window_set_cursor
        compares pointers, so the common case is setting the cursor that's already there.
        """
        window = self.editor_view.get_window(Gtk.TextWindowType.TEXT)
        if window is None:
            return False   # not realised yet; the realize handler calls back here
        writable = (self._editor_note is not None
                    and self._editor_note.path not in self._unrecognized_paths)
        if self._editor_mode != "read":
            cursor = self._named_cursor("text")
        elif writable:
            cursor = self._pen_cursor()
        else:
            cursor = self._named_cursor("default")
        if cursor is not None:
            window.set_cursor(cursor)
        return False   # never claim the motion event — the view still needs it

    def _named_cursor(self, name: str) -> Gdk.Cursor | None:
        if name not in self._cursors:
            self._cursors[name] = Gdk.Cursor.new_from_name(self.editor_view.get_display(), name)
        return self._cursors[name]

    def _pen_cursor(self) -> Gdk.Cursor | None:
        """The highlighter cursor, drawn once per colour and cached. Drawn at the window's scale
        factor so it isn't a blurry upscale on a HiDPI screen."""
        if self._cursors.get("pen") is None:
            scale = max(1, self.get_scale_factor())
            surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, PEN_SIZE * scale, PEN_SIZE * scale)
            surface.set_device_scale(scale, scale)
            nib = Gdk.RGBA()
            if not nib.parse(self._highlight_colors()[0]):
                nib.parse(Settings.highlight_color)
            _draw_highlighter(cairo.Context(surface), nib)
            self._cursors["pen"] = Gdk.Cursor.new_from_surface(
                self.editor_view.get_display(), surface, *PEN_HOTSPOT)
        return self._cursors["pen"]

    def _on_pane_mode_button(self, button: Gtk.ToggleButton, mode: str) -> None:
        if button.get_active():
            self._set_editor_mode(mode)
        elif self._editor_mode == mode:
            button.set_active(True)   # radio behaviour: the active one can't be switched off

    def _set_editor_mode(self, mode: str) -> None:
        if mode == self._editor_mode:
            return
        self._editor_flush()          # leaving edit mode with an unwritten change would lose it
        self._editor_mode = mode
        self.settings.editor_pane_mode = mode
        self.settings.save()
        self._sync_pane_mode_buttons()
        self._editor_render(keep_scroll=True)   # same note, different view of it — stay put

    def _sync_pane_mode_buttons(self) -> None:
        for button, mode in ((self.read_button, "read"), (self.edit_button, "edit")):
            button.handler_block_by_func(self._on_pane_mode_button)
            button.set_active(self._editor_mode == mode)
            button.handler_unblock_by_func(self._on_pane_mode_button)

    def _on_editor_button(self, button: Gtk.ToggleButton) -> None:
        self._set_editor_pane(button.get_active())

    def _set_editor_pane(self, visible: bool, persist: bool = True, focus: bool = True) -> None:
        """Show/hide the pane (Ctrl+E, the toolbar toggle, the pane's own ✕). Hiding flushes first —
        a pending autosave must never be lost to closing the thing that was holding it.

        ``persist=False`` is for applying the remembered state at startup, where writing the config
        back out and stealing the cursor from the add field would both be wrong.
        """
        if not visible:
            self._editor_flush()
            width = self.editor_pane.get_allocated_width()
            if width > 120:                       # remember the drag, ignore a not-yet-laid-out 1px
                self.settings.editor_pane_width = width
        self.settings.editor_pane_visible = visible
        if persist:
            self.settings.save()
        # show/hide, not show_all: the children were shown once at build time (see the no_show_all
        # note there), so the pane itself is the only thing that toggles.
        if visible:
            self.editor_pane.show()
        else:
            self.editor_pane.hide()
        self.editor_button.handler_block_by_func(self._on_editor_button)
        self.editor_button.set_active(visible)
        self.editor_button.handler_unblock_by_func(self._on_editor_button)
        if visible:
            self._apply_editor_width()
            self._editor_load(self.selected_note)
            if focus:
                self.editor_view.grab_focus()

    def _apply_editor_width(self) -> None:
        """Give the pane its remembered width — as a size REQUEST, not a splitter position.

        A GtkPaned position is measured from the left, which makes it wrong twice over here: it
        needs the split to already have a real allocation to subtract from, and it can't go below
        the board's own minimum width. Ask for 420 on a window too narrow to grant it and GTK
        clamps the position to the board's minimum, leaving the pane at its bare floor — where it
        then stays, because pack1(resize=True) hands every later pixel to the board.

        A size request has neither problem: with the position unset, GtkPaned allocates child2 its
        requisition and gives the remainder to child1, at any window size. Once that layout has
        happened _relax_editor_width hands the width over to the position and drops the request,
        so the splitter is still free to be dragged narrower afterwards.
        """
        self.editor_pane.set_size_request(max(EDITOR_PANE_MIN, self.settings.editor_pane_width), -1)
        self._editor_settle_tries = 20
        GLib.timeout_add(EDITOR_PANE_SETTLE_MS, self._relax_editor_width)

    def _relax_editor_width(self) -> bool:
        total = self.main_paned.get_allocated_width()
        width = self.editor_pane.get_allocated_width()
        self._editor_settle_tries -= 1
        if total <= 1 or width <= 1:
            return self._editor_settle_tries > 0   # not laid out yet — look again
        # Pin what the size request just achieved as the splitter position BEFORE dropping the
        # request. Order matters: with no position set, GtkPaned recomputes the split from the
        # children's requisitions on every allocation, so relaxing the request first would send the
        # pane straight back to its floor. The position is what makes a width stick.
        self.main_paned.set_position(total - width)
        self.editor_pane.set_size_request(EDITOR_PANE_MIN, -1)
        return False

    # ------------------------------------------------------- quick capture

    def _build_quick_panels(self) -> Gtk.Widget:
        """The two lists under the editor pane: quick notes on the left, quick tasks on the right.

        They exist to remove every decision from capture. A real note needs a type, a priority, a
        category and a file name before it exists; these need a sentence and the Enter key. What
        lands here is explicitly NOT a note — it never reaches the board (see
        NoteRepository.IGNORED_FILE_STEMS) and is never promoted — so nothing has to be settled
        later either. Anything that turns out to deserve a note gets written as one.

        Both are plain markdown at the root of the folder, so they sync and can be read or edited
        anywhere: a bullet list and a GFM task list.
        """
        self.quick_files: dict[str, QuickFile] = {}
        self.quick_lists: dict[str, Gtk.ListBox] = {}
        self.quick_entries: dict[str, Gtk.Entry] = {}
        self.quick_counts: dict[str, Gtk.Label] = {}
        self._quick_stamp: dict[str, float] = {}
        self._quick_rebuilding = False

        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=0)
        box.get_style_context().add_class("quick-strip")
        box.pack_start(self._build_quick_panel("note"), True, True, 0)
        box.pack_start(self._build_quick_panel("task"), True, True, 0)
        self.quick_box = box
        # Same no_show_all dance as the pane itself: the window's show_all() must not reveal a strip
        # the user closed, but the children still have to be shown once so show() has something.
        box.set_no_show_all(True)
        for child in box.get_children():
            child.show_all()
        self._quick_settle_tries = 0
        return box

    def _build_quick_panel(self, kind: str) -> Gtk.Widget:
        """One of the two columns. [kind] is "note" or "task" — the only difference is that a task
        row carries a checkbox and its file uses ``- [ ]`` instead of ``- ``."""
        tasks = kind == "task"
        panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        panel.get_style_context().add_class("quick-panel")
        if tasks:
            panel.get_style_context().add_class("quick-panel-right")

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        title = Gtk.Label(label="Quick tasks" if tasks else "Quick notes", xalign=0)
        title.get_style_context().add_class("quick-title")
        header.pack_start(title, True, True, 0)
        count = Gtk.Label(xalign=1)
        count.get_style_context().add_class("quick-count")
        header.pack_end(count, False, False, 0)
        self.quick_counts[kind] = count
        panel.pack_start(header, False, False, 0)

        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.set_overlay_scrolling(False)   # as everywhere else: no bar floating over the text
        listbox = Gtk.ListBox()
        listbox.set_selection_mode(Gtk.SelectionMode.NONE)
        listbox.get_style_context().add_class("quick-list")
        scroller.add(listbox)
        panel.pack_start(scroller, True, True, 0)
        self.quick_lists[kind] = listbox

        entry = Gtk.Entry()
        entry.get_style_context().add_class("quick-entry")
        entry.set_placeholder_text("a task…  (Enter)" if tasks else "a note…  (Enter)")
        entry.connect("activate", self._on_quick_activate, kind)
        panel.pack_start(entry, False, False, 0)
        self.quick_entries[kind] = entry
        return panel

    def _quick_file(self, kind: str) -> QuickFile:
        """The backing file for [kind] in the ACTIVE folder, rebuilt when the folder changes — the
        lists belong to the note folder, so switching folders (Ctrl+1…9) switches lists too."""
        path = os.path.join(self.repo.directory,
                            QUICK_TASKS_FILE if kind == "task" else QUICK_NOTES_FILE)
        quick = self.quick_files.get(kind)
        if quick is None or quick.path != path:
            quick = QuickFile(path, tasks=kind == "task")
            self.quick_files[kind] = quick
            self._quick_stamp[kind] = -1.0   # force a rebuild against the new file
        return quick

    def _sync_quick_panels(self, force: bool = False) -> None:
        """Refresh either list whose file has changed since it was last drawn.

        Called from reload_notes, so an edit made on the phone, in another editor, or by the other
        panel shows up on its own — but guarded on mtime, because reload_notes runs on every folder
        event and rebuilding the rows unprompted would fight whatever is being typed.
        """
        for kind in ("note", "task"):
            quick = self._quick_file(kind)
            stamp = quick.mtime()
            if not force and stamp == self._quick_stamp.get(kind):
                continue
            self._quick_stamp[kind] = stamp
            self._fill_quick_list(kind, quick)

    def _fill_quick_list(self, kind: str, quick: QuickFile) -> None:
        listbox = self.quick_lists[kind]
        # Guard the rebuild: adding a CheckButton fires "toggled" as it takes its initial state,
        # which would write the file back for every task in the list on every refresh.
        self._quick_rebuilding = True
        try:
            for child in listbox.get_children():
                listbox.remove(child)
            entries = quick.entries()
            for entry in entries:
                listbox.add(self._quick_row(kind, quick, entry))
            listbox.show_all()
        finally:
            self._quick_rebuilding = False
        if kind == "task":
            left = sum(1 for e in entries if not e.done)
            self.quick_counts[kind].set_text(f"{fmt_count(left)} left" if entries else "")
        else:
            self.quick_counts[kind].set_text(fmt_count(len(entries)) if entries else "")

    def _quick_row(self, kind: str, quick: QuickFile, entry: QuickEntry) -> Gtk.Widget:
        row = Gtk.ListBoxRow()
        row.set_activatable(False)
        row.get_style_context().add_class("quick-row")
        line = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        # Per-item direction: half these notes are Arabic, and a mixed list where every row is
        # laid out left-to-right puts Arabic punctuation on the wrong end.
        line.set_direction(Gtk.TextDirection.RTL if is_rtl(entry.text) else Gtk.TextDirection.LTR)

        if kind == "task":
            check = Gtk.CheckButton(label=entry.text)
            check.set_active(bool(entry.done))
            check.get_style_context().add_class("quick-check")
            if entry.done:
                check.get_style_context().add_class("quick-done")
            label = check.get_child()
            if isinstance(label, Gtk.Label):
                label.set_line_wrap(True)
                label.set_xalign(0)
            check.connect("toggled", self._on_quick_toggled, kind, quick, entry)
            line.pack_start(check, True, True, 0)
        else:
            label = Gtk.Label(label=entry.text, xalign=0)
            label.set_line_wrap(True)
            label.set_selectable(True)   # a jotted phone number is there to be copied back out
            label.get_style_context().add_class("quick-text")
            line.pack_start(label, True, True, 0)

        drop = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        drop.add(Gtk.Image.new_from_icon_name("window-close-symbolic", Gtk.IconSize.MENU))
        drop.get_style_context().add_class("card-action")
        drop.set_can_focus(False)
        drop.set_tooltip_text("Remove this line")
        drop.connect("clicked", self._on_quick_remove, kind, quick, entry)
        line.pack_end(drop, False, False, 0)

        row.add(line)
        return row

    def _on_quick_activate(self, entry: Gtk.Entry, kind: str) -> None:
        """Enter in a quick field: append the line and clear the field, ready for the next one.
        The field keeps the focus — capture comes in bursts, and re-aiming after every item is the
        friction this whole strip exists to remove."""
        text = entry.get_text().strip()
        if not text:
            return
        quick = self._quick_file(kind)
        if not quick.append(text):
            self._flash_status(f"Could not write {os.path.basename(quick.path)}")
            return
        entry.set_text("")
        self._quick_stamp[kind] = quick.mtime()
        self._fill_quick_list(kind, quick)
        self._scroll_quick_to_end(kind)

    def _scroll_quick_to_end(self, kind: str) -> None:
        """Put the newest line in view. Deferred: the row that was just added has no allocation
        yet, so the adjustment's upper bound is still the pre-insert one."""
        def later() -> bool:
            adj = self.quick_lists[kind].get_parent().get_vadjustment()
            if adj is not None:
                adj.set_value(max(0.0, adj.get_upper() - adj.get_page_size()))
            return False
        GLib.idle_add(later)

    def _on_quick_toggled(self, button: Gtk.CheckButton, kind: str,
                          quick: QuickFile, entry: QuickEntry) -> None:
        if self._quick_rebuilding:
            return
        if not quick.set_done(entry, button.get_active()):
            self._flash_status(f"Could not write {os.path.basename(quick.path)}")
            return
        self._quick_stamp[kind] = quick.mtime()
        ctx = button.get_style_context()
        (ctx.add_class if button.get_active() else ctx.remove_class)("quick-done")
        entries = quick.entries()
        self.quick_counts[kind].set_text(
            f"{fmt_count(sum(1 for e in entries if not e.done))} left" if entries else "")

    def _on_quick_remove(self, _button: Gtk.Button, kind: str,
                         quick: QuickFile, entry: QuickEntry) -> None:
        if not quick.remove(entry):
            self._flash_status(f"Could not write {os.path.basename(quick.path)}")
            return
        self._quick_stamp[kind] = quick.mtime()
        self._fill_quick_list(kind, quick)

    def _on_quick_toggle_button(self, button: Gtk.ToggleButton) -> None:
        if button.get_active() != self.settings.quick_panels_visible:
            self._set_quick_panels(button.get_active())

    def _set_quick_panels(self, visible: bool, persist: bool = True, focus: str | None = None) -> None:
        """Show or hide the quick strip. [focus] names the field to land in ("note"/"task")."""
        if visible:
            # Remember the height before showing, then re-apply it: a strip that came back at the
            # splitter's default would have to be dragged into place every time.
            self.quick_box.show()
            self._apply_quick_height()
            self._sync_quick_panels(force=True)
        else:
            self._save_quick_height()
            self.quick_box.hide()
        self.settings.quick_panels_visible = visible
        if self.quick_toggle.get_active() != visible:
            self.quick_toggle.set_active(visible)
        if persist:
            self.settings.save()
        if visible and focus:
            self.quick_entries[focus].grab_focus()

    def _focus_quick(self, kind: str) -> None:
        """Ctrl+J / Ctrl+K from anywhere: open whatever has to be open and put the caret in the
        field. One keystroke from any state to typing is the point of the feature."""
        if not self.settings.editor_pane_visible:
            self._set_editor_pane(True, focus=False)
        if not self.settings.quick_panels_visible:
            self._set_quick_panels(True, focus=kind)
        else:
            self.quick_entries[kind].grab_focus()

    def _apply_quick_height(self) -> None:
        """Give the strip its remembered height. Same two-step as the editor pane's width, and for
        the same reason — see _relax_editor_width: a size request survives any window size, and the
        position is what makes it stick once the layout has happened."""
        self.quick_box.set_size_request(-1, max(QUICK_PANELS_MIN_H, self.settings.quick_panels_height))
        self._quick_settle_tries = 20
        GLib.timeout_add(EDITOR_PANE_SETTLE_MS, self._relax_quick_height)

    def _relax_quick_height(self) -> bool:
        total = self.right_split.get_allocated_height()
        height = self.quick_box.get_allocated_height()
        self._quick_settle_tries -= 1
        if total <= 1 or height <= 1:
            return self._quick_settle_tries > 0
        self.right_split.set_position(total - height)
        self.quick_box.set_size_request(-1, QUICK_PANELS_MIN_H)
        return False

    def _save_quick_height(self) -> None:
        height = self.quick_box.get_allocated_height()
        if height > QUICK_PANELS_MIN_H:
            self.settings.quick_panels_height = height

    def _build_files_panel(self) -> Gtk.Widget:
        """The strip under the note listing its companion folder (``<stem>.d/``): one row per file,
        a click opens it; "Add files…" (or a drop from the file manager) copies files in. The folder
        is still only created when something is actually added — browsing a note never makes one."""
        panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        panel.get_style_context().add_class("files-panel")

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        title = Gtk.Label(label="Files", xalign=0)
        title.get_style_context().add_class("quick-title")
        header.pack_start(title, False, False, 0)
        self.files_count = Gtk.Label(xalign=0)
        self.files_count.get_style_context().add_class("quick-count")
        header.pack_start(self.files_count, True, True, 0)
        for icon, label, tip, handler in (
                ("folder-open-symbolic", "open folder", "Open the note's files folder  (Ctrl+O)",
                 lambda _b: self._open_files_folder()),
                ("list-add-symbolic", "add files…",
                 "Copy files into the note's folder — or drop them on this panel",
                 lambda _b: self._choose_note_files())):
            button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
            row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
            row.pack_start(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.MENU), False, False, 0)
            row.pack_start(Gtk.Label(label=label), False, False, 0)
            button.add(row)
            button.get_style_context().add_class("card-action")
            button.set_can_focus(False)
            button.set_tooltip_text(tip)
            button.connect("clicked", handler)
            header.pack_end(button, False, False, 0)
        panel.pack_start(header, False, False, 0)

        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.EXTERNAL, Gtk.PolicyType.AUTOMATIC)
        scroller.set_overlay_scrolling(False)
        scroller.set_propagate_natural_height(True)
        scroller.set_max_content_height(120)   # a handful of rows, then it scrolls
        self.files_list = Gtk.ListBox()
        self.files_list.set_selection_mode(Gtk.SelectionMode.NONE)
        self.files_list.get_style_context().add_class("quick-list")
        self.files_list.connect("row-activated", self._on_note_file_activated)
        placeholder = Gtk.Label(label="No files yet — add some, or drop them here", xalign=0)
        placeholder.get_style_context().add_class("files-empty")
        placeholder.show()
        self.files_list.set_placeholder(placeholder)
        scroller.add(self.files_list)
        panel.pack_start(scroller, False, False, 0)

        # Files dragged in from the file manager arrive as a uri-list.
        panel.drag_dest_set(Gtk.DestDefaults.ALL, [], Gdk.DragAction.COPY)
        panel.drag_dest_add_uri_targets()
        panel.connect("drag-data-received", self._on_note_files_dropped)
        self.files_panel = panel
        return panel

    def _files_panel_note(self) -> Note | None:
        """The note the files panel is about: the pane's note, if it's a real (settled) one. An
        unsettled item has no companion folder of its own — see _open_files_folder."""
        note = self._editor_note
        if note is None or note.path in self._unrecognized_paths:
            return None
        return note

    def _refresh_note_files(self) -> None:
        """Re-list the pane note's companion folder (folders first, then files, by name)."""
        if not hasattr(self, "files_list"):
            return
        for child in self.files_list.get_children():
            self.files_list.remove(child)
        note = self._files_panel_note()
        self.files_panel.set_visible(note is not None)
        if note is None:
            return
        folder = companion_dir(note.path)
        try:
            names = os.listdir(folder) if os.path.isdir(folder) else []
        except OSError:
            names = []
        entries = sorted(((os.path.isdir(os.path.join(folder, n)), n) for n in names
                          if not n.startswith(".")), key=lambda e: (not e[0], e[1].lower()))
        for is_dir, name in entries:
            path = os.path.join(folder, name)
            row = Gtk.ListBoxRow()
            row.get_style_context().add_class("quick-row")
            row.file_path = path
            line = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
            line.set_margin_start(4)
            line.set_margin_end(4)
            icon = "folder-symbolic" if is_dir else "text-x-generic-symbolic"
            line.pack_start(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.MENU), False, False, 0)
            label = Gtk.Label(label=name, xalign=0)
            label.set_ellipsize(Pango.EllipsizeMode.MIDDLE)
            label.get_style_context().add_class("quick-text")
            line.pack_start(label, True, True, 0)
            if not is_dir:
                try:
                    size = Gtk.Label(label=GLib.format_size(os.path.getsize(path)), xalign=1)
                    size.get_style_context().add_class("quick-count")
                    line.pack_end(size, False, False, 0)
                except OSError:
                    pass
            row.set_tooltip_text(f"{path}\nClick to open")
            row.add(line)
            self.files_list.add(row)
        self.files_list.show_all()
        self.files_count.set_text(fmt_count(len(entries)) if entries else "")

    def _on_note_file_activated(self, _listbox: Gtk.ListBox, row: Gtk.ListBoxRow) -> None:
        path = getattr(row, "file_path", None)
        if path:
            self._open_attachment(path)

    def _choose_note_files(self) -> None:
        note = self._files_panel_note()
        if note is None:
            return
        dialog = Gtk.FileChooserDialog(title="Add files to this note", transient_for=self,
                                       action=Gtk.FileChooserAction.OPEN)
        dialog.add_buttons("_Cancel", Gtk.ResponseType.CANCEL, "_Add", Gtk.ResponseType.ACCEPT)
        dialog.set_select_multiple(True)
        paths = dialog.get_filenames() if dialog.run() == Gtk.ResponseType.ACCEPT else []
        dialog.destroy()
        self._attach_to_pane_note(paths)

    def _on_note_files_dropped(self, _widget, _context, _x, _y, data, _info, _time) -> None:
        paths = []
        for uri in data.get_uris() or []:
            try:
                path, _host = GLib.filename_from_uri(uri)
            except GLib.Error:
                continue   # not a local file (a web link dragged from a browser)
            paths.append(path)
        self._attach_to_pane_note(paths)

    def _attach_to_pane_note(self, paths: list[str]) -> None:
        """Copy [paths] into the pane note's companion folder, then refresh the list and the board
        (the card's file count comes from the same folder)."""
        note = self._files_panel_note()
        if note is None or not paths:
            return
        added = self.repo.attach(note, paths)
        skipped = len(paths) - added
        self._refresh_note_files()
        self.reload_notes()
        message = f"Added {added} file{'s' if added != 1 else ''}"
        if skipped:
            message += f" · {skipped} skipped (folders or unreadable)"
        self._flash_status(message)

    def _editor_load(self, note: Note | None) -> None:
        """Point the pane at a note (or at nothing). Flushes whatever was being edited first, so
        moving the selection can't strand an unsaved change on the note you just left."""
        if note is not None and note.path == self._editor_path:
            return   # same note — never stomp on what's being typed (a reload lands here too)
        self._editor_flush()
        self._editor_note = note
        self._editor_path = note.path if note is not None else None
        self._editor_saved_text = note.content if note is not None else ""
        self._editor_render()
        self._refresh_note_files()

    def _editor_render(self, keep_scroll: bool = False) -> None:
        """Fill the buffer from the current note in the current mode. Reading mode hides the ``==``
        markers and paints the clauses they mark; editing mode shows the file's text as it is.

        ``keep_scroll`` holds the view where it was, for a re-render of the note already on screen
        (a mode switch, a new highlight colour). Loading a *different* note leaves it off, so a new
        note opens at its beginning.
        """
        for completer in self._completers:
            completer.hide()   # a list left pointing at text that just changed underneath it
        note = self._editor_note
        raw = note.content if note is not None else ""
        reading = self._editor_mode == "read"
        text, flags = split_highlights(raw) if reading else (raw, [])

        vadj = self.editor_scroller.get_vadjustment()   # the view's own is deprecated in GTK3
        offset = vadj.get_value() if (keep_scroll and vadj is not None) else None
        self._editor_loading = True   # our own set_text must not look like a user edit
        try:
            self.editor_buffer.set_text(text)
            # Both modes get the markdown styling. Editing mode shows the file's text as it is,
            # markers and all — styling it costs nothing there either, since the markers were
            # always going to be on screen in that mode.
            self._paint_markup(text)
            if reading and any(flags):
                # Highlights last: the tag is younger than the markup ones, so it wins on the one
                # property they both set (background).
                self._paint_highlights(flags)
        finally:
            self._editor_loading = False
        if offset is not None:
            # After the buffer is refilled the view has to be laid out again before the adjustment
            # has a range to be set within — restoring it now would clamp to zero.
            GLib.idle_add(vadj.set_value, offset)

        if note is None:
            self.editor_title.set_text("No note selected")
            self.editor_view.set_editable(False)
            self.editor_view.set_cursor_visible(False)
            self.editor_view.set_sensitive(False)
            self._editor_say("Click a card to read or edit it here.")
            self._apply_editor_cursor()
            return
        self.editor_view.set_sensitive(True)
        self.editor_title.set_text(os.path.basename(note.path.rstrip(os.sep + "/")))
        # An RTL note gets an RTL pane: GTK picks each paragraph's direction from its own text, and
        # falls back to the widget's for a line with no strong character (a number, a date, the
        # empty line you're about to type Arabic into). Following the note is what an Arabic writer
        # expects; following the locale would put the cursor on the wrong side of every new line.
        self.editor_view.set_direction(Gtk.TextDirection.RTL if note.is_rtl else Gtk.TextDirection.LTR)
        # An unsettled item is shown but never written: repo.update would rename it into the
        # canonical format — a silent promotion, from nothing but a keystroke or a stray click.
        settled = note.path not in self._unrecognized_paths
        self.editor_view.set_editable(settled and not reading)
        self.editor_view.set_cursor_visible(settled and not reading)
        if not settled:
            self._editor_say("Read-only — needs metadata. Settle it with Ctrl+Shift+<key> first.")
        elif reading:
            self._editor_say("Reading — click a clause to highlight it.")
        else:
            self._editor_say("Editing — saved automatically as you type.")
        self._apply_editor_cursor()

    def _paint_highlights(self, flags: list[bool]) -> None:
        """Apply the highlight tag over every run of True in ``flags`` (offsets into the buffer,
        which in reading mode is the marker-free text those flags came from)."""
        i, n = 0, len(flags)
        while i < n:
            if not flags[i]:
                i += 1
                continue
            end = i
            while end < n and flags[end]:
                end += 1
            self.editor_buffer.apply_tag(self._hl_tag,
                                         self.editor_buffer.get_iter_at_offset(i),
                                         self.editor_buffer.get_iter_at_offset(end))
            i = end

    def _on_editor_click(self, view: Gtk.TextView, event) -> bool:
        """Reading mode: a left click toggles the highlight on the clause under the pointer.

        Left button only, and only in reading mode — in editing mode a click is a click, placing the
        cursor. Returns False either way so the view still gets its normal handling.
        """
        if self._editor_mode != "read" or event.button != 1 or self._editor_note is None:
            return False
        if not self.editor_view.get_sensitive() or self._editor_note.path in self._unrecognized_paths:
            return False
        bx, by = view.window_to_buffer_coords(Gtk.TextWindowType.TEXT, int(event.x), int(event.y))
        # A click BELOW the text is not aimed at any clause — the pane is mostly empty space on a
        # short note, and highlighting the last line from a click in the void would be a surprise.
        last = view.get_iter_location(self.editor_buffer.get_end_iter())
        if by > last.y + last.height:
            return False
        # The returned flag is deliberately ignored. get_iter_at_location reports False for a click
        # past the END OF A LINE — landing in the empty run after a short line, which is an ordinary
        # way to aim at that line — while still setting the iter to the right place. Honouring the
        # flag made every such click do nothing at all.
        _over_text, it = view.get_iter_at_location(bx, by)
        plain, flags = split_highlights(self._editor_note.content)
        start, end = clause_bounds(plain, it.get_offset())
        if start >= end:
            return False   # a click on a break or past the text — nothing to toggle
        turning_on = not all(flags[start:end])
        for i in range(start, end):
            flags[i] = turning_on
        if self._editor_write(apply_highlights(plain, flags)):
            # Repaint, never re-render: toggling a highlight only moves ``==`` markers around in the
            # FILE, and reading mode doesn't show those — the text on screen is character-for-
            # character the same. Refilling the buffer would be a no-op the user still pays for, as
            # set_text drops the insert mark to offset 0 and the view jumps to the top of the note.
            self._repaint_highlights(flags)
        return False

    def _repaint_highlights(self, flags: list[bool]) -> None:
        start, end = self.editor_buffer.get_bounds()
        self.editor_buffer.remove_tag(self._hl_tag, start, end)
        self._paint_highlights(flags)

    def _editor_say(self, message: str) -> None:
        self.editor_status.set_text(message)
        self.editor_status.set_tooltip_text(message)

    def _on_editor_changed(self, _buffer) -> None:
        if self._editor_loading or self._editor_note is None or self._editor_mode != "edit":
            return
        self._editor_say("Editing…")
        if self._editor_timer:
            GLib.source_remove(self._editor_timer)
        self._editor_timer = GLib.timeout_add(max(150, self.settings.editor_autosave_ms),
                                              self._editor_timer_fired)

    def _editor_timer_fired(self) -> bool:
        self._editor_timer = 0
        self._editor_flush()
        # Restyle now the typing has settled. GtkTextBuffer extends whatever tags border an
        # insertion point over the new text, so typing just after a "**bold**" run drags the bold
        # with it; and a marker only becomes a pair once its closing half is typed. Re-scanning on
        # the autosave beat keeps the styling honest without restyling on every keystroke.
        self._restyle_editor()
        return False  # one-shot

    def _restyle_editor(self) -> None:
        """Re-apply the markdown tags to the buffer as it currently stands. Tags only — the text
        is not touched, so this can never interfere with what gets saved."""
        if self._editor_note is None:
            return
        buffer = self.editor_buffer
        start, end = buffer.get_bounds()
        for tag in self._md_tags.values():
            buffer.remove_tag(tag, start, end)
        if self._editor_mode == "edit":
            buffer.remove_tag(self._hl_tag, start, end)   # re-applied from the text below
        self._paint_markup(buffer.get_text(start, end, False))

    def _editor_text(self) -> str:
        start, end = self.editor_buffer.get_bounds()
        return self.editor_buffer.get_text(start, end, True)

    def _editor_flush(self) -> None:
        """Write the pane's buffer to disk if it differs from what's there. Safe to call at any
        time, and called before anything that renames, reloads or closes — retagging, deleting,
        merging, switching folders, quitting. Cheap when there's nothing to do.
        """
        if self._editor_timer:
            GLib.source_remove(self._editor_timer)
            self._editor_timer = 0
        if self._editor_mode != "edit" or not self.editor_view.get_editable():
            return   # reading mode never holds an unwritten edit — a clause toggle writes at once
        self._editor_write(self._editor_text())

    def _editor_write(self, text: str) -> bool:
        """Write ``text`` as the note's new body if it differs from what's on disk. The single write
        path for the pane, shared by the autosave and by a clause highlight. Returns True on a
        successful write (False for "nothing to do" as well as for a failure — the caller only
        re-renders when something actually changed)."""
        note = self._editor_note
        if note is None or note.path in self._unrecognized_paths or text == self._editor_saved_text:
            return False
        try:
            self.repo.update(note, text, note.type, note.priority, note.category)
        except OSError as exc:
            # Never silently: the buffer is the only copy of the change at this point.
            self._editor_say(f"Not saved — {exc.strerror or exc}")
            return False
        self._editor_saved_text = text
        # Keep the in-memory note in step. Quick-append and the merge summary read note.content, and
        # after an autosave the object the board is holding would otherwise describe the old file.
        note.content = text
        self._editor_say(f"Saved  {datetime.now().strftime('%H:%M:%S')}")
        return True

    def _editor_sync_after_reload(self, notes: list[Note]) -> None:
        """Re-point the pane at the freshly built Note for the same path after a board rebuild.

        The old object is about to be dropped, and it's the one _editor_flush would write through.
        Matching on path only, so a rebuild triggered by the pane's own save (the folder watch sees
        it) leaves the buffer — and the cursor — exactly where they were.
        """
        if self._editor_path is None:
            return
        for note in notes:
            if note.path == self._editor_path:
                self._editor_note = note
                return
        # The file is gone (deleted, merged away, or retagged into a new name). Nothing to write
        # back to; follow the selection instead, which by now points at whatever replaced it.
        self._editor_note = None
        self._editor_path = None
        self._editor_load(self.selected_note)

    def _build_input_bar(self) -> Gtk.Widget:
        bar = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        bar.get_style_context().add_class("inputbar")
        bar.set_border_width(8)

        # Tag chip rows: a left-packed horizontal row of compact chips. The chips are few and the
        # window is wide, so they always fit on one line — no need for a (collapse-prone) FlowBox,
        # and each chip keeps its small natural width instead of stretching across the screen.
        def _chip_row() -> Gtk.Box:
            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
            box.set_halign(Gtk.Align.START)
            return box

        self.type_flow = _chip_row()
        self.category_flow = _chip_row()
        self.priority_flow = _chip_row()

        # Each chip group (Type / Prio / Cat) is a row: a fixed-width caption then its chips. The rows
        # are stacked vertically so the window can stay narrow (focused reading vs. one long line).
        def _group(caption: str, flow: Gtk.Box) -> Gtk.Box:
            group = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
            cap = Gtk.Label(label=caption, xalign=0)
            cap.set_width_chars(5)          # align the chips of all three rows to the same left edge
            cap.get_style_context().add_class("tag-caption")
            group.pack_start(cap, False, False, 0)
            group.pack_start(flow, False, False, 0)
            return group

        self.type_row = _group("Type", self.type_flow)
        self.priority_row = _group("Prio", self.priority_flow)
        self.category_row = _group("Cat", self.category_flow)

        self.chips_row = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.chips_row.set_halign(Gtk.Align.START)
        self.chips_row.set_margin_top(8)
        self.chips_row.set_margin_bottom(8)
        self.chips_row.set_margin_start(4)
        # Type on the first row, priority below it, category below that.
        self.chips_row.pack_start(self.type_row, False, False, 0)
        self.chips_row.pack_start(self.priority_row, False, False, 0)
        self.chips_row.pack_start(self.category_row, False, False, 0)
        bar.pack_start(self.chips_row, False, False, 0)
        self._build_chips()

        # Minimal mode: a one-line summary of the CURRENTLY selected tags (type · pN · category),
        # shown instead of the full chip rows. Order: type, priority, subject.
        self.minimal_tags = Gtk.Label(xalign=0)
        self.minimal_tags.set_no_show_all(True)
        self.minimal_tags.set_margin_top(6)
        self.minimal_tags.set_margin_start(6)
        self.minimal_tags.set_margin_bottom(4)
        self.minimal_tags.get_style_context().add_class("tag-caption")
        bar.pack_start(self.minimal_tags, False, False, 0)

        # Text field + send button.
        input_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)

        # Keyboard-layout indicator, left of the text field (same height): "ع" while an Arabic layout
        # is producing text, "EN" otherwise — so you can see the active layout before/while typing.
        self.layout_label = Gtk.Label(label="EN")
        self.layout_label.set_width_chars(3)
        self.layout_label.set_valign(Gtk.Align.FILL)
        self.layout_label.get_style_context().add_class("badge")
        input_row.pack_start(self.layout_label, False, False, 0)

        self.text_scroller = Gtk.ScrolledWindow()
        # No horizontal scrollbar (the text wraps) — but EXTERNAL, not NEVER. A wrapping GtkTextView
        # reports the width its text was last laid out at as its MINIMUM width, and NEVER makes the
        # scroller demand its child's minimum: so every line typed (or every note loaded into the
        # pane) ratcheted the window wider and it never came back. EXTERNAL hides the bar without
        # inheriting the child's width, and the view simply wraps at whatever width it is given.
        # Overlay scrolling off so the vertical scrollbar gets its own gutter instead of covering
        # the last line.
        self.text_scroller.set_policy(Gtk.PolicyType.EXTERNAL, Gtk.PolicyType.AUTOMATIC)
        self.text_scroller.set_overlay_scrolling(False)
        # Start a single line tall; we grow it ourselves to fit the content (capped) on each edit.
        # (Deliberately NOT using propagate_natural_height: with a wrapping TextView its height-for-
        # width recompute flashes a phantom horizontal scrollbar on every newline.)
        self.text_scroller.set_min_content_height(EDITOR_MIN_HEIGHT)
        self.text_scroller.get_style_context().add_class("editor")
        self.textview = Gtk.TextView()
        self.textview.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        # Per-paragraph automatic direction (each line flips for Arabic vs. English),
        # the way xed/kate behave. GTK detects the base direction of each paragraph.
        # Inner padding so the text isn't flush against the field's border.
        self.textview.set_left_margin(10)
        self.textview.set_right_margin(10)
        self.textview.set_top_margin(7)
        self.textview.set_bottom_margin(7)
        self.buffer = self.textview.get_buffer()
        self.buffer.connect("changed", lambda *_: GLib.idle_add(self._sync_editor_height))
        # The quick-capture entries copy this field's resting (one-line) height — measured from its
        # real allocation, since theme padding and font make it more than the text margins suggest.
        self.text_scroller.connect("size-allocate", self._sync_quick_entry_height)
        self.text_scroller.add(self.textview)
        input_row.pack_start(self.text_scroller, True, True, 0)

        # Folder picker (by code) right before the add button — chooses which note folder a new note
        # is written to (and which folder's notes the board shows). Hidden when only one is configured.
        self.repo_combo = Gtk.ComboBoxText()
        # FILL so the picker matches the text field's height (uniform, tight input row).
        self.repo_combo.set_valign(Gtk.Align.FILL)
        self.repo_combo.set_tooltip_text("Folder new notes are saved to  (Ctrl+1 … Ctrl+9)")
        self.repo_combo.set_no_show_all(True)
        self.repo_combo.connect("changed", self._on_repo_changed)
        input_row.pack_start(self.repo_combo, False, False, 0)
        self._populate_repo_combo()

        self.send_button = Gtk.Button()
        self.send_button.add(Gtk.Image.new_from_icon_name("document-send-symbolic", Gtk.IconSize.BUTTON))
        self.send_button.get_style_context().add_class("suggested-action")
        # FILL so the button matches the text field / picker height (uniform, tight input row).
        self.send_button.set_valign(Gtk.Align.FILL)
        self.send_button.connect("clicked", lambda b: self._send())
        input_row.pack_start(self.send_button, False, False, 0)
        bar.pack_start(input_row, False, False, 0)

        # One-line index of all #hashtags used across the notes, each with the number of notes using
        # it. Updated on every reload; hidden (no_show_all) when there are none.
        self.tags_summary = Gtk.Label(xalign=0)
        self.tags_summary.set_single_line_mode(True)
        self.tags_summary.set_ellipsize(3)  # END
        self.tags_summary.set_no_show_all(True)
        self.tags_summary.get_style_context().add_class("tags-summary")
        bar.pack_start(self.tags_summary, False, False, 0)

        return bar

    def _build_chips(self) -> None:
        """(Re)create all tag chips from the current settings."""
        for flow in (self.type_flow, self.category_flow, self.priority_flow):
            for child in flow.get_children():
                flow.remove(child)
        self.type_buttons.clear()
        self.category_buttons.clear()
        self.priority_buttons.clear()

        self.type_codes = [code for code, _ in self.repo_cfg.types]
        self.category_codes = [code for code, _ in self.repo_cfg.categories]

        # No visible shortcut hint on the chips (kept compact); the shortcut lives in the tooltip.
        # Types & categories: Ctrl+Shift+<letter>; priorities: Ctrl+Shift+<digit>.
        for code, name in self.repo_cfg.types:
            self.type_buttons[code] = self._add_chip(
                self.type_flow, code,
                f"Type {code}" + (f" — {name}" if name else "") + f"  (Ctrl+Shift+{code.upper()})",
                lambda b, c=code: self._on_type_toggled(c))
        for p in self.repo_cfg.priorities():
            self.priority_buttons[p] = self._add_chip(
                self.priority_flow, f"P{p}", f"Priority {p}  (Ctrl+Shift+{p})",
                lambda b, v=p: self._on_priority_toggled(v))
        for code, name in self.repo_cfg.categories:
            self.category_buttons[code] = self._add_chip(
                self.category_flow, code,
                f"Category {code}" + (f" — {name}" if name else "") + f"  (Ctrl+Shift+{code.upper()})",
                lambda b, c=code: self._on_category_toggled(c))

        self.type_row.set_visible(bool(self.repo_cfg.types))
        self.category_row.set_visible(bool(self.repo_cfg.categories))

    def _backend_conn(self) -> tuple[str, str, str]:
        """(url, user, password) of the ACTIVE folder's backend — each repository can bind its own
        backend in its .knotes.json; blank fields fall back to the global settings."""
        url = (self.repo_cfg.backend_url or self.settings.backend_url).strip()
        user = self.repo_cfg.backend_user or self.settings.backend_user
        pw = self.repo_cfg.backend_password or self.settings.backend_password
        return url, user, pw

    def _sync_backend_taxonomy(self) -> None:
        """Refresh the capture 'types' from the backend's modules and 'categories' from its bookmarked
        departments so codes line up with the backend on import. Uses the active folder's own backend
        (fallback: global). Runs off the UI thread; on any failure (no URL, offline, bad credentials)
        it silently keeps the current codes — note-taking is never blocked on the network."""
        url, user, pw = self._backend_conn()
        if not url:
            return
        repo_path = self.settings.active_repo_path()

        def worker():
            types, categories = fetch_taxonomy(url, user, pw)
            if types or categories:
                GLib.idle_add(self._apply_taxonomy, repo_path, types, categories)

        threading.Thread(target=worker, daemon=True).start()

    def _apply_taxonomy(self, repo_path: str, types, categories) -> bool:
        """Apply backend-fetched (code, name) lists to the active folder's tag config and refresh the
        chips (on the GTK main thread via idle_add). Persists them so a later offline launch reuses
        them. Skipped if the user switched folders while the fetch was in flight."""
        if repo_path != self.settings.active_repo_path():
            return False
        if types:
            self.repo_cfg.types = [tuple(t) for t in types]
        if categories:
            self.repo_cfg.categories = [tuple(c) for c in categories]
        self.repo_cfg.save(repo_path)  # cache: next launch uses these even if the backend is down
        self._build_chips()
        self._refresh_taxonomy()
        self._apply_defaults()
        self.reload_notes()  # card tag colours reflect the refreshed codes
        return False

    def _add_chip(self, flow: Gtk.Box, label: str, tooltip: str, on_toggled) -> Gtk.ToggleButton:
        button = Gtk.ToggleButton()
        caption = Gtk.Label(label=label, justify=Gtk.Justification.CENTER)
        button.add(caption)
        button.set_tooltip_text(tooltip)
        button.get_style_context().add_class("chip")
        button.connect("toggled", on_toggled)
        flow.pack_start(button, False, False, 0)
        flow.show_all()
        return button

    # -------------------------------------------------------------- chips

    def _on_type_toggled(self, code: str) -> None:
        if self._suppress_toggle:
            return
        self._select_type(code if self.type_buttons[code].get_active() else None)

    def _on_category_toggled(self, code: str) -> None:
        if self._suppress_toggle:
            return
        self._select_category(code if self.category_buttons[code].get_active() else None)

    def _on_priority_toggled(self, value: int) -> None:
        if self._suppress_toggle:
            return
        self._set_priority(value)  # priority is single-choice; re-assert on any toggle

    def _select_type(self, code: str | None) -> None:
        self.selected_type = code
        self._refresh_group(self.type_buttons, code)
        self._refresh_minimal_tags()

    def _select_category(self, code: str | None) -> None:
        self.selected_category = code
        self._refresh_group(self.category_buttons, code)
        self._refresh_minimal_tags()

    def _set_priority(self, value: int) -> None:
        if value not in self.priority_buttons:
            return
        self.priority = value
        self._refresh_group(self.priority_buttons, value)
        self._refresh_minimal_tags()

    def _refresh_group(self, buttons: dict, active_key) -> None:
        self._suppress_toggle = True
        for key, button in buttons.items():
            button.set_active(key == active_key)
        self._suppress_toggle = False

    # --------------------------------------------------------- note list

    def reload_notes(self) -> None:
        self._unpin_scroll()  # a pin from an earlier rebuild must not hold this one's layout
        for child in self.board.get_children():
            self.board.remove(child)

        # Root-level notes plus non-conforming files/folders shown as "needs metadata" placeholders
        # (merge-sorted by mtime, not concatenated, so the "just added" card below still picks the
        # actual newest item regardless of which list it came from).
        unrecognized = self.repo.list_unrecognized()
        self._unrecognized_paths = {n.path for n in unrecognized}
        self._refresh_note_files()   # files may have been added or removed outside the app
        all_notes = sorted(self.repo.list_notes() + unrecognized, key=lambda n: n.mtime)
        query = self._search_query
        searched = [n for n in all_notes if not query or self._matches(n, query)]
        # Two stages, in this order on purpose: the search box narrows the folder, then the sidebar
        # counts every filter option against what's left (so its numbers track the search), and only
        # then do the filters themselves decide what reaches the board.
        self.sidebar.refresh(searched)
        notes = [n for n in searched if self.filters.matches(n, self.tax)]
        # Folder note count next to the app name (no subtitle, so the header bar stays one line tall).
        narrowed = bool(query) or self.filters.active()
        count = (f"{fmt_count(len(notes))} / {fmt_count(len(all_notes))} notes" if narrowed
                 else f"{fmt_count(len(all_notes))} notes")
        # The backlog, right after the total: how many items are sitting there un-settled. It's a
        # work count, not a note count — every one is a file or folder waiting to be tagged — so it
        # counts the WHOLE folder regardless of the filters, and disappears at zero rather than
        # showing "0 to settle" on a folder that has nothing to do.
        if unrecognized:
            count += f" · {fmt_count(len(unrecognized))} to settle"
        self._base_title = f'{APP_NAME} v{VERSION}.b{BUILD_NUMBER} · {count}'
        self._apply_header_title()
        self._update_filter_summary(query, len(notes), len(all_notes))
        self._update_tags_summary(all_notes)
        # The quick lists live in this folder too, so a folder event may be about one of them (or
        # about a switch to a different folder entirely). Guarded on mtime — see _sync_quick_panels.
        self._sync_quick_panels()

        # "Just added" — the newest note (the repo lists oldest→newest, so it's the last one).
        self._render_last_note(notes[-1] if notes else None)

        if self.minimal_mode:
            self._editor_sync_after_reload(all_notes)
            return   # side-note mode: the board is hidden, only the just-added card is shown
        if self.settings.view_mode == "week":
            # The calendar answers "what did I journal / plan, when", so it takes the whole
            # (searched) folder rather than the sidebar's selection — the sidebar opens filtered to
            # today, which would leave six empty days on a week view.
            self._render_week(searched)
        elif not notes:
            self._render_empty(bool(all_notes), query)
        elif self.settings.view_mode == "list":
            self._render_list(notes)
        elif self.settings.view_mode == "columns":
            self._render_columns(notes)
        else:
            self._render_kanban(notes)
        self.board.show_all()
        self._restore_selection()
        # Last: _restore_selection may have re-pointed the pane already, and this only fixes up the
        # stale Note object behind an unchanged path.
        self._editor_sync_after_reload(all_notes)

    def _refresh_taxonomy(self) -> None:
        """Re-snapshot the ACTIVE folder's tag options for the filters. They're per-folder and the
        backend sync can replace them mid-session, so this runs anywhere repo_cfg changes — the
        sidebar's "is this code one we define?" answer must never lag the folder on screen."""
        self.tax = Taxonomy.from_repo_config(self.repo_cfg, self.settings.timestamp_format)
        self.sidebar.set_taxonomy(self.tax, self.repo_cfg.types, self.repo_cfg.categories)

    def _on_filters_changed(self) -> bool:
        """A sidebar click landed — re-render the board. The sidebar persists its own layout, so
        there's nothing to save here."""
        self.reload_notes()
        return False   # called via GLib.idle_add; don't repeat

    def _sorted(self, notes: list[Note]) -> list[Note]:
        """Order notes by the configured ``sort_by``: priority/category/type by their defined order,
        "created" by the note's original filename timestamp (stable across metadata edits — see
        parse_created), "length" by how much text the note holds (longest first), "datetime"
        (default) by the file's own last-modified time."""
        by = self.settings.sort_by
        if by == "priority":
            return sorted(notes, key=lambda n: (n.priority if n.priority is not None else -1, n.mtime),
                          reverse=True)
        if by == "type":
            order = {c: i for i, (c, _) in enumerate(self.repo_cfg.types)}
            return sorted(notes, key=lambda n: (order.get(n.type, len(order)), -n.mtime))
        if by == "category":
            order = {c: i for i, (c, _) in enumerate(self.repo_cfg.categories)}
            return sorted(notes, key=lambda n: (order.get(n.category, len(order)), -n.mtime))
        if by == "length":
            # Longest first — the whole point is to surface the notes that have grown into
            # something that should have been split, and those are at the top or nowhere.
            # Measured on the note's text rather than the file size on disk, so the metadata in the
            # name and any highlight markers don't count towards "how much have I written here".
            return sorted(notes, key=lambda n: (len(n.content), n.mtime), reverse=True)
        if by == "created":
            # Ascending — newest last, same convention as "datetime" — but keyed on the note's
            # ORIGINAL creation token, which a metadata-only edit never changes, so retagging a
            # note during a mass-editing pass doesn't relocate it in the list.
            fmt = self.settings.timestamp_format
            return sorted(notes, key=lambda n: parse_created(n.timestamp, fmt, n.mtime))
        return sorted(notes, key=lambda n: n.mtime)  # datetime ascending — newest last (by the add form)

    def _render_kanban(self, notes: list[Note]) -> None:
        # Vertical scrolling off (the rows are equal-height and fit the viewport; each column scrolls
        # its own cards); horizontal off too so the columns stretch to fill the width.
        self.board_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.NEVER)

        # One column per category that HAS notes, in definition order — empty categories are
        # hidden so the board only spends width on panels with content. A trailing "—" column
        # collects uncategorized notes only when some exist.
        cat_order = [code for code, _ in self.repo_cfg.categories]
        cat_names = dict(self.repo_cfg.categories)
        by_cat: dict[str, list[Note]] = {}
        for n in notes:
            by_cat.setdefault(n.category if n.category in cat_order else "—", []).append(n)

        columns = [code for code in cat_order if by_cat.get(code)]
        if "—" in by_cat:
            columns.append("—")

        # Spread the columns across the configured number of rows (1–3), filling each row left→right,
        # then have every column expand to share its row's width. Record the navigation grid.
        nrows = max(1, min(self.settings.kanban_rows, 3))
        nrows = min(nrows, max(1, len(columns)))
        per_row = (len(columns) + nrows - 1) // nrows  # ceil, so earlier rows are the full ones
        rows_codes = [columns[i * per_row:(i + 1) * per_row] for i in range(nrows)]
        rows_codes = [codes for codes in rows_codes if codes] or [[]]

        self.board.set_homogeneous(len(rows_codes) > 1)  # equal-height rows when there's more than one
        self._grid = []
        for codes in rows_codes:
            row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
            row.set_homogeneous(True)  # equal-width columns that together fill the row
            self.board.pack_start(row, True, True, 0)
            grid_row: list[list[tuple[Note, Gtk.Widget]]] = []
            for code in codes:
                name = "Uncategorized" if code == "—" else (cat_names.get(code) or code)
                col_cards: list[tuple[Note, Gtk.Widget]] = []
                column = self._build_column(code, name, by_cat.get(code, []), col_cards)
                row.pack_start(column, True, True, 0)  # expand+fill → columns share the row width
                grid_row.append(col_cards)
            self._grid.append(grid_row)

    # ---------------------------------------------------------- week calendar

    @staticmethod
    def _monday_of(moment: datetime) -> datetime:
        """Midnight on the Monday of ``moment``'s week (the sidebar's calendar starts on Monday too)."""
        day = moment.replace(hour=0, minute=0, second=0, microsecond=0)
        return day - timedelta(days=day.weekday())

    def _calendar_type_name(self, code: str) -> str:
        return dict(self.repo_cfg.types).get(code) or CALENDAR_TYPES.get(code, code)

    def _shift_week(self, step: int) -> None:
        """‹ / › (and Ctrl+PgUp / Ctrl+PgDn): move the calendar ``step`` weeks; 0 = this week."""
        self._week_start = (self._monday_of(datetime.now()) if step == 0
                            else self._week_start + timedelta(weeks=step))
        self.reload_notes()

    def _render_week(self, notes: list[Note]) -> None:
        """An Outlook-style week: seven day columns of WEEK_SLOT_MINUTES slots, with every journal /
        planner note in the slot its file-name timestamp falls in — however the note was created.
        Clicking an empty slot opens _new_calendar_entry dated at that slot."""
        self.board_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.NEVER)
        self.board.set_homogeneous(False)
        fmt = self.settings.timestamp_format
        start = self._week_start
        end = start + timedelta(days=7)
        today = datetime.now().date()

        # Bucket the week's J/P notes by (day, slot), each slot in time order.
        slot_len = WEEK_SLOT_MINUTES
        buckets: dict[tuple[int, int], list[tuple[datetime, Note]]] = {}
        for note in notes:
            if note.type not in CALENDAR_TYPES or note.path in self._unrecognized_paths:
                continue
            when = datetime.fromtimestamp(parse_created(note.timestamp, fmt, note.mtime))
            if not (start <= when < end):
                continue
            key = ((when - start).days, (when.hour * 60 + when.minute) // slot_len)
            buckets.setdefault(key, []).append((when, note))
        shown = sum(len(v) for v in buckets.values())

        # The bar: week navigation, what the week holds, and the one non-obvious gesture.
        bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        bar.get_style_context().add_class("week-bar")
        for icon, tip, step in (("go-previous-symbolic", "Previous week (Ctrl+PgUp)", -1),
                                (None, "Back to this week", 0),
                                ("go-next-symbolic", "Next week (Ctrl+PgDn)", 1)):
            btn = Gtk.Button(label="Today") if icon is None else Gtk.Button()
            if icon is not None:
                btn.add(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.BUTTON))
            btn.get_style_context().add_class("week-nav")
            btn.set_tooltip_text(tip)
            btn.connect("clicked", lambda _b, k=step: self._shift_week(k))
            bar.pack_start(btn, False, False, 0)
        last = end - timedelta(days=1)
        span = (f"{start.day} – {last.day} {last:%b %Y}" if start.month == last.month
                else f"{start.day} {start:%b} – {last.day} {last:%b %Y}")
        title = Gtk.Label(label=f"{span}   ·   week {start.isocalendar()[1]}", xalign=0)
        title.get_style_context().add_class("week-title")
        bar.pack_start(title, False, False, 8)
        codes = "/".join(CALENDAR_TYPES)
        count = Gtk.Label(label=f"{fmt_count(shown)} {codes} " + ("entry" if shown == 1 else "entries")
                          + "  ·  click a slot to add", xalign=0)
        count.get_style_context().add_class("week-hint")
        count.set_ellipsize(3)
        count.set_tooltip_text(
            "Shows every " + " and ".join(f"{self._calendar_type_name(c).lower()} ({c})"
                                          for c in CALENDAR_TYPES)
            + " note in this folder by the time in its file name — including ones added from the "
            "normal form. Click an empty slot to add one dated then. The search box narrows the "
            "week; the sidebar filters don't apply here.")
        bar.pack_start(count, True, True, 0)
        self.board.pack_start(bar, False, False, 0)

        # Day headings and the slot grid share per-column size groups, so the headings stay over
        # their columns although they sit outside the grid's scroller (and so don't scroll away).
        groups = [Gtk.SizeGroup(mode=Gtk.SizeGroupMode.HORIZONTAL) for _ in range(8)]
        head = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        head.get_style_context().add_class("week-head")
        gutter = Gtk.Label(label="")
        gutter.set_size_request(WEEK_GUTTER, -1)
        groups[0].add_widget(gutter)
        head.pack_start(gutter, False, False, 0)
        for d in range(7):
            day = start + timedelta(days=d)
            lbl = Gtk.Label(label=f"{day:%a} {day.day}")
            lbl.set_max_width_chars(1)   # natural width ~0, so hexpand shares the width equally
            ctx = lbl.get_style_context()
            ctx.add_class("week-day-head")
            if day.date() == today:
                ctx.add_class("today")
            lbl.set_hexpand(True)
            groups[d + 1].add_widget(lbl)
            head.pack_start(lbl, True, True, 0)
        self.board.pack_start(head, False, False, 0)

        grid = Gtk.Grid()
        grid.get_style_context().add_class("week-grid")
        columns: list[list[tuple[Note, Gtk.Widget]]] = [[] for _ in range(7)]
        hour_labels: dict[int, Gtk.Widget] = {}
        for slot in range(24 * 60 // slot_len):
            minute = slot * slot_len
            on_hour = minute % 60 == 0
            tlabel = Gtk.Label(label=f"{minute // 60:02d}:00" if on_hour else "", xalign=1, yalign=0)
            tlabel.get_style_context().add_class("week-time")
            tlabel.set_size_request(WEEK_GUTTER, -1)
            groups[0].add_widget(tlabel)
            grid.attach(tlabel, 0, slot, 1, 1)
            if on_hour:
                hour_labels[minute // 60] = tlabel
            for d in range(7):
                when = start + timedelta(days=d, minutes=minute)
                cell = Gtk.EventBox()
                ctx = cell.get_style_context()
                ctx.add_class("week-slot")
                if on_hour:
                    ctx.add_class("hour")
                if when.date() == today:
                    ctx.add_class("today")
                cell.set_hexpand(True)
                cell.set_tooltip_text(f"Add a journal / planner entry at {when:%a %d %b, %H:%M}")
                cell.connect("button-press-event", self._on_week_slot_click, when)
                # A drop target too: dragging an entry here moves it to this slot's time.
                cell.drag_dest_set(Gtk.DestDefaults.ALL, [], Gdk.DragAction.MOVE)
                cell.drag_dest_add_text_targets()
                cell.connect("drag-data-received", self._on_week_drop, when)
                inner = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
                inner.set_border_width(2)   # an EventBox ignores CSS padding (see _build_card)
                for at, note in sorted(buckets.get((d, slot), []), key=lambda p: p[0]):
                    entry = self._build_week_entry(note, at)
                    inner.pack_start(entry, False, False, 0)
                    columns[d].append((note, entry))
                cell.add(inner)
                groups[d + 1].add_widget(cell)
                grid.attach(cell, d + 1, slot, 1, 1)

        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.add(grid)
        self.board.pack_start(scroller, True, True, 0)
        # Each day is a column of its entries in time order, so ↑/↓ walk a day and ←/→ step
        # across days, the same as the kanban's columns.
        self._grid = [columns]

        adj = scroller.get_vadjustment()
        target = self._week_scroll
        anchor = hour_labels[WEEK_SCROLL_HOUR]

        def restore() -> bool:
            # Only once the grid has a real allocation; until then there's nothing to scroll to.
            if anchor.get_allocated_height() <= 1:
                return True
            adj.set_value(target if target is not None else anchor.get_allocation().y)
            adj.connect("value-changed", lambda a: setattr(self, "_week_scroll", a.get_value()))
            return False

        GLib.timeout_add(30, restore)

    def _build_week_entry(self, note: Note, when: datetime) -> Gtk.Widget:
        """One note inside a slot: its time and title, coloured by type. Selecting and opening go
        through the board's own handlers, so it behaves exactly like a card."""
        box = Gtk.EventBox()
        ctx = box.get_style_context()
        ctx.add_class("week-entry")
        ctx.add_class(f"type-{note.type}")
        box.set_can_focus(True)
        # Drag source BEFORE the click handler: both listen to button-press, and the click handler
        # returns True, which would stop emission before GTK's drag code ever saw the press.
        box.drag_source_set(Gdk.ModifierType.BUTTON1_MASK, [], Gdk.DragAction.MOVE)
        box.drag_source_add_text_targets()
        box.drag_source_set_icon_name("x-office-calendar-symbolic")
        box.connect("drag-data-get",
                    lambda _w, _ctx, data, _info, _t, p=note.path: data.set_text(p, -1))
        box.connect("button-press-event", self._on_week_entry_press, note)
        label = Gtk.Label(label=f"{when:%H:%M}  {note.title(80)}", xalign=0)
        label.set_ellipsize(3)
        label.set_max_width_chars(1)   # natural width ~0: the slot's width decides, not the title
        box.add(label)
        # The whole note on hover — a slot is too narrow to show more than the first words. Capped
        # in lines only because a tooltip taller than the screen is clipped, not scrolled.
        body, _flags = split_highlights(note.content)
        lines = body.strip().splitlines() or ["(empty)"]
        if len(lines) > WEEK_TOOLTIP_LINES:
            lines = lines[:WEEK_TOOLTIP_LINES] + [f"… {len(lines) - WEEK_TOOLTIP_LINES} more lines "
                                                  f"— click to read it all in the pane"]
        box.set_tooltip_text(
            f"{self._calendar_type_name(note.type)} · {when:%a %d %b %Y, %H:%M}\n\n"
            + "\n".join(lines)
            + "\n\nClick: read it in the pane · double-click: change its time or details · "
              "drag: move it to another slot")
        return box

    def _on_week_entry_press(self, _box, event, note: Note) -> bool:
        """Click selects (like a card); double-click opens the entry form to edit it — the
        calendar's "open appointment", rather than the card's open-in-your-editor."""
        if event.button == 1 and event.type == Gdk.EventType._2BUTTON_PRESS:
            # Deferred: a modal dialog's nested loop shouldn't run inside the press handler.
            GLib.idle_add(lambda: self._new_calendar_entry(None, note) and False)
            return True
        return self._on_card_click(note, event if event.type != Gdk.EventType._2BUTTON_PRESS else None)

    def _on_week_drop(self, _cell, _ctx, _x, _y, data, _info, _time, when: datetime) -> None:
        """An entry dropped on a slot: rename it to that slot's time (snapped to the slot start,
        the way Outlook moves an appointment). Dropping it back into its own slot does nothing."""
        path = (data.get_text() or "").strip()
        if not path or not os.path.isfile(path):
            return
        old = datetime.fromtimestamp(parse_created(parse_name(os.path.basename(path)).timestamp,
                                                   self.settings.timestamp_format,
                                                   os.path.getmtime(path)))
        slot = timedelta(minutes=WEEK_SLOT_MINUTES)
        if when <= old < when + slot:
            return
        # The drag's source widget is still alive until the drag ends, so the rebuild that
        # destroys it waits for the main loop.
        GLib.idle_add(self._retime_entry, path, when)

    def _retime_entry(self, path: str, when: datetime) -> bool:
        if self._editor_path == path:
            self._editor_flush()   # a pending pane edit must land in the file before it's renamed
        try:
            new_path = self.repo.retime(path, when, self.settings.timestamp_format)
        except OSError as err:
            self._message("Couldn't move the entry", f"{os.path.basename(path)}\n\n{err}")
            return False
        if self._sel_path == path:
            self._sel_path = new_path
        self.reload_notes()
        self._flash_status(f"Moved to {when:%a %d %b %Y, %H:%M}")
        return False

    def _on_week_slot_click(self, _cell, event, when: datetime) -> bool:
        if event.button == 1 and event.type == Gdk.EventType.BUTTON_PRESS:
            self._new_calendar_entry(when)
        return True

    def _new_calendar_entry(self, when: datetime | None, note: Note | None = None) -> None:
        """The calendar's appointment form: a journal or planner note, dated at the clicked slot
        (adjustable), with its priority, category and text. A past slot defaults to Journal, a
        future one to Planner. Given ``note`` it edits that entry instead — same fields, prefilled,
        and a changed date/time renames the file (see NoteRepository.retime)."""
        self._editor_flush()
        fmt = self.settings.timestamp_format
        if note is not None:
            if not os.path.isfile(note.path):
                return   # gone since the click (synced away, or moved by a drop)
            note = self.repo._to_note(note.path)   # fresh from disk — the pane may just have saved it
            when = datetime.fromtimestamp(parse_created(note.timestamp, fmt, note.mtime))
        dialog = Gtk.Dialog(title="Edit calendar entry" if note is not None else "New calendar entry",
                            transient_for=self, modal=True)
        dialog.add_button("Cancel", Gtk.ResponseType.CANCEL)
        save = dialog.add_button("Save", Gtk.ResponseType.OK)
        save.get_style_context().add_class("suggested-action")
        save.set_tooltip_text("Save (Ctrl+Enter)")
        dialog.set_default_size(620, 420)
        area = dialog.get_content_area()
        area.set_spacing(10)
        area.set_border_width(12)

        form = Gtk.Grid(column_spacing=12, row_spacing=8)
        area.pack_start(form, False, False, 0)

        def caption(text: str, row: int) -> None:
            lbl = Gtk.Label(label=text, xalign=1)
            lbl.get_style_context().add_class("dim-label")
            form.attach(lbl, 0, row, 1, 1)

        # Type — a two-button radio, J and P.
        caption("Type", 0)
        type_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        type_box.get_style_context().add_class("linked")
        type_buttons: dict[str, Gtk.RadioButton] = {}
        group = None
        for code in CALENDAR_TYPES:
            btn = Gtk.RadioButton.new_with_label_from_widget(
                group, f"{code} · {self._calendar_type_name(code)}")
            btn.set_mode(False)   # draw as a toggle button, not a radio dot
            group = group or btn
            type_buttons[code] = btn
            type_box.pack_start(btn, False, False, 0)
        if note is not None and note.type in type_buttons:
            type_buttons[note.type].set_active(True)
        else:
            type_buttons["J" if when <= datetime.now() else "P"].set_active(True)
        form.attach(type_box, 1, 0, 1, 1)

        # When — a date picker and the slot's time, both prefilled and adjustable.
        caption("When", 1)
        when_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        date_button = Gtk.MenuButton()
        date_label = Gtk.Label()
        date_button.add(date_label)
        popover = Gtk.Popover()
        picker = MonthPicker(when, on_pick=lambda _d: (show_date(), popover.popdown()))
        popover.add(picker)
        picker.show_all()
        date_button.set_popover(popover)

        def picked_date() -> datetime:
            return picker.selected

        def show_date(*_a) -> None:
            date_label.set_text(f"{picked_date():%a %d %b %Y}")

        show_date()
        when_box.pack_start(date_button, False, False, 0)

        def spin(value: int, upper: int) -> Gtk.SpinButton:
            sb = Gtk.SpinButton.new_with_range(0, upper, 1)
            sb.set_value(value)
            sb.set_wrap(True)
            sb.set_width_chars(2)
            sb.connect("output", lambda b: (b.set_text(f"{int(b.get_value()):02d}"), True)[1])
            return sb

        hour = spin(when.hour, 23)
        minute = spin(when.minute, 59)
        # Seconds aren't on the form, so an edit that leaves the time alone keeps the original's.
        seconds = when.second if note is not None else 0
        when_box.pack_start(hour, False, False, 0)
        when_box.pack_start(Gtk.Label(label=":"), False, False, 0)
        when_box.pack_start(minute, False, False, 0)
        form.attach(when_box, 1, 1, 1, 1)

        # Priority and category, from this folder's own tag set.
        caption("Priority", 2)
        prio = Gtk.ComboBoxText()
        for p in self.repo_cfg.priorities():
            prio.append(str(p), f"P{p}")
        prio.set_active_id(str(note.priority if note is not None and note.priority is not None
                               else self.repo_cfg.priority_default))
        if prio.get_active_id() is None:
            prio.set_active(0)
        form.attach(prio, 1, 2, 1, 1)
        caption("Category", 3)
        cat = Gtk.ComboBoxText()
        cat.append("", "—  none")
        for code, name in self.repo_cfg.categories:
            cat.append(code, f"{code} · {name}" if name else code)
        cat.set_active_id((note.category or "") if note is not None
                          else (self.selected_category or self.repo_cfg.default_category or ""))
        if cat.get_active_id() is None:
            cat.set_active_id("")
        form.attach(cat, 1, 3, 1, 1)

        # The text.
        view = Gtk.TextView()
        view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        view.set_left_margin(6)
        view.set_right_margin(6)
        view.set_top_margin(6)
        view.get_style_context().add_class("calendar-entry-text")
        text_scroller = Gtk.ScrolledWindow()
        text_scroller.set_shadow_type(Gtk.ShadowType.IN)
        text_scroller.set_min_content_height(160)
        text_scroller.add(view)
        if note is not None:
            view.get_buffer().set_text(note.content.rstrip("\n"))
        area.pack_start(text_scroller, True, True, 0)
        hint = Gtk.Label(label="Ctrl+Enter saves · Esc cancels", xalign=0)
        hint.get_style_context().add_class("dim-label")
        area.pack_start(hint, False, False, 0)

        def on_key(_w, ev) -> bool:
            ctrl_down = ev.state & Gdk.ModifierType.CONTROL_MASK
            if ctrl_down and ev.keyval in (Gdk.KEY_Return, Gdk.KEY_KP_Enter):
                dialog.response(Gtk.ResponseType.OK)
                return True
            return False

        dialog.connect("key-press-event", on_key)
        dialog.show_all()
        view.grab_focus()

        while True:
            resp = dialog.run()
            if resp != Gtk.ResponseType.OK:
                break
            buf = view.get_buffer()
            text = buf.get_text(buf.get_start_iter(), buf.get_end_iter(), True).strip()
            if not text:
                hint.set_text("Write something first — an empty entry isn't saved.")
                view.grab_focus()
                continue
            type_ = next(c for c, b in type_buttons.items() if b.get_active())
            at = picked_date().replace(hour=int(hour.get_value()), minute=int(minute.get_value()),
                                       second=seconds)
            priority, category = int(prio.get_active_id()), cat.get_active_id() or None
            try:
                if note is None:
                    path = self.repo.save_new(text, type_, priority, category, fmt, when=at)
                else:
                    # Tags and text first (that rename keeps the timestamp), then the time.
                    path = self.repo.update(note, text, type_, priority, category)
                    path = self.repo.retime(path, at, fmt)
            except OSError as err:
                hint.set_text(f"Couldn't save: {err}")
                continue
            # Jump the calendar to the week the entry landed in (the date may have been changed),
            # and select it so it opens in the pane.
            if not (self._week_start <= at < self._week_start + timedelta(days=7)):
                self._week_start = self._monday_of(at)
                self._week_scroll = None
            self._sel_path = path
            self.reload_notes()
            self._flash_status(f"{'Updated' if note is not None else 'Saved'} "
                               f"{self._calendar_type_name(type_).lower()} entry for "
                               f"{at:%a %d %b %Y, %H:%M}")
            break
        dialog.destroy()


    def _render_empty(self, folder_has_notes: bool, query: str) -> None:
        """A line of text where the cards would be, so a blank board always says WHY it's blank.

        The case worth naming is the launch filter (Settings.start_filter_today) landing on a day
        with nothing captured yet: that's a status line, not a dead end, and reading "No notes
        entered today" beats staring at an empty board wondering where the notes went.
        """
        if not folder_has_notes:
            text = "No notes in this folder yet"
        elif not query and self.settings.start_filter_today and self.filters.is_like(
                default_filter(self.settings)):
            # The state the app opens in.
            text = "No notes entered today or yesterday"
        elif not query and self.filters == FilterState(date_field=self.filters.date_field,
                                                       days={datetime.now().date()}):
            text = "No notes entered today"
        elif query:
            text = f"No note's text or file name contains “{query}”"
        else:
            text = "No notes match the filters"
        self._grid = []   # nothing to walk with the arrow keys; a stale grid would point at dead cards
        label = Gtk.Label(label=text)
        label.get_style_context().add_class("empty-board")
        label.set_valign(Gtk.Align.CENTER)
        self.board.pack_start(label, True, True, 0)

    def _render_list(self, notes: list[Note]) -> None:
        # A single vertically scrolling column of cards, in the chosen sort order.
        self.board_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        self.board.set_homogeneous(False)
        container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        container.set_border_width(4)
        col_cards: list[tuple[Note, Gtk.Widget]] = []
        for note in self._sorted(notes):
            card = self._build_card(note)
            col_cards.append((note, card))
            container.pack_start(card, False, False, 0)
        self.board.pack_start(container, True, True, 0)
        self._grid = [[col_cards]]  # one row, one column — ↑/↓ navigation works unchanged

    def _column_count(self) -> int:
        """How many whole cards of the configured max width fit across the board right now.

        Measured on the SCROLLER, never on the board inside it: the board only ever gets as wide as
        the grid it holds, so reading it would feed this calculation its own last answer and pin the
        view at one column forever. The scroller's allocation is the window's, independent of the
        content — hence the constant reserves below rather than measured ones.
        """
        width = self.board_scroller.get_allocated_width()
        if width <= 1:                      # not laid out yet (the very first render);
            width = self.get_size()[0]      # _on_board_allocate re-flows once GTK has measured it
        usable = width - 2 * BOARD_BORDER - SCROLLBAR_ALLOWANCE
        return max(1, usable // (max(80, self.settings.card_max_width) + COLUMN_GAP))

    def _render_columns(self, notes: list[Note]) -> None:
        """The list view, wrapped: no headings and no grouping — just every note in the chosen sort
        order, read left→right and then down, in as many columns as the window fits.

        Cards are pinned to `card_max_width` rather than sharing the width out, so they stay
        readable on a wide screen instead of stretching into unreadably long lines; the column
        count follows from that width, which is why widening the window adds a column instead of
        fattening the cards.
        """
        self.board_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        self.board.set_homogeneous(False)
        ncols = self._column_count()
        grid = Gtk.Grid(column_spacing=COLUMN_GAP, row_spacing=COLUMN_GAP, column_homogeneous=True)
        grid.set_border_width(4)
        # START, not FILL: a filling grid would stretch its columns past card_max_width whenever the
        # leftover width isn't enough for one more card.
        grid.set_halign(Gtk.Align.START)

        columns: list[list[tuple[Note, Gtk.Widget]]] = [[] for _ in range(ncols)]
        for i, note in enumerate(self._sorted(notes)):
            card = self._build_card(note)
            card.set_size_request(self.settings.card_max_width, -1)
            row, col = divmod(i, ncols)     # row-major: the reading order is left→right, then down
            grid.attach(card, col, row, 1, 1)
            columns[col].append((note, card))
        self.board.pack_start(grid, False, False, 0)
        # One grid row of ncols columns, each holding its cards top-to-bottom — so ↑/↓ walks a
        # column and ←/→ steps across, exactly as in the kanban.
        self._grid = [columns]
        self._columns_ncols = ncols

    def _on_board_allocate(self, _widget, _allocation) -> None:
        """Re-flow the columns view when a resize changes how many cards fit across. Only when the
        count actually changes — otherwise every pixel of a drag would rebuild the board."""
        if (self.minimal_mode or self.settings.view_mode != "columns"
                or self._reflow_pending or self._column_count() == self._columns_ncols):
            return
        self._reflow_pending = True
        GLib.idle_add(self._reflow_columns)   # never rebuild from inside a size-allocate

    def _reflow_columns(self) -> bool:
        self._reflow_pending = False
        if not self.minimal_mode and self.settings.view_mode == "columns" \
                and self._column_count() != self._columns_ncols:
            self.reload_notes()
        return False

    def _describe_filters(self, query: str) -> list[tuple[str, str]]:
        """The active filters as ``(dimension, what's picked)`` pairs, in the sidebar's own order so
        the line and the panel read the same way round.

        Values are spelled the way the sidebar spells them — "N · Note", "P2", a day as a date —
        rather than as raw codes, because the point is to be readable at a glance by someone who
        has just come back to a board they narrowed twenty minutes ago.
        """
        f = self.filters
        parts: list[tuple[str, str]] = []
        if query:
            parts.append(("Search", f'"{query}"'))

        type_names = dict(self.repo_cfg.types)
        cat_names = dict(self.repo_cfg.categories)

        def named(keys: set, names: dict, blank: str) -> str:
            out = []
            for key in sorted(keys, key=lambda k: (k is None, str(k))):
                if key is None:
                    out.append(blank)
                else:
                    label = names.get(key, "")
                    out.append(f"{key} · {label}" if label else str(key))
            return ", ".join(out)

        if f.types:
            parts.append(("Type", named(f.types, type_names, "untyped")))
        if f.categories:
            parts.append(("Category", named(f.categories, cat_names, "uncategorized")))
        if f.priorities:
            parts.append(("Priority", ", ".join(
                "no priority" if p is None else f"P{p}"
                for p in sorted(f.priorities, key=lambda p: (p is None, p)))))
        if f.days:
            parts.append(("Created" if f.date_field == BY_CREATED else "Updated",
                          self._describe_days(f.days)))
        if f.tags:
            parts.append(("Tag", ", ".join(f"#{t}" for t in sorted(f.tags))))
        if f.nonstandard != NS_INCLUDE:
            parts.append(("Non-standard",
                          "only these" if f.nonstandard == NS_ONLY else "hidden"))
        if f.taxonomy != TX_INCLUDE:
            parts.append(("Bad taxonomy",
                          "only these" if f.taxonomy == TX_ONLY else "hidden"))
        return parts

    @staticmethod
    def _describe_days(days: set) -> str:
        """A day selection in as few words as it takes. One day is a date; a contiguous run is a
        range (which is what a shift-click in the calendar makes, and listing thirty dates for it
        would be unreadable); anything scattered is just counted."""
        ordered = sorted(days)
        if len(ordered) == 1:
            return ordered[0].strftime("%-d %b %Y")
        contiguous = (ordered[-1].toordinal() - ordered[0].toordinal() + 1) == len(ordered)
        if contiguous:
            first, last = ordered[0], ordered[-1]
            if (first.year, first.month) == (last.year, last.month):
                return f"{first.strftime('%-d')}–{last.strftime('%-d %b %Y')}"
            return f"{first.strftime('%-d %b')} – {last.strftime('%-d %b %Y')}"
        return f"{fmt_count(len(ordered))} days"

    def _update_filter_summary(self, query: str, shown: int, total: int) -> None:
        """Draw (or hide) the line above the board. Hidden when nothing is filtering: a permanent
        "no filters" strip would be a line of furniture that never says anything."""
        # Remembered so a theme switch can redraw the line without a full reload — its colours are
        # baked in as markup, so they'd otherwise stay light-on-light until the next render.
        self._filter_summary_args = (query, shown, total)
        parts = self._describe_filters(query)
        if not parts or (self.settings.view_mode == "week" and not self.minimal_mode):
            # The week view ignores the sidebar filters (see reload_notes), so a line saying the
            # board is narrowed would be wrong there; the calendar's own bar says what it shows.
            self.filter_summary_row.hide()
            return
        muted = self._theme_color("fg_muted", "#808080")
        strong = self._theme_color("fg_strong", "#000000")
        chunks = [f'<span foreground="{muted}">{GLib.markup_escape_text(dim)}</span> '
                  f'<span foreground="{strong}" weight="bold">{GLib.markup_escape_text(value)}</span>'
                  for dim, value in parts]
        # A vertical bar between chunks, NOT the "·" the values themselves use: a type prints as
        # "N · Note", so "Type N · Note · Priority P2" reads as three filters instead of two.
        separator = f'<span foreground="{muted}">   │   </span>'
        count = f'<span foreground="{muted}">{fmt_count(shown)} of {fmt_count(total)}</span>'
        self.filter_summary.set_markup(separator.join([count] + chunks))
        self.filter_summary.set_tooltip_text(
            "\n".join(f"{dim}: {value}" for dim, value in parts))   # the full text when it ellipsises
        self.filter_summary_row.show()

    def _clear_everything(self) -> None:
        """The summary line's "clear": drop every filter AND the search box in one go. They are one
        thing from where the user is sitting — both narrow the board, and the line lists them
        together — so clearing half of it would leave the line still showing."""
        if self.search_bar.get_search_mode():
            self._set_search(False)      # also clears the entry text
        if self.filters.active():
            # The launch view first, the whole folder only on a second click — see
            # FilterState.reset_to_default for why straight-to-everything was the wrong default.
            self.filters.reset_to_default(default_filter(self.settings))
        self.reload_notes()

    def _update_tags_summary(self, notes: list[Note]) -> None:
        """Refresh the one-line #hashtag strip under the add field. With "show tags under add
        form" on: each tag + the number of notes that use it, most-used first. Off: a single
        compact count of total #hashtag occurrences, in place of listing the tags themselves
        (still shown — never fully hidden — just without spelling out every tag)."""
        occurrences = sum(len(_HASHTAG_RE.findall(n.content)) for n in notes)
        if occurrences == 0:
            self.tags_summary.hide()
            return
        if not self.settings.show_tags_summary:
            self.tags_summary.set_markup(
                f'<span alpha="70%">{fmt_count(occurrences)} '
                f'#tag use{"s" if occurrences != 1 else ""}</span>')
            self.tags_summary.set_tooltip_text(
                "Turn on \"Show #hashtag index under the add field\" in Settings to list them.")
            self.tags_summary.show()
            return
        counts: dict[str, int] = {}
        label: dict[str, str] = {}
        for n in notes:
            for tag in _hashtags(n.content):  # distinct per note → counts notes, not occurrences
                key = tag.lower()
                counts[key] = counts.get(key, 0) + 1
                label.setdefault(key, tag)
        items = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
        self.tags_summary.set_markup("   ".join(
            f'{_tag_span("#" + label[k], TAG_COLOR_HASH)}<span alpha="55%"> {fmt_count(c)}</span>'
            for k, c in items))
        self.tags_summary.set_tooltip_text(
            "   ".join(f"#{label[k]} ({fmt_count(c)})" for k, c in items))
        self.tags_summary.show()

    @staticmethod
    def _matches(note: Note, query: str) -> bool:
        """Does [note] match the search box text (already stripped and lowercased)?

        Content, then the metadata codes, then the FILE NAME — plain substring throughout. The name
        matters because in knotes the name IS the metadata: a whole name pasted off the copy button
        finds its one note, and a fragment of the tag block ("n~p2") lists every note carrying it,
        which no content search could do.

        The extension is part of what's matched, so a pasted "…~I.md" still lands. A query that came
        off the copy-PATH button instead is reduced to its last component first: matching the folder
        part would match every note in the folder, which is the opposite of a search."""
        name = os.path.basename(note.path.rstrip(os.sep + "/")).lower()
        wanted = query.rsplit("/", 1)[-1].rsplit("\\", 1)[-1]
        return (query in note.content.lower()
                or (note.type or "").lower().find(query) >= 0
                or (note.category or "").lower().find(query) >= 0
                or (bool(wanted) and wanted in name))

    def _build_column(self, cat_code: str, name: str, notes: list[Note],
                      col_cards: list[tuple[Note, Gtk.Widget]]) -> Gtk.Widget:
        """A kanban column: a header, then a collapsible subgroup for every *defined* type (always
        shown, even empty), each sorted by priority (highest first). Built cards (in visual order)
        are appended to [col_cards] for keyboard navigation."""
        column = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        column.get_style_context().add_class("column")
        # A modest minimum; columns expand past this to share the row width (screen-size sensitive).
        column.set_size_request(180, -1)

        header = Gtk.Label(xalign=0)
        header.set_markup(
            f'{GLib.markup_escape_text(name)}  '
            f'<span alpha="55%">{fmt_count(len(notes))}</span>')
        header.get_style_context().add_class("column-header")
        column.pack_start(header, False, False, 0)

        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        inner = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        inner.set_border_width(4)
        scroller.add(inner)
        column.pack_start(scroller, True, True, 0)

        type_order = [t for t, _ in self.repo_cfg.types]
        type_names = dict(self.repo_cfg.types)
        by_type: dict[str, list[Note]] = {}
        for n in notes:
            by_type.setdefault(n.type if n.type in type_order else "—", []).append(n)

        # Every defined type gets a subgroup, but only when it actually has notes — an empty
        # subgroup is just a header taking up space with nothing to show or collapse.
        groups = list(type_order)
        if "—" in by_type:
            groups.append("—")
        for t in groups:
            recs = self._sorted(by_type.get(t, []))
            if not recs:
                continue
            tname = "Untyped" if t == "—" else (type_names.get(t) or t)
            inner.pack_start(self._build_subgroup(cat_code, t, tname, recs, col_cards), False, False, 0)

        return column

    def _build_subgroup(self, cat_code: str, type_code: str, tname: str, recs: list[Note],
                        col_cards: list[tuple[Note, Gtk.Widget]]) -> Gtk.Widget:
        """A collapsible type subgroup: a clickable header (chevron + name + count), then its cards."""
        collapsed = (cat_code, type_code) in self._collapsed_groups
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)

        header = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        header.get_style_context().add_class("group-header")
        hbox = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        chevron = Gtk.Image.new_from_icon_name(
            "pan-end-symbolic" if collapsed else "pan-down-symbolic", Gtk.IconSize.MENU)
        hbox.pack_start(chevron, False, False, 0)
        label = Gtk.Label(xalign=0)
        label.set_markup(f'{GLib.markup_escape_text(tname)}  '
                         f'<span alpha="55%">{fmt_count(len(recs))}</span>')
        hbox.pack_start(label, True, True, 0)
        header.add(hbox)
        header.connect("clicked", lambda _b, key=(cat_code, type_code): self._toggle_group(key))
        box.pack_start(header, False, False, 0)

        if not collapsed:
            for note in recs:
                card = self._build_card(note)
                col_cards.append((note, card))
                box.pack_start(card, False, False, 0)
        return box

    def _toggle_group(self, key: tuple[str, str]) -> None:
        if key in self._collapsed_groups:
            self._collapsed_groups.discard(key)
        else:
            self._collapsed_groups.add(key)
        self.reload_notes()

    def _build_card(self, note: Note) -> Gtk.Widget:
        card = Gtk.EventBox()
        card.get_style_context().add_class("card")
        # Carry the merge marking across rebuilds (marks are kept by path, not widget).
        if note.path in self._merge_marks:
            card.get_style_context().add_class("merge-mark")
            if note.path == self._merge_marks[0]:
                card.get_style_context().add_class("merge-anchor")
        # A non-conforming file/folder (see NoteRepository.list_unrecognized): flag it and explain
        # the fix — the same Ctrl+Shift+<key> shortcut used to retag any note promotes it.
        if note.path in self._unrecognized_paths:
            card.get_style_context().add_class("needs-metadata")
            card.set_tooltip_text(
                "Needs metadata — select it and press Ctrl+Shift+<type/category letter> or "
                "Ctrl+Shift+<priority digit> to set it and file it in.")
        # Focusable so the ScrolledWindow auto-scrolls the selected card into view; clicking selects.
        card.set_can_focus(True)
        card.connect("button-press-event", lambda w, e, n=note: self._on_card_click(n, e))

        # Title on top, then any #hashtags from the body as chips beneath it. border_width (not CSS
        # padding) does the insetting — a GtkEventBox ignores CSS padding when allocating its child.
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=5)
        outer.set_border_width(12)

        tags = []
        if note.type:
            tags.append((note.type, TAG_COLOR_TYPE))
        tags.append((f"P{note.priority}" if note.priority is not None else "P·", TAG_COLOR_PRIO))
        if note.category:
            tags.append((note.category, TAG_COLOR_CAT))
        # The three meta tags get their OWN label in a fixed left gutter, never inlined into the
        # body text. An RTL note right-aligns its body (below), which — with the tags inline — swept
        # them over to the right edge, so the type/priority/category landed in a different place on
        # an Arabic card than on an English one. In a gutter the eye runs straight down one column
        # whatever the note's language. (The trade: a wrapped body's later lines start after the
        # gutter rather than under it.)
        # Order is type · priority · category, and the cluster is forced LTR so it reads
        # left-to-right ("T P3 Z") even on an Arabic card. (It used to be one label carrying an
        # LTR isolate, U+2066…U+2069; separate widgets get the same ordering from the box direction
        # and can be given a corner radius, which a Pango background run cannot have.)
        tag_label = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        tag_label.set_direction(Gtk.TextDirection.LTR)
        tag_label.set_valign(Gtk.Align.START)
        for text, colour in tags:
            tag_label.pack_start(_tag_chip(text, colour), False, False, 0)

        # A "needs metadata" placeholder: show its actual file/folder name (note.title() would show
        # its raw content, or "(empty)" for a folder — neither identifies which item this is).
        if note.path in self._unrecognized_paths:
            head, rest = [(os.path.basename(note.path.rstrip(os.sep + "/")), False)], []
        else:
            head, rest = note.card_preview(self.settings.preview_chars,
                                           self.settings.preview_tail_chars)
        wc = note.word_count()
        parts = [f'{fmt_count(wc)} word{"" if wc == 1 else "s"}']
        # Past a length (Settings: "Counts shown from"), a card also reports how much of the note has
        # been worked through. The highlight count shows even at zero — on a long note that IS the
        # signal, "nothing marked yet" — but the time-mark count only appears when there are .tmk
        # files to count, since a note with no recording has no notion of one to be at zero of.
        if len(note.content) >= self.settings.card_counts_min_chars:
            hl = note.highlight_count()
            parts.append(f'{fmt_count(hl)} highlight{"" if hl == 1 else "s"}')
            marks = note.time_mark_count()
            if marks:
                parts.append(f'{fmt_count(marks)} time mark{"" if marks == 1 else "s"}')
        # The attached files are listed in the pane's files panel; the card only says how many.
        if note.n_files:
            parts.append(f'{fmt_count(note.n_files)} file{"" if note.n_files == 1 else "s"}')
        # Rendered at the end of the action row rather than trailing the title: the title is what the
        # eye lands on when scanning the board, and a parenthetical of three numbers in front of the
        # note's own first words got in the way of reading it. Down with the actions it's a footer.
        counts = " · ".join(parts)

        # The #hashtags are shown once, as tinted spans at the end of the text: taken out of the
        # preview where they stand (the tag line, mostly) and appended after its last line. They
        # used to show twice — as plain text in the preview and again as a chip row beneath it.
        hashtags = _hashtags(note.content)
        if hashtags and note.path not in self._unrecognized_paths:
            head, rest = _split_first_line(_without_hashtags(
                head + ([("\n", False)] + rest if rest else [])))

        # Line 1 bold, the rest beneath it at normal weight — see Note.card_preview for why the
        # note's own line breaks are worth keeping.
        markup = f"<b>{self._runs_markup(head)}</b>" if head else ""
        if rest:
            markup += "\n" + self._runs_markup(rest)
        if hashtags:
            spans = " ".join(_tag_span("#" + h, TAG_COLOR_HASH) for h in hashtags)
            markup = f"{markup} {spans}" if markup else spans

        title = Gtk.Label(xalign=0)
        title.set_line_wrap(True)
        title.set_lines(4)
        title.set_ellipsize(3)  # END — ellipsize after the wrap cap
        title.set_valign(Gtk.Align.START)
        # Direction is per LINE, not per note: an Arabic line reads from the right and an English
        # one from the left, even inside the same note. Pango resolves each paragraph's direction
        # from its first strong letter and MIRRORS the alignment of an RTL one, so asking for LEFT
        # lands every Arabic line on the right and leaves every English line on the left. xalign
        # only matters when the text is narrower than the column (a short single line), and then
        # follows the direction the text opens in.
        title.set_justify(Gtk.Justification.LEFT)
        title.set_xalign(1.0 if _opens_rtl(markup) else 0.0)
        title.set_markup(markup)
        title.get_style_context().add_class("title")

        # LTR on the row too: widget direction comes from the locale, and under an RTL one
        # pack_start would fill from the right and put the gutter on the wrong side of every card.
        title_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        title_row.set_direction(Gtk.TextDirection.LTR)
        title_row.pack_start(tag_label, False, False, 0)
        title_row.pack_start(title, True, True, 0)
        outer.pack_start(title_row, False, False, 0)

        outer.pack_start(self._build_card_actions(note, counts), False, False, 0)

        card.add(outer)
        return card

    def _date_markup(self, note: Note) -> str:
        """``created → last modified`` for the action row, the second coloured when they diverge.

        The two dates come from different places on purpose. Creation is read from the FILE NAME's
        timestamp, which is fixed at capture and survives every later edit; modification is the
        filesystem's mtime, which any edit bumps. Comparing the pair is the only way to see that a
        note captured a fortnight ago is still being worked on — the mtime alone can't tell you
        that, and neither can the name.

        Coloured by whole calendar days apart, not elapsed hours: the user reads these as dates, so
        "three days" should mean three dates on the calendar and not 72 hours to the minute.
        """
        created_epoch = parse_created(note.timestamp, self.settings.timestamp_format, note.mtime)
        created = datetime.fromtimestamp(created_epoch)
        modified = datetime.fromtimestamp(note.mtime)
        drift = (modified.date() - created.date()).days
        colour = ""
        if drift > STALE_ALERT_DAYS:
            colour = STALE_ALERT_COLOR
        elif drift > STALE_WARN_DAYS:
            colour = STALE_WARN_COLOR
        shown = modified.strftime("%Y-%m-%d")
        if colour:
            shown = f'<span foreground="{colour}" weight="bold">{shown}</span>'
        return f'{created.strftime("%Y-%m-%d %H:%M")}  →  {shown}'

    def _runs_markup(self, runs) -> str:
        """Pango markup for a line of note text, painting the clauses marked ``==like this==``.

        Same colours as the reading pane, from the same setting, so a highlight looks the same
        wherever the note is shown — and the markers themselves are never rendered anywhere.
        """
        out = []
        for text, highlighted in runs:
            escaped = GLib.markup_escape_text(text)
            if highlighted:
                bg, fg = self._highlight_colors()
                escaped = f'<span background="{bg}" foreground="{fg}">{escaped}</span>'
            out.append(escaped)
        return "".join(out)

    def _highlight_colors(self) -> tuple[str, str]:
        """(background, foreground) for a highlighted clause, both as ``#rrggbb``.

        Hex, not Gdk's own ``to_string()``: that gives ``rgb(242,199,68)``, which Gdk parses back
        but **Pango does not** — and an unparseable colour in a span attribute makes the whole
        markup invalid, which on a card means its text renders as nothing at all. Hex is the one
        spelling both accept. See _refresh_highlight_tag for why the foreground is computed.
        """
        colour = Gdk.RGBA()
        if not colour.parse(self.settings.highlight_color):
            colour.parse(Settings.highlight_color)   # an unparseable setting falls back, not crashes
        luminance = 0.299 * colour.red + 0.587 * colour.green + 0.114 * colour.blue
        return _rgba_hex(colour), "#101010" if luminance > 0.55 else "#f5f5f5"

    def _build_card_actions(self, note: Note, counts: str = "") -> Gtk.Widget:
        """The row of visible actions under a card: open folder · open note · copy name · copy path,
        with the note's counts (words, highlights, time marks) closing the row.

        These all had shortcuts already (Ctrl+O, Enter) or no way in at all (the two copies), which
        made them invisible unless you'd read the F1 list. Spelling them out on the card is the
        point; each tooltip still names the shortcut, so the row teaches the keys rather than
        replacing them.

        The two "open" actions carry words as well as an icon — they're the ones reached for
        constantly. The copies are icon-only with the exact value they'd copy in the tooltip, which
        is also what keeps the pair from being confused with each other.
        """
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)
        # LTR like the title row: an Arabic note makes its card's text RTL, and the actions would
        # otherwise mirror into a different order on Arabic cards than on English ones.
        row.set_direction(Gtk.TextDirection.LTR)
        row.get_style_context().add_class("card-actions")

        def add(icon: str, text: str, tooltip: str, action, enabled: bool = True) -> None:
            button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
            button.get_style_context().add_class("card-action")
            button.set_tooltip_text(tooltip)
            button.set_sensitive(enabled)
            # Not a focus stop: the board's Tab order and arrow-key walk run over whole cards, and
            # four focusable buttons per card would sit between one card and the next.
            button.set_can_focus(False)
            content = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
            content.pack_start(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.MENU), False, False, 0)
            if text:
                content.pack_start(Gtk.Label(label=text), False, False, 0)
            button.add(content)
            # Selecting the card first keeps "clicking a card selects it" true for the whole card,
            # and leaves the keyboard on the note you just acted on.
            button.connect("clicked", lambda _b: (self._on_card_click(note), action()))
            row.pack_start(button, False, False, 0)

        # A "needs metadata" placeholder has no companion folder of its own (see _open_files_folder):
        # shown but disabled, so the row doesn't change shape and the tooltip says why.
        settled = note.path not in self._unrecognized_paths
        add("folder-open-symbolic", "open folder",
            "Open the note's files folder in the file manager — created on demand  (Ctrl+O)"
            if settled else
            "Needs metadata before it can have a files folder — retag it with Ctrl+Shift+<key> first",
            lambda: self._reveal_folder(note.path), enabled=settled)
        add("document-edit-symbolic", "open note",
            "Open the note in your editor  (Enter)",
            lambda: self._open_external(note))

        name = os.path.basename(note.path.rstrip(os.sep + "/"))
        add("edit-copy-symbolic", "", f"Copy the file name to the clipboard\n{name}",
            lambda: self._copy_to_clipboard(name, "file name"))
        add("insert-link-symbolic", "", f"Copy the full path to the clipboard\n{note.path}",
            lambda: self._copy_to_clipboard(note.path, "path"))

        # Import: the backend files the note by its filename's type code, so a "needs metadata"
        # placeholder can't go yet — disabled with the reason, like "open folder" above.
        add("document-send-symbolic", "",
            "Import this note into the Nibras backend — it becomes a record there and the file "
            "moves to this folder's imported/ archive  (Ctrl+I)"
            if settled else
            "Needs metadata before it can be imported — retag it with Ctrl+Shift+<key> first",
            # Deferred like delete: a successful import rebuilds the board, destroying this button.
            lambda: GLib.idle_add(self._import_to_backend, note), enabled=settled)

        # Delete: on the card as well as on Ctrl+D, and disabled — with the reason in the tooltip —
        # for a note that still owns files. The row keeps its shape either way, so the button never
        # moves under the pointer between one card and the next.
        attached = companion_files(note.path)
        deletable = not companion_has_content(note.path)
        add("user-trash-symbolic", "",
            "Move this note to the .deleted folder  (Ctrl+D)" if deletable else
            f"Has {len(attached)} attached file(s) — empty its folder first, "
            "then the note can be deleted",
            # Deferred to an idle callback: confirming rebuilds the board, which destroys this very
            # button while its "clicked" signal is still being emitted from it.
            lambda: GLib.idle_add(self._delete_selected), enabled=deletable)

        dates = self._date_markup(note)
        if dates:
            # Pushed to the right-hand end: the buttons are what the row is for, and two timestamps
            # in the middle of them would be read as another control.
            stamp = Gtk.Label(xalign=1)
            stamp.set_markup(dates)
            stamp.get_style_context().add_class("card-counts")
            row.pack_end(stamp, False, False, 0)

        if counts:
            # No LTR isolate needed here, unlike when this rode on the title: the row itself is
            # forced LTR (above), so an Arabic note can't reverse "(4 words)" into "(words 4)".
            label = Gtk.Label(label=counts, xalign=0)
            label.get_style_context().add_class("card-counts")
            label.get_style_context().add_class("card-counts-strong")
            label.set_margin_start(8)
            row.pack_start(label, False, False, 0)
        return row

    # ------------------------------------------------ card selection / navigation

    def _on_card_click(self, note: Note, event=None) -> bool:
        pos = self._pos_of_path(note.path)
        if pos is not None:
            self._board_active = True
            self._select(*pos)
            # Ctrl+click toggles this card's merge mark (add several, then Ctrl+Shift+G) — matches the
            # familiar "Ctrl+click to add to a selection" gesture; a plain click just selects.
            if event is not None and (event.state & Gdk.ModifierType.CONTROL_MASK):
                self._toggle_merge_mark()
            # Double-click opens the note in the editor, same as Enter on a selected card.
            elif event is not None and event.type == Gdk.EventType._2BUTTON_PRESS:
                self._open_external(note)
        return True  # don't propagate

    def _pos_of_path(self, path: str | None) -> tuple[int, int, int] | None:
        if not path:
            return None
        for gr, grid_row in enumerate(self._grid):
            for gc, col in enumerate(grid_row):
                for i, (note, _card) in enumerate(col):
                    if note.path == path:
                        return (gr, gc, i)
        return None

    def _first_cell(self) -> tuple[int, int, int] | None:
        for gr, grid_row in enumerate(self._grid):
            for gc, col in enumerate(grid_row):
                if col:
                    return (gr, gc, 0)
        return None

    def _apply_select(self, gr: int, gc: int, idx: int, grab: bool) -> None:
        if self._selected_widget is not None:
            self._selected_widget.get_style_context().remove_class("selected")
        note, card = self._grid[gr][gc][idx]
        self._sel = (gr, gc, idx)
        self._sel_path = note.path
        self.selected_note = note
        self._selected_widget = card
        card.get_style_context().add_class("selected")
        # The pane follows the selection — clicking or arrowing to a card is what "open this note
        # for editing" means, so there's no second gesture to learn.
        if self.settings.editor_pane_visible:
            self._editor_load(note)
        if grab:
            card.grab_focus()  # focusing scrolls it into view in both scrollers

    def _select(self, gr: int, gc: int, idx: int) -> None:
        # A user-initiated move (arrow keys, click) releases any scroll pin from a preceding rebuild:
        # grab_focus below should scroll the newly selected card into view unopposed. The pin only
        # needs to survive the rebuild itself, not the next thing the user does — otherwise, during
        # a fast retag/Down mass-editing run, it freezes the view while the selection walks off it.
        self._unpin_scroll()
        self._board_active = True
        self._apply_select(gr, gc, idx, grab=True)

    def _restore_selection(self) -> None:
        """After a rebuild, re-select the same note (by path); if it's gone but we're in board mode,
        fall back to the first card.

        The selection is re-applied, but the keyboard is only taken back if nobody is writing. The
        pane's autosave writes the note, the folder watch sees the write and reloads the board a
        moment later, and this runs — so re-grabbing unconditionally pulls the caret out of the note
        roughly a second and a half into every sentence typed in the pane, which reads as the app
        randomly refusing to be typed into. Same for a quick-list burst: each Enter writes the list's
        file, and the reload that follows would pull the caret out before the next line.
        """
        self._selected_widget = None
        self.selected_note = None
        self._sel = None
        pos = self._pos_of_path(self._sel_path)
        if pos is None and self._board_active:
            pos = self._first_cell()
        if pos is None:
            self._sel_path = None
            return
        # Any text field counts — the add field, the pane, and the quick note/task entries, whose
        # own file write triggers this same watch reload right after each Enter.
        writing = isinstance(self.get_focus(), (Gtk.TextView, Gtk.Entry))
        self._apply_select(*pos, grab=self._board_active and not writing)

    def _focus_board(self) -> None:
        """Enter board navigation (Esc from the text field). Re-selects the last card, else the first."""
        self._board_active = True
        pos = self._pos_of_path(self._sel_path) or self._first_cell()
        if pos is not None:
            self._apply_select(*pos, grab=True)

    def _focus_textfield(self) -> None:
        """Leave board navigation and put the cursor in the add field (selection kept, just unfocused)."""
        self._board_active = False
        if self._selected_widget is not None:
            self._selected_widget.get_style_context().remove_class("selected")
        self.textview.grab_focus()

    def _toggle_card_focus(self) -> None:
        """F12: flip focus between the card area and the add field. In minimal mode the board is
        hidden, so it selects/deselects the lone 'just added' card (then Ctrl+Shift+<code> retags it,
        Ctrl+D deletes it and Ctrl+I imports it)."""
        if self.minimal_mode:
            if self._minimal_card_selected:
                self._deselect_minimal_card()
            else:
                self._select_minimal_card()
        elif self._board_active:
            self._focus_textfield()
        else:
            self._focus_board()

    def _select_minimal_card(self) -> None:
        if self._last_note is None or self._last_note_card is None:
            return
        self._minimal_card_selected = True
        self.selected_note = self._last_note
        self._board_active = True
        self._last_note_card.get_style_context().add_class("selected")
        self._last_note_card.grab_focus()  # take focus off the text field so shortcuts target the card

    def _deselect_minimal_card(self) -> None:
        self._minimal_card_selected = False
        self._board_active = False
        self.selected_note = None
        if self._last_note_card is not None:
            self._last_note_card.get_style_context().remove_class("selected")
        self.textview.grab_focus()

    def _navigate(self, kv: int) -> None:
        if self._sel is None:
            pos = self._first_cell()
            if pos is not None:
                self._select(*pos)
            return
        gr, gc, idx = self._sel
        col = self._grid[gr][gc]
        if kv == Gdk.KEY_Up:
            if idx > 0:
                self._select(gr, gc, idx - 1)
            elif gr > 0 and gc < len(self._grid[gr - 1]) and self._grid[gr - 1][gc]:
                self._select(gr - 1, gc, len(self._grid[gr - 1][gc]) - 1)
        elif kv == Gdk.KEY_Down:
            if idx < len(col) - 1:
                self._select(gr, gc, idx + 1)
            elif gr + 1 < len(self._grid) and gc < len(self._grid[gr + 1]) and self._grid[gr + 1][gc]:
                self._select(gr + 1, gc, 0)
        elif kv in (Gdk.KEY_Left, Gdk.KEY_Right):
            step = -1 if kv == Gdk.KEY_Left else 1
            c = gc + step
            while 0 <= c < len(self._grid[gr]):
                if self._grid[gr][c]:
                    self._select(gr, c, min(idx, len(self._grid[gr][c]) - 1))
                    return
                c += step

    def _import_selected(self) -> None:
        """Ctrl+I: import the selected card. Said as a message for a "needs metadata" placeholder,
        because the key never sees the card button's disabled state."""
        note = self.selected_note
        minimal_card = self.minimal_mode and self._minimal_card_selected
        if note is None or (self._sel is None and not minimal_card):
            return
        if note.path in self._unrecognized_paths:
            self._message("This note needs metadata first",
                          "The backend picks the record type from the file name's type code — "
                          "retag it with Ctrl+Shift+<key>, then import.")
            return
        self._import_to_backend(note)

    def _import_to_backend(self, note: Note) -> bool:
        """Push one note to the backend as a record (POST /api/import/notes). Confirmed first: on
        success the backend moves the file into imported/, so the card leaves the board. The call
        runs off the UI thread; the outcome comes back via idle_add."""
        self._editor_flush()   # import what's on disk, so an unsaved pane edit must land first
        url, user, pw = self._backend_conn()
        if not url:
            self._message("No backend configured",
                          "Set the backend URL in Settings (Nibras backend) to import notes.")
            return False
        kind = dict(self.repo_cfg.types).get(note.type) or note.type or "?"
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.YES_NO, text=f"Import this note into the backend as a {kind}?")
        dialog.format_secondary_text(
            f"{note.title(80)}\n\nBackend: {url}\n"
            "The file moves to this folder's imported/ archive afterwards.")
        dialog.set_default_response(Gtk.ResponseType.YES)
        resp = dialog.run()
        dialog.destroy()
        if resp != Gtk.ResponseType.YES:
            return False

        self._flash_status("Importing to backend…")
        path = note.path

        def worker():
            record, error = import_note(url, user, pw, path)
            GLib.idle_add(self._import_done, path, record, error)

        threading.Thread(target=worker, daemon=True).start()
        return False

    def _import_done(self, path: str, record: dict | None, error: str) -> bool:
        if record is None:
            self._message("Import failed", f"{os.path.basename(path)}\n\n{error}")
            return False
        self._merge_marks = [p for p in self._merge_marks if p != path]
        if self._sel_path == path:
            self._sel_path = None   # moved to imported/; don't try to restore it
        if self.minimal_mode and self._minimal_card_selected and self.selected_note \
                and self.selected_note.path == path:
            # Same as deleting the lone minimal card: drop the selection, back to the add field.
            self._minimal_card_selected = False
            self.selected_note = None
            self._board_active = False
            self.reload_notes()
            self.textview.grab_focus()
        else:
            self.reload_notes()
        self._flash_status(f"Imported → {record.get('module', '?')} #{record.get('id', '?')}:  "
                           f"{record.get('displayTitle') or ''}")
        return False

    def _message(self, text: str, detail: str) -> None:
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.INFO,
            buttons=Gtk.ButtonsType.OK, text=text)
        dialog.format_secondary_text(detail)
        dialog.run()
        dialog.destroy()

    def _delete_selected(self) -> None:
        self._editor_flush()   # the pane may hold an unwritten edit to the note being confirmed
        note = self.selected_note
        minimal_card = self.minimal_mode and self._minimal_card_selected
        if note is None or (self._sel is None and not minimal_card):
            return
        # A note that still owns files isn't deletable from here at all — see NoteRepository.delete.
        # Said as a message rather than a silent no-op, because Ctrl+D reaches this without ever
        # seeing the card button's disabled state.
        attached = companion_files(note.path)
        if companion_has_content(note.path):
            blocked = Gtk.MessageDialog(
                transient_for=self, modal=True, message_type=Gtk.MessageType.INFO,
                buttons=Gtk.ButtonsType.OK, text="This note still has attached files")
            names = "\n".join(os.path.basename(p) for p in attached[:5])
            blocked.format_secondary_text(
                f"Its files folder holds {len(attached)} file(s):\n{names}\n\n"
                "Empty the folder first (Ctrl+O opens it), then the note can be deleted.")
            blocked.run()
            blocked.destroy()
            return
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.YES_NO, text="Move this note to the .deleted folder?")
        dialog.format_secondary_text(self._delete_summary(note))
        # Focus/highlight "Yes" (not "No") so a confirming Enter/click is the default action.
        dialog.set_default_response(Gtk.ResponseType.YES)
        yes_button = dialog.get_widget_for_response(Gtk.ResponseType.YES)
        if yes_button is not None:
            yes_button.grab_focus()
        resp = dialog.run()
        dialog.destroy()
        if resp != Gtk.ResponseType.YES:
            return
        self.repo.delete(note)
        self._merge_marks = [p for p in self._merge_marks if p != note.path]  # drop it if marked
        self._sel_path = None  # it's gone; don't try to restore it
        if minimal_card:
            self._minimal_card_selected = False
            self.selected_note = None
            self._board_active = False
            self.reload_notes()
            self.textview.grab_focus()
            return
        gr, gc, idx = self._sel
        # Keep the view where it is: after the rebuild, select the card that slides into the deleted
        # one's place (the note below, or the one above if we deleted the last).
        col = self._grid[gr][gc]
        if idx + 1 < len(col):
            neighbour = col[idx + 1][0]
        elif idx - 1 >= 0:
            neighbour = col[idx - 1][0]
        else:
            neighbour = None
        self._reload_keeping_scroll(neighbour.path if neighbour is not None else None)

    def _delete_summary(self, note: Note) -> str:
        """A readable summary for the delete confirmation — the note's tags/time then the first few
        lines of its body — so it's easy to be sure it's the right note."""
        meta = []
        if note.type:
            meta.append(f"type {note.type}")
        if note.priority is not None:
            meta.append(f"p{note.priority}")
        if note.category:
            meta.append(f"cat {note.category}")
        meta.append(note.time_text())
        header = "  ·  ".join(meta)
        body = note.preview(8).strip()
        if len(body) > 400:
            body = body[:400].rstrip() + " …"
        return f"{header}\n\n{body}" if body else header

    def _open_files_folder(self) -> None:
        """Ctrl+O on a selected card: create the note's companion folder *on demand* and reveal it in
        the file manager (Nemo), so files can be dropped straight in. Nothing is created until you ask
        for it here — plain notes never get an empty folder. The card's file count refreshes on the next
        reload (e.g. saving a note or switching folders).

        A "needs metadata" placeholder has no companion folder of its own — note.path *is* the raw
        file/folder itself, so companion_dir(note.path) would compute a bogus sibling (e.g. a
        placeholder folder "Photos" would get a spurious "Photos.d" next to it). Ignored until the
        item is promoted (Ctrl+Shift+<tag>) into a real note."""
        note = self.selected_note
        if note is None or self._sel is None:
            return
        if note.path in self._unrecognized_paths:
            return
        self._reveal_folder(note.path)

    # Cached answer to "which tab flags does the installed Nemo understand?" — see _nemo_tab_flags.
    _nemo_flags: list[str] | None = None

    @classmethod
    def _nemo_tab_flags(cls) -> list[str]:
        """The flags this Nemo understands for "new tab in the window that's already open", probed
        once per session and cached. Empty when Nemo is missing or too old for them.

        Worth the probe because Popen can't tell us: it only raises when the *binary* is missing, so
        a Nemo too old for a flag would parse-error, exit, open nothing — and the fallback chain in
        _reveal_folder would never fire, leaving Ctrl+O silently doing nothing. ``--help`` parses
        locally and returns in milliseconds; the timeout is only a guard.

        Both flags are needed. ``--tabs`` alone opens the URIs as tabs of a BRAND-NEW window;
        ``--existing-window`` is what makes them land in the running one.
        """
        if cls._nemo_flags is None:
            try:
                done = subprocess.run(["nemo", "--help"], capture_output=True, text=True, timeout=3)
                help_text = done.stdout + done.stderr
                cls._nemo_flags = [f for f in ("--existing-window", "--tabs") if f in help_text]
            except (OSError, subprocess.SubprocessError):
                cls._nemo_flags = []   # no Nemo, or it didn't answer — the fallbacks cover it
        return cls._nemo_flags

    def _reveal_folder(self, note_path: str) -> None:
        """Create (on demand) a note's companion folder and reveal it in the file manager.

        Opened as a new TAB in the Nemo window that's already up rather than yet another window:
        with auto-open-folder on, a capture session fires this on every save, and a window per note
        buries the desktop within minutes. ``--existing-window --tabs`` still opens a window when
        none exists, so the first call behaves as before.
        """
        folder = companion_dir(note_path)
        try:
            os.makedirs(folder, exist_ok=True)
        except OSError:
            return
        if IS_WINDOWS:
            commands = [["explorer", os.path.normpath(folder)]]
        elif IS_MACOS:
            commands = [["open", folder]]
        else:
            # Most specific first: a tab in the open Nemo, plain Nemo, then the freedesktop default.
            commands = [["nemo", folder], ["xdg-open", folder]]
            flags = self._nemo_tab_flags()
            if flags:
                commands.insert(0, ["nemo", *flags, folder])
        for cmd in commands:
            try:
                subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                return
            except OSError:
                continue

    def _quick_append(self) -> None:
        """Space on a selected card: open a one-line field under it to append a comment to the note,
        without opening the full editor. Enter appends + saves; Esc cancels."""
        self._editor_flush()   # the append is built from note.content — flush before reading it
        note = self.selected_note
        if note is None or self._sel is None or self._append_entry is not None:
            return
        outer = self._selected_widget.get_child() if self._selected_widget is not None else None
        if outer is None:
            return
        entry = Gtk.Entry()
        entry.set_placeholder_text("Append a comment…  (Enter to add · Esc to cancel)")
        entry.connect("activate", self._commit_append)
        entry.connect("key-press-event", self._append_key)
        outer.pack_start(entry, False, False, 0)
        entry.show()
        self._append_entry = entry
        self._append_note = note
        entry.grab_focus()

    def _append_key(self, _entry, event) -> bool:
        if event.keyval == Gdk.KEY_Escape:
            self._close_append()
            self._focus_board()   # return to card navigation
            return True
        return False

    def _commit_append(self, entry) -> None:
        text = entry.get_text().strip()
        note = self._append_note
        self._close_append()
        if not text or note is None:
            self._focus_board()
            return
        # append_template's "$date" is substituted with now, formatted per append_date_format —
        # e.g. template "\n---$date:\n" + format "%Y-%m-%d %H:%M" -> "\n---2026-07-22 10:30:\n".
        # The template is edited as plain single-line text (see SettingsDialog), so a literal
        # "\n" (backslash + n) stands for a newline and is unescaped here, after substitution.
        stamp = datetime.now().strftime(self.settings.append_date_format)
        separator = self.settings.append_template.replace("$date", stamp).replace("\\n", "\n")
        new_content = note.content.rstrip() + separator + text + "\n"
        self.repo.update(note, new_content, note.type, note.priority, note.category)
        self._board_active = True
        self._reload_keeping_scroll(note.path)   # keep the note selected + the view put

    def _close_append(self) -> None:
        if self._append_entry is not None:
            parent = self._append_entry.get_parent()
            if parent is not None:
                parent.remove(self._append_entry)
            self._append_entry = None
            self._append_note = None

    def _toggle_merge_mark(self) -> None:
        """Ctrl+M: add/remove the selected card from the merge set. The first note marked is the
        survivor (keeps its tags/filename); the rest fold into it, in mark order, on Ctrl+Shift+M."""
        note = self.selected_note
        if note is None or self._sel is None:
            return
        if note.path in self._merge_marks:
            self._merge_marks.remove(note.path)
        else:
            self._merge_marks.append(note.path)
        self._apply_merge_classes()
        self._apply_header_title()

    def _apply_merge_classes(self) -> None:
        """Reflect the merge set on the cards: every marked card gets ``merge-mark``; the survivor
        (first marked) also gets ``merge-anchor``."""
        anchor = self._merge_marks[0] if self._merge_marks else None
        for grid_row in self._grid:
            for col in grid_row:
                for note, card in col:
                    ctx = card.get_style_context()
                    marked = note.path in self._merge_marks
                    (ctx.add_class if marked else ctx.remove_class)("merge-mark")
                    (ctx.add_class if note.path == anchor else ctx.remove_class)("merge-anchor")

    def _apply_header_title(self) -> None:
        """Set the header title, appending a live merge hint while notes are marked so it's obvious
        the mark registered and how many are staged."""
        n = len(self._merge_marks)
        hint = f"   ·   ⛓ {fmt_count(n)} marked — Ctrl+Shift+M to merge, Esc to clear" if n else ""
        self.header.set_title(getattr(self, "_base_title", APP_NAME) + hint)

    def _clear_merge_marks(self) -> None:
        if self._merge_marks:
            self._merge_marks = []
            self._apply_merge_classes()
            self._apply_header_title()

    def _merge_marked(self) -> None:
        """Ctrl+Shift+M: fold the marked notes into the first-marked one and move the rest to the
        ``merged`` subfolder, keeping the view put."""
        self._editor_flush()   # merge reads note.content — write the pane's edit through first
        by_path = {n.path: n for row in self._grid for col in row for (n, _c) in col}
        marked = [by_path[p] for p in self._merge_marks if p in by_path]
        if len(marked) < 2:
            dialog = Gtk.MessageDialog(
                transient_for=self, modal=True, message_type=Gtk.MessageType.INFO,
                buttons=Gtk.ButtonsType.OK, text="Mark at least two notes to merge")
            dialog.format_secondary_text(
                "Select a card and press Ctrl+M to mark it (first one marked is the survivor); "
                "mark the others, then press Ctrl+Shift+M.")
            dialog.run()
            dialog.destroy()
            return
        survivor, others = marked[0], marked[1:]
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.YES_NO,
            text=f"Merge {fmt_count(len(others))} note(s) into the survivor?")
        dialog.format_secondary_text(self._merge_summary(survivor, others))
        dialog.set_default_response(Gtk.ResponseType.YES)
        yes_button = dialog.get_widget_for_response(Gtk.ResponseType.YES)
        if yes_button is not None:
            yes_button.grab_focus()
        resp = dialog.run()
        dialog.destroy()
        if resp != Gtk.ResponseType.YES:
            return
        if self.repo.merge(survivor, others) is None:
            return
        self._merge_marks = []
        self._reload_keeping_scroll(survivor.path)

    def _merge_summary(self, survivor: Note, others: list[Note]) -> str:
        """Confirmation text: which note survives (keeps its tags) and which fold into it."""
        def line(tag: str, note: Note) -> str:
            title = note.title(60, 0).strip() or "(empty)"
            return f"{tag}  {note.time_text()}  ·  {title}"
        lines = [line("survivor →", survivor)]
        lines += [line("  merge  ·", n) for n in others]
        return "\n".join(lines)

    def _edit_selected_by_shortcut(self, event) -> bool:
        """Ctrl+Shift+<letter/digit> on the selected card: set its type/category or priority,
        renaming the file. Always sets (never clears) — safe to mass-apply the same tag across
        many notes without checking each one's current state first; un-setting a type/category
        still needs the full editor. Returns True if it matched a code.

        With cards marked (Ctrl+click / Ctrl+M), the change applies to every marked card as well as
        the selected one — mark several, then one keystroke sets them all. The marks follow the
        renamed files, so a second retag (type, then priority, …) hits the same set again."""
        # Retagging RENAMES the file, so the pane's pending write has to land on the old name first;
        # the rename then carries it, and the reload re-opens the note under its new name.
        self._editor_flush()
        note = self.selected_note
        if note is None:
            return False
        ch = self._base_char(event)
        if not ch:
            return False
        up = ch.upper()
        field, value = None, None
        code = next((c for c in self.type_codes if c.upper() == up), None)
        if code is not None:
            field, value = "type", code
        else:
            code = next((c for c in self.category_codes if c.upper() == up), None)
            if code is not None:
                field, value = "category", code
            elif ch.isdigit() and int(ch) in self.priority_buttons:
                field, value = "priority", int(ch)
            else:
                return False

        by_path = {n.path: n for row in self._grid for col in row for (n, _c) in col}
        targets = [note] + [by_path[p] for p in self._merge_marks
                            if p in by_path and p != note.path]
        renamed: dict[str, str] = {}
        for target in targets:
            new_type, new_cat = target.type, target.category
            if target.priority is not None:
                new_prio = target.priority
            else:
                lo = self.repo_cfg.priority_min
                hi = max(self.repo_cfg.priority_max, lo)
                new_prio = min(max(self.repo_cfg.priority_default, lo), hi)
            if field == "type":
                new_type = value
            elif field == "category":
                new_cat = value
            else:
                new_prio = value
            if target.path in self._unrecognized_paths:
                new_path = self.repo.promote(target, new_type, new_prio, new_cat,
                                             self.settings.timestamp_format)
            else:
                new_path = self.repo.update(target, target.content, new_type, new_prio, new_cat)
            if new_path:
                renamed[target.path] = new_path
        self._merge_marks = [renamed.get(p, p) for p in self._merge_marks]
        # Follow the edited note itself — a note may be retagged several times in a row (type,
        # then priority, then category, ...), and jumping selection to a NEIGHBOUR each time (the
        # earlier fix here) compounds: after a few edits you're focused several cards away from
        # where you started. The real fix for "mass-tagging relocates the card" is sorting by
        # "Date created" instead of "Last updated" — that keeps a retagged note's position stable,
        # so following it here is a no-op move in that mode, and a correct one in every other mode.
        self._reload_keeping_scroll(renamed.get(note.path, note.path))
        if len(targets) > 1:
            label = f"P{value}" if field == "priority" else value
            self._flash_status(f"{label} set on {fmt_count(len(targets))} notes")
        return True

    def _save_draft(self) -> None:
        """Persist whatever is in the add field (with the current tags) so a forgotten note isn't
        lost — and write out the editor pane's pending edit for the same reason."""
        self._editor_flush()
        start, end = self.buffer.get_bounds()
        text = self.buffer.get_text(start, end, True).strip()
        if text:
            self.repo.save_new(text, self.selected_type, self.priority,
                               self.selected_category, self.settings.timestamp_format)
            self.buffer.set_text("")

    def _on_delete_event(self, _widget, _event) -> bool:
        self._save_draft()
        # The strip's height is only knowable while it's on screen, so read it now rather than
        # from a settings value that a drag never updated.
        if self.settings.quick_panels_visible:
            self._save_quick_height()
            self.settings.save()
        return False  # proceed with closing

    def _render_last_note(self, note: Note | None) -> None:
        for child in self.last_note_bar.get_children():
            self.last_note_bar.remove(child)
        self._last_note = note
        self._last_note_card = None
        if note is None:
            self.last_note_bar.hide()
            self._minimal_card_selected = False
            return
        card = Gtk.EventBox()
        card.set_can_focus(True)  # so it can take focus off the text field (F12 select in minimal mode)
        card.get_style_context().add_class("last-note")
        card.connect("button-press-event", lambda w, e, n=note: self._open_and_stop(n))
        self._last_note_card = card

        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        row.set_border_width(16)  # inset content from the card border (EventBox ignores CSS padding)
        row.set_direction(Gtk.TextDirection.LTR)  # tags stay left even under an RTL locale
        caption = Gtk.Label(label="Just added", xalign=0)
        caption.get_style_context().add_class("last-note-caption")
        row.pack_start(caption, False, False, 0)
        # Tags left-to-right in a fixed order: type, priority, subject (category).
        if note.type:
            row.pack_start(self._badge(note.type, "type"), False, False, 0)
        row.pack_start(self._badge(f"P{note.priority}" if note.priority is not None else "P·", "prio"), False, False, 0)
        if note.category:
            row.pack_start(self._badge(note.category, "cat"), False, False, 0)
        title = Gtk.Label(label=self._word_preview(note), xalign=0)
        title.set_ellipsize(3)  # END
        title.set_single_line_mode(True)
        if note.is_rtl:
            title.set_xalign(1.0)
        title.get_style_context().add_class("title")
        row.pack_start(title, True, True, 0)

        card.add(row)
        self.last_note_bar.pack_start(card, True, True, 0)
        self.last_note_bar.show_all()
        # Keep the card selected across an edit/reload (e.g. after changing its type in minimal mode).
        if self.minimal_mode and self._minimal_card_selected:
            self.selected_note = note
            card.get_style_context().add_class("selected")
            card.grab_focus()

    def _open_and_stop(self, note: Note) -> bool:
        self._open_external(note)
        return True  # handled — don't propagate the click further

    def _badge(self, text: str, kind: str) -> Gtk.Label:
        label = Gtk.Label(label=text)
        ctx = label.get_style_context()
        ctx.add_class("badge")
        ctx.add_class(kind)
        return label

    def _word_preview(self, note: Note) -> str:
        """First N + last M words of the note (both configurable) plus the word count — a compact
        'just added' preview, e.g. 'buy milk … the shop  (7w)'."""
        # Markers stripped like everywhere else the note is shown; this strip is a plain label with
        # no markup, so the highlight itself isn't painted here — only its "==" kept out of sight.
        words = split_highlights(note.content)[0].split()
        n = len(words)
        head = max(0, self.settings.preview_head_words)
        tail = max(0, self.settings.preview_tail_words)
        if n <= head + tail or head + tail == 0:
            preview = " ".join(words)
        else:
            preview = " ".join(words[:head]) + " … " + " ".join(words[-tail:])
        return f"{preview}   ({fmt_count(n)}w)" if preview else f"({fmt_count(n)}w)"

    # ------------------------------------------------ minimal side-note mode

    def _toggle_minimal_mode(self) -> None:
        self._set_minimal_mode(not self.minimal_mode)

    def _on_mode_button(self, _button: Gtk.ToggleButton, mode: str) -> None:
        """Toolbar mode group: any click (even un-toggling the active one) selects [mode];
        _sync_mode_buttons then enforces the exactly-one-active radio look."""
        if not self._suppress_minimal_toggle:
            self._set_view_mode(mode)

    def _set_view_mode(self, mode: str) -> None:
        """'kanban' / 'list' / 'columns' switch the board view (leaving minimal mode if on);
        'minimal' enters the minimal side-note mode. Persisted; the board re-renders immediately."""
        if mode == "minimal":
            self._set_minimal_mode(True)   # syncs the buttons via _apply_minimal_mode
            return
        changed = self.settings.view_mode != mode
        self.settings.view_mode = mode
        if self.minimal_mode:
            self._set_minimal_mode(False)  # saves + reloads + syncs the buttons
        else:
            self.settings.save()
            if changed:
                self.reload_notes()
        self._sync_mode_buttons()

    def _sync_mode_buttons(self) -> None:
        """Reflect (minimal_mode, view_mode) in the toolbar group without re-triggering handlers.
        Kanban is the fallback for an unrecognised stored mode — the same way reload_notes
        dispatches — so the lit button always names what's actually on the board."""
        self._suppress_minimal_toggle = True
        mode = self.settings.view_mode
        self.minimal_button.set_active(self.minimal_mode)
        self.list_button.set_active(not self.minimal_mode and mode == "list")
        self.columns_button.set_active(not self.minimal_mode and mode == "columns")
        self.week_button.set_active(not self.minimal_mode and mode == "week")
        self.kanban_button.set_active(not self.minimal_mode and mode not in ("list", "columns", "week"))
        self._suppress_minimal_toggle = False

    def _on_sort_selected(self, combo: Gtk.ComboBoxText) -> None:
        """Toolbar sort dropdown — persisted; re-sorts the board immediately."""
        code = combo.get_active_id()
        if code and code != self.settings.sort_by:
            self.settings.sort_by = code
            self.settings.save()
            self.reload_notes()

    def _on_auto_folder_toggled(self, button: Gtk.ToggleButton) -> None:
        """Toolbar toggle: open the just-saved note's files folder after every save (persisted)."""
        self.settings.auto_open_folder = button.get_active()
        self.settings.save()

    def _set_minimal_mode(self, enabled: bool) -> None:
        self.minimal_mode = enabled
        self.settings.minimal_mode = enabled
        self.settings.save()
        self._apply_minimal_mode()
        self.reload_notes()

    def _apply_minimal_mode(self) -> None:
        """Minimal side-note mode: hide the board and the full tag chip rows; show only the just-added
        card, a compact selected-tags line and the text field. Off = back to the list/kanban view."""
        minimal = self.minimal_mode
        # Keep the toolbar mode group in sync without re-triggering _on_mode_button.
        self._sync_mode_buttons()
        if not minimal and self._minimal_card_selected:
            # leaving minimal mode drops the lone-card selection
            self._minimal_card_selected = False
            self._board_active = False
            self.selected_note = None
        self.board_scroller.set_no_show_all(minimal)
        self.board_scroller.set_visible(not minimal)
        # The sidebar goes with the board: minimal mode is about occupying the least screen space,
        # and a filter bar next to a hidden board would also block _shrink_to_minimum.
        self.sidebar.set_no_show_all(minimal)
        self.sidebar.set_visible(not minimal)
        self.chips_row.set_no_show_all(minimal)
        self.chips_row.set_visible(not minimal)
        self.minimal_tags.set_visible(minimal)
        if minimal:
            self._refresh_minimal_tags()
            if self._pre_minimal_size is None:
                self._pre_minimal_size = self.get_size()   # remember the size to restore on exit
            self.unmaximize()
            # After the board/chips are hidden, shrink the window to the minimum that fits what's left.
            GLib.idle_add(self._shrink_to_minimum)
            self.textview.grab_focus()
        elif self._pre_minimal_size is not None:
            w, h = self._pre_minimal_size
            self._pre_minimal_size = None
            GLib.idle_add(self.resize, w, h)   # restore the pre-minimal size

    def _shrink_to_minimum(self) -> bool:
        self.resize(1, 1)   # GTK clamps to the minimum size the remaining content needs
        return False

    def _setup_layout_watch(self) -> None:
        """Reflect the active keyboard layout in the indicator and auto-update when it switches
        (e.g. via Caps Lock) — GdkKeymap reports the effective layout's direction, RTL for Arabic."""
        self._keymap = Gdk.Keymap.get_for_display(self.get_display())
        self._keymap.connect("state-changed", lambda *_: self._sync_layout_indicator())
        self._keymap.connect("direction-changed", lambda *_: self._sync_layout_indicator())
        self._sync_layout_indicator()

    def _sync_layout_indicator(self) -> None:
        rtl = self._keymap.get_direction() == Pango.Direction.RTL
        self.layout_label.set_text("ع" if rtl else "EN")

    # ----------------------------------------------------- note-folder watch (Syncthing etc.)

    def _setup_note_dir_watch(self) -> None:
        """Auto-refresh the board when the active note folder changes on disk — e.g. Syncthing
        pulling in notes written on another machine — instead of only picking them up on the next
        manual refresh (Ctrl+R / the toolbar button)."""
        self._watch_monitor: Gio.FileMonitor | None = None
        self._watch_reload_pending = False
        self._rearm_note_dir_watch()

    def _rearm_note_dir_watch(self) -> None:
        """(Re)point the filesystem watch at the current repo's folder — called on startup and
        whenever the active folder changes (see _on_repo_changed)."""
        if self._watch_monitor is not None:
            self._watch_monitor.cancel()
            self._watch_monitor = None
        gfile = Gio.File.new_for_path(self.repo.directory)
        try:
            monitor = gfile.monitor_directory(Gio.FileMonitorFlags.NONE, None)
        except GLib.Error:
            return  # e.g. a filesystem without inotify support — the manual refresh still works
        monitor.connect("changed", self._on_note_dir_changed)
        self._watch_monitor = monitor  # keep a reference; an unreferenced GFileMonitor stops firing

    def _on_note_dir_changed(self, *_args) -> None:
        # Syncthing writes several files per sync batch, each its own filesystem event — coalesce
        # a burst into a single reload a moment after the last change, instead of thrashing.
        if self._watch_reload_pending:
            return
        self._watch_reload_pending = True
        GLib.timeout_add(700, self._debounced_dir_reload)

    def _debounced_dir_reload(self) -> bool:
        self._watch_reload_pending = False
        self.reload_notes()
        return False  # one-shot timeout

    def _refresh_minimal_tags(self) -> None:
        """One-line summary of the selected tags for minimal mode, order: type · pN · subject.
        Rendered big/bold/coloured so the active options stand out at a glance."""
        esc = GLib.markup_escape_text
        parts = []
        if self.selected_type:
            parts.append(f"<span foreground='#4fc3f7'>{esc(self.selected_type)}</span>")
        parts.append(f"<span foreground='#ffb74d'>p{self.priority}</span>")
        if self.selected_category:
            parts.append(f"<span foreground='#81c784'>{esc(self.selected_category)}</span>")
        sep = "  <span foreground='#7a7a7a'>·</span>  "
        self.minimal_tags.set_markup(
            f"<span size='large' weight='bold'>{sep.join(parts)}</span>")

    # ----------------------------------------------------- new notes

    def _send(self) -> None:
        start, end = self.buffer.get_bounds()
        text = self.buffer.get_text(start, end, True).strip()
        if not text:
            return
        # Always a new note; the chosen tags stay selected for the next one (sticky).
        path = self.repo.save_new(text, self.selected_type, self.priority,
                                  self.selected_category, self.settings.timestamp_format)
        if self.settings.auto_open_folder:
            self._reveal_folder(path)  # toolbar toggle: drop the note's files in right away
        # Saving ends board navigation, so the cursor lands back in the add field ready for the next
        # note. Order matters: _reset_input's grab_focus would otherwise be undone a line later by
        # reload_notes → _restore_selection (which re-grabs the selected card while _board_active) or
        # by _render_last_note (which grabs the lone card in minimal mode). Clearing both flags first
        # stops the re-grab at the source rather than racing it with a second grab afterwards.
        # The card keeps its "selected" look, just unfocused — the same deal Esc/_focus_textfield
        # gives you, since _sel_path survives and _restore_selection re-applies the style.
        self._board_active = False
        self._minimal_card_selected = False
        if self._selected_widget is not None:
            self._selected_widget.get_style_context().remove_class("selected")
        self._reset_input()
        self.reload_notes()
        # Immediate save feedback: scroll the list so the just-saved note (added at the end) is visible.
        GLib.idle_add(self._scroll_board_to_bottom)

    def _scroll_board_to_bottom(self) -> bool:
        """Scroll the note list to the bottom, revealing the most recently saved note. In kanban mode
        the board doesn't scroll vertically, so this is a harmless no-op there."""
        adj = self.board_scroller.get_vadjustment()
        if adj is not None:
            adj.set_value(max(adj.get_lower(), adj.get_upper() - adj.get_page_size()))
        return False

    def _reload_keeping_scroll(self, keep_path: str | None) -> None:
        """Rebuild the board but keep the view put: re-select ``keep_path`` and restore the scroll
        offset of its scroller. Works in list view (board scroller) and kanban (the card's own column
        scroller). Used by delete and merge so the list doesn't jump to the top/bottom on a focus
        grab. Falls back to the input field when nothing is left to select."""
        pos = self._pos_of_path(keep_path)  # look it up in the still-current (pre-reload) grid
        card = self._grid[pos[0]][pos[1]][pos[2]][1] if pos is not None else None
        # A retag renames the file, so ``keep_path`` is a name the pre-reload grid has never seen and
        # the lookup above misses — measure the card we're standing on instead, or the offset reads
        # as 0 and "keep the view put" becomes "jump to the top" on every Ctrl+Shift+<code>.
        scroller = self._card_scroller(card or self._selected_widget)
        adj = scroller.get_vadjustment() if scroller is not None else None
        scroll = adj.get_value() if adj is not None else 0.0
        self._sel_path = keep_path
        self.reload_notes()  # _restore_selection re-selects keep_path by path
        if self._sel is None:
            self._focus_textfield()
        GLib.idle_add(self._restore_scroll, scroll)

    def _card_scroller(self, card: Gtk.Widget | None) -> Gtk.ScrolledWindow | None:
        """The nearest ScrolledWindow ancestor of a card: the board scroller in list view, or the
        card's own column scroller in kanban (where the board itself doesn't scroll)."""
        w = card
        while w is not None and not isinstance(w, Gtk.ScrolledWindow):
            w = w.get_parent()
        return w

    def _restore_scroll(self, value: float) -> bool:
        """Put the view back to a previously captured scroll offset after a rebuild, so it stays put
        instead of jumping. Targets the re-selected card's scroller (board in list view, column
        scroller in kanban) and clamps to the new content height."""
        scroller = self._card_scroller(self._selected_widget) if self._selected_widget else None
        if scroller is None:
            return False
        self._apply_scroll(scroller, value)
        # On this idle pass the rebuilt cards still haven't been size-allocated: the adjustment
        # reports the pre-rebuild (often empty) content height, so the clamp above can floor the
        # offset to 0, and the scroll-into-view GTK does for the re-focused card aims at a card
        # whose position isn't known yet. Either one drops the view at the top — which is what lost
        # your place on every Ctrl+Shift+<code> retag. Hold the offset until the layout settles.
        self._pin_scroll(scroller, value)
        return False

    @staticmethod
    def _apply_scroll(scroller: Gtk.ScrolledWindow, value: float) -> None:
        adj = scroller.get_vadjustment()
        if adj is not None:
            adj.set_value(min(value, max(adj.get_lower(), adj.get_upper() - adj.get_page_size())))

    def _pin_scroll(self, scroller: Gtk.ScrolledWindow, value: float) -> None:
        """Hold ``scroller`` at ``value`` while the rebuilt cards are measured and laid out: re-apply
        it on every content-height change ("changed") and after every allocation of the scroller —
        the point at which GTK has already done its own scroll-into-view for the focused card — then
        let go after SCROLL_PIN_MS so a later, deliberate scroll is never fought."""
        self._unpin_scroll()
        adj = scroller.get_vadjustment()
        if adj is None:
            return
        def reapply(*_args) -> None:
            self._apply_scroll(scroller, value)

        self._scroll_pin = [(adj, adj.connect("changed", reapply)),
                            (scroller, scroller.connect("size-allocate", reapply))]
        self._scroll_pin_timeout = GLib.timeout_add(SCROLL_PIN_MS, self._unpin_scroll, True)

    def _unpin_scroll(self, from_timeout: bool = False) -> bool:
        """Stop holding the scroll offset — the layout has settled, or a new rebuild has taken over."""
        for obj, handler in self._scroll_pin:
            obj.disconnect(handler)
        self._scroll_pin = []
        if self._scroll_pin_timeout is not None and not from_timeout:
            GLib.source_remove(self._scroll_pin_timeout)
        self._scroll_pin_timeout = None
        return False

    def _on_focus_in(self, *_args) -> bool:
        """When the window gains focus, put the cursor in the add field so a paste is immediately
        ready — unless the user is navigating cards or has the search box open."""
        if not self._board_active and not self.search_bar.get_search_mode():
            self.textview.grab_focus()
        return False

    def _apply_defaults(self) -> None:
        """Set the tag selection a fresh note starts with (sticky thereafter, until changed)."""
        self._select_type(self.repo_cfg.default_type if self.repo_cfg.default_type in self.type_buttons else None)
        self._select_category(
            self.repo_cfg.default_category if self.repo_cfg.default_category in self.category_buttons else None)
        lo, hi = self.repo_cfg.priority_min, max(self.repo_cfg.priority_max, self.repo_cfg.priority_min)
        self._set_priority(min(max(self.repo_cfg.priority_default, lo), hi))

    # -------------------------------------------------------- tag bank

    _completers: list[TagCompleter] = []
    _bank: list[tagbank.Tag] = []
    _bank_stamp: tuple[str, float] | None = None   # (path, mtime) the cache was read from

    def tag_bank(self) -> list[tagbank.Tag]:
        """The hashtag vocabulary, re-read whenever the file changes on disk.

        Checked by mtime rather than watched: the completer asks for the bank on every keystroke
        that could open it, a stat is far cheaper than the parse, and the file is one the user
        edits by hand in another window — noticing that without a restart is the point.
        """
        path = tagbank.default_bank_path(HERE, self.settings.tag_bank_path)
        # Written on first use rather than shipped: the bank is per-machine and never tracked, so a
        # fresh checkout has none. Cheap to attempt — it returns immediately once the file exists.
        tagbank.ensure_bank(path)
        try:
            stamp = (path, os.path.getmtime(path))
        except OSError:
            stamp = (path, 0.0)
        if stamp != self._bank_stamp:
            self._bank_stamp = stamp
            self._bank = tagbank.load_bank(path)
        return self._bank

    def _completer_for(self, view: Gtk.TextView) -> TagCompleter | None:
        """The completer attached to whichever view has the keyboard, or None."""
        for completer in self._completers:
            if completer.view is view:
                return completer
        return None

    def _active_completer(self) -> TagCompleter | None:
        return next((c for c in self._completers if c.active), None)

    def _copy_to_clipboard(self, text: str, what: str) -> None:
        """Put ``text`` on the clipboard and say so in the status bar. The message isn't decoration:
        a copy that succeeds looks exactly like a click that missed the button."""
        Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD).set_text(text, -1)
        self._flash_status(f"Copied {what}:  {text}")

    # GLib source id of the pending "put the folder path back" timeout, 0 when none is armed.
    _status_flash: int = 0

    def _flash_status(self, message: str) -> None:
        """Show a transient message where the status bar's folder path is, then restore it. A second
        flash restarts the clock rather than letting the first one's timeout cut it short."""
        self.status_path.set_text(message)
        self.status_path.set_tooltip_text(message)
        if self._status_flash:
            GLib.source_remove(self._status_flash)
        self._status_flash = GLib.timeout_add_seconds(4, self._end_status_flash)

    def _end_status_flash(self) -> bool:
        self._status_flash = 0
        self._refresh_status()
        return False  # one-shot

    def _refresh_status(self) -> None:
        """Update the status bar (active folder path + save key) and the send button's tooltip."""
        keys = "Enter" if self.settings.save_on_enter else "Ctrl+Enter"
        path = self.settings.active_repo_path()
        self.status_path.set_text(path)
        self.status_path.set_tooltip_text(path)
        self.status_key.set_text(f"Save: {keys}")
        if hasattr(self, "send_button"):
            self.send_button.set_tooltip_text(f"Save note ({keys})")

    def _populate_repo_combo(self) -> None:
        """Fill the folder picker with the configured repo codes and select the active one."""
        self._suppress_repo = True
        self.repo_combo.remove_all()
        for code, _path in self.settings.repos():
            self.repo_combo.append(code, code)
        self.repo_combo.set_active_id(self.settings.active_repo_code())
        self._suppress_repo = False
        self.repo_combo.set_visible(len(self.settings.repos()) > 1)

    def _select_repo(self, index: int) -> bool:
        """Switch to the index-th (1-based) note folder via its picker; no-op if out of range.
        Returns True if there was such a folder (so the shortcut is consumed)."""
        repos = self.settings.repos()
        if not (1 <= index <= len(repos)):
            return False
        self.repo_combo.set_active_id(repos[index - 1][0])  # fires _on_repo_changed if it changed
        return True

    def _seed_repo_configs(self) -> None:
        """Ensure every configured folder has its own tag config, seeded from the global settings.
        Idempotent: RepoConfig.load only writes when the folder has no .knotes.json yet, so existing
        per-folder options are never overwritten. Skips folders that don't exist on disk."""
        for _code, path in self.settings.repos():
            full = os.path.expanduser(path)
            if os.path.isdir(full):
                RepoConfig.load(full, self.settings)

    def _apply_start_filter(self) -> None:
        """The launch view (filters.default_filter): today and yesterday, non-standard items hidden.
        Also what a folder opens on the first time it's switched to."""
        self.filters.restore(default_filter(self.settings))

    def _on_repo_changed(self, combo: Gtk.ComboBoxText) -> None:
        """Switch the active note folder: new notes go there and the board shows its notes. The text
        being composed stays in the field, so the menu redirects where the current note will land."""
        if self._suppress_repo:
            return
        self._editor_flush()   # the pane's note belongs to the folder being left
        code = combo.get_active_id()
        if not code or code == self.settings.active_repo_code():
            return
        old_code = self.settings.active_repo_code()
        self.settings.active_repo = code
        self.settings.save()
        self.repo = NoteRepository(self.settings.active_repo_path())
        # Each folder has its own tag options — load them and rebuild the chips for the new folder.
        self.repo_cfg = RepoConfig.load(self.settings.active_repo_path(), self.settings)
        self._build_chips()
        # Filters name codes from the folder we're leaving; carried over they'd silently hide most
        # of the new folder. Starting clean was worse: the whole folder rendered, pinning the CPU on
        # a big one. So each folder keeps its own picks — stash the old folder's, bring back the new
        # one's, and a folder not yet visited this session opens like a launch (today + yesterday).
        self._repo_filters[old_code] = self.filters.snapshot()
        self._filters_before_search = None   # a search's stash belongs to the folder being left
        if code in self._repo_filters:
            self.filters.restore(self._repo_filters[code])
        else:
            self.filters.clear()
            self._apply_start_filter()
        self._refresh_taxonomy()
        self._apply_defaults()
        self._sel_path = None      # selection belonged to the previous folder's board
        self._board_active = False
        self._refresh_status()
        self.reload_notes()
        self._rearm_note_dir_watch()   # follow the switch — watch the new folder, not the old one
        self._sync_backend_taxonomy()  # the new folder may bind its own backend
        self._apply_app_theme()        # recolour the chrome so the switch is visible

    def _reset_input(self) -> None:
        """Clear the editor; tag selections are kept (sticky) for the next note."""
        self.buffer.set_text("")
        self.textview.grab_focus()

    def _sync_editor_height(self) -> bool:
        """Resize the editor to fit its content (between EDITOR_MIN/MAX_HEIGHT), so it grows as lines
        are added and scrolls vertically once full — without propagate_natural_height's phantom
        horizontal scrollbar. Runs on idle after each buffer change."""
        width = self.textview.get_allocated_width()
        if width <= 1:
            return False  # not laid out yet; the next buffer change will retry
        _min, nat = self.textview.get_preferred_height_for_width(width)
        target = max(EDITOR_MIN_HEIGHT, min(nat, EDITOR_MAX_HEIGHT))
        if self.text_scroller.get_min_content_height() != target:
            self.text_scroller.set_min_content_height(target)
        return False  # don't repeat the idle callback

    def _sync_quick_entry_height(self, scroller: Gtk.ScrolledWindow, alloc) -> None:
        """Make the quick note/task entries as tall as the add field at rest. Only read while the
        field is empty; once it grows with its text the entries stay where they were."""
        if self.buffer.get_char_count() > 0 or not getattr(self, "quick_entries", None):
            return
        for entry in self.quick_entries.values():
            # A size request counts the CSS margin (.quick-entry's gap above it); add it back.
            margin = entry.get_style_context().get_margin(entry.get_state_flags())
            height = alloc.height + margin.top + margin.bottom
            if entry.get_size_request()[1] != height:
                entry.set_size_request(-1, height)

    def _open_external(self, note: Note) -> None:
        """Open the note's .md in the configured opener, or the system default."""
        self._open_path(note.path, "the note", self.settings.opener.strip())

    def _open_attachment(self, path: str) -> None:
        """Open one of a note's attached files (a click on its name on the card).

        Always the system default handler, never the configured ``opener``: that setting exists to
        pick a text editor for the .md (e.g. "xed"), and an attachment is just as likely to be a
        PDF, an image or a spreadsheet — which that editor couldn't open.
        """
        self._open_path(path, os.path.basename(path), "")

    def _open_path(self, path: str, what: str, opener: str) -> None:
        argv = ([opener] if opener else [DEFAULT_OPENER]) + [path]
        try:
            if not opener and IS_WINDOWS:
                os.startfile(os.path.normpath(path))  # no shell default binary to exec
            else:
                subprocess.Popen(argv)
        except OSError as exc:
            dialog = Gtk.MessageDialog(
                transient_for=self, modal=True, message_type=Gtk.MessageType.ERROR,
                buttons=Gtk.ButtonsType.OK, text=f"Couldn't open {what} with '{argv[0]}'")
            dialog.format_secondary_text(str(exc))
            dialog.run()
            dialog.destroy()

    # ----------------------------------------------------------- search

    def _set_search(self, active: bool) -> None:
        self.search_button.set_active(active)
        self.search_bar.set_search_mode(active)
        if active:
            # Opening the box costs nothing: the board stays as it is until a query is submitted.
            # It used to clear the filters and re-render right here — which drew every card in
            # the folder before the box had even appeared, a multi-second stall on a big folder.
            # The clearing now happens on Enter, where only the matches get drawn.
            # Focus now AND once the reveal has finished: Gtk.SearchBar reveals the entry through a
            # GtkRevealer, so at this instant the widget is not yet mapped and a grab on it is
            # dropped when the animation completes — which is why clicking the button used to leave
            # the keyboard on the board while Ctrl+F appeared to work.
            self.search_entry.grab_focus()
            GLib.timeout_add(250, self._focus_search_entry)
        else:
            self.search_entry.set_text("")
            self.textview.grab_focus()

    def _focus_search_entry(self) -> bool:
        """Put the caret in the search entry once it is actually on screen. One-shot."""
        if self.search_bar.get_search_mode() and self.search_entry.get_mapped():
            self.search_entry.grab_focus()
        return False

    def _on_search_activate(self, entry: Gtk.SearchEntry) -> None:
        """Enter in the search box: apply what's typed."""
        query = entry.get_text().strip().lower()
        if query != self._search_query:
            self._search_query = query
            # Searching runs over everything. A search that quietly ran inside yesterday's sidebar
            # selection is the worst kind of empty result: the note IS there, the filter hid it,
            # and nothing on screen says so. Cleared here, with the query, so the one render is of
            # the matches — and stashed, so ending the search brings the picks back.
            if query and self.filters.active():
                self._filters_before_search = self.filters.snapshot()
                self.filters.clear()
            self.reload_notes()

    def _on_search_changed(self, entry: Gtk.SearchEntry) -> None:
        """Typing doesn't search (see the entry's setup) — but emptying the box does un-search."""
        if not entry.get_text().strip() and self._search_query:
            self._search_query = ""
            self._restore_filters_after_search()
            self.reload_notes()

    def _restore_filters_after_search(self) -> None:
        """Put back the sidebar picks a search cleared — unless new ones were made meanwhile, which
        are the user's latest word. Without this, ending a search drew the whole unfiltered folder:
        the same stall the search used to cost on the way in."""
        saved, self._filters_before_search = self._filters_before_search, None
        if saved is not None and not self.filters.active():
            self.filters.restore(saved)

    # -------------------------------------------------------- shortcuts

    def _on_key_press(self, _widget, event) -> bool:
        mods = event.state & Gtk.accelerator_get_default_mod_mask()
        ctrl = bool(mods & Gdk.ModifierType.CONTROL_MASK)
        alt = bool(mods & Gdk.ModifierType.MOD1_MASK)
        shift = bool(mods & Gdk.ModifierType.SHIFT_MASK)
        kv = event.keyval

        # Reflect the active keyboard layout: if this key would produce an Arabic letter show "ع",
        # otherwise "EN". (Control chars aren't alpha, so shortcuts don't flip it.)
        uni = Gdk.keyval_to_unicode(event.keyval)
        if uni:
            ch = chr(uni)
            if ch.isalpha():
                self.layout_label.set_text("ع" if is_rtl(ch) else "EN")

        # Board mode = a card is selected and we're not typing in the text field / search.
        # The editor pane counts as a text field: without it the arrow keys would walk the board
        # instead of the cursor, and Ctrl+D would delete the note being typed into.
        in_quick = any(e.has_focus() for e in self.quick_entries.values())
        in_text = (self.textview.has_focus() or self.search_entry.has_focus()
                   or self.editor_view.has_focus() or in_quick
                   or (self._append_entry is not None and self._append_entry.has_focus()))
        board = self._board_active and self.selected_note is not None and not in_text

        # The tag completer owns the arrows, Enter/Tab and Esc while its list is up. Checked before
        # anything else because every one of those keys is claimed further down — and because this
        # window handler runs before the focused view's, so there is nowhere later to do it.
        completer = self._active_completer()
        if completer is not None and not ctrl and not alt:
            if kv in (Gdk.KEY_Down, Gdk.KEY_Up):
                completer.move(1 if kv == Gdk.KEY_Down else -1)
                return True
            if kv in (Gdk.KEY_Return, Gdk.KEY_KP_Enter, Gdk.KEY_Tab, Gdk.KEY_ISO_Left_Tab):
                completer.accept()
                return True
            if kv == Gdk.KEY_Escape:
                completer.hide()
                return True

        if kv == Gdk.KEY_F1:
            self._show_help()
            return True
        # F12 flips focus between the card area and the add field (in minimal mode, selects the lone card).
        if kv == Gdk.KEY_F12:
            self._toggle_card_focus()
            return True
        # Saving the new note: Ctrl+Enter always; with "Save on enter" on, a plain Enter in the add
        # field saves too (and Shift+Enter then inserts a newline). The board's own Enter — open the
        # selected note — is handled below and only fires when the text field isn't focused.
        if kv in (Gdk.KEY_Return, Gdk.KEY_KP_Enter) and not alt:
            # In the side pane Ctrl+Enter means "done with this note" — save it and hand the
            # keyboard back to the board. Checked here, not in a handler on the pane itself: this
            # window-level handler runs BEFORE the event reaches the focused widget, so a pane
            # handler would never see it and the chord would file a spurious NEW note instead.
            if ctrl and self.editor_view.has_focus():
                self._editor_flush()
                self._focus_board()
                return True
            if ctrl:
                self._send()
                return True
            if self.settings.save_on_enter and self.textview.has_focus() and not shift:
                self._send()
                return True

        # Esc toggles focus: from the text field → board; from anywhere else → text field.
        # (So a single Esc on the board drops to the field; pressing it again returns to the board.)
        if kv == Gdk.KEY_Escape:
            if self._merge_marks:
                self._clear_merge_marks()  # a pending merge set: first Esc just clears it
            elif self.search_bar.get_search_mode():
                self._set_search(False)
            elif in_quick:
                # Out of a quick field and back to the cards. Whatever was half-typed is dropped:
                # nothing is written until Enter, so there is no unsaved state to protect here.
                self._focus_board()
            elif self.editor_view.has_focus():
                # Out of the side pane and back to the cards — saving on the way, so the next
                # keystroke (a retag, a delete) can't get ahead of a pending autosave.
                self._editor_flush()
                self._focus_board()
            elif self.textview.has_focus():
                self._focus_board()
            else:
                self._focus_textfield()
            return True

        # Ctrl+Shift+M: commit the pending merge (checked before the Ctrl+Shift+<code> tag editor so
        # 'm' isn't swallowed as a type/category code). Layout-independent via _base_char. (Merge uses
        # M, not G — Ctrl+Shift+G collided with retagging a card to the 'G' type.)
        if ctrl and shift and not alt and self._base_char(event).lower() == "m":
            self._merge_marked()
            return True

        # Ctrl+Shift+<letter/digit>: with a card selected, edit that note's type/category/priority;
        # otherwise drive the add-form chips (original behavior). Checked before the single-letter
        # Ctrl shortcuts so e.g. Ctrl+Shift+T picks the T type rather than toggling the theme.
        if ctrl and shift and not alt:
            if board:
                if self._edit_selected_by_shortcut(event):
                    return True
            elif self._select_by_shortcut(event):
                return True

        # Selected-card actions.
        if board:
            # Space: open an inline field under the card to quickly append a comment to the note.
            if kv == Gdk.KEY_space and not ctrl and not alt:
                self._quick_append()
                return True
            if kv in (Gdk.KEY_Up, Gdk.KEY_Down, Gdk.KEY_Left, Gdk.KEY_Right):
                self._navigate(kv)
                return True
            if kv in (Gdk.KEY_Return, Gdk.KEY_KP_Enter):
                self._open_external(self.selected_note)
                return True
            if ctrl and not shift and not alt and kv in (Gdk.KEY_d, Gdk.KEY_D):
                self._delete_selected()
                return True
            # Ctrl+M ("mark"): mark/unmark this card for merging. Layout-independent via _base_char.
            if ctrl and not shift and not alt and self._base_char(event).lower() == "m":
                self._toggle_merge_mark()
                return True
            # Ctrl+O ("open"): reveal the note's files folder in the file manager (created on demand).
            if ctrl and not shift and not alt and self._base_char(event).lower() == "o":
                self._open_files_folder()
                return True
            # Ctrl+I ("import"): push this note to the backend. Layout-independent via _base_char.
            if ctrl and not shift and not alt and self._base_char(event).lower() == "i":
                self._import_selected()
                return True

        # Ctrl+PgUp / Ctrl+PgDn: previous / next week in the calendar view. The same keys as the
        # bar's ‹ › buttons, which name them in their tooltips.
        if ctrl and not shift and not alt and kv in (Gdk.KEY_Page_Up, Gdk.KEY_Page_Down) \
                and self.settings.view_mode == "week" and not self.minimal_mode:
            self._shift_week(-1 if kv == Gdk.KEY_Page_Up else 1)
            return True

        # Ctrl+1 … Ctrl+9: switch to the 1st … 9th configured note folder. (Priority uses
        # Ctrl+Shift+digit, so a plain Ctrl+digit is free for this.) Layout-independent via _base_char.
        if ctrl and not shift and not alt:
            ch = self._base_char(event)
            if ch.isdigit() and ch != "0" and self._select_repo(int(ch)):
                return True

        if ctrl and not shift and not alt and kv in (Gdk.KEY_f, Gdk.KEY_F):
            self._set_search(not self.search_bar.get_search_mode())
            return True
        # Ctrl+E ("edit"): the side editor pane. Layout-independent via _base_char.
        if ctrl and not shift and not alt and self._base_char(event).lower() == "e":
            self._set_editor_pane(not self.settings.editor_pane_visible)
            return True
        # Ctrl+J ("jot") / Ctrl+K: straight into the quick note or quick task field, opening the
        # pane and the strip if they're closed. Both are plain Ctrl chords on purpose —
        # Ctrl+Shift+<letter> is taken by retagging, and J is itself a type code there.
        if ctrl and not shift and not alt and self._base_char(event).lower() in ("j", "k"):
            self._focus_quick("note" if self._base_char(event).lower() == "j" else "task")
            return True
        if ctrl and not shift and not alt and kv in (Gdk.KEY_q, Gdk.KEY_Q):
            self._save_draft()
            self.get_application().quit()
            return True
        if ctrl and not shift and not alt and kv == Gdk.KEY_comma:
            self._open_settings()
            return True
        if ctrl and not shift and not alt and kv in (Gdk.KEY_t, Gdk.KEY_T):
            self._toggle_theme()
            return True
        if ctrl and not shift and not alt and kv in (Gdk.KEY_r, Gdk.KEY_R):
            self.reload_notes()
            return True
        # Ctrl+L ("less") toggles minimal mode. _base_char keys off the physical 'l', so it works on
        # any keyboard layout (incl. Arabic). Ctrl+L (unlike Ctrl+M) has no Return-quirk to dodge.
        if ctrl and not shift and not alt and self._base_char(event).lower() == "l":
            self._toggle_minimal_mode()
            return True
        # Ctrl+B ("bar") shows/hides the filter sidebar. Layout-independent via _base_char.
        if ctrl and not shift and not alt and self._base_char(event).lower() == "b":
            self.sidebar.toggle_collapsed()
            return True
        return False

    def _select_by_shortcut(self, event) -> bool:
        """Resolve a Ctrl+Shift chord to the physical key's base character (layout-independent, so
        Shift doesn't turn a digit into a symbol), then toggle the type/category/priority it names.
        Type and category codes are assumed not to overlap."""
        ch = self._base_char(event)
        if not ch:
            return False
        up = ch.upper()
        code = next((c for c in self.type_codes if c.upper() == up), None)
        if code is not None:
            self._select_type(None if self.selected_type == code else code)
            return True
        code = next((c for c in self.category_codes if c.upper() == up), None)
        if code is not None:
            self._select_category(None if self.selected_category == code else code)
            return True
        if ch.isdigit() and int(ch) in self.priority_buttons:
            self._set_priority(int(ch))
            return True
        return False

    def _base_char(self, event) -> str:
        """The Latin character engraved on the event's physical key, independent of the active
        layout, Shift, or Caps. We scan every (group, level) the keycode maps to and return the first
        ASCII letter/digit — so the shortcuts still work while typing in Arabic (where the current
        group would otherwise translate the key to an Arabic character that matches no code)."""
        keymap = Gdk.Keymap.get_for_display(self.get_display())
        entries = keymap.get_entries_for_keycode(event.hardware_keycode)
        if entries and entries[0]:
            for keyval in entries[-1]:  # the keyvals list (last element of the result tuple)
                uni = Gdk.keyval_to_unicode(keyval)
                if uni:
                    ch = chr(uni)
                    if ch.isascii() and ch.isalnum():
                        return ch
        uni = Gdk.keyval_to_unicode(event.keyval)
        return chr(uni) if uni else ""

    # ------------------------------------------------------------ theme

    def _refresh_theme_icon(self) -> None:
        # The icon previews where the toggle will take you: a sun while dark, a moon while light.
        icon = "weather-clear-symbolic" if self.settings.theme == "dark" else "weather-clear-night-symbolic"
        child = self.theme_button.get_child()
        if child is not None:
            self.theme_button.remove(child)
        image = Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.BUTTON)
        self.theme_button.add(image)
        image.show()

    def _apply_app_theme(self) -> None:
        """Restyle for the current theme AND active folder (each folder tints the chrome)."""
        self.get_application().apply_theme(
            self.settings.theme, repo_color(self.settings, self.settings.active_repo_code()))

    def _toggle_theme(self) -> None:
        self.settings.theme = "light" if self.settings.theme == "dark" else "dark"
        self.settings.save()
        self._apply_app_theme()
        self._refresh_theme_icon()
        # The pane's markdown tags hold real colours read out of the theme block, so they don't
        # follow a stylesheet swap on their own — a code span keeps the old ground until they're
        # rebuilt, and the note already on screen has to be repainted with them.
        self._build_markup_tags()
        if self._editor_mode == "read":
            self._editor_render(keep_scroll=True)
        self._update_filter_summary(*getattr(self, "_filter_summary_args", ("", 0, 0)))

    # --------------------------------------------------------- settings

    def _open_settings(self) -> None:
        # The tag options edited in the dialog belong to the repo active when it opened.
        dialog = SettingsDialog(self, self.settings, self.repo_cfg)
        if dialog.run() == Gtk.ResponseType.OK:
            edited_repo_path = self.settings.active_repo_path()
            dialog.apply_to(self.settings, self.repo_cfg)
            self.settings.save()
            self.repo_cfg.save(edited_repo_path)
            # The dialog may have switched the active repo — reload that folder's own tag config.
            self.repo = NoteRepository(self.settings.active_repo_path())
            self.repo_cfg = RepoConfig.load(self.settings.active_repo_path(), self.settings)
            self.priority = min(max(self.priority, self.repo_cfg.priority_min),
                                max(self.repo_cfg.priority_max, self.repo_cfg.priority_min))
            self._build_chips()
            self._refresh_taxonomy()
            self._apply_defaults()
            self._populate_repo_combo()
            self._apply_app_theme()        # the dialog may have switched or reordered folders
            self._refresh_status()
            self._refresh_highlight_tag()  # a new highlight colour shows without reopening the pane
            self._editor_render(keep_scroll=True)
            self.reload_notes()
            self._sync_backend_taxonomy()  # a newly-entered backend URL takes effect right away
        dialog.destroy()

    def _show_help(self) -> None:
        save_keys = "Enter (or Ctrl+Enter)" if self.settings.save_on_enter else "Ctrl+Enter"
        rows = [
            (save_keys, "Save the new note" + (" · Shift+Enter for a newline" if self.settings.save_on_enter else "")),
            ("Esc", "Text field ⇄ board (once → field · again → board)"),
            ("Ctrl+Shift+<letter>", "Type/category: edit selected card and every marked one, else the add form"),
            ("Ctrl+Shift+<digit>", "Priority: edit selected card and every marked one, else the add form"),
            ("Click a card / Arrows", "Select a card · move between cards (↑↓ in a column, ←→ across)"),
            ("Enter (card selected)", "Open the selected note in your editor"),
            ("Ctrl+D (card selected)",
             "Move the selected note to the .deleted folder (with confirmation). "
             "Refused while the note still has attached files — empty its folder first"),
            ("Ctrl+M / Ctrl+click", "Mark/unmark card for merge (first marked = survivor) · Esc clears"),
            ("Ctrl+Shift+M", "Merge marked notes into the survivor, rest → .merged folder"),
            ("Space (card selected)", "Quick-append a comment to the note (inline field · Enter adds · Esc cancels)"),
            ("Ctrl+O (card selected)", "Open the note's files folder in the file manager (created on demand)"),
            ("Ctrl+I (card selected)",
             "Import the note into the Nibras backend (with confirmation); the file moves to imported/"),
            ("Ctrl+1 … Ctrl+9", "Switch to the 1st … 9th note folder"),
            ("Ctrl+PgUp / Ctrl+PgDn",
             "Week calendar: previous / next week. Click an empty slot to add a journal (J) or "
             "planner (P) note dated at that slot's time"),
            ("Ctrl+E", "Side editor pane — edit the selected note in place, saved as you type"),
            ("Ctrl+J / Ctrl+K",
             "Quick note / quick task — jump into the capture strip under the pane (opens it if "
             "closed). Enter files the line and keeps the field, Esc returns to the cards. They go "
             "to quick-notes.md / quick-tasks.md in the folder, never to the board"),
            ("Ctrl+F", "Toggle search — matches note text, the type/category codes, and any part "
                       "of the file name (so \"N~p2\" lists every note tagged that way)"),
            ("Ctrl+B", "Show / hide the filter sidebar (calendar heat map, tags, counts)"),
            ("Ctrl+R", "Refresh from disk (pick up notes synced from another machine)"),
            ("Ctrl+T", "Toggle light / dark theme"),
            ("Ctrl+L", "Toggle minimal side-note mode (board hidden)"),
            ("F12", "Focus the card area / add field (in minimal mode, select the card)"),
            ("Ctrl+,", "Settings"),
            ("F1", "This help"),
            ("Ctrl+Q", "Quit (saves an unsent draft)"),
        ]
        dialog = Gtk.Dialog(title="Keyboard shortcuts", transient_for=self, modal=True)
        dialog.add_button("Close", Gtk.ResponseType.CLOSE)
        grid = Gtk.Grid(column_spacing=18, row_spacing=8, border_width=16)
        for i, (keys, desc) in enumerate(rows):
            k = Gtk.Label(label=keys, xalign=0)
            k.get_style_context().add_class("shortcut-key")
            grid.attach(k, 0, i, 1, 1)
            grid.attach(Gtk.Label(label=desc, xalign=0), 1, i, 1, 1)
        dialog.get_content_area().add(grid)
        dialog.show_all()
        dialog.run()
        dialog.destroy()


class SettingsDialog(Gtk.Dialog):
    def __init__(self, parent: KnotesWindow, settings: Settings, repo_cfg: RepoConfig):
        super().__init__(title="Settings", transient_for=parent, modal=True)
        self.add_button("Cancel", Gtk.ResponseType.CANCEL)
        self.add_button("Save", Gtk.ResponseType.OK)
        self.set_default_size(480, -1)

        grid = Gtk.Grid(column_spacing=10, row_spacing=10, border_width=16)
        # The settings list is long (folders, previews, backend, per-folder tags) — scroll it so the
        # dialog never grows taller than the screen and the Save/Cancel buttons stay reachable.
        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.set_propagate_natural_height(True)
        scroller.set_max_content_height(640)
        scroller.add(grid)
        self.get_content_area().add(scroller)

        row = 0

        def add(label: str, widget: Gtk.Widget) -> None:
            nonlocal row
            grid.attach(Gtk.Label(label=label, xalign=0), 0, row, 1, 1)
            grid.attach(widget, 1, row, 1, 1)
            widget.set_hexpand(True)
            row += 1

        self.repos_view = self._tag_textview(format_tag_block(settings.repos(), settings.active_repo_code()))
        self.repos_view.set_tooltip_text(
            "One folder per line as code:path (e.g. A:~/personal). Mark the active one with * .")
        add("Note folders (code:path per line; * = active)", self._wrap_scroller(self.repos_view))

        # One colour button per folder (as configured when the dialog opened), plus a reset to the
        # theme's own chrome. A folder added in the box above gets its default until it's reopened.
        self.repo_color_buttons: dict[str, Gtk.ColorButton] = {}
        self.repo_color_cleared: set[str] = set()
        colours = Gtk.FlowBox(selection_mode=Gtk.SelectionMode.NONE, column_spacing=12, row_spacing=4)
        colours.set_max_children_per_line(4)
        for code, _path in settings.repos():
            cell = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
            cell.pack_start(Gtk.Label(label=code), False, False, 0)
            rgba = Gdk.RGBA()
            current = repo_color(settings, code)
            if not current:
                self.repo_color_cleared.add(code)
            rgba.parse(current or "#eee8d5")
            button = Gtk.ColorButton.new_with_rgba(rgba)
            button.set_tooltip_text(f"Toolbar, sidebar and status bar colour while folder {code} is active")
            button.connect("color-set", lambda _b, c=code: self.repo_color_cleared.discard(c))
            cell.pack_start(button, False, False, 0)
            reset = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
            reset.add(Gtk.Image.new_from_icon_name("edit-clear-symbolic", Gtk.IconSize.MENU))
            reset.set_tooltip_text("No tint — use the theme's own colours for this folder")
            reset.connect("clicked", lambda _b, c=code: self.repo_color_cleared.add(c))
            cell.pack_start(reset, False, False, 0)
            self.repo_color_buttons[code] = button
            colours.add(cell)
        add("Folder colours", colours)

        self.format_entry = Gtk.Entry(text=settings.timestamp_format)
        self.format_entry.set_tooltip_text("Python strftime, e.g. %Y_%m_%d-%H%M%S")
        add("Timestamp format", self.format_entry)

        self.opener_entry = Gtk.Entry(text=settings.opener)
        self.opener_entry.set_placeholder_text(f"system default ({DEFAULT_OPENER})")
        self.opener_entry.set_tooltip_text("Command used to open a note on click, e.g. xed. Blank = system default.")
        add("Open notes with", self.opener_entry)

        self.preview_spin = Gtk.SpinButton.new_with_range(20, 400, 10)
        self.preview_spin.set_value(settings.preview_chars)
        self.preview_spin.set_tooltip_text("Characters shown from the start of a note on its card.")
        add("Preview characters (head)", self.preview_spin)

        self.counts_min_spin = Gtk.SpinButton.new_with_range(0, 5000, 20)
        self.counts_min_spin.set_value(settings.card_counts_min_chars)
        self.counts_min_spin.set_tooltip_text(
            "Notes at least this many characters long also show their highlight count on the card "
            "(and their time-mark count, if the companion folder holds any .tmk files). Short "
            "captures don't need it — the whole note is already on the card. 0 = every card.")
        add("Counts shown from", self.counts_min_spin)

        # The side pane opens in whichever mode it was last switched to; this is what a fresh
        # window (and a fresh install) starts in, for people who always want one or the other.
        self.pane_mode_combo = Gtk.ComboBoxText()
        for code, label in (("read", "Read (highlights, no editing)"), ("edit", "Edit (type straight in)")):
            self.pane_mode_combo.append(code, label)
        self.pane_mode_combo.set_active_id(settings.editor_pane_mode if settings.editor_pane_mode in ("read", "edit") else "read")
        self.pane_mode_combo.set_tooltip_text(
            "Which mode the side editor pane (Ctrl+E) starts in. Switching mode in the pane itself "
            "still sticks — this is the starting point.")
        add("Editor pane starts in", self.pane_mode_combo)

        self.head_words_spin = Gtk.SpinButton.new_with_range(0, 20, 1)
        self.head_words_spin.set_value(settings.preview_head_words)
        self.head_words_spin.set_tooltip_text("Words from the START shown on the 'just added' card (minimal mode).")
        add("Preview words (head)", self.head_words_spin)

        self.tail_words_spin = Gtk.SpinButton.new_with_range(0, 20, 1)
        self.tail_words_spin.set_value(settings.preview_tail_words)
        self.tail_words_spin.set_tooltip_text("Words from the END shown on the 'just added' card (minimal mode).")
        add("Preview words (tail)", self.tail_words_spin)

        self.preview_tail_spin = Gtk.SpinButton.new_with_range(0, 200, 10)
        self.preview_tail_spin.set_value(settings.preview_tail_chars)
        self.preview_tail_spin.set_tooltip_text(
            "Characters shown from the end of a longer note (0 = none, just the head).")
        add("Preview characters (tail)", self.preview_tail_spin)

        self.save_enter_check = Gtk.CheckButton(label="Save on Enter (Shift+Enter for a newline)")
        self.save_enter_check.set_active(settings.save_on_enter)
        self.save_enter_check.set_tooltip_text(
            "On: a plain Enter saves the note. Off: only Ctrl+Enter saves; Enter inserts a newline.")
        add("Saving", self.save_enter_check)

        self.show_tags_summary_check = Gtk.CheckButton(label="List every #hashtag under the add field")
        self.show_tags_summary_check.set_active(settings.show_tags_summary)
        self.show_tags_summary_check.set_tooltip_text(
            "On: every #hashtag in use, with how many notes use each. Off: just a compact "
            "total-uses count in its place — the strip stays, but without spelling out every tag.")
        add("Tags", self.show_tags_summary_check)

        self.start_today_check = Gtk.CheckButton(label="Open filtered to today's and yesterday's notes")
        self.start_today_check.set_active(settings.start_filter_today)
        self.start_today_check.set_tooltip_text(
            "On: launch with today and yesterday selected in the calendar, so the board opens on "
            "what you've captured lately — a folder switched to for the first time opens the same "
            "way, and every \"clear\" button returns here first (a second click shows "
            "everything). Off: no day filter.")
        add("Startup", self.start_today_check)

        self.hide_nonstandard_check = Gtk.CheckButton(label="Hide non-standard items by default")
        self.hide_nonstandard_check.set_active(settings.hide_nonstandard_default)
        self.hide_nonstandard_check.set_tooltip_text(
            "On: the default view leaves out the files and folders that aren't canonical notes yet "
            "(the \"to settle\" backlog). The sidebar's Non-standard switch still shows them, or "
            "shows only them.")
        add("Startup", self.hide_nonstandard_check)

        # Colour of a ==highlighted== clause in the side pane's reading mode. A colour button rather
        # than a hex field: the point of the setting is to pick something that reads well against
        # the theme, which is a thing you judge by eye.
        highlight = Gdk.RGBA()
        if not highlight.parse(settings.highlight_color):
            highlight.parse("#f2c744")
        self.highlight_button = Gtk.ColorButton.new_with_rgba(highlight)
        self.highlight_button.set_halign(Gtk.Align.START)
        self.highlight_button.set_tooltip_text(
            "Background painted behind a clause highlighted in the side pane's reading mode "
            "(stored in the note as ==clause==). The text on top switches between black and white "
            "to stay readable, so a dark colour works as well as a light one.")
        add("Highlight colour", self.highlight_button)

        self.append_template_entry = Gtk.Entry(text=settings.append_template)
        self.append_template_entry.set_tooltip_text(
            "Inserted between a note's existing content and text appended via Space (quick-append). "
            '"$date" is replaced with the current time, formatted per the field below. '
            r'Use \n for a newline.')
        add("Append separator", self.append_template_entry)

        self.append_date_format_entry = Gtk.Entry(text=settings.append_date_format)
        self.append_date_format_entry.set_tooltip_text(
            "Python strftime format for \"$date\" in the append separator above, e.g. "
            "%Y-%m-%d %H:%M.")
        add("Append date format", self.append_date_format_entry)

        # Display mode (kanban/list/columns/minimal) and sort order moved to the TOOLBAR — they're
        # switched too often to live behind this dialog. The two numbers that SHAPE those views are
        # set once and left alone, so they do belong here.
        self.rows_spin = Gtk.SpinButton.new_with_range(1, 3, 1)
        self.rows_spin.set_value(settings.kanban_rows)
        self.rows_spin.set_tooltip_text("How many rows the kanban columns are spread across (1–3).")
        add("Kanban rows", self.rows_spin)

        self.card_width_spin = Gtk.SpinButton.new_with_range(120, 1200, 20)
        self.card_width_spin.set_value(settings.card_max_width)
        self.card_width_spin.set_tooltip_text(
            "Columns view: the widest a card may get. The board fits as many whole cards of this "
            "width as it can, so a smaller number means more, narrower columns.")
        add("Card max width (columns view)", self.card_width_spin)

        # --- Optional nibras backend: on launch it refreshes the type codes from the backend's
        #     modules and the category codes from its bookmarked departments, so a capture maps to the
        #     right record on import. Blank URL = fully offline (the codes below/defaults apply).
        backend_hint = Gtk.Label(xalign=0)
        backend_hint.set_markup(
            "<b>Nibras backend — default</b>  (used by folders without their own; blank = offline)")
        grid.attach(backend_hint, 0, row, 2, 1)
        row += 1

        self.backend_url_entry = Gtk.Entry(text=settings.backend_url)
        self.backend_url_entry.set_placeholder_text("http://192.168.0.47:8081   (blank = offline)")
        self.backend_url_entry.set_tooltip_text(
            "Base URL of the nibras backend. Types ← its modules, categories ← its bookmarked "
            "departments. Codes are cached, so it still works offline after the first sync. "
            "Each folder can override this below with its own backend.")
        add("Backend URL", self.backend_url_entry)

        self.backend_user_entry = Gtk.Entry(text=settings.backend_user)
        add("Backend username", self.backend_user_entry)

        self.backend_pass_entry = Gtk.Entry(text=settings.backend_password)
        self.backend_pass_entry.set_visibility(False)
        self.backend_pass_entry.set_input_purpose(Gtk.InputPurpose.PASSWORD)
        add("Backend password", self.backend_pass_entry)

        # --- Per-folder tag options (types/categories/priorities), stored in this folder's own
        #     .knotes.json — not global. The header notes which folder they apply to.
        folder_hint = Gtk.Label(xalign=0)
        folder_hint.set_markup(
            f"<b>Tag options for folder “{settings.active_repo_code()}”</b>  (saved in this folder only)")
        grid.attach(folder_hint, 0, row, 2, 1)
        row += 1

        self.min_spin = Gtk.SpinButton.new_with_range(0, 99, 1)
        self.min_spin.set_value(repo_cfg.priority_min)
        add("Priority min", self.min_spin)

        self.max_spin = Gtk.SpinButton.new_with_range(0, 99, 1)
        self.max_spin.set_value(repo_cfg.priority_max)
        add("Priority max", self.max_spin)

        self.default_prio_spin = Gtk.SpinButton.new_with_range(0, 99, 1)
        self.default_prio_spin.set_value(repo_cfg.priority_default)
        add("Default priority", self.default_prio_spin)

        self.types_view = self._tag_textview(format_tag_block(repo_cfg.types, repo_cfg.default_type))
        add("Types (code:name per line; * = default)", self._wrap_scroller(self.types_view))

        self.categories_view = self._tag_textview(format_tag_block(repo_cfg.categories, repo_cfg.default_category))
        add("Categories (code:name per line; * = default)", self._wrap_scroller(self.categories_view))

        # This folder's own backend — overrides the default backend above (blank = use default).
        self.repo_backend_url_entry = Gtk.Entry(text=repo_cfg.backend_url)
        self.repo_backend_url_entry.set_placeholder_text("blank = use the default backend above")
        self.repo_backend_url_entry.set_tooltip_text(
            "This folder's OWN nibras backend (e.g. a different Tomcat context per folder). "
            "Taxonomy sync for this folder talks to it. Blank fields fall back to the default.")
        add("This folder's backend URL", self.repo_backend_url_entry)

        self.repo_backend_user_entry = Gtk.Entry(text=repo_cfg.backend_user)
        add("This folder's backend username", self.repo_backend_user_entry)

        self.repo_backend_pass_entry = Gtk.Entry(text=repo_cfg.backend_password)
        self.repo_backend_pass_entry.set_visibility(False)
        self.repo_backend_pass_entry.set_input_purpose(Gtk.InputPurpose.PASSWORD)
        add("This folder's backend password", self.repo_backend_pass_entry)

        self.show_all()

    def _tag_textview(self, text: str) -> Gtk.TextView:
        view = Gtk.TextView()
        view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        view.get_buffer().set_text(text)
        return view

    def _wrap_scroller(self, view: Gtk.TextView) -> Gtk.ScrolledWindow:
        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.set_min_content_height(80)
        scroller.add(view)
        return scroller

    @staticmethod
    def _buffer_text(view: Gtk.TextView) -> str:
        buf = view.get_buffer()
        return buf.get_text(buf.get_start_iter(), buf.get_end_iter(), True)

    def apply_to(self, settings: Settings, repo_cfg: RepoConfig) -> None:
        # Global (app-wide) preferences → settings.
        repos, active = parse_tag_block(self._buffer_text(self.repos_view))
        if repos:
            settings.repositories = repos
            settings.active_repo = active or repos[0][0]
            settings.notes_dir = repos[0][1]  # keep notes_dir pointing at the first folder
        for code, button in self.repo_color_buttons.items():
            settings.repo_colors[code] = ("" if code in self.repo_color_cleared
                                          else _rgba_hex(button.get_rgba()))
        settings.timestamp_format = self.format_entry.get_text().strip() or settings.timestamp_format
        settings.opener = self.opener_entry.get_text().strip()
        settings.preview_chars = int(self.preview_spin.get_value())
        settings.preview_tail_chars = int(self.preview_tail_spin.get_value())
        settings.card_counts_min_chars = int(self.counts_min_spin.get_value())
        settings.editor_pane_mode = self.pane_mode_combo.get_active_id() or "read"
        settings.preview_head_words = int(self.head_words_spin.get_value())
        settings.preview_tail_words = int(self.tail_words_spin.get_value())
        settings.save_on_enter = self.save_enter_check.get_active()
        settings.show_tags_summary = self.show_tags_summary_check.get_active()
        settings.start_filter_today = self.start_today_check.get_active()
        settings.hide_nonstandard_default = self.hide_nonstandard_check.get_active()
        # Stored as #rrggbb rather than Gdk's "rgb(r,g,b)": Pango markup can't parse the latter (see
        # _highlight_colors), and hex is what a hand-edited config.json is expected to hold anyway.
        settings.highlight_color = _rgba_hex(self.highlight_button.get_rgba())
        settings.append_template = self.append_template_entry.get_text()
        settings.append_date_format = self.append_date_format_entry.get_text().strip() or settings.append_date_format
        settings.kanban_rows = int(self.rows_spin.get_value())
        settings.card_max_width = int(self.card_width_spin.get_value())
        settings.backend_url = self.backend_url_entry.get_text().strip()
        settings.backend_user = self.backend_user_entry.get_text().strip()
        settings.backend_password = self.backend_pass_entry.get_text()
        # Per-folder tag options → repo_cfg (persisted into the folder's own .knotes.json).
        repo_cfg.priority_min = int(self.min_spin.get_value())
        repo_cfg.priority_max = int(self.max_spin.get_value())
        repo_cfg.priority_default = int(self.default_prio_spin.get_value())
        repo_cfg.types, repo_cfg.default_type = parse_tag_block(self._buffer_text(self.types_view))
        repo_cfg.categories, repo_cfg.default_category = parse_tag_block(self._buffer_text(self.categories_view))
        repo_cfg.backend_url = self.repo_backend_url_entry.get_text().strip()
        repo_cfg.backend_user = self.repo_backend_user_entry.get_text().strip()
        repo_cfg.backend_password = self.repo_backend_pass_entry.get_text()


class KnotesApp(Gtk.Application):
    def __init__(self):
        super().__init__(application_id=APP_ID)
        self.settings = Settings.load()
        self.window: KnotesWindow | None = None
        self._css_provider: Gtk.CssProvider | None = None
        self._structural_css = ""

    def do_startup(self):
        Gtk.Application.do_startup(self)
        self._load_css()
        # A thick, steady text caret while typing: disable blinking and widen the caret (the ratio
        # is the caret width as a fraction of the line height — the default is a thin ~0.04).
        gtk_settings = Gtk.Settings.get_default()
        if gtk_settings is not None:
            gtk_settings.set_property("gtk-cursor-blink", False)
            gtk_settings.set_property("gtk-cursor-aspect-ratio", 0.125)

    def do_activate(self):
        if self.window is None:
            try:
                self.window = KnotesWindow(self)
            except Exception:
                # A failure here (e.g. an unwritable notes directory) is raised inside GTK's
                # activate vfunc, which would otherwise swallow it and leave a running-but-
                # windowless process holding the D-Bus name — so the next launch just re-
                # activates this ghost and shows nothing. Surface it and quit instead.
                self._show_fatal_error(traceback.format_exc())
                return
        self.window.show_all()
        self.window.present()
        self.window.textview.grab_focus()

    def _show_fatal_error(self, details: str) -> None:
        sys.stderr.write(details)
        dialog = Gtk.MessageDialog(
            transient_for=None, modal=True, message_type=Gtk.MessageType.ERROR,
            buttons=Gtk.ButtonsType.CLOSE, text="knotes could not start")
        dialog.format_secondary_text(
            "Something went wrong while opening the window. This is often a notes folder "
            "that doesn't exist or isn't writable — check the paths in config.json.\n\n"
            + details.strip())
        dialog.run()
        dialog.destroy()
        self.quit()

    def _load_css(self) -> None:
        css_path = os.path.join(HERE, "style.css")
        if os.path.exists(css_path):
            with open(css_path, "r", encoding="utf-8") as f:
                self._structural_css = f.read()
        self._css_provider = Gtk.CssProvider()
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(), self._css_provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        self.apply_theme(self.settings.theme,
                         repo_color(self.settings, self.settings.active_repo_code()))

    def apply_theme(self, theme: str, chrome_colour: str = "") -> None:
        """Recolour the whole app: swap the named-colour block and nudge the base GTK theme to its
        dark/light variant so the chrome (entries, scrollbars, dialogs) matches. [chrome_colour] is
        the active folder's tint (repo_color); later @define-colors win, so it goes after the theme."""
        if theme not in THEMES:
            theme = "dark"
        gtk_settings = Gtk.Settings.get_default()
        if gtk_settings is not None:
            gtk_settings.set_property("gtk-application-prefer-dark-theme", theme == "dark")
        if self._css_provider is not None:
            css = THEMES[theme] + repo_chrome_css(chrome_colour) + self._structural_css
            self._css_provider.load_from_data(css.encode("utf-8"))


def main() -> int:
    # Sets the X11 program class, which (with set_wmclass) gives a stable WM_CLASS for panel pinning.
    GLib.set_prgname("knotes")
    return KnotesApp().run(None)


if __name__ == "__main__":
    raise SystemExit(main())
