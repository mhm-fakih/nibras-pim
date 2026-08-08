#!/usr/bin/env python3
"""knotes — a small GTK3 note taker (Linux desktop port of the knotes Android homepage).

Notes are plain-text files whose names encode their tags (see ``notes.py``). The UI is a
chronological list of note cards with a tagging input bar at the bottom; clicking a card
loads it for editing. Everything is reachable from the keyboard — see the Help dialog (F1).

Run it with:  python3 knotes.py
Needs:        PyGObject + GTK 3  (Debian/Ubuntu: sudo apt install python3-gi gir1.2-gtk-3.0)
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import threading
import traceback
import warnings
from datetime import datetime

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, Gdk, GLib, Pango, Gio  # noqa: E402

from config import Settings, RepoConfig, parse_tag_block, format_tag_block  # noqa: E402
from notes import NoteRepository, Note, is_rtl, companion_dir, parse_created  # noqa: E402
from backend import fetch_taxonomy  # noqa: E402
from filters import FilterState, Taxonomy  # noqa: E402
from sidebar import FilterSidebar  # noqa: E402

APP_ID = "org.knotes.desktop"
HERE = os.path.dirname(os.path.abspath(__file__))

VERSION = "1.2.32"
CODENAME = "Dastgheib"


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
TAG_COLOR_CAT = "#2aa198"   # cyan
TAG_COLOR_HASH = "#859900"  # green — inline #hashtags pulled from the note body

# A #hashtag: a '#' (not mid-word) followed by word characters (Unicode, so Arabic tags work too).
_HASHTAG_RE = re.compile(r"(?<!\w)#(\w+)", re.UNICODE)


def _hashtags(text: str) -> list[str]:
    """Distinct hashtags in [text], in first-seen order, compared case-insensitively."""
    out: list[str] = []
    seen: set[str] = set()
    for m in _HASHTAG_RE.finditer(text):
        tag = m.group(1)
        if tag.lower() not in seen:
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

# How long (ms) a restored scroll offset is held against the layout that follows a board rebuild
# (see _pin_scroll). Long enough to outlast measuring + the focus scroll, short enough that it can
# never fight the user's own next scroll.
SCROLL_PIN_MS = 350


def _tag_span(text: str, color: str) -> str:
    """An inline, badge-like Pango span: bold coloured text on a faint tint of the same colour, with
    surrounding spaces for a little horizontal padding."""
    t = GLib.markup_escape_text(text)
    return f'<span fgcolor="{color}" bgcolor="{color}" bgalpha="18%" weight="bold">  {t}  </span>'

# Colour blocks prepended to style.css at runtime. Both are Solarized palettes — a dark slate
# (the default; grey/teal rather than pure black) and the matching light parchment. Only these
# named colours change between themes; the structural rules in style.css reference them.
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
    """,
    "light": """
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
    """,
}


