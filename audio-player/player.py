#!/usr/bin/env python3
"""A GTK3 desktop audio/video player with a folder library browser and strong ``.tmk`` bookmarking —
the Linux companion to the phone's player. Sibling to ``desktop/knotes`` (shared look, separate app).

Two pages (switch in the header):
  * **Library** — pick a folder; it's scanned for audio *and* video, and browsed by a 3-pane column
    browser (Genres → Artists → Albums, iTunes-style). An "Untagged" page collects files with no
    metadata; you can stamp genre/artist/album onto a whole folder of them at once. Double-click a
    track to play it.
  * **Player** — video renders in-window; an editable Title field writes the file's title tag;
    the seek bar shows bookmark ticks loaded from the file's ``.tmk`` sidecar; plus transport,
    0.75–1.25× pitch-preserving speed, per-file resume, folder-as-queue auto-advance, sleep timer
    and gain. Adding a bookmark creates/updates the ``.tmk`` sidecar so marks sync with the phone.

Run:   python3 player.py [media-file]
Needs: PyGObject + GTK 3 + GStreamer (+ python3-mutagen to write tags). See run.sh for the packages.
"""

from __future__ import annotations

import os
import re
import sys
import warnings

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, Gdk, GLib, Pango, Gio  # noqa: E402

import tmk  # noqa: E402
import library  # noqa: E402
import lyrics  # noqa: E402
import subtitles  # noqa: E402
import waveform  # noqa: E402
from config import Settings  # noqa: E402
from audio import AudioPlayer, MIN_RATE, MAX_RATE  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
VERSION = "0.2"

# Playlist track-store layout: 6 visible columns (#, Title, Artist, Album, Length, Bkm) then two
# hidden data columns — the file path and the Pango font weight used to bold the playing row.
PATH_COL = 6
WEIGHT_COL = 7

THEMES = {
    "dark": """
        @define-color bg        #002b36;
        @define-color bg_alt    #073642;
        @define-color fg        #93a1a1;
        @define-color fg_strong #fdf6e3;
        @define-color fg_muted  #657b83;
        @define-color accent    #268bd2;
        @define-color tick      #b58900;
    """,
    "light": """
        @define-color bg        #fdf6e3;
        @define-color bg_alt    #eee8d5;
        @define-color fg        #586e75;
        @define-color fg_strong #002b36;
        @define-color fg_muted  #93a1a1;
        @define-color accent    #268bd2;
        @define-color tick      #b58900;
    """,
}

STYLE = """
    window { background: @bg; color: @fg; }
    .nowplaying { font-size: 13px; color: @fg_strong; }
    .time { color: @fg_muted; font-family: monospace; }
    .flash { color: @accent; }
    .section { color: @fg_muted; font-size: 11px; letter-spacing: 1px; }
    .placeholder { color: @fg_muted; font-style: italic; }
    .title-entry { font-size: 15px; font-weight: bold; }
    scale mark { color: @tick; }
    scale trough { min-height: 6px; }
    /* The spectrum seek bar: a tall, see-through trough so the waveform painted behind it shows,
       while the scale itself stays the interactive seek control (slider + bookmark ticks). */
    scale.waveseek trough { background: transparent; border: none; min-height: 44px; }
    scale.waveseek trough highlight { background: transparent; }
    scale.waveseek slider {
        background: @fg_strong;
        border: 1px solid @accent;
        min-width: 4px;
        min-height: 44px;
        border-radius: 2px;
        margin: -8px 0;
    }
    .videobox { background: #000000; }
    treeview { background: @bg; color: @fg; }
    treeview:selected { background: @accent; color: @fg_strong; }
    .statusbar { background: @bg_alt; }
    .statusbar label { color: @fg_strong; }
    .pane-header {
        background: @bg_alt;
        color: @fg_strong;
        font-size: 11px;
        font-weight: bold;
        letter-spacing: 1px;
        padding: 5px 10px;
        border-bottom: 1px solid @fg_muted;
    }
    .pane { border: 1px solid @bg_alt; }
    paned > separator {
        background: alpha(@fg_muted, 0.35);
        min-width: 1px;
        min-height: 1px;
    }
    .subtitle {
        color: #ffffff;
        background: rgba(0,0,0,0.72);
        padding: 3px 12px;
        border-radius: 6px;
        font-size: 15px;
    }
    .foldertree { background: @bg_alt; }
"""


def fmt_time(ms: int) -> str:
    """``M:SS`` / ``H:MM:SS`` for the on-screen time labels (no brackets — brackets are the .tmk
    file's job)."""
    if ms is None or ms < 0:
        return "--:--"
    return tmk.format_timestamp(ms)