class KnotesWindow(Gtk.ApplicationWindow):
    def __init__(self, app: "KnotesApp"):
        super().__init__(application=app, title="knotes")
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
        self._setup_layout_watch()
        self._setup_note_dir_watch()
        self._sync_backend_taxonomy()  # best-effort; offline/misconfigured → keeps current codes

    # ------------------------------------------------------------------ UI

    def _build_ui(self) -> None:
        # Row 1 — the native title bar: app name + build + live stats (note count / merge hint)
        # only. All the actual controls live in their own toolbar row below it (see `self.toolbar`)
        # instead of crowding into the title bar, so each row has one job.
        self.header = Gtk.HeaderBar(
            title=f'knotes v{VERSION}.b{BUILD_NUMBER} "{CODENAME}"', show_close_button=True)
        self.set_titlebar(self.header)

        root = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.add(root)

        # Row 2 — the toolbar: every view/action control, left-to-right in the same order they used
        # to appear (right-to-left) packed into the title bar.
        self.toolbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.toolbar.set_border_width(6)
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
        self.minimal_button = Gtk.ToggleButton()
        self.minimal_button.add(Gtk.Image.new_from_icon_name("view-restore-symbolic", Gtk.IconSize.BUTTON))
        self.minimal_button.set_tooltip_text("Minimal side-note mode (Ctrl+L)")
        for btn, mode in ((self.kanban_button, "kanban"), (self.list_button, "list"),
                          (self.columns_button, "columns"), (self.minimal_button, "minimal")):
            btn.connect("toggled", lambda b, m=mode: self._on_mode_button(b, m))
            mode_box.pack_start(btn, False, False, 0)
        self.toolbar.pack_start(mode_box, False, False, 0)

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
                            ("category", "Category"), ("type", "Type"), ("priority", "Priority")):
            self.sort_selector.append(code, label)
        SORT_CODES = ("datetime", "created", "category", "type", "priority")
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
        content.pack_start(right, True, True, 0)
        root.pack_start(content, True, True, 0)

        # Search bar (hidden until toggled).
        self.search_bar = Gtk.SearchBar()
        self.search_entry = Gtk.SearchEntry()
        self.search_entry.set_width_chars(30)
        self.search_entry.connect("search-changed", self._on_search_changed)
        self.search_bar.add(self.search_entry)
        self.search_bar.connect_entry(self.search_entry)
        right.pack_start(self.search_bar, False, False, 0)

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
        self.statusbar.set_border_width(4)
        self.status_path = Gtk.Label(xalign=0)
        self.status_path.set_ellipsize(1)  # MIDDLE — keep both ends of a long path readable
        self.status_key = Gtk.Label(xalign=1)
        self.statusbar.pack_start(self.status_path, True, True, 0)
        self.statusbar.pack_end(self.status_key, False, False, 0)
        root.pack_start(self.statusbar, False, False, 0)
        self._refresh_status()

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
        # Horizontal NEVER (the text wraps); overlay scrolling off so the vertical scrollbar gets its
        # own gutter instead of covering the last line.
        self.text_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
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
        all_notes = sorted(self.repo.list_notes() + unrecognized, key=lambda n: n.mtime)
        query = self.search_entry.get_text().strip().lower()
        searched = [n for n in all_notes if not query or self._matches(n, query)]
        # Two stages, in this order on purpose: the search box narrows the folder, then the sidebar
        # counts every filter option against what's left (so its numbers track the search), and only
        # then do the filters themselves decide what reaches the board.
        self.sidebar.refresh(searched)
        notes = [n for n in searched if self.filters.matches(n, self.tax)]
        # Folder note count next to the app name (no subtitle, so the header bar stays one line tall).
        narrowed = bool(query) or self.filters.active()
        count = f"{len(notes)} / {len(all_notes)} notes" if narrowed else f"{len(all_notes)} notes"
        self._base_title = f'knotes v{VERSION}.b{BUILD_NUMBER} "{CODENAME}" · {count}'
        self._apply_header_title()
        self._update_tags_summary(all_notes)

        # "Just added" — the newest note (the repo lists oldest→newest, so it's the last one).
        self._render_last_note(notes[-1] if notes else None)

        if self.minimal_mode:
            return   # side-note mode: the board is hidden, only the just-added card is shown
        if self.settings.view_mode == "list":
            self._render_list(notes)
        elif self.settings.view_mode == "columns":
            self._render_columns(notes)
        else:
            self._render_kanban(notes)
        self.board.show_all()
        self._restore_selection()

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
        parse_created), "datetime" (default) by the file's own last-modified time."""
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
                f'<span alpha="70%">{occurrences} #tag use{"s" if occurrences != 1 else ""}</span>')
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
            f'{_tag_span("#" + label[k], TAG_COLOR_HASH)}<span alpha="55%"> {c}</span>'
            for k, c in items))
        self.tags_summary.set_tooltip_text("   ".join(f"#{label[k]} ({c})" for k, c in items))
        self.tags_summary.show()

    @staticmethod
    def _matches(note: Note, query: str) -> bool:
        return (query in note.content.lower()
                or (note.type or "").lower().find(query) >= 0
                or (note.category or "").lower().find(query) >= 0)

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
            f'<span alpha="55%">{len(notes)}</span>')
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
        label.set_markup(f'{GLib.markup_escape_text(tname)}  <span alpha="55%">{len(recs)}</span>')
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
            tags.append(_tag_span(note.type, TAG_COLOR_TYPE))
        tags.append(_tag_span(f"P{note.priority}" if note.priority is not None else "P·", TAG_COLOR_PRIO))
        if note.category:
            tags.append(_tag_span(note.category, TAG_COLOR_CAT))
        # The three meta tags get their OWN label in a fixed left gutter, never inlined into the
        # body text. An RTL note right-aligns its body (below), which — with the tags inline — swept
        # them over to the right edge, so the type/priority/category landed in a different place on
        # an Arabic card than on an English one. In a gutter the eye runs straight down one column
        # whatever the note's language. (The trade: a wrapped body's later lines start after the
        # gutter rather than under it.)
        # Order is type · priority · category, and the cluster sits in an LTR isolate (U+2066…U+2069)
        # so it reads left-to-right ("T P3 Z") even when the tag text itself neighbours RTL content.
        tag_label = Gtk.Label(xalign=0)
        tag_label.set_markup("⁦" + " ".join(tags) + "⁩")
        tag_label.set_valign(Gtk.Align.START)
        tag_label.set_direction(Gtk.TextDirection.LTR)

        # A "needs metadata" placeholder: show its actual file/folder name (note.title() would show
        # its raw content, or "(empty)" for a folder — neither identifies which item this is).
        if note.path in self._unrecognized_paths:
            body = GLib.markup_escape_text(os.path.basename(note.path.rstrip(os.sep)))
        else:
            body = GLib.markup_escape_text(
                note.title(self.settings.preview_chars, self.settings.preview_tail_chars))
        wc = note.word_count()
        # The count sits in its own LTR isolate: with the tags gone from this label, an Arabic note
        # makes the paragraph RTL, and the bidi algorithm would render "(4 words)" as "(words 4)".
        # The isolate fixes the run's internal order while still letting it fall at the text's end.
        count = f'  <span alpha="55%">⁦({wc} word{"" if wc == 1 else "s"})⁩</span>'

        title = Gtk.Label(xalign=0)
        title.set_markup(body + count)
        title.set_line_wrap(True)
        title.set_lines(4)
        title.set_ellipsize(3)  # END — ellipsize after the wrap cap
        title.set_valign(Gtk.Align.START)
        # Arabic (and other RTL) content reads from the right — of its own column, not of the card,
        # so the gutter to its left stays put.
        if note.is_rtl:
            title.set_xalign(1.0)
            title.set_justify(Gtk.Justification.RIGHT)
        title.get_style_context().add_class("title")

        # LTR on the row too: widget direction comes from the locale, and under an RTL one
        # pack_start would fill from the right and put the gutter on the wrong side of every card.
        title_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        title_row.set_direction(Gtk.TextDirection.LTR)
        title_row.pack_start(tag_label, False, False, 0)
        title_row.pack_start(title, True, True, 0)
        outer.pack_start(title_row, False, False, 0)

        # #hashtags extracted from the body, shown as chips at the end so they're always visible even
        # when the content above is truncated.
        hashtags = _hashtags(note.content)
        if hashtags:
            ht = Gtk.Label(xalign=0)
            ht.set_markup("  ".join(_tag_span("#" + h, TAG_COLOR_HASH) for h in hashtags))
            ht.set_line_wrap(True)
            ht.set_xalign(1.0 if note.is_rtl else 0.0)
            outer.pack_start(ht, False, False, 0)

        # Attached files (companion folder): a quick overview — up to 5 names (each truncated to the
        # configured leading chars + its extension), then "…" if there are more, and the total count.
        if note.file_names:
            chars = max(1, self.settings.card_file_chars)

            def _short(fn: str) -> str:
                stem, ext = os.path.splitext(fn)
                trimmed = stem[:chars] + ("…" if len(stem) > chars else "")
                return GLib.markup_escape_text(trimmed + ext)

            shown = "   ".join(_short(f) for f in note.file_names[:5])
            more = "   …" if note.n_files > 5 else ""
            files = Gtk.Label(xalign=0)
            files.set_markup(f'<span alpha="70%">📎 {shown}{more}  ({note.n_files})</span>')
            files.set_line_wrap(True)
            files.set_xalign(1.0 if note.is_rtl else 0.0)
            outer.pack_start(files, False, False, 0)

        card.add(outer)
        return card

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
        fall back to the first card."""
        self._selected_widget = None
        self.selected_note = None
        self._sel = None
        pos = self._pos_of_path(self._sel_path)
        if pos is None and self._board_active:
            pos = self._first_cell()
        if pos is None:
            self._sel_path = None
            return
        self._apply_select(*pos, grab=self._board_active)

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
        hidden, so it selects/deselects the lone 'just added' card (then Ctrl+Shift+<code> retags it
        and Ctrl+D deletes it)."""
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

    def _delete_selected(self) -> None:
        note = self.selected_note
        minimal_card = self.minimal_mode and self._minimal_card_selected
        if note is None or (self._sel is None and not minimal_card):
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
        for it here — plain notes never get an empty folder. The 📎 count refreshes on the next
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

    # Cached answer to "does the installed Nemo understand --tabs?" — see _nemo_supports_tabs.
    _nemo_tabs: bool | None = None

    @classmethod
    def _nemo_supports_tabs(cls) -> bool:
        """Whether ``nemo --tabs`` is understood, probed once per session and cached.

        Worth the probe because Popen can't tell us: it only raises when the *binary* is missing, so
        a Nemo too old for the flag would parse-error, exit, open nothing — and the fallback chain in
        _reveal_folder would never fire, leaving Ctrl+O silently doing nothing. ``--help`` parses
        locally and returns in milliseconds; the timeout is only a guard.
        """
        if cls._nemo_tabs is None:
            try:
                done = subprocess.run(["nemo", "--help"], capture_output=True, text=True, timeout=3)
                cls._nemo_tabs = "--tabs" in (done.stdout + done.stderr)
            except (OSError, subprocess.SubprocessError):
                cls._nemo_tabs = False   # no Nemo, or it didn't answer — the fallbacks cover it
        return cls._nemo_tabs

    def _reveal_folder(self, note_path: str) -> None:
        """Create (on demand) a note's companion folder and reveal it in the file manager.

        Opened as a new TAB in the Nemo window that's already up rather than yet another window:
        with auto-open-folder on, a capture session fires this on every save, and a window per note
        buries the desktop within minutes. ``--tabs`` still opens a window when none exists, so the
        first call behaves as before.
        """
        folder = companion_dir(note_path)
        try:
            os.makedirs(folder, exist_ok=True)
        except OSError:
            return
        # Most specific first: Nemo in a tab, plain Nemo, then the freedesktop default.
        commands = [["nemo", folder], ["xdg-open", folder]]
        if self._nemo_supports_tabs():
            commands.insert(0, ["nemo", "--tabs", folder])
        for cmd in commands:
            try:
                subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                return
            except OSError:
                continue

    def _quick_append(self) -> None:
        """Space on a selected card: open a one-line field under it to append a comment to the note,
        without opening the full editor. Enter appends + saves; Esc cancels."""
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
        hint = f"   ·   ⛓ {n} marked — Ctrl+Shift+M to merge, Esc to clear" if n else ""
        self.header.set_title(getattr(self, "_base_title", "knotes") + hint)

    def _clear_merge_marks(self) -> None:
        if self._merge_marks:
            self._merge_marks = []
            self._apply_merge_classes()
            self._apply_header_title()

    def _merge_marked(self) -> None:
        """Ctrl+Shift+M: fold the marked notes into the first-marked one and move the rest to the
        ``merged`` subfolder, keeping the view put."""
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
            text=f"Merge {len(others)} note(s) into the survivor?")
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
        still needs the full editor. Returns True if it matched a code."""
        note = self.selected_note
        if note is None:
            return False
        ch = self._base_char(event)
        if not ch:
            return False
        up = ch.upper()
        new_type, new_cat = note.type, note.category
        if note.priority is not None:
            new_prio = note.priority
        else:
            lo, hi = self.repo_cfg.priority_min, max(self.repo_cfg.priority_max, self.repo_cfg.priority_min)
            new_prio = min(max(self.repo_cfg.priority_default, lo), hi)
        code = next((c for c in self.type_codes if c.upper() == up), None)
        if code is not None:
            new_type = code
        else:
            code = next((c for c in self.category_codes if c.upper() == up), None)
            if code is not None:
                new_cat = code
            elif ch.isdigit() and int(ch) in self.priority_buttons:
                new_prio = int(ch)
            else:
                return False
        if note.path in self._unrecognized_paths:
            new_path = self.repo.promote(note, new_type, new_prio, new_cat, self.settings.timestamp_format)
        else:
            new_path = self.repo.update(note, note.content, new_type, new_prio, new_cat)
        # Follow the edited note itself — a note may be retagged several times in a row (type,
        # then priority, then category, ...), and jumping selection to a NEIGHBOUR each time (the
        # earlier fix here) compounds: after a few edits you're focused several cards away from
        # where you started. The real fix for "mass-tagging relocates the card" is sorting by
        # "Date created" instead of "Last updated" — that keeps a retagged note's position stable,
        # so following it here is a no-op move in that mode, and a correct one in every other mode.
        self._reload_keeping_scroll(new_path)
        return True

    def _save_draft(self) -> None:
        """Persist whatever is in the add field (with the current tags) so a forgotten note isn't lost."""
        start, end = self.buffer.get_bounds()
        text = self.buffer.get_text(start, end, True).strip()
        if text:
            self.repo.save_new(text, self.selected_type, self.priority,
                               self.selected_category, self.settings.timestamp_format)
            self.buffer.set_text("")

    def _on_delete_event(self, _widget, _event) -> bool:
        self._save_draft()
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
        words = note.content.split()
        n = len(words)
        head = max(0, self.settings.preview_head_words)
        tail = max(0, self.settings.preview_tail_words)
        if n <= head + tail or head + tail == 0:
            preview = " ".join(words)
        else:
            preview = " ".join(words[:head]) + " … " + " ".join(words[-tail:])
        return f"{preview}   ({n}w)" if preview else f"({n}w)"

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
        self.kanban_button.set_active(not self.minimal_mode and mode not in ("list", "columns"))
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

    def _on_repo_changed(self, combo: Gtk.ComboBoxText) -> None:
        """Switch the active note folder: new notes go there and the board shows its notes. The text
        being composed stays in the field, so the menu redirects where the current note will land."""
        if self._suppress_repo:
            return
        code = combo.get_active_id()
        if not code or code == self.settings.active_repo_code():
            return
        self.settings.active_repo = code
        self.settings.save()
        self.repo = NoteRepository(self.settings.active_repo_path())
        # Each folder has its own tag options — load them and rebuild the chips for the new folder.
        self.repo_cfg = RepoConfig.load(self.settings.active_repo_path(), self.settings)
        self._build_chips()
        # Filters name codes/dates from the folder we're leaving; carried over they'd silently hide
        # most of the new folder — so the sidebar starts clean on every switch.
        self.filters.clear()
        self._refresh_taxonomy()
        self._apply_defaults()
        self._sel_path = None      # selection belonged to the previous folder's board
        self._board_active = False
        self._refresh_status()
        self.reload_notes()
        self._rearm_note_dir_watch()   # follow the switch — watch the new folder, not the old one
        self._sync_backend_taxonomy()  # the new folder may bind its own backend

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

    def _open_external(self, note: Note) -> None:
        """Open the note's file in the configured opener, or the system default (xdg-open)."""
        opener = self.settings.opener.strip()
        argv = ([opener] if opener else ["xdg-open"]) + [note.path]
        try:
            subprocess.Popen(argv)
        except OSError as exc:
            dialog = Gtk.MessageDialog(
                transient_for=self, modal=True, message_type=Gtk.MessageType.ERROR,
                buttons=Gtk.ButtonsType.OK, text=f"Couldn't open the note with '{argv[0]}'")
            dialog.format_secondary_text(str(exc))
            dialog.run()
            dialog.destroy()

    # ----------------------------------------------------------- search

    def _set_search(self, active: bool) -> None:
        self.search_button.set_active(active)
        self.search_bar.set_search_mode(active)
        if active:
            self.search_entry.grab_focus()
        else:
            self.search_entry.set_text("")
            self.textview.grab_focus()

    def _on_search_changed(self, entry: Gtk.SearchEntry) -> None:
        self.reload_notes()

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
        in_text = (self.textview.has_focus() or self.search_entry.has_focus()
                   or (self._append_entry is not None and self._append_entry.has_focus()))
        board = self._board_active and self.selected_note is not None and not in_text

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

        # Ctrl+1 … Ctrl+9: switch to the 1st … 9th configured note folder. (Priority uses
        # Ctrl+Shift+digit, so a plain Ctrl+digit is free for this.) Layout-independent via _base_char.
        if ctrl and not shift and not alt:
            ch = self._base_char(event)
            if ch.isdigit() and ch != "0" and self._select_repo(int(ch)):
                return True

        if ctrl and not shift and not alt and kv in (Gdk.KEY_f, Gdk.KEY_F):
            self._set_search(not self.search_bar.get_search_mode())
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

    def _toggle_theme(self) -> None:
        self.settings.theme = "light" if self.settings.theme == "dark" else "dark"
        self.settings.save()
        self.get_application().apply_theme(self.settings.theme)
        self._refresh_theme_icon()

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
            self._refresh_status()
            self.reload_notes()
            self._sync_backend_taxonomy()  # a newly-entered backend URL takes effect right away
        dialog.destroy()

    def _show_help(self) -> None:
        save_keys = "Enter (or Ctrl+Enter)" if self.settings.save_on_enter else "Ctrl+Enter"
        rows = [
            (save_keys, "Save the new note" + (" · Shift+Enter for a newline" if self.settings.save_on_enter else "")),
            ("Esc", "Text field ⇄ board (once → field · again → board)"),
            ("Ctrl+Shift+<letter>", "Type/category: edit selected card, else the add form"),
            ("Ctrl+Shift+<digit>", "Priority: edit selected card, else the add form"),
            ("Click a card / Arrows", "Select a card · move between cards (↑↓ in a column, ←→ across)"),
            ("Enter (card selected)", "Open the selected note in your editor"),
            ("Ctrl+D (card selected)", "Move the selected note to the .deleted folder (with confirmation)"),
            ("Ctrl+M / Ctrl+click", "Mark/unmark card for merge (first marked = survivor) · Esc clears"),
            ("Ctrl+Shift+M", "Merge marked notes into the survivor, rest → .merged folder"),
            ("Space (card selected)", "Quick-append a comment to the note (inline field · Enter adds · Esc cancels)"),
            ("Ctrl+O (card selected)", "Open the note's files folder in the file manager (created on demand)"),
            ("Ctrl+1 … Ctrl+9", "Switch to the 1st … 9th note folder"),
            ("Ctrl+F", "Toggle search"),
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

        self.format_entry = Gtk.Entry(text=settings.timestamp_format)
        self.format_entry.set_tooltip_text("Python strftime, e.g. %Y_%m_%d-%H%M%S")
        add("Timestamp format", self.format_entry)

        self.opener_entry = Gtk.Entry(text=settings.opener)
        self.opener_entry.set_placeholder_text("system default (xdg-open)")
        self.opener_entry.set_tooltip_text("Command used to open a note on click, e.g. xed. Blank = system default.")
        add("Open notes with", self.opener_entry)

        self.preview_spin = Gtk.SpinButton.new_with_range(20, 400, 10)
        self.preview_spin.set_value(settings.preview_chars)
        self.preview_spin.set_tooltip_text("Characters shown from the start of a note on its card.")
        add("Preview characters (head)", self.preview_spin)

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
        settings.timestamp_format = self.format_entry.get_text().strip() or settings.timestamp_format
        settings.opener = self.opener_entry.get_text().strip()
        settings.preview_chars = int(self.preview_spin.get_value())
        settings.preview_tail_chars = int(self.preview_tail_spin.get_value())
        settings.preview_head_words = int(self.head_words_spin.get_value())
        settings.preview_tail_words = int(self.tail_words_spin.get_value())
        settings.save_on_enter = self.save_enter_check.get_active()
        settings.show_tags_summary = self.show_tags_summary_check.get_active()
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
        self.apply_theme(self.settings.theme)

    def apply_theme(self, theme: str) -> None:
        """Recolour the whole app: swap the named-colour block and nudge the base GTK theme to its
        dark/light variant so the chrome (entries, scrollbars, dialogs) matches."""
        if theme not in THEMES:
            theme = "dark"
        gtk_settings = Gtk.Settings.get_default()
        if gtk_settings is not None:
            gtk_settings.set_property("gtk-application-prefer-dark-theme", theme == "dark")
        if self._css_provider is not None:
            css = THEMES[theme] + self._structural_css
            self._css_provider.load_from_data(css.encode("utf-8"))


def main() -> int:
    # Sets the X11 program class, which (with set_wmclass) gives a stable WM_CLASS for panel pinning.
    GLib.set_prgname("knotes")
    return KnotesApp().run(None)


if __name__ == "__main__":
    raise SystemExit(main())