class PlayerWindow(Gtk.ApplicationWindow):
    def __init__(self, app: "PlayerApp", open_path: str | None):
        super().__init__(application=app, title="audio player")
        self.set_default_size(app.settings.win_w or 1000, app.settings.win_h or 720)
        self.set_position(Gtk.WindowPosition.CENTER)
        self.maximize()  # start maximized (the saved size applies when you un-maximize)
        try:
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", DeprecationWarning)
                self.set_wmclass("audio-player", "audio-player")
        except Exception:
            pass
        icon = os.path.join(HERE, "audio-player.svg")
        if os.path.exists(icon):
            self.set_icon_from_file(icon)

        self.settings = app.settings

        # Player state.
        self.bookmarks: list[tmk.Bookmark] = []
        self.siblings: list[str] = []
        self._duration_ms = -1
        self._seeking = False
        self._flash_source = 0
        self._sleep_source = 0
        self._tick = 0
        self._cues: list[subtitles.Cue] = []   # active subtitle track's cues ([] = subtitles off)
        self._suppress_sleep = False           # guards programmatic sleep-combo changes
        self._wave_peaks: list[float] = []      # amplitude envelope drawn behind the seek bar
        self._wave_job: waveform.Job | None = None  # in-flight envelope decode (cancel on new file)
        self._wave_path: str | None = None      # file the envelope belongs to (guards needless redecodes)

        # Library state.
        self.tracks: list[library.Track] = []
        self.lib_folder: str = ""
        self.sel_genre: str | None = None     # None = "All"
        self.sel_artist: str | None = None
        self.sel_album: str | None = None
        self.untagged_only = False
        self._suppress_col = False            # guard programmatic column-selection changes

        self.audio = AudioPlayer(
            on_eos=self._on_eos, on_error=self._on_error,
            on_duration=self._on_duration, on_state=self._on_state)

        self._apply_theme()
        self._build_ui()
        self.connect("key-press-event", self._on_key)
        self.connect("delete-event", self._on_close)

        GLib.timeout_add(250, self._on_timer)

        start = open_path or (self.settings.last_media or None)
        if start and os.path.exists(start):
            self.open_media(start, autoplay=bool(open_path))
        if self.settings.last_folder and os.path.isdir(self.settings.last_folder):
            self._reveal_and_select(self.settings.last_folder)
            self._scan(self.settings.last_folder)

    # ================================================================== UI shell

    def _build_ui(self) -> None:
        header = Gtk.HeaderBar(show_close_button=True, title=f"audio player v{VERSION}")
        self.set_titlebar(header)

        open_btn = Gtk.Button()
        open_btn.add(Gtk.Image.new_from_icon_name("document-open-symbolic", Gtk.IconSize.BUTTON))
        open_btn.set_tooltip_text("Open a media file (Ctrl+O)")
        open_btn.connect("clicked", lambda _b: self._choose_file())
        header.pack_start(open_btn)

        browse_btn = Gtk.Button()
        browse_btn.add(Gtk.Image.new_from_icon_name("folder-symbolic", Gtk.IconSize.BUTTON))
        browse_btn.set_tooltip_text("Focus the folder tree (Ctrl+L)")
        browse_btn.connect("clicked", lambda _b: self._focus_folder_tree())
        header.pack_start(browse_btn)

        self.theme_btn = Gtk.Button()
        self.theme_btn.add(Gtk.Image.new_from_icon_name("weather-clear-night-symbolic", Gtk.IconSize.BUTTON))
        self.theme_btn.set_tooltip_text("Toggle light / dark (Ctrl+T)")
        self.theme_btn.connect("clicked", lambda _b: self._toggle_theme())
        header.pack_end(self.theme_btn)

        settings_btn = Gtk.Button()
        settings_btn.add(Gtk.Image.new_from_icon_name("emblem-system-symbolic", Gtk.IconSize.BUTTON))
        settings_btn.set_tooltip_text("Settings (Ctrl+,)")
        settings_btn.connect("clicked", lambda _b: self._open_settings())
        header.pack_end(settings_btn)

        # One unified window: [ left sidebar | right ], with a status bar underneath.
        root = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        self.add(root)

        self.outer_paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        self.outer_paned.set_position(self.settings.pane_sidebar or 260)
        root.pack_start(self.outer_paned, True, True, 0)

        # LEFT sidebar: folder tree (top) + recently-bookmarked files (short, bottom).
        self.left_paned = Gtk.Paned(orientation=Gtk.Orientation.VERTICAL)
        self.left_paned.set_position(self.settings.pane_left or 430)
        self.left_paned.pack1(self._build_folder_tree(), True, False)
        self.left_paned.pack2(self._build_recent_bookmarked(), False, False)
        self.outer_paned.pack1(self.left_paned, False, False)

        # RIGHT: folder header + 3-pane browser on top, then [ playlist | player ].
        right = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        right.set_border_width(10)
        self.outer_paned.pack2(right, True, True)

        top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.folder_label = Gtk.Label(label="Double-click a folder on the left to scan it.", xalign=0)
        self.folder_label.set_ellipsize(1)
        self.folder_label.get_style_context().add_class("time")
        top.pack_start(self.folder_label, True, True, 0)
        self.untagged_toggle = Gtk.ToggleButton.new_with_label("Untagged only")
        self.untagged_toggle.set_tooltip_text("Show only files with no genre/artist/album")
        self.untagged_toggle.connect("toggled", self._on_untagged_toggled)
        top.pack_start(self.untagged_toggle, False, False, 0)
        right.pack_start(top, False, False, 0)

        # 3-pane column browser — generous gap between the columns, no etched scroller borders.
        panes = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=18)
        panes.set_homogeneous(True)
        self.genre_view, self.genre_store = self._make_column("Genres", self._on_genre_selected)
        self.artist_view, self.artist_store = self._make_column("Artists", self._on_artist_selected)
        self.album_view, self.album_store = self._make_column("Albums", self._on_album_selected)
        for v in (self.genre_view, self.artist_view, self.album_view):
            sc = Gtk.ScrolledWindow()
            sc.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
            sc.set_min_content_height(130)
            sc.set_shadow_type(Gtk.ShadowType.NONE)
            sc.add(v)
            panes.pack_start(sc, True, True, 0)
        right.pack_start(panes, False, False, 0)

        # The playlist and the player share the space below the browser, side by side. A lower split
        # position gives the player pane more room (wider by default).
        self.split_paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        self.split_paned.set_position(self.settings.pane_split or 360)
        self.split_paned.pack1(self._build_playlist_pane(), True, True)
        player_scroller = Gtk.ScrolledWindow()
        player_scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        player_scroller.add(self._build_player_page())
        # The player and the lyrics side panel (sidecar .ai.txt transcriptions) share the right
        # side; the panel is hidden until a transcription language is picked.
        self.player_split = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        self.player_split.pack1(player_scroller, True, True)
        self.player_split.pack2(self._build_lyrics_panel(), False, True)
        self.split_paned.pack2(self.player_split, True, True)
        right.pack_start(self.split_paned, True, True, 0)

        # Bottom status bar: "Scanning…" during a scan, then the media-file count.
        root.pack_start(Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL), False, False, 0)
        statusbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        statusbar.get_style_context().add_class("statusbar")
        statusbar.set_border_width(4)
        self.scan_spinner = Gtk.Spinner()
        self.scan_spinner.set_no_show_all(True)  # only shown while a scan is running
        statusbar.pack_start(self.scan_spinner, False, False, 6)
        self.scan_status = Gtk.Label(label="Ready", xalign=0)
        self.scan_status.get_style_context().add_class("time")
        statusbar.pack_start(self.scan_status, True, True, 0)
        root.pack_start(statusbar, False, False, 0)

    @staticmethod
    def _pane_header(text: str) -> Gtk.Widget:
        """A titled header bar with a bottom border, for clear separation between panes."""
        lbl = Gtk.Label(label=text, xalign=0)
        lbl.get_style_context().add_class("pane-header")
        return lbl

    @staticmethod
    def _cmp_int(model, a, b, col):
        """Sort a numeric text column ('#', 'Bkm') by value; blanks sort as -1 (before 0)."""
        def val(it):
            try:
                return int(model[it][col])
            except (ValueError, TypeError):
                return -1
        va, vb = val(a), val(b)
        return (va > vb) - (va < vb)

    @staticmethod
    def _cmp_time(model, a, b, col):
        """Sort the 'Length' column by its real duration, parsing the ``M:SS`` / ``H:MM:SS`` text."""
        def val(it):
            ms = tmk.parse_timestamp(model[it][col])
            return ms if ms is not None else -1
        va, vb = val(a), val(b)
        return (va > vb) - (va < vb)

    def _build_playlist_pane(self) -> Gtk.Widget:
        """The playlist: the scanned track list plus the bulk folder-tag bar beneath it."""
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        box.get_style_context().add_class("pane")
        box.pack_start(self._pane_header("PLAYLIST"), False, False, 0)

        # Columns 0-5 are shown; index 6 (path) and 7 (font weight, for bolding the playing row) are
        # hidden data columns. The weight column is bound to every visible cell's "weight" attribute.
        self.track_store = Gtk.ListStore(str, str, str, str, str, str, str, int)
        # Numeric / time columns sort by value, not by their displayed string.
        self.track_store.set_sort_func(0, self._cmp_int, 0)     # #
        self.track_store.set_sort_func(4, self._cmp_time, 4)    # Length
        self.track_store.set_sort_func(5, self._cmp_int, 5)     # Bkm
        self.track_view = Gtk.TreeView(model=self.track_store)
        self.track_view.set_tooltip_text("Double-click a track to play it · click a header to sort")
        for i, (title, expand) in enumerate(
                [("#", False), ("Title", True), ("Artist", False), ("Album", False),
                 ("Length", False), ("Bkm", False)]):
            r = Gtk.CellRendererText()
            if title == "Title":
                r.set_property("ellipsize", 3)
            if title == "Bkm":
                r.set_property("xalign", 1.0)  # right-align the count
            col = Gtk.TreeViewColumn(title, r, text=i, weight=WEIGHT_COL)
            col.set_expand(expand)
            col.set_resizable(True)
            col.set_reorderable(True)     # drag column headers to reorder (e.g. Length before Title)
            col.set_sort_column_id(i)     # click a header to sort by that column
            self.track_view.append_column(col)
        self.track_view.connect("row-activated", self._on_track_activated)
        self._apply_column_order()                                   # restore saved header order
        self.track_view.connect("columns-changed", self._save_column_order)  # persist future drags
        tsc = Gtk.ScrolledWindow()
        tsc.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        tsc.add(self.track_view)
        box.pack_start(tsc, True, True, 0)

        # Bulk folder-tag bar (used mainly for the untagged page): 3 standard fields for the folder.
        tagbar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        for m in ("set_margin_start", "set_margin_end", "set_margin_bottom"):
            getattr(tagbar, m)(6)
        tagbar.set_margin_top(4)
        tagbar.pack_start(Gtk.Label(label="Tag →", xalign=0), False, False, 0)
        self.tag_genre = Gtk.Entry(); self.tag_genre.set_placeholder_text("Genre")
        self.tag_artist = Gtk.Entry(); self.tag_artist.set_placeholder_text("Artist")
        self.tag_album = Gtk.Entry(); self.tag_album.set_placeholder_text("Album")
        for e in (self.tag_genre, self.tag_artist, self.tag_album):
            e.set_width_chars(10)
            tagbar.pack_start(e, True, True, 0)
        self.apply_tag_btn = Gtk.Button.new_with_label("Apply")
        self.apply_tag_btn.get_style_context().add_class("suggested-action")
        self.apply_tag_btn.connect("clicked", lambda _b: self._apply_folder_tags())
        tagbar.pack_start(self.apply_tag_btn, False, False, 0)
        box.pack_start(tagbar, False, False, 0)
        if not library.mutagen_available():
            tagbar.set_tooltip_text("Install python3-mutagen to write tags")
            self.apply_tag_btn.set_sensitive(False)
        return box

    def _build_recent_bookmarked(self) -> Gtk.Widget:
        """A short list, under the folder tree, of files you recently bookmarked — click to reopen."""
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        box.get_style_context().add_class("pane")
        box.pack_start(self._pane_header("RECENTLY BOOKMARKED"), False, False, 0)
        sc = Gtk.ScrolledWindow()
        sc.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        sc.set_min_content_height(96)
        self.recent_bm_list = Gtk.ListBox()
        self.recent_bm_list.set_selection_mode(Gtk.SelectionMode.NONE)
        self.recent_bm_list.connect("row-activated", self._on_recent_bm_activated)
        sc.add(self.recent_bm_list)
        box.pack_start(sc, True, True, 0)
        self._refresh_recent_bookmarked()
        return box

    def _refresh_recent_bookmarked(self) -> None:
        for child in self.recent_bm_list.get_children():
            self.recent_bm_list.remove(child)
        for path in self.settings.recent_bookmarked:
            row = Gtk.ListBoxRow()
            lbl = Gtk.Label(label=os.path.basename(path), xalign=0)
            lbl.set_ellipsize(3)
            lbl.set_margin_start(6)
            lbl.set_margin_end(6)
            lbl.set_tooltip_text(path)
            row.add(lbl)
            row._path = path  # carried for activation
            self.recent_bm_list.add(row)
        self.recent_bm_list.show_all()

    def _on_recent_bm_activated(self, _list, row) -> None:
        path = getattr(row, "_path", None)
        if path and os.path.exists(path):
            self.open_media(path, autoplay=True)

    def _build_folder_tree(self) -> Gtk.Widget:
        """A permanent, lazily-populated directory tree rooted at one configurable folder (default:
        the home directory — not the whole filesystem). Expanding a node fills in its subdirectories
        on demand; double-clicking a folder scans it into the library (a mere expand never scans, so
        drilling toward a folder can't accidentally kick off a huge scan)."""
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        box.get_style_context().add_class("pane")
        box.pack_start(self._pane_header("FOLDERS"), False, False, 0)

        # Root chooser: a symbolic gear icon + the current root name; click to point the tree elsewhere.
        self.root_btn = Gtk.Button()
        self.root_btn.set_relief(Gtk.ReliefStyle.NONE)
        self.root_btn.set_image(Gtk.Image.new_from_icon_name("emblem-system-symbolic", Gtk.IconSize.BUTTON))
        self.root_btn.set_always_show_image(True)
        self.root_btn.set_tooltip_text("Change the tree's root folder")
        self.root_btn.connect("clicked", lambda _b: self._choose_root())
        box.pack_start(self.root_btn, False, False, 0)
        box.pack_start(Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL), False, False, 0)

        # Columns: icon-name (symbolic), display name, absolute path.
        self.folder_store = Gtk.TreeStore(str, str, str)
        self.folder_tree = Gtk.TreeView(model=self.folder_store)
        self.folder_tree.set_headers_visible(False)
        self.folder_tree.set_tooltip_text("Double-click a folder to scan it into the library")
        self.folder_tree.get_style_context().add_class("foldertree")
        col = Gtk.TreeViewColumn("")
        icon_r = Gtk.CellRendererPixbuf()
        col.pack_start(icon_r, False)
        col.add_attribute(icon_r, "icon-name", 0)
        text_r = Gtk.CellRendererText()
        col.pack_start(text_r, True)
        col.add_attribute(text_r, "text", 1)
        self.folder_tree.append_column(col)
        self.folder_tree.connect("row-expanded", self._on_folder_expanded)
        self.folder_tree.connect("row-activated", self._on_folder_activated)

        sc = Gtk.ScrolledWindow()
        sc.set_policy(Gtk.PolicyType.AUTOMATIC, Gtk.PolicyType.AUTOMATIC)
        sc.set_min_content_width(220)
        sc.add(self.folder_tree)
        box.pack_start(sc, True, True, 0)

        self._populate_root()
        return box

    def _root_path(self) -> str:
        """The configured tree root, expanded; a blank setting means the home directory."""
        return os.path.expanduser(self.settings.library_root or "~")

    def _populate_root(self) -> None:
        """(Re)build the tree from a single root node at the configured folder."""
        self.folder_store.clear()
        root = self._root_path()
        home = os.path.abspath(os.path.expanduser("~"))
        is_home = os.path.abspath(root) == home
        name = "Home" if is_home else (os.path.basename(root.rstrip("/")) or root)
        self.root_btn.set_label(f"Root: {name}")
        self._add_folder_root(root, name, "user-home-symbolic" if is_home else "folder-symbolic")

    def _choose_root(self) -> None:
        dialog = Gtk.FileChooserDialog(
            title="Choose the tree's root folder", transient_for=self,
            action=Gtk.FileChooserAction.SELECT_FOLDER)
        dialog.add_buttons(Gtk.STOCK_CANCEL, Gtk.ResponseType.CANCEL, "Set root", Gtk.ResponseType.OK)
        dialog.set_current_folder(self._root_path())
        if dialog.run() == Gtk.ResponseType.OK:
            self.settings.library_root = dialog.get_filename()
            self.settings.save()
            self._populate_root()
        dialog.destroy()

    def _add_folder_root(self, path: str, label: str, icon: str) -> None:
        it = self.folder_store.append(None, [icon, label, path])
        self._maybe_add_placeholder(it, path)

    def _maybe_add_placeholder(self, parent_it, path: str) -> None:
        """Give a node an expander by seeding a dummy child, if the directory has any subdirectory."""
        if self._first_subdir(path) is not None:
            self.folder_store.append(parent_it, ["", "", ""])  # placeholder, filled on expand

    def _first_subdir(self, path: str):
        try:
            with os.scandir(path) as sc:
                for e in sc:
                    if not e.name.startswith(".") and e.is_dir(follow_symlinks=False):
                        return e.name
        except OSError:
            pass
        return None

    def _subdirs(self, path: str) -> list[str]:
        try:
            with os.scandir(path) as sc:
                names = [e.name for e in sc
                         if not e.name.startswith(".") and e.is_dir(follow_symlinks=False)]
        except OSError:
            return []
        return sorted(names, key=str.lower)

    def _on_folder_expanded(self, _view, parent_it, _tpath) -> None:
        store = self.folder_store
        first = store.iter_children(parent_it)
        if first is None or store[first][2] != "":  # no placeholder → already populated
            return
        parent_path = store[parent_it][2]
        for name in self._subdirs(parent_path):
            full = os.path.join(parent_path, name)
            child = store.append(parent_it, ["folder-symbolic", name, full])
            self._maybe_add_placeholder(child, full)
        store.remove(first)  # drop the placeholder

    def _on_folder_activated(self, view, tpath, _col) -> None:
        it = self.folder_store.get_iter(tpath)
        path = self.folder_store[it][2]
        if path and os.path.isdir(path):
            if view.row_expanded(tpath):
                view.collapse_row(tpath)
            else:
                view.expand_row(tpath, False)   # double-click also toggles the branch, like a file manager
            self._scan(path)

    def _reveal_and_select(self, target: str) -> None:
        """Best-effort: expand the tree down to ``target`` and select it (used on launch to restore
        the last folder). Walks whichever root ('~' or '/') is a prefix, populating each level."""
        target = os.path.abspath(target)
        store = self.folder_store
        root_it = next((row.iter for row in store
                        if row[2] and (target == row[2] or target.startswith(os.path.join(row[2], "")))),
                       None)
        if root_it is None:
            return
        it, base = root_it, store[root_it][2]
        rel = os.path.relpath(target, base)
        parts = [] if rel == "." else rel.split(os.sep)
        for part in parts:
            self.folder_tree.expand_row(store.get_path(it), False)  # populate this level
            child = store.iter_children(it)
            match = None
            while child is not None:
                if os.path.basename(store[child][2]) == part:
                    match = child
                    break
                child = store.iter_next(child)
            if match is None:
                break
            it = match
        self.folder_tree.expand_to_path(store.get_path(it))
        self.folder_tree.get_selection().select_iter(it)
        self.folder_tree.scroll_to_cell(store.get_path(it), None, False, 0, 0)

    def _apply_column_order(self) -> None:
        """Reorder the playlist columns to the saved order (by title). Runs before the
        columns-changed handler is connected, so it doesn't re-trigger a save."""
        saved = self.settings.track_columns
        if not saved:
            return
        by_title = {c.get_title(): c for c in self.track_view.get_columns()}
        prev = None
        for title in saved:
            col = by_title.get(title)
            if col is not None:
                self.track_view.move_column_after(col, prev)  # prev=None → move to the front
                prev = col

    def _save_column_order(self, _tv) -> None:
        """Persist the current playlist column order after a header drag."""
        order = [c.get_title() for c in self.track_view.get_columns()]
        if order and order != self.settings.track_columns:
            self.settings.track_columns = order
            self.settings.save()

    def _make_column(self, title: str, on_selected):
        store = Gtk.ListStore(str, str)  # display, value ("" = All)
        view = Gtk.TreeView(model=store)
        view.set_headers_visible(True)
        col = Gtk.TreeViewColumn(title, Gtk.CellRendererText(), text=0)
        col.set_expand(True)
        view.append_column(col)
        view.get_selection().connect("changed", on_selected)
        return view, store

    # ----------------------------------------------------------- scanning

    def _scan(self, folder: str) -> None:
        self.lib_folder = folder
        self.tracks = []
        self.folder_label.set_text(folder)
        self.scan_status.set_text("Scanning…")
        self.scan_spinner.show()
        self.scan_spinner.start()   # animates on the GTK main loop while the scan runs off-thread
        self.settings.last_folder = folder
        self.settings.save()

        def on_track(track, done, total):
            self.tracks.append(track)
            self.scan_status.set_text(f"Scanning…  {done}/{total}")
            return False

        def on_done(tracks):
            self.tracks = tracks
            n = len(tracks)
            self.scan_spinner.stop()
            self.scan_spinner.hide()
            self.scan_status.set_text(f"{n} media file{'' if n == 1 else 's'} found  ·  {folder}")
            self.sel_genre = self.sel_artist = self.sel_album = None
            self._refresh_columns()
            self._refresh_track_list()
            return False

        library.scan_folder(folder, on_track=on_track, on_done=on_done)

    # ----------------------------------------------------------- filtering / views

    def _filtered(self, by_genre=True, by_artist=True, by_album=True) -> list[library.Track]:
        out = self.tracks
        if by_genre and self.sel_genre is not None:
            out = [t for t in out if t.g() == self.sel_genre]
        if by_artist and self.sel_artist is not None:
            out = [t for t in out if t.a() == self.sel_artist]
        if by_album and self.sel_album is not None:
            out = [t for t in out if t.al() == self.sel_album]
        return out

    def _visible_tracks(self) -> list[library.Track]:
        """What the track list shows: the untagged files when the toggle is on, else the
        genre/artist/album-filtered set."""
        if self.untagged_only:
            return [t for t in self.tracks if not t.has_tags]
        return self._filtered()

    def _refresh_columns(self) -> None:
        self._suppress_col = True
        self._fill_column(self.genre_store, library.counts(self.tracks, library.Track.g), self.sel_genre)
        self._fill_column(self.artist_store,
                          library.counts(self._filtered(by_artist=False, by_album=False), library.Track.a),
                          self.sel_artist)
        self._fill_column(self.album_store,
                          library.counts(self._filtered(by_album=False), library.Track.al), self.sel_album)
        self._suppress_col = False

    def _fill_column(self, store, pairs, selected) -> None:
        store.clear()
        total = sum(c for _v, c in pairs)
        store.append([f"All  ({total})", ""])
        for value, count in pairs:
            store.append([f"{value}  ({count})", value])

    def _refresh_track_list(self) -> None:
        self.track_store.clear()
        cur = os.path.abspath(self.audio.current_path) if self.audio.current_path else None
        for t in sorted(self._visible_tracks(),
                        key=lambda t: (t.al().lower(), t.track_no, t.display_title().lower())):
            num = str(t.track_no) if t.track_no else ""
            n_bm = len(tmk.load(t.path))                       # bookmark count from the .tmk sidecar
            playing = cur is not None and os.path.abspath(t.path) == cur
            weight = int(Pango.Weight.BOLD if playing else Pango.Weight.NORMAL)
            self.track_store.append(
                [num, t.display_title(), t.artist or "—", t.album or "—", fmt_time(t.duration_ms),
                 str(n_bm) if n_bm else "", t.path, weight])
        self.apply_tag_btn.set_label(f"Apply to {len(self._visible_tracks())} file(s)")
        # Rebuilding the store resets the scroll to the top; keep the playing file in view instead.
        GLib.idle_add(self._scroll_playlist_to_current)

    def _scroll_playlist_to_current(self) -> bool:
        cur = os.path.abspath(self.audio.current_path) if self.audio.current_path else None
        if cur:
            for i, row in enumerate(self.track_store):
                if os.path.abspath(row[PATH_COL]) == cur:
                    # use_align=True with row_align=0.5 centres the row vertically in the view.
                    self.track_view.scroll_to_cell(Gtk.TreePath(i), None, True, 0.5, 0.0)
                    break
        return False  # one-shot idle callback

    # ----------------------------------------------------------- column callbacks

    def _selected_value(self, view) -> str | None:
        model, it = view.get_selection().get_selected()
        if it is None:
            return None
        value = model[it][1]
        return None if value == "" else value  # "" is the All row

    def _on_genre_selected(self, _sel) -> None:
        if self._suppress_col:
            return
        self.sel_genre = self._selected_value(self.genre_view)
        self.sel_artist = self.sel_album = None
        self._refresh_columns()
        self._refresh_track_list()

    def _on_artist_selected(self, _sel) -> None:
        if self._suppress_col:
            return
        self.sel_artist = self._selected_value(self.artist_view)
        self.sel_album = None
        self._suppress_col = True
        self._fill_column(self.album_store,
                          library.counts(self._filtered(by_album=False), library.Track.al), None)
        self._suppress_col = False
        self._refresh_track_list()

    def _on_album_selected(self, _sel) -> None:
        if self._suppress_col:
            return
        self.sel_album = self._selected_value(self.album_view)
        self._refresh_track_list()

    def _on_untagged_toggled(self, btn) -> None:
        self.untagged_only = btn.get_active()
        # On the untagged page the column browser doesn't apply; keep it visible but the list shows
        # only untagged files, which are the target of the bulk tag bar.
        self._refresh_track_list()

    def _on_track_activated(self, view, path, _col) -> None:
        it = self.track_store.get_iter(path)
        media = self.track_store[it][PATH_COL]
        # Queue becomes the tracks currently shown, so next/prev walk this view.
        self.open_media(media, autoplay=True, queue=[t.path for t in self._visible_tracks()])

    def _apply_folder_tags(self) -> None:
        targets = [t.path for t in self._visible_tracks()]
        genre, artist, album = (self.tag_genre.get_text(), self.tag_artist.get_text(),
                                self.tag_album.get_text())
        if not targets or not any(v.strip() for v in (genre, artist, album)):
            self._flash("Enter a genre/artist/album and have files listed first")
            return
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.YES_NO,
            text=f"Write these tags to {len(targets)} file(s)?")
        dialog.format_secondary_text(
            f"Genre: {genre or '—'}\nArtist: {artist or '—'}\nAlbum: {album or '—'}")
        dialog.set_default_response(Gtk.ResponseType.YES)
        resp = dialog.run(); dialog.destroy()
        if resp != Gtk.ResponseType.YES:
            return
        written, skipped = library.write_tags(targets, genre, artist, album)
        self._flash(f"Tagged {written} file(s)" + (f", {len(skipped)} unsupported" if skipped else ""))
        self._scan(self.lib_folder)  # re-scan so the browser reflects the new tags

    # ================================================================== Player page

    def _build_player_page(self) -> Gtk.Widget:
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        outer.get_style_context().add_class("pane")
        outer.pack_start(self._pane_header("PLAYER"), False, False, 0)
        page = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        page.set_border_width(14)
        outer.pack_start(page, True, True, 0)

        # Video area (hidden for audio-only). The engine's embeddable widget lives here, with the
        # subtitle overlaid at its bottom (for video files).
        self.video_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.video_box.get_style_context().add_class("videobox")
        self.video_box.set_no_show_all(True)
        overlay = Gtk.Overlay()
        if self.audio.video_widget is not None:
            self.audio.video_widget.set_size_request(-1, 300)
            overlay.add(self.audio.video_widget)
        self.sub_video_label = Gtk.Label(label="")
        self.sub_video_label.get_style_context().add_class("subtitle")
        self.sub_video_label.set_halign(Gtk.Align.CENTER)
        self.sub_video_label.set_valign(Gtk.Align.END)
        self.sub_video_label.set_margin_bottom(14)
        self.sub_video_label.set_line_wrap(True)
        self.sub_video_label.set_no_show_all(True)
        overlay.add_overlay(self.sub_video_label)
        self.video_box.pack_start(overlay, True, True, 0)
        page.pack_start(self.video_box, False, False, 0)

        # Editable title of the playing file (writes the title tag on Enter).
        self.title_entry = Gtk.Entry()
        self.title_entry.get_style_context().add_class("title-entry")
        self.title_entry.set_placeholder_text("Title of the playing file — edit and press Enter to save")
        self.title_entry.set_tooltip_text("Set the file's title tag (Enter to save)")
        self.title_entry.connect("activate", lambda _e: self._save_title())
        page.pack_start(self.title_entry, False, False, 0)

        name_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.filename_label = Gtk.Label(label="No file open", xalign=0)
        self.filename_label.set_ellipsize(3)
        self.filename_label.get_style_context().add_class("nowplaying")
        name_row.pack_start(self.filename_label, True, True, 0)
        self.delete_btn = Gtk.Button()
        self.delete_btn.add(Gtk.Image.new_from_icon_name("user-trash-symbolic", Gtk.IconSize.BUTTON))
        self.delete_btn.set_relief(Gtk.ReliefStyle.NONE)
        self.delete_btn.set_tooltip_text("Move this file to the Trash (Ctrl+Delete)")
        self.delete_btn.connect("clicked", lambda _b: self._delete_current_file())
        name_row.pack_start(self.delete_btn, False, False, 0)
        page.pack_start(name_row, False, False, 0)

        # Subtitle language chips (one per sidecar .srt found; hidden when there are none).
        self.subs_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.subs_row.set_no_show_all(True)
        page.pack_start(self.subs_row, False, False, 0)

        # Transcription ("lyrics") language chips — sidecar .ai.txt files, e.g. x.ar.ai.txt.
        # Picking a language opens the scrollable side panel; the trash button deletes the file.
        self.lyrics_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.lyrics_row.set_no_show_all(True)
        page.pack_start(self.lyrics_row, False, False, 0)

        # For audio-only files the active subtitle shows here, just above the seek bar.
        self.sub_bar_label = Gtk.Label(label="")
        self.sub_bar_label.get_style_context().add_class("subtitle")
        self.sub_bar_label.set_halign(Gtk.Align.CENTER)
        self.sub_bar_label.set_line_wrap(True)
        self.sub_bar_label.set_no_show_all(True)
        page.pack_start(self.sub_bar_label, False, False, 0)

        # Seek bar with the audio's amplitude "spectrum" painted behind it (loud stretches show as
        # tall bars, quiet ones as flat) and bookmark ticks on top. The waveform is a DrawingArea at
        # the base of an overlay; the Scale rides on top with a transparent trough (see the .waveseek
        # CSS) so the bars show through while the Scale stays the interactive seek control.
        self.wave_area = Gtk.DrawingArea()
        self.wave_area.set_size_request(-1, 44)
        self.wave_area.connect("draw", self._draw_waveform)

        self.scale = Gtk.Scale(orientation=Gtk.Orientation.HORIZONTAL)
        self.scale.get_style_context().add_class("waveseek")
        self.scale.set_range(0, 1)
        self.scale.set_draw_value(False)
        self.scale.set_sensitive(False)
        self.scale.set_valign(Gtk.Align.FILL)
        self.scale.connect("change-value", self._on_scale_change)
        self.scale.connect("button-press-event", lambda *_: self._set_seeking(True))
        self.scale.connect("button-release-event", lambda *_: self._set_seeking(False))

        seek_overlay = Gtk.Overlay()
        seek_overlay.add(self.wave_area)
        seek_overlay.add_overlay(self.scale)
        page.pack_start(seek_overlay, False, False, 0)

        times = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        self.cur_label = Gtk.Label(label="0:00", xalign=0)
        self.rem_label = Gtk.Label(label="", xalign=0.5)
        self.dur_label = Gtk.Label(label="--:--", xalign=1)
        for lb in (self.cur_label, self.rem_label, self.dur_label):
            lb.get_style_context().add_class("time")
        times.pack_start(self.cur_label, True, True, 0)
        times.pack_start(self.rem_label, True, True, 0)
        times.pack_end(self.dur_label, False, False, 0)
        page.pack_start(times, False, False, 0)

        # Transport.
        transport = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        transport.set_halign(Gtk.Align.CENTER)

        def tbtn(icon, tip, cb, wide=False):
            b = Gtk.Button()
            b.add(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.LARGE_TOOLBAR))
            b.set_tooltip_text(tip)
            b.connect("clicked", lambda _b: cb())
            if wide:
                b.set_size_request(96, -1)  # the primary seek buttons are ~double width
            transport.pack_start(b, False, False, 0)
            return b

        tbtn("media-skip-backward-symbolic", "Previous track", self._prev_track)
        # The primary seek buttons (±skip seconds) read the configured duration live, so changing it
        # in Settings takes effect without a rebuild; they're the wide buttons.
        self.seek_back_btn = tbtn("media-seek-backward-symbolic", "",
                                  lambda: self._skip(-self.settings.skip_primary_secs), wide=True)
        self.play_btn = tbtn("media-playback-start-symbolic", "Play / Pause (Space · Ctrl+P)",
                             self._toggle, wide=True)
        self.seek_fwd_btn = tbtn("media-seek-forward-symbolic", "",
                                 lambda: self._skip(self.settings.skip_primary_secs), wide=True)
        tbtn("media-skip-forward-symbolic", "Next track", self._next_track)
        page.pack_start(transport, False, False, 2)

        # Add-bookmark row.
        add_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        self.note_entry = Gtk.Entry()
        self.note_entry.set_placeholder_text("Optional note for the next bookmark…")
        self.note_entry.connect("activate", lambda _e: self._add_bookmark())
        add_row.pack_start(self.note_entry, True, True, 0)
        self.add_bm_btn = Gtk.Button.new_with_label("＋ Bookmark")
        self.add_bm_btn.get_style_context().add_class("suggested-action")
        self.add_bm_btn.connect("clicked", lambda _b: self._add_bookmark())
        add_row.pack_start(self.add_bm_btn, False, False, 0)
        page.pack_start(add_row, False, False, 0)
        self._update_skip_tooltips()  # now that seek + add-bookmark buttons all exist

        self.flash_label = Gtk.Label(label="", xalign=0)
        self.flash_label.get_style_context().add_class("flash")
        page.pack_start(self.flash_label, False, False, 0)

        bm_header = Gtk.Label(label="BOOKMARKS", xalign=0)
        bm_header.get_style_context().add_class("section")
        page.pack_start(bm_header, False, False, 0)
        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        self.bm_list = Gtk.ListBox()
        self.bm_list.set_selection_mode(Gtk.SelectionMode.NONE)
        scroller.add(self.bm_list)
        page.pack_start(scroller, True, True, 0)

        page.pack_start(self._build_controls(), False, False, 0)

        self.status = Gtk.Label(label="", xalign=0)
        self.status.set_ellipsize(1)
        self.status.get_style_context().add_class("time")
        page.pack_start(self.status, False, False, 0)
        return outer

    def _build_controls(self) -> Gtk.Widget:
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)

        speed_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        speed_row.pack_start(Gtk.Label(label="Speed", xalign=0), False, False, 0)
        self.speed_scale = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, MIN_RATE, MAX_RATE, 0.05)
        self.speed_scale.set_value(self.settings.rate)
        self.speed_scale.set_draw_value(False)
        for v in (0.75, 1.0, 1.25):
            self.speed_scale.add_mark(v, Gtk.PositionType.BOTTOM, None)
        self.speed_scale.connect("value-changed", self._on_speed)
        speed_row.pack_start(self.speed_scale, True, True, 0)
        self.speed_label = Gtk.Label(label=f"{self.settings.rate:.2f}×")
        self.speed_label.get_style_context().add_class("time")
        speed_row.pack_start(self.speed_label, False, False, 0)
        box.pack_start(speed_row, False, False, 0)

        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        # Sleep timer: hidden unless enabled in Settings. Wrapped in its own box so it can be shown/
        # hidden as a unit.
        self.sleep_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.sleep_box.set_no_show_all(True)
        self.sleep_box.pack_start(Gtk.Label(label="Sleep", xalign=0), False, False, 0)
        self.sleep_combo = Gtk.ComboBoxText()
        self.sleep_combo.connect("changed", self._on_sleep)
        self.sleep_box.pack_start(self.sleep_combo, False, False, 0)
        row.pack_start(self.sleep_box, False, False, 0)
        self._rebuild_sleep_presets()

        row.pack_start(Gtk.Label(label="Vol", xalign=0), False, False, 0)
        self.vol_scale = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 0.0, 3.0, 0.05)
        self.vol_scale.set_value(self.settings.volume)
        self.vol_scale.set_draw_value(False)
        self.vol_scale.add_mark(1.0, Gtk.PositionType.BOTTOM, None)
        self.vol_scale.connect("value-changed", self._on_volume)
        row.pack_start(self.vol_scale, True, True, 0)
        box.pack_start(row, False, False, 0)
        self._apply_sleep_visibility()
        return box

    def _rebuild_sleep_presets(self) -> None:
        """(Re)populate the sleep-timer presets from settings (Off + each configured minute value)."""
        self._suppress_sleep = True
        self.sleep_combo.remove_all()
        self.sleep_combo.append("0", "Off")
        for m in self.settings.sleep_presets_min:
            self.sleep_combo.append(str(m), f"{m} min")
        self.sleep_combo.set_active_id("0")
        self._suppress_sleep = False

    def _apply_sleep_visibility(self) -> None:
        """Show or hide the sleep timer per the setting; cancel any running timer when hidden."""
        on = self.settings.sleep_enabled
        self.sleep_box.set_visible(on)
        if not on and self._sleep_source:
            GLib.source_remove(self._sleep_source)
            self._sleep_source = 0
            self.sleep_combo.set_active_id("0")

    # ================================================================== media control

    def open_media(self, path: str, autoplay: bool = False, queue: list[str] | None = None) -> None:
        path = os.path.abspath(path)
        self.audio.load(path, autoplay=autoplay)
        self.audio.set_rate(self.settings.rate)
        self.audio.set_volume(self.settings.volume)
        self._duration_ms = -1
        self.filename_label.set_text(os.path.basename(path))
        self.title_entry.set_text(self._title_for(path))
        self.scale.set_sensitive(True)
        self.scale.set_range(0, 1)
        self._start_waveform(None, 0)   # clear the old file's spectrum until this one's is decoded
        self.video_box.hide()
        self.settings.push_recent(path)

        self.bookmarks = tmk.load(path)
        self._refresh_bookmarks()
        self._refresh_subtitle_langs(path)
        self._refresh_lyrics_langs(path)

        if queue is not None:
            self.siblings = list(queue)
            if path not in self.siblings:
                self.siblings.insert(0, path)
        else:
            self._scan_siblings(path)
        self._refresh_status()

        resume = self.settings.saved_position(path)
        if resume > 0:
            self.audio.seek(resume)
        self.settings.save()
        self._refresh_track_list()   # re-bold the now-playing row in the playlist

    def _title_for(self, path: str) -> str:
        """The title to show in the editable field: the file's title tag if the library knows it,
        otherwise the file's base name (so there's always something to edit)."""
        for t in self.tracks:
            if os.path.abspath(t.path) == path and t.title:
                return t.title
        return os.path.splitext(os.path.basename(path))[0]

    def _save_title(self) -> None:
        path = self.audio.current_path
        if path is None:
            return
        title = self.title_entry.get_text().strip()
        if library.write_file_tags(path, {"title": title}):
            self._flash(f"✓ Title saved: {title}")
            for t in self.tracks:                       # keep the library view in sync
                if os.path.abspath(t.path) == os.path.abspath(path):
                    t.title = title
            self._refresh_track_list()                  # show the new title in the playlist
        else:
            self._flash("⚠ Could not write title (unsupported format or mutagen missing)")

    def _scan_siblings(self, path: str) -> None:
        folder = os.path.dirname(path)
        try:
            names = sorted(os.listdir(folder), key=str.lower)
        except OSError:
            names = [os.path.basename(path)]
        self.siblings = [os.path.join(folder, n) for n in names
                         if os.path.splitext(n)[1].lower() in library.MEDIA_EXTS]
        if path not in self.siblings:
            self.siblings.append(path)

    def _sibling_index(self) -> int:
        cur = self.audio.current_path
        return self.siblings.index(cur) if cur in self.siblings else -1

    def _prev_track(self) -> None:
        i = self._sibling_index()
        if i > 0:
            self._remember_position(persist=True)
            self.open_media(self.siblings[i - 1], autoplay=self.audio.is_playing, queue=self.siblings)

    def _next_track(self, autoplay: bool | None = None) -> None:
        i = self._sibling_index()
        if 0 <= i < len(self.siblings) - 1:
            play = self.audio.is_playing if autoplay is None else autoplay
            self._remember_position(persist=True)
            self.open_media(self.siblings[i + 1], autoplay=play, queue=self.siblings)

    def _delete_current_file(self) -> None:
        """Move the playing file (and its ``.tmk`` sidecar) to the Trash, after confirming. Then move
        to the next track in the queue, or clear the player if there is none."""
        path = self.audio.current_path
        if path is None:
            return
        playing = self.audio.is_playing
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.WARNING,
            buttons=Gtk.ButtonsType.NONE, text="Move this file to the Trash?")
        dialog.format_secondary_text(path)
        dialog.add_button(Gtk.STOCK_CANCEL, Gtk.ResponseType.CANCEL)
        del_btn = dialog.add_button("Move to Trash", Gtk.ResponseType.OK)
        del_btn.get_style_context().add_class("destructive-action")
        dialog.set_default_response(Gtk.ResponseType.OK)  # Enter confirms
        del_btn.grab_focus()                              # the confirm button starts focused
        resp = dialog.run()
        dialog.destroy()
        if resp != Gtk.ResponseType.OK:
            return
        self._do_delete_file(path, playing)

    @staticmethod
    def _trash(path: str) -> bool:
        """Move ``path`` to the freedesktop Trash (recoverable), not a physical delete. Returns True
        if it was trashed (or already gone)."""
        if not os.path.exists(path):
            return True
        try:
            return Gio.File.new_for_path(path).trash(None)
        except GLib.Error:
            return False

    def _do_delete_file(self, path: str, playing: bool) -> None:
        """The actual trashing (split out from the confirm dialog so it's testable)."""
        # Pick the next file to play *before* mutating the queue.
        i = self._sibling_index()
        nxt = None
        if 0 <= i < len(self.siblings) - 1:
            nxt = self.siblings[i + 1]
        elif i > 0:
            nxt = self.siblings[i - 1]

        self.audio.stop()  # release the file handle before trashing it
        for p in (path, tmk.sidecar_path(path)):
            self._trash(p)

        # Forget the file everywhere it's referenced.
        ap = os.path.abspath(path)
        self.siblings = [s for s in self.siblings if os.path.abspath(s) != ap]
        self.tracks = [t for t in self.tracks if os.path.abspath(t.path) != ap]
        self.settings.recent = [p for p in self.settings.recent if os.path.abspath(p) != ap]
        self.settings.recent_bookmarked = [p for p in self.settings.recent_bookmarked
                                           if os.path.abspath(p) != ap]
        self.settings.resume.pop(ap, None)
        self.settings.save()

        self._flash(f"Moved {os.path.basename(path)} to Trash")
        if nxt and os.path.exists(nxt):
            self.open_media(nxt, autoplay=playing, queue=self.siblings)
        else:
            self._clear_player()
        self._refresh_track_list()
        self._refresh_recent_bookmarked()

    def _clear_player(self) -> None:
        """Reset the player UI after the last file is gone."""
        self.audio.stop()
        self.filename_label.set_text("No file open")
        self.title_entry.set_text("")
        self.scale.set_sensitive(False)
        self.scale.set_range(0, 1)
        self.bookmarks = []
        self._duration_ms = -1
        self._refresh_bookmarks()
        self.video_box.hide()

    # ----------------------------------------------------------- bookmarks

    def _add_bookmark(self) -> None:
        """Bookmark what you *just heard*: drop the mark a configurable number of seconds back, then
        rewind the playhead there and keep playing — so you immediately re-hear the bookmarked spot."""
        if self.audio.current_path is None:
            return
        rewind = self.settings.bookmark_rewind_secs * 1000
        pos = max(0, self.audio.position_ms() - rewind)
        note = self.note_entry.get_text().strip()
        # Rewind the playhead first, then stamp the bookmark at that rewound moment and keep playing.
        self.audio.seek(pos)
        self.audio.play()
        self.bookmarks.append(tmk.Bookmark(pos, note))
        self.bookmarks.sort()
        tmk.save(self.audio.current_path, self.bookmarks)
        self.note_entry.set_text("")
        self._refresh_bookmarks()
        self._note_bookmarked(self.audio.current_path)
        self._flash(f"✓ Bookmarked {fmt_time(pos)} (−{self.settings.bookmark_rewind_secs}s)"
                    + (f" — {note}" if note else ""))

    def _note_bookmarked(self, path: str) -> None:
        """Record a file in the 'recently bookmarked' sidebar list (and persist it), and refresh the
        playlist so its Bkm count reflects the change."""
        self.settings.push_bookmarked(path)
        self.settings.save()
        self._refresh_recent_bookmarked()
        self._refresh_track_list()

    def _delete_bookmark(self, bm: tmk.Bookmark) -> None:
        self.bookmarks = [b for b in self.bookmarks if b is not bm]
        tmk.save(self.audio.current_path, self.bookmarks)
        self._refresh_bookmarks()
        self._refresh_track_list()  # update the Bkm count in the playlist

    # ----------------------------------------------------------- waveform / spectrum

    def _start_waveform(self, path: str | None, ms: int) -> None:
        """Begin decoding ``path``'s amplitude envelope (once its duration is known). Any envelope
        job still running for a previously-open file is cancelled first.

        ``on_duration`` re-fires after *every* seek (a fresh ``async-done`` on the bus), so this
        must be a no-op when the envelope for ``path`` is already built or still decoding — otherwise
        scrubbing a long file would blank the waveform for the seconds its decode takes."""
        if path is not None and ms > 0 and path == self._wave_path \
                and (self._wave_peaks or self._wave_job is not None):
            return
        if self._wave_job is not None:
            self._wave_job.cancel()
            self._wave_job = None
        self._wave_peaks = []
        self._wave_path = path
        self.wave_area.queue_draw()
        if path is None or ms <= 0:
            return
        self._wave_job = waveform.compute(path, ms, self._on_waveform_ready)

    def _on_waveform_ready(self, path: str, peaks: list[float]) -> bool:
        """Store the finished envelope and repaint — but only if that file is still the one playing
        (the user may have moved on while it decoded)."""
        cur = self.audio.current_path
        if cur is not None and os.path.abspath(cur) == os.path.abspath(path):
            self._wave_peaks = peaks
            self.wave_area.queue_draw()
        return False

    def _draw_waveform(self, area: Gtk.DrawingArea, cr) -> bool:
        peaks = self._wave_peaks
        alloc = area.get_allocation()
        w, h = alloc.width, alloc.height
        if not peaks or w <= 0 or h <= 0:
            return False

        ctx = self.scale.get_style_context()
        played = self._lookup_rgb(ctx, "accent", (0.15, 0.55, 0.82))
        unplayed = self._lookup_rgb(ctx, "fg_muted", (0.40, 0.48, 0.51))

        adj = self.scale.get_adjustment()
        upper = adj.get_upper()
        frac = self.scale.get_value() / upper if upper > 0 else 0.0

        n = len(peaks)
        mid = h / 2.0
        half = mid - 1.0
        step = 2  # 1px bar + 1px gap → a clean spectrum-bar look
        cr.set_line_width(1.0)
        for x in range(0, w, step):
            amp = peaks[min(n - 1, x * n // w)]
            bar = max(1.0, amp * half)
            if x / w <= frac:
                cr.set_source_rgba(played[0], played[1], played[2], 0.95)
            else:
                cr.set_source_rgba(unplayed[0], unplayed[1], unplayed[2], 0.55)
            cr.move_to(x + 0.5, mid - bar)
            cr.line_to(x + 0.5, mid + bar)
            cr.stroke()
        return False

    @staticmethod
    def _lookup_rgb(ctx: Gtk.StyleContext, name: str, fallback: tuple) -> tuple:
        ok, rgba = ctx.lookup_color(name)
        return (rgba.red, rgba.green, rgba.blue) if ok else fallback

    def _refresh_bookmarks(self) -> None:
        self.scale.clear_marks()
        if self._duration_ms > 0:
            for b in self.bookmarks:
                self.scale.add_mark(b.position_ms, Gtk.PositionType.TOP, None)
        for child in self.bm_list.get_children():
            self.bm_list.remove(child)
        for b in self.bookmarks:
            self.bm_list.add(self._bookmark_row(b))
        self.bm_list.show_all()

    def _bookmark_row(self, b: tmk.Bookmark) -> Gtk.Widget:
        row = Gtk.ListBoxRow()
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        box.set_border_width(4)
        time_lbl = Gtk.Label(label=fmt_time(b.position_ms), xalign=0)
        time_lbl.get_style_context().add_class("time")
        box.pack_start(time_lbl, False, False, 0)

        # The label area: click it to edit inline. Its own EventBox swallows the click so the row's
        # seek handler doesn't also fire.
        holder = Gtk.EventBox()
        holder.set_tooltip_text("Click to edit the label")
        text_lbl = Gtk.Label(xalign=0)
        text_lbl.set_ellipsize(3)
        self._set_bm_label_text(text_lbl, b.text)
        holder.add(text_lbl)
        holder.connect("button-press-event",
                       lambda _w, _e, bm=b, ho=holder, la=text_lbl: self._begin_edit_bm_label(bm, ho, la))
        box.pack_start(holder, True, True, 0)

        del_btn = Gtk.Button()
        del_btn.add(Gtk.Image.new_from_icon_name("user-trash-symbolic", Gtk.IconSize.BUTTON))
        del_btn.set_relief(Gtk.ReliefStyle.NONE)
        del_btn.set_tooltip_text("Delete bookmark")
        del_btn.connect("clicked", lambda _b, bm=b: self._delete_bookmark(bm))
        box.pack_end(del_btn, False, False, 0)

        ev = Gtk.EventBox()
        ev.add(box)
        # A click on the row (time / empty space, but not the label or the trash) seeks there.
        ev.connect("button-press-event", lambda _w, _e, bm=b: (self.audio.seek(bm.position_ms), False)[1])
        row.add(ev)
        return row

    @staticmethod
    def _set_bm_label_text(label: Gtk.Label, text: str) -> None:
        """Show the bookmark's label, or a dimmed 'no label' placeholder when it's empty."""
        ctx = label.get_style_context()
        if text.strip():
            label.set_text(text)
            ctx.remove_class("placeholder")
        else:
            label.set_text("no label")
            ctx.add_class("placeholder")

    def _begin_edit_bm_label(self, b: tmk.Bookmark, holder: Gtk.EventBox, label: Gtk.Label) -> bool:
        """Swap the label for an inline entry (only the label is editable — the time isn't). Enter
        saves, Escape or focus-out cancels."""
        entry = Gtk.Entry()
        entry.set_text(b.text)
        entry.set_placeholder_text("label…")
        holder.remove(label)
        holder.add(entry)
        entry.show()
        entry.grab_focus()
        entry.connect("activate", lambda e, bm=b, ho=holder, la=label: self._commit_bm_label(bm, ho, la, e))
        entry.connect("key-press-event",
                      lambda e, ev, ho=holder, la=label: self._restore_bm_label(ho, la, e)
                      if ev.keyval == Gdk.KEY_Escape else False)
        entry.connect("focus-out-event", lambda e, ev, ho=holder, la=label: self._restore_bm_label(ho, la, e))
        return True  # don't let the click fall through to the row's seek handler

    def _commit_bm_label(self, b: tmk.Bookmark, holder: Gtk.EventBox, label: Gtk.Label, entry: Gtk.Entry) -> None:
        """Save the edited label to the ``.tmk`` sidecar, then restore the (updated) label."""
        b.text = entry.get_text().strip()
        tmk.save(self.audio.current_path, self.bookmarks)
        self._set_bm_label_text(label, b.text)
        self._restore_bm_label(holder, label, entry)

    @staticmethod
    def _restore_bm_label(holder: Gtk.EventBox, label: Gtk.Label, entry: Gtk.Entry) -> bool:
        """Put the label back in place of the inline entry (safe to call more than once)."""
        if entry.get_parent() is holder:
            holder.remove(entry)
        if label.get_parent() is None:
            holder.add(label)
            label.show()
        return False

    # ----------------------------------------------------------- transport helpers

    def _toggle(self) -> None:
        if self.audio.current_path is not None:
            self.audio.toggle()

    def _skip(self, secs: int) -> None:
        if self.audio.current_path is not None:
            self.audio.seek_by(secs * 1000)

    def _set_seeking(self, on: bool) -> bool:
        self._seeking = on
        if not on:
            self.audio.seek(int(self.scale.get_value()))
        return False

    def _on_scale_change(self, _scale, _scroll, value) -> bool:
        self.audio.seek(int(value))
        self.cur_label.set_text(fmt_time(int(value)))
        return False

    # ----------------------------------------------------------- controls

    def _on_speed(self, scale) -> None:
        self.audio.set_rate(scale.get_value())
        self.settings.rate = self.audio.rate
        self.speed_label.set_text(f"{self.audio.rate:.2f}×")

    def _on_volume(self, scale) -> None:
        self.settings.volume = scale.get_value()
        self.audio.set_volume(self.settings.volume)

    def _on_sleep(self, combo) -> None:
        if self._suppress_sleep:
            return
        if self._sleep_source:
            GLib.source_remove(self._sleep_source)
            self._sleep_source = 0
        minutes = int(combo.get_active_id() or "0")
        if minutes > 0:
            self._sleep_source = GLib.timeout_add_seconds(minutes * 60, self._sleep_fire)

    def _sleep_fire(self) -> bool:
        self.audio.pause()
        self.sleep_combo.set_active_id("0")
        self._sleep_source = 0
        self._flash("💤 Sleep timer — paused")
        return False

    # ----------------------------------------------------------- engine callbacks

    def _on_duration(self, ms: int) -> None:
        self._duration_ms = ms
        self.scale.set_range(0, ms)
        self.dur_label.set_text(fmt_time(ms))
        self._refresh_bookmarks()
        self._start_waveform(self.audio.current_path, ms)
        self._update_video_visibility()

    def _update_video_visibility(self) -> None:
        if self.audio.video_widget is not None and self.audio.has_video():
            self.video_box.show()
            self.audio.video_widget.show()
        else:
            self.video_box.hide()

    # ----------------------------------------------------------- lyrics (.ai.txt transcriptions)

    def _build_lyrics_panel(self) -> Gtk.Widget:
        """The scrollable side panel showing a sidecar transcription (no timestamps — rendered
        whole, like lyrics). Hidden until a language chip is picked."""
        self.lyrics_panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        self.lyrics_panel.get_style_context().add_class("pane")
        self.lyrics_panel.set_no_show_all(True)
        self.lyrics_panel.pack_start(self._pane_header("TRANSCRIPT"), False, False, 0)
        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scroller.set_size_request(300, -1)
        self.lyrics_view = Gtk.TextView()
        self.lyrics_view.set_editable(False)
        self.lyrics_view.set_cursor_visible(False)
        self.lyrics_view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        self.lyrics_view.set_left_margin(12)
        self.lyrics_view.set_right_margin(12)
        self.lyrics_view.set_top_margin(10)
        self.lyrics_view.set_bottom_margin(10)
        scroller.add(self.lyrics_view)
        self.lyrics_panel.pack_start(scroller, True, True, 0)
        self._lyrics_path: str | None = None
        return self.lyrics_panel

    def _refresh_lyrics_langs(self, media_path: str) -> None:
        """Rebuild the transcription chips for a freshly-opened file (an 'Off' chip plus one per
        sidecar ``.ai.txt`` found, and a delete button). Hidden when the file has none."""
        for c in self.lyrics_row.get_children():
            self.lyrics_row.remove(c)
        self._select_lyrics(None, None)

        found = lyrics.find(media_path)
        if not found:
            self.lyrics_row.hide()
            return
        caption = Gtk.Label(label="Transcript:")
        caption.get_style_context().add_class("time")
        self.lyrics_row.pack_start(caption, False, False, 0)
        off = Gtk.RadioButton.new_with_label_from_widget(None, "Off")
        off.set_mode(False)  # render as a linked toggle button, not a radio dot
        off.connect("toggled", lambda b: b.get_active() and self._select_lyrics(None, None))
        self.lyrics_row.pack_start(off, False, False, 0)
        for lang, path in found:
            rb = Gtk.RadioButton.new_with_label_from_widget(off, lang)
            rb.set_mode(False)
            rb.connect("toggled",
                       lambda b, la=lang, pa=path: b.get_active() and self._select_lyrics(la, pa))
            self.lyrics_row.pack_start(rb, False, False, 0)
        self.lyrics_delete_btn = Gtk.Button()
        self.lyrics_delete_btn.add(Gtk.Image.new_from_icon_name("user-trash-symbolic", Gtk.IconSize.BUTTON))
        self.lyrics_delete_btn.set_relief(Gtk.ReliefStyle.NONE)
        self.lyrics_delete_btn.set_tooltip_text("Delete the shown transcription file")
        self.lyrics_delete_btn.set_sensitive(False)  # nothing shown yet
        self.lyrics_delete_btn.connect("clicked", lambda _b: self._delete_lyrics())
        self.lyrics_row.pack_start(self.lyrics_delete_btn, False, False, 0)
        off.set_active(True)
        self.lyrics_row.show_all()

    def _select_lyrics(self, lang: str | None, path: str | None) -> None:
        """Show a transcription in the side panel (or hide it for lang=None). Arabic (and any
        RTL text) reads right-aligned; GTK's per-paragraph bidi handles the glyph order."""
        self._lyrics_path = path
        btn = getattr(self, "lyrics_delete_btn", None)
        if btn is not None:
            btn.set_sensitive(path is not None)
        if path is None:
            self.lyrics_panel.hide()
            return
        self.lyrics_view.set_justification(
            Gtk.Justification.RIGHT if (lang or "").lower().startswith("ar") else Gtk.Justification.LEFT)
        self.lyrics_view.get_buffer().set_text(lyrics.read(path) or "(empty transcription)")
        # no_show_all guards the panel against the window's initial show_all(); lift it here,
        # since show_all() is a no-op while the flag is set (children would stay hidden).
        self.lyrics_panel.set_no_show_all(False)
        self.lyrics_panel.show_all()

    def _delete_lyrics(self) -> None:
        """Delete the transcription file shown in the panel (with confirmation), then refresh."""
        path = self._lyrics_path
        if not path:
            return
        dialog = Gtk.MessageDialog(
            transient_for=self, modal=True, message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.YES_NO, text=f"Delete {os.path.basename(path)}?")
        dialog.format_secondary_text("The transcription file is removed from disk permanently.")
        answer = dialog.run()
        dialog.destroy()
        if answer != Gtk.ResponseType.YES:
            return
        lyrics.delete(path)
        if self.audio.current_path:
            self._refresh_lyrics_langs(self.audio.current_path)

    # ----------------------------------------------------------- subtitles

    def _refresh_subtitle_langs(self, media_path: str) -> None:
        """Rebuild the subtitle language chips for a freshly-opened file (an 'Off' chip plus one per
        sidecar ``.srt`` found). Hidden when the file has no subtitles."""
        for c in self.subs_row.get_children():
            self.subs_row.remove(c)
        self._cues = []
        self._update_subtitle(0)  # clears any leftover subtitle text

        subs = subtitles.find(media_path)
        if not subs:
            self.subs_row.hide()
            return
        caption = Gtk.Label(label="Subtitles:")
        caption.get_style_context().add_class("time")
        self.subs_row.pack_start(caption, False, False, 0)
        off = Gtk.RadioButton.new_with_label_from_widget(None, "Off")
        off.set_mode(False)  # render as a linked toggle button, not a radio dot
        off.connect("toggled", lambda b: b.get_active() and self._select_subtitle(None, None))
        self.subs_row.pack_start(off, False, False, 0)
        for lang, path in subs:
            rb = Gtk.RadioButton.new_with_label_from_widget(off, lang)
            rb.set_mode(False)
            rb.connect("toggled",
                       lambda b, la=lang, pa=path: b.get_active() and self._select_subtitle(la, pa))
            self.subs_row.pack_start(rb, False, False, 0)
        off.set_active(True)
        self.subs_row.show_all()

    def _select_subtitle(self, lang: str | None, path: str | None) -> None:
        """Activate a subtitle track (parse its cues) or turn subtitles off (lang=None)."""
        self._cues = subtitles.parse(path) if path else []
        self._update_subtitle(self.audio.position_ms())

    def _update_subtitle(self, position_ms: int) -> None:
        """Show the cue active at ``position_ms`` — over the video for video files, else over the
        seek bar for audio-only. Called on the playback timer."""
        text = subtitles.cue_at(self._cues, position_ms) if self._cues else None
        over_video = text and self.audio.video_widget is not None and self.audio.has_video()
        if over_video:
            self.sub_video_label.set_text(text)
            self.sub_video_label.show()
            self.sub_bar_label.hide()
        elif text:
            self.sub_bar_label.set_text(text)
            self.sub_bar_label.show()
            self.sub_video_label.hide()
        else:
            self.sub_video_label.hide()
            self.sub_bar_label.hide()

    def _on_state(self, playing: bool) -> None:
        icon = "media-playback-pause-symbolic" if playing else "media-playback-start-symbolic"
        img = self.play_btn.get_child()
        if isinstance(img, Gtk.Image):
            img.set_from_icon_name(icon, Gtk.IconSize.LARGE_TOOLBAR)

    def _on_eos(self) -> None:
        self._remember_position(persist=True)
        i = self._sibling_index()
        if 0 <= i < len(self.siblings) - 1:
            self._next_track(autoplay=True)
        else:
            self.audio.pause()
            self._flash("End of queue")

    def _on_error(self, message: str) -> None:
        self._flash(f"⚠ {message}")

    # ----------------------------------------------------------- timer / lifecycle

    def _on_timer(self) -> bool:
        if self.audio.current_path is not None and not self._seeking:
            pos = self.audio.position_ms()
            self.cur_label.set_text(fmt_time(pos))
            if self._duration_ms > 0:
                self.scale.set_value(pos)
                self.rem_label.set_text("-" + fmt_time(self._duration_ms - pos))
                if self._wave_peaks:
                    self.wave_area.queue_draw()   # advance the played/unplayed colour split
            if self._cues:
                self._update_subtitle(pos)
            self._remember_position(persist=False)
            self._tick += 1
            if self._tick % 20 == 0:
                self.settings.save()
        return True

    def _remember_position(self, persist: bool) -> None:
        path = self.audio.current_path
        if path is None:
            return
        self.settings.remember_position(path, self.audio.position_ms())
        if persist:
            self.settings.save()

    def _refresh_status(self) -> None:
        i = self._sibling_index()
        folder = os.path.dirname(self.audio.current_path) if self.audio.current_path else ""
        pos = f"{i + 1}/{len(self.siblings)}" if i >= 0 else ""
        self.status.set_text(f"{folder}   ·   {pos}" if folder else "")

    def _flash(self, text: str) -> None:
        self.flash_label.set_text(text)
        if self._flash_source:
            GLib.source_remove(self._flash_source)
        self._flash_source = GLib.timeout_add_seconds(4, self._clear_flash)

    def _clear_flash(self) -> bool:
        self.flash_label.set_text("")
        self._flash_source = 0
        return False

    # ----------------------------------------------------------- theme / choosers

    def _apply_theme(self) -> None:
        if getattr(self, "_provider", None) is None:
            self._provider = Gtk.CssProvider()
            Gtk.StyleContext.add_provider_for_screen(
                Gdk.Screen.get_default(), self._provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)
        self._provider.load_from_data((THEMES.get(self.settings.theme, THEMES["dark"]) + STYLE).encode())

    def _toggle_theme(self) -> None:
        self.settings.theme = "light" if self.settings.theme == "dark" else "dark"
        self._apply_theme()
        self.settings.save()

    def _update_skip_tooltips(self) -> None:
        """Reflect the configured skip / rewind durations in the button tooltips + labels."""
        s = self.settings.skip_primary_secs
        self.seek_back_btn.set_tooltip_text(f"Back {s}s (←)")
        self.seek_fwd_btn.set_tooltip_text(f"Forward {s}s (→)")
        self.add_bm_btn.set_tooltip_text(
            f"Bookmark here, {self.settings.bookmark_rewind_secs}s back (Ctrl+B · B)")

    def _open_settings(self) -> None:
        """A small settings dialog for the durable playback durations."""
        dialog = Gtk.Dialog(title="Settings", transient_for=self, modal=True)
        dialog.add_buttons(Gtk.STOCK_CANCEL, Gtk.ResponseType.CANCEL, "Save", Gtk.ResponseType.OK)
        dialog.set_default_response(Gtk.ResponseType.OK)
        grid = Gtk.Grid(row_spacing=10, column_spacing=12, border_width=16)

        def spin(value, lo, hi):
            sb = Gtk.SpinButton(numeric=True)
            sb.set_adjustment(Gtk.Adjustment(value=value, lower=lo, upper=hi,
                                             step_increment=1, page_increment=5))
            sb.set_value(value)
            return sb

        skip_spin = spin(self.settings.skip_primary_secs, 1, 600)
        rewind_spin = spin(self.settings.bookmark_rewind_secs, 0, 120)
        sleep_check = Gtk.CheckButton(label="Show the sleep timer")
        sleep_check.set_active(self.settings.sleep_enabled)
        sleep_presets = Gtk.Entry()
        sleep_presets.set_text(", ".join(str(m) for m in self.settings.sleep_presets_min))
        rows = [("Skip forward / back (seconds)", skip_spin,
                 "The ± seek buttons and Ctrl+← / Ctrl+→ jump by this many seconds."),
                ("Bookmark rewind (seconds)", rewind_spin,
                 "A new bookmark lands this many seconds back, and playback rewinds there so you "
                 "re-hear the moment you just bookmarked."),
                ("Sleep timer", sleep_check,
                 "Off by default. Enable to show a sleep-timer picker among the player controls."),
                ("Sleep presets (minutes)", sleep_presets,
                 "Comma-separated minute options offered by the sleep timer, e.g. 15, 30, 45, 60.")]
        for i, (label, widget, hint) in enumerate(rows):
            lbl = Gtk.Label(label=label, xalign=0)
            grid.attach(lbl, 0, i, 1, 1)
            grid.attach(widget, 1, i, 1, 1)
            widget.set_tooltip_text(hint)
        dialog.get_content_area().add(grid)
        dialog.show_all()
        if dialog.run() == Gtk.ResponseType.OK:
            self.settings.skip_primary_secs = int(skip_spin.get_value())
            self.settings.bookmark_rewind_secs = int(rewind_spin.get_value())
            self.settings.sleep_enabled = sleep_check.get_active()
            presets = [int(x) for x in re.findall(r"\d+", sleep_presets.get_text()) if int(x) > 0]
            if presets:
                self.settings.sleep_presets_min = presets
            self.settings.save()
            self._update_skip_tooltips()
            self._rebuild_sleep_presets()
            self._apply_sleep_visibility()
        dialog.destroy()

    def _choose_file(self) -> None:
        dialog = Gtk.FileChooserDialog(
            title="Open a media file", transient_for=self, action=Gtk.FileChooserAction.OPEN)
        dialog.add_buttons(Gtk.STOCK_CANCEL, Gtk.ResponseType.CANCEL, Gtk.STOCK_OPEN, Gtk.ResponseType.OK)
        flt = Gtk.FileFilter()
        flt.set_name("Media")
        for ext in sorted(library.MEDIA_EXTS):
            flt.add_pattern("*" + ext)
        dialog.add_filter(flt)
        if self.audio.current_path:
            dialog.set_current_folder(os.path.dirname(self.audio.current_path))
        if dialog.run() == Gtk.ResponseType.OK:
            self.open_media(dialog.get_filename(), autoplay=True)
        dialog.destroy()

    def _focus_folder_tree(self) -> None:
        """Header folder button / Ctrl+L: put keyboard focus on the folder tree."""
        self.folder_tree.grab_focus()

    # ----------------------------------------------------------- keyboard

    def _on_key(self, _widget, event) -> bool:
        focus = self.get_focus()
        typing = isinstance(focus, Gtk.Entry)
        ctrl = event.state & Gdk.ModifierType.CONTROL_MASK
        kv = event.keyval

        # Media-control shortcuts work globally (even while a list/tree is focused or you're typing a
        # note), so Ctrl is the modifier for them.
        if ctrl and kv in (Gdk.KEY_b, Gdk.KEY_B):
            self._add_bookmark(); return True
        if ctrl and kv in (Gdk.KEY_p, Gdk.KEY_P):
            self._toggle(); return True
        if ctrl and kv == Gdk.KEY_Left:
            self._skip(-self.settings.skip_primary_secs); return True
        if ctrl and kv == Gdk.KEY_Right:
            self._skip(self.settings.skip_primary_secs); return True
        if ctrl and kv in (Gdk.KEY_Delete, Gdk.KEY_KP_Delete):
            self._delete_current_file(); return True
        if ctrl and kv in (Gdk.KEY_o, Gdk.KEY_O):
            self._choose_file(); return True
        if ctrl and kv in (Gdk.KEY_l, Gdk.KEY_L):
            self._focus_folder_tree(); return True
        if ctrl and kv in (Gdk.KEY_t, Gdk.KEY_T):
            self._toggle_theme(); return True
        if ctrl and kv == Gdk.KEY_comma:
            self._open_settings(); return True
        if ctrl and kv in (Gdk.KEY_q, Gdk.KEY_Q):
            self.close(); return True
        if kv == Gdk.KEY_Escape and typing:
            self.track_view.grab_focus(); return True
        if typing:
            return False

        # Plain keys (only when not typing in a text field).
        if kv == Gdk.KEY_space:
            self._toggle(); return True
        if kv == Gdk.KEY_Left:
            self._skip(-self.settings.skip_primary_secs); return True
        if kv == Gdk.KEY_Right:
            self._skip(self.settings.skip_primary_secs); return True
        if kv in (Gdk.KEY_b, Gdk.KEY_B):
            self._add_bookmark(); return True
        if kv in (Gdk.KEY_n, Gdk.KEY_N):
            self.note_entry.grab_focus(); return True
        return False

    def _on_close(self, *_a) -> bool:
        self._remember_position(persist=True)
        # Persist the layout so the next launch restores it.
        self.settings.pane_sidebar = self.outer_paned.get_position()
        self.settings.pane_left = self.left_paned.get_position()
        self.settings.pane_split = self.split_paned.get_position()
        self.settings.win_w, self.settings.win_h = self.get_size()
        self.settings.save()
        self.audio.dispose()
        return False


class PlayerApp(Gtk.Application):
    def __init__(self, open_path: str | None):
        # NON_UNIQUE: each launch is an independent process (no single-instance D-Bus registration),
        # so relaunching never races a still-exiting instance.
        super().__init__(application_id="org.knotes.audioplayer",
                         flags=Gio.ApplicationFlags.NON_UNIQUE)
        self.settings = Settings.load()
        self._open_path = open_path
        self._win: PlayerWindow | None = None

    def do_activate(self) -> None:
        if self._win is None:
            self._win = PlayerWindow(self, self._open_path)
        self._win.show_all()
        self._win.present()


def main() -> int:
    open_path = next((a for a in sys.argv[1:] if os.path.exists(a)), None)
    return PlayerApp(open_path).run(None)


if __name__ == "__main__":
    sys.exit(main())
