#!/usr/bin/env python3

"""Nibras Image Annotator — keyboard-first review of a folder of images.

Pick a folder in the tree, type a label, mark up the image, move on. Labels and
annotations save the moment you navigate away; there is no save key. Two
dialogs in the whole app: the one that deletes a file, and the folder chooser
behind the Move button.

Run:
    ./scr_annotator.py [FOLDER]

With no FOLDER the tree opens on $HOME and nothing is loaded until you pick one.

Design notes
------------
The label is appended to whatever the file is already called, unchanged:

    2025-04-22_09-55-54 sch.png   +  "fixing vcn script"
    -> 2025-04-22_09-55-54 sch fixing vcn script.png

Nothing is parsed out of the original name, because there is no format to rely
on. That raises one problem: on a second visit the app cannot tell which part of
"a b c.png" was the label and which was always there, so a naive implementation
appends again and the name grows every pass. Every rename is therefore recorded
in LOG_NAME, and on startup those records are replayed to recover each file's
original stem. Editing a label then replaces it instead of stacking, and
clearing it restores the original name exactly.

Annotations are destructive and saved without confirmation, by request. They
stay pending — visible, adjustable, undoable — until you navigate away, which is
the moment they are burnt in. KEEP_ORIGINALS keeps one pristine copy of a file
in ORIGINALS_DIR before the first time it is modified, so any markup is undoable
later with a plain `cp`. Set it to False to disable.
"""

import csv
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime

import cairo
import gi

# Every namespace needs pinning, not just Gtk. Gdk sorts before Gtk in the
# import below, so without its own require_version it loads as 4.0 first and
# Gtk 3.0 then refuses to start.
gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
gi.require_version("GdkPixbuf", "2.0")
from gi.repository import Gdk, GdkPixbuf, Gio, GLib, Gtk, Pango   # noqa: E402

from PIL import Image, ImageDraw                             # noqa: E402

# ── configuration ─────────────────────────────────────────────────────────
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif"}

# Widths and offsets are in *image* pixels, so a mark is the same weight on the
# file whatever the window is doing.
PEN_RGB    = (0.13, 0.83, 0.30)   # green
PEN_WIDTH  = 3

HL_RGB     = (1.00, 0.92, 0.20)   # yellow, like a text highlighter
HL_ALPHA   = 0.35
HL_HEIGHT  = 26                   # band thickness; the drag only sets x extent

RECT_RGB   = (0.13, 0.83, 0.30)
RECT_WIDTH = 3

CROP_DIM   = 0.62                 # how far the discarded area is dimmed
JPEG_QUALITY = 95

# Solarized light, for the chrome only, matching the audio-player: base3 is
# the paper, base2 the dark beige on the bars. The canvas behind the image
# stays a neutral near-black whatever the chrome does: a tinted surround
# shifts how you read the colours of the screenshot sitting on it, which is
# the one thing this app must not do.
SOL_BASE03 = "#002b36"      # near-black — strong text on the light chrome
SOL_BASE01 = "#586e75"      # body text
SOL_BASE00 = "#657b83"      # secondary text
SOL_BASE1  = "#93a1a1"      # hairlines and the subtitle
SOL_BASE2  = "#eee8d5"      # dark beige — header and status bars
SOL_BASE3  = "#fdf6e3"      # paper — window, tree, entry
SOL_CYAN   = "#2aa198"
SOL_RED    = "#dc322f"
CANVAS_RGB = (0.055, 0.06, 0.07)

# One pristine copy per file, taken before its first modification.
KEEP_ORIGINALS = True
ORIGINALS_DIR  = ".originals"

# Append-only record of every rename, used to undo them in bulk and — more
# importantly — to recover which part of a filename is the label. See above.
LOG_NAME = ".scr-annotator-log.tsv"

MAX_LABEL = 150          # filename components are capped at 255 bytes on ext4
CACHE_MAX = 7            # decoded neighbours kept around, so paging is instant

TREE_WIDTH  = 0          # folder pane, in pixels; 0 = as narrow as the
                         # tree can be drawn without clipping the names
TREE_MIN    = 110        # ... but not so narrow there is nothing to grab
TREE_MAX    = 300        # ... and not so wide one deep path takes over
SHOW_HIDDEN = False      # dotted directories in the folder tree

# Where a bare launch starts. The capture hotkey drops screenshots here, so
# this is nearly always the folder wanted.
DEFAULT_FOLDER = os.path.expanduser("~/new")

# Newest first: a screenshot is most likely to be reviewed seconds after it is
# taken, and the capture script opens this app straight after saving.
SORT_NEWEST_FIRST = True

APP_NAME = "Nibras Image Annotator"     # what the user sees: titles, menu

# The identity the desktop treats as this app, kept as it was through the
# rename: it has to match the .desktop file's StartupWMClass and the DBus
# application id, and changing either would orphan the launcher icon and
# the single-instance handoff the capture scripts depend on.
APP_ID = "scr-annotator"

# Whether the tool letters need Alt held.
#
# True by design, not caution. The label field is always focused — that is what
# makes typing a label need no click and no mode — so a bare "p" goes into the
# label. Binding bare letters would mean p, h, r and c could never be typed
# into one, and "crop", "http" and "chart" all contain them. Alt+letter is
# still one letter, and it cannot be swallowed.
#
# Set to False for bare letters if you would rather have the keystroke and
# accept that those four letters stop reaching the label. The button captions
# follow this setting on their own.
TOOL_KEYS_NEED_ALT = True

# Quarter turns clockwise -> how each library says it. Pixbuf rotates the view,
# Pillow the file, and the two count in opposite directions.
PIXBUF_ROTATION = {
    1: GdkPixbuf.PixbufRotation.CLOCKWISE,
    2: GdkPixbuf.PixbufRotation.UPSIDEDOWN,
    3: GdkPixbuf.PixbufRotation.COUNTERCLOCKWISE,
}
PIL_ROTATION = {
    1: Image.Transpose.ROTATE_270,
    2: Image.Transpose.ROTATE_180,
    3: Image.Transpose.ROTATE_90,
}

# key, toolbar caption, letter
TOOLS = [
    ("pen",  "Pen",       "p"),
    ("hl",   "Highlight", "h"),
    ("rect", "Rect",      "r"),
    ("crop", "Crop",      "c"),
]

HELP = """  keys

  Enter · ↓ · PgDn      save and go to the next image
  ↑ · PgUp              save and go back
  scroll                previous / next
  ← →                   move the text cursor (they edit, they do not navigate)
  Backspace             delete a character — Del belongs to the file

  Alt+P Alt+H Alt+R Alt+C   pen · highlight · rectangle · crop
                        (Alt, because a bare letter goes into the label)
  drag                  apply the selected tool
  right-click · Ctrl+Z  undo the last mark
  [ ]                   rotate a quarter turn left / right
  F7                    apply pending marks now, without leaving the image

  Alt+M                 move this image to the chosen folder, then next
                        (the Move button in the header picks the folder)

  F3                    show or hide the folder tree
  Ctrl+L                type or paste a path into the field above the tree;
                        Tab completes, Enter opens it, Esc goes back
  Tab                   move focus to the tree, then arrows and Enter
                        (PgUp/PgDn still page through images from there)

  Ctrl+Shift+C          copy the full path of this image to the clipboard
  Del · F8              move the file to the trash (asks; Enter confirms)
  F5                    reload from disk, discarding unsaved marks
  F11                   fullscreen        F1  this help
  Esc · Ctrl+Q          save and quit

  Marks stay adjustable until you leave the image. There is no save key."""


# ── names ─────────────────────────────────────────────────────────────────

def sanitize(label: str) -> str:
    """A label is going into a filename, so it cannot contain a separator."""
    label = re.sub(r"[/\x00-\x1f]", " ", label)
    return re.sub(r"\s+", " ", label).strip()[:MAX_LABEL]


def read_origins(folder: str) -> dict:
    """basename -> the name it had before this app first renamed it.

    Replaying the log rather than storing state per file means the mapping
    survives files being moved in and out of the folder, and stays readable.
    """
    origins: dict[str, str] = {}
    path = os.path.join(folder, LOG_NAME)
    try:
        with open(path, encoding="utf-8", newline="") as f:
            for row in csv.reader(f, delimiter="\t"):
                if len(row) < 3:
                    continue
                _, old, new = row[0], row[1], row[2]
                origins[new] = origins.pop(old, old)   # follow the chain
    except OSError:
        pass
    return origins


def log_rename(folder: str, old: str, new: str) -> None:
    with open(os.path.join(folder, LOG_NAME), "a", encoding="utf-8",
              newline="") as f:
        csv.writer(f, delimiter="\t").writerow(
            [datetime.now().isoformat(timespec="seconds"), old, new])


def unique_path(path: str) -> str:
    """Never overwrite a file that is already there."""
    if not os.path.exists(path):
        return path
    stem, ext = os.path.splitext(path)
    n = 2
    while os.path.exists(f"{stem} ({n}){ext}"):
        n += 1
    return f"{stem} ({n}){ext}"


def split_label(basename: str, origins: dict) -> tuple[str, str, str]:
    """(original stem, current label, extension).

    The label is whatever the name has gained since this app first saw the
    file. Without the origins map there is no way to tell — "a b c.png" could
    be a stem of "a" with label "b c", or a stem of "a b c" with no label —
    and guessing wrong makes the name grow on every visit.
    """
    origin = origins.get(basename, basename)
    stem, ext = os.path.splitext(origin)
    cur_stem = os.path.splitext(basename)[0]
    label = cur_stem[len(stem):].strip() if cur_stem.startswith(stem) else ""
    return stem, label, ext


def relabel(folder: str, basename: str, label: str, origins: dict) -> str:
    """Rename so the file's suffix is exactly `label`. Returns the new name.

    Replaces any previous label rather than appending to it, and an empty
    label restores the original name.
    """
    stem, current, ext = split_label(basename, origins)
    if label == current:
        return basename
    target = os.path.join(folder, f"{stem} {label}{ext}" if label
                          else f"{stem}{ext}")
    if os.path.abspath(target) == os.path.abspath(
            os.path.join(folder, basename)):
        return basename

    new = unique_path(target)
    os.rename(os.path.join(folder, basename), new)
    new_base = os.path.basename(new)
    origins[new_base] = origins.pop(basename, basename)
    log_rename(folder, basename, new_base)
    return new_base


def move_file(folder: str, basename: str, dest: str, origins: dict) -> str:
    """Move one image into another folder. Returns its basename there.

    The file keeps its name, so the label travels with it; what would not
    travel on its own is the *origin* — the log that says which part of the
    name is the label lives in the source folder. So the move is written into
    the destination's log as a rename from the original name, which is what
    read_origins() there needs to recover the same stem. The pristine copy in
    ORIGINALS_DIR follows too, or a later undo would look in the wrong place.
    """
    origin = origins.get(basename, basename)
    new = unique_path(os.path.join(dest, basename))
    shutil.move(os.path.join(folder, basename), new)
    new_base = os.path.basename(new)
    origins.pop(basename, None)
    if new_base != origin:
        log_rename(dest, origin, new_base)

    src_copy = os.path.join(folder, ORIGINALS_DIR, origin)
    if os.path.isfile(src_copy):
        dst_dir = os.path.join(dest, ORIGINALS_DIR)
        dst_copy = os.path.join(dst_dir, origin)
        if not os.path.exists(dst_copy):
            os.makedirs(dst_dir, exist_ok=True)
            shutil.move(src_copy, dst_copy)
    return new_base


# ── folders ───────────────────────────────────────────────────────────────

def list_images(folder: str) -> list[str]:
    """Image basenames, newest first by default.

    Renaming preserves mtime, so labelling a file does not reshuffle the
    folder. Drawing rewrites it and does — but the list is read once when the
    folder is opened, so the order never moves under you mid-review.
    """
    try:
        with os.scandir(folder) as it:
            entries = [e for e in it
                       if e.is_file()
                       and os.path.splitext(e.name)[1].lower() in IMAGE_EXTS]
    except OSError:
        return []
    if not SORT_NEWEST_FIRST:
        return sorted(e.name for e in entries)

    def mtime(e):
        try:
            return e.stat().st_mtime
        except OSError:
            return 0.0
    entries.sort(key=mtime, reverse=True)
    return [e.name for e in entries]


def scan_dir(path: str) -> tuple[int, bool]:
    """(image count, has subdirectories), in one pass.

    One scandir per directory *shown*, which is what lets the tree display a
    count without walking anything: the count is for that directory alone,
    never its descendants.
    """
    images, subdirs = 0, False
    try:
        with os.scandir(path) as it:
            for e in it:
                if not SHOW_HIDDEN and e.name.startswith("."):
                    continue
                try:
                    if e.is_dir(follow_symlinks=False):
                        subdirs = True
                    elif os.path.splitext(e.name)[1].lower() in IMAGE_EXTS:
                        images += 1
                except OSError:
                    continue
    except OSError:
        pass                      # unreadable directory: listed, just empty
    return images, subdirs


def child_dirs(path: str) -> list[str]:
    try:
        with os.scandir(path) as it:
            names = [e.name for e in it
                     if e.is_dir(follow_symlinks=False)
                     and (SHOW_HIDDEN or not e.name.startswith("."))]
    except OSError:
        return []
    return sorted(names, key=str.lower)


# ── the app ───────────────────────────────────────────────────────────────

NAME, PATH, COUNT, FILLED = range(4)          # folder-tree model columns


class Annotator(Gtk.Window):

    def __init__(self, folder: str | None):
        super().__init__(title=APP_NAME)
        self.folder  = folder or os.path.expanduser("~")
        self.files: list[str] = list_images(folder) if folder else []
        self.idx     = 0
        self.origins = read_origins(self.folder) if folder else {}

        self.pixbuf: GdkPixbuf.Pixbuf | None = None
        self.cache: dict[str, GdkPixbuf.Pixbuf] = {}
        self.edits: list[dict] = []     # pending marks, burnt in on navigate
        self.dragging = False
        self.tool = "pen"
        self.show_help = False
        self.loaded_label = ""
        self._syncing = False           # guards toolbar <-> state feedback
        self._selecting = False         # guards tree selection during reveal
        self.move_dest: str | None = None   # where Alt+M sends the image

        self.set_default_size(1500, 950)
        self.connect("delete-event", self.on_delete)
        self.connect("destroy", self.on_destroy)
        self.connect("key-press-event", self.on_key)

        self._build_header()
        self._build_tree()
        self._build_canvas()
        self._build_statusbar()
        self._build_layout()
        self._apply_css()

        self.monitor = None
        self.refresh_title()
        self.watch_folder()
        self.set_tool("pen")
        self.load(0)
        if folder:
            self.reveal(folder)
        self.entry.grab_focus()

    # ── construction ──────────────────────────────────────────────────────

    def _build_header(self) -> None:
        self.head = Gtk.HeaderBar()
        self.head.set_show_close_button(True)
        self.head.set_title(APP_NAME)
        self.head.set_subtitle(self.folder)

        tools = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        tools.get_style_context().add_class("linked")
        self.tool_buttons: dict[str, Gtk.ToggleButton] = {}
        for key, label, letter in TOOLS:
            accel = f"{'Alt+' if TOOL_KEYS_NEED_ALT else ''}{letter.upper()}"
            b = Gtk.ToggleButton(label=f"{label} ({accel})")
            b.set_tooltip_text(f"{label}  —  {accel}")
            b.connect("toggled", self.on_tool_toggled, key)
            b.set_can_focus(False)      # clicking a tool must not steal typing
            tools.pack_start(b, False, False, 0)
            self.tool_buttons[key] = b
        self.head.pack_start(tools)

        self.copy_btn = Gtk.Button(label="Copy path")
        self.copy_btn.set_tooltip_text(
            "Copy the full path of this image  \u2014  Ctrl+Shift+C")
        self.copy_btn.set_can_focus(False)   # as with the tools, keep typing
        self.copy_btn.connect("clicked", self.copy_path)
        self.head.pack_end(self.copy_btn)

        # The destination of the move key, shown so that the state is on
        # screen rather than remembered, like the tool. Clicking picks it.
        self.move_btn = Gtk.Button()
        self.move_btn.set_can_focus(False)
        self.move_btn.connect("clicked", self.choose_move_dest)
        self.head.pack_end(self.move_btn)
        self.refresh_move_btn()

        self.set_titlebar(self.head)

    def _build_tree(self) -> None:
        home = os.path.expanduser("~")
        self.tree_root = home if self.folder.startswith(home) else "/"

        self.store = Gtk.TreeStore(str, str, str, bool)
        self.tree = Gtk.TreeView(model=self.store)
        self.tree.set_headers_visible(False)
        self.tree.set_enable_search(False)   # or typing would drive the tree

        # Name and count are two columns rather than two cells in one, which
        # they were until the rail was made to open narrow: cells sharing a
        # column split the spare width evenly whatever their expand flag says,
        # so the count took half the rail and the names ellipsized in a pane
        # wide enough for them. Separate columns, and the name column expands
        # while the count keeps only what its digits need.
        self.name_cell = Gtk.CellRendererText(
            ellipsize=Pango.EllipsizeMode.MIDDLE)
        self.name_col = Gtk.TreeViewColumn("folder", self.name_cell, text=NAME)
        self.name_col.set_sizing(Gtk.TreeViewColumnSizing.AUTOSIZE)
        self.name_col.set_expand(True)
        self.tree.append_column(self.name_col)

        self.count_col = Gtk.TreeViewColumn(
            "n", Gtk.CellRendererText(xalign=1.0), text=COUNT)
        self.count_col.set_sizing(Gtk.TreeViewColumnSizing.AUTOSIZE)
        self.tree.append_column(self.count_col)

        root_iter = self.store.append(
            None, [self.tree_root, self.tree_root, "", False])
        self.store.append(root_iter, ["", "", "", False])     # expander stub

        self.tree.connect("row-expanded", self.on_row_expanded)
        self.tree.get_selection().connect("changed", self.on_tree_selection)

        self.tree_scroll = Gtk.ScrolledWindow()
        self.tree_scroll.set_policy(Gtk.PolicyType.AUTOMATIC,
                                    Gtk.PolicyType.AUTOMATIC)
        self.tree_scroll.add(self.tree)
        # No floor of its own: the rail opens at the tree's own minimum and
        # the handle may be dragged narrower still.
        self.tree_scroll.set_size_request(1, -1)

        # A path field above the tree, for when the folder is known and the
        # tree would be a dozen expand-and-scroll clicks to reach it. Enter
        # opens a folder or a single image, the same as the command line.
        self.path_entry = Gtk.Entry()
        self.path_entry.set_name("pathentry")
        self.path_entry.set_placeholder_text("path — Ctrl+L, Tab completes")
        self.path_entry.set_tooltip_text(
            "Type or paste a folder or image path, Enter to open it  (Ctrl+L)")
        self.path_store = Gtk.ListStore(str)
        completion = Gtk.EntryCompletion(model=self.path_store)
        completion.set_text_column(0)
        completion.set_popup_single_match(False)
        completion.connect("match-selected", self.on_path_completed)
        self.path_entry.set_completion(completion)
        self.path_entry.connect("changed", self.on_path_typed)
        self._path_listed = None        # the directory path_store lists

        self.left = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.left.pack_start(self.path_entry, False, False, 0)
        self.left.pack_start(self.tree_scroll, True, True, 0)

    def _build_canvas(self) -> None:
        self.area = Gtk.DrawingArea()
        self.area.add_events(
            Gdk.EventMask.BUTTON_PRESS_MASK | Gdk.EventMask.BUTTON_RELEASE_MASK
            | Gdk.EventMask.POINTER_MOTION_MASK | Gdk.EventMask.SCROLL_MASK)
        self.area.connect("draw", self.on_render)
        self.area.connect("button-press-event", self.on_press)
        self.area.connect("motion-notify-event", self.on_motion)
        self.area.connect("button-release-event", self.on_release)
        self.area.connect("scroll-event", self.on_scroll)

    def _build_statusbar(self) -> None:
        self.counter = Gtk.Label(xalign=0.0)
        self.counter.set_size_request(90, -1)
        self.stem_lbl = Gtk.Label(xalign=1.0)
        self.stem_lbl.set_ellipsize(Pango.EllipsizeMode.MIDDLE)
        self.entry = Gtk.Entry(hexpand=True)
        self.entry.set_placeholder_text(
            "label — becomes a suffix on the filename")
        self.entry.set_has_frame(False)
        self.status = Gtk.Label(xalign=1.0)
        self.status.set_size_request(210, -1)

        self.bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        self.bar.set_name("statusbar")
        self.bar.set_margin_start(10)
        self.bar.set_margin_end(10)
        self.bar.set_margin_top(6)
        self.bar.set_margin_bottom(6)
        for w in (self.counter, self.stem_lbl, self.entry, self.status):
            self.bar.pack_start(w, w is self.entry, w is self.entry, 0)

    def _build_layout(self) -> None:
        right = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        right.pack_start(self.area, True, True, 0)
        right.pack_start(self.bar, False, False, 0)

        self.paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        self.paned.pack1(self.left, False, False)
        self.paned.pack2(right, True, True)
        if TREE_WIDTH:
            self.paned.set_position(TREE_WIDTH)
        else:
            GLib.idle_add(self.fit_tree_width)

        self.add(self.paned)

    def fit_tree_width(self) -> bool:
        """Open the folder rail as narrow as the folder names allow.

        Asking GTK is useless: the name cell ellipsizes, and such a cell
        reports a few characters as both its minimum and its natural width, so
        the rail would open on rows reading "sy…wt". Turning ellipsizing off
        for the measurement does not help either — the column caches its size.
        So the text is measured directly, one Pango layout over the rows the
        store holds, indentation added back per level.

        Content-derived, so it cannot run before the tree has rows: it is
        queued for after __init__ has revealed the folder. Once set the rail
        stays put — expanding a deeper folder scrolls the tree sideways inside
        it rather than pushing the image over. Clamped both ways: TREE_MAX so
        one long path cannot hand the rail half the window, TREE_MIN so a
        near-empty tree still leaves something to grab.
        """
        layout = Pango.Layout(self.tree.get_pango_context())
        # The context carries the default UI font, not the monospace the CSS
        # puts on the tree, and monospace is the wider of the two.
        layout.set_font_description(self.tree.get_style_context().get_property(
            "font", Gtk.StateFlags.NORMAL))
        step = (self.tree.style_get_property("expander-size") or 16) + 4
        widest = 0

        def walk(it, depth: int) -> None:
            nonlocal widest
            while it is not None:
                layout.set_text(self.store.get_value(it, NAME) or "", -1)
                widest = max(widest,
                             (depth + 1) * step + layout.get_pixel_size().width)
                child = self.store.iter_children(it)
                if child is not None:
                    walk(child, depth + 1)
                it = self.store.iter_next(it)

        walk(self.store.get_iter_first(), 0)
        vsb = self.tree_scroll.get_vscrollbar()
        widest += vsb.get_preferred_width()[1] if vsb else 0
        widest += max(self.count_col.get_width(), 24)   # the count column
        widest += 8                     # cell padding either side
        self.paned.set_position(max(TREE_MIN, min(widest, TREE_MAX)))
        return False

    def _apply_css(self) -> None:
        css = Gtk.CssProvider()
        css.load_from_data(f"""
            window     {{ background: {SOL_BASE3}; }}
            headerbar  {{ background: {SOL_BASE2};
                          border-bottom: 1px solid {SOL_BASE1};
                          box-shadow: none; }}
            headerbar .title    {{ color: {SOL_BASE03}; font-weight: bold; }}
            headerbar .subtitle {{ color: {SOL_BASE00};
                                   font-family: monospace; font-size: 11px; }}
            headerbar button {{ background: {SOL_BASE3}; color: {SOL_BASE01};
                                border: 1px solid {SOL_BASE1};
                                padding: 2px 12px; }}
            headerbar button:hover {{ background: {SOL_BASE2};
                                      color: {SOL_BASE03}; }}
            headerbar button:checked {{ background: {SOL_CYAN};
                                        color: {SOL_BASE3};
                                        font-weight: bold; }}
            #statusbar {{ background: {SOL_BASE2};
                          border-top: 1px solid {SOL_BASE1}; }}
            #statusbar label {{ color: {SOL_BASE01}; font-family: monospace; }}
            #statusbar entry {{ background: {SOL_BASE3}; color: {SOL_BASE03};
                                font-family: monospace; font-size: 14px;
                                caret-color: {SOL_CYAN}; }}
            treeview {{ background: {SOL_BASE3}; color: {SOL_BASE01};
                        font-family: monospace; font-size: 12px; }}
            treeview:selected {{ background: {SOL_CYAN};
                                 color: {SOL_BASE3}; }}
            scrolledwindow {{ border-right: 1px solid {SOL_BASE1}; }}
            #pathentry {{ background: {SOL_BASE2}; color: {SOL_BASE03};
                          font-family: monospace; font-size: 12px;
                          caret-color: {SOL_CYAN}; border-radius: 0;
                          border: none; border-bottom: 1px solid {SOL_BASE1};
                          border-right: 1px solid {SOL_BASE1}; }}
        """.encode())
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(), css,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)

    # ── folder tree ───────────────────────────────────────────────────────

    def populate(self, parent, path: str) -> None:
        """Fill one level. Children get a stub only if they have children of
        their own, so the expander arrow never lies."""
        for name in child_dirs(path):
            full = os.path.join(path, name)
            images, subdirs = scan_dir(full)
            it = self.store.append(
                parent, [name, full, str(images) if images else "", False])
            if subdirs:
                self.store.append(it, ["", "", "", False])

    def on_row_expanded(self, _view, it, _path) -> None:
        if self.store[it][FILLED]:
            return
        self.store[it][FILLED] = True
        stub = self.store.iter_children(it)
        self.populate(it, self.store[it][PATH])
        if stub:                       # remove after appending, or it collapses
            self.store.remove(stub)

    def on_tree_selection(self, selection) -> None:
        if self._selecting:
            return
        model, it = selection.get_selected()
        if it is None:
            return
        self.switch_folder(model[it][PATH])

    def reset_tree_root(self, target: str) -> None:
        """Re-root the tree at "/" when `target` lies outside it, or reveal()
        could never reach it."""
        rel = os.path.relpath(target, self.tree_root)
        if rel != ".." and not rel.startswith(".." + os.sep):
            return
        self.tree_root = "/"
        self._selecting = True
        try:
            self.store.clear()
            root_iter = self.store.append(None, ["/", "/", "", False])
            self.store.append(root_iter, ["", "", "", False])
        finally:
            self._selecting = False

    # ── path field ────────────────────────────────────────────────────────

    def focus_path(self) -> None:
        self.left.set_visible(True)
        self.path_entry.grab_focus()
        self.path_entry.set_position(-1)

    def on_path_typed(self, entry) -> None:
        """Keep the completion list on the subfolders of the directory being
        typed — one scandir per directory, not per keystroke."""
        text = os.path.expanduser(entry.get_text())
        parent = text if text.endswith(os.sep) else os.path.dirname(text)
        if parent == self._path_listed:
            return
        self._path_listed = parent
        self.path_store.clear()
        if not os.path.isdir(parent):
            return
        prefix = entry.get_text()[:len(entry.get_text()) - len(os.path.basename(text))]
        for name in child_dirs(parent):
            self.path_store.append([prefix + name + os.sep])

    def on_path_completed(self, _completion, model, it) -> bool:
        entry = self.path_entry
        entry.set_text(model[it][0])
        entry.set_position(-1)
        return True

    def complete_path(self) -> None:
        """Tab: extend the typed path by as much as every match shares, like a
        shell. A single folder match gets its trailing slash."""
        text = self.path_entry.get_text()
        full = os.path.expanduser(text)
        parent, stem = os.path.split(full)
        if not os.path.isdir(parent or "/"):
            return
        try:
            names = sorted(e.name for e in os.scandir(parent or "/")
                           if e.name.startswith(stem)
                           and (SHOW_HIDDEN or stem.startswith(".")
                                or not e.name.startswith(".")))
        except OSError:
            return
        if not names:
            self.flash("no match")
            return
        common = os.path.commonprefix(names)
        add = common[len(stem):]
        if len(names) == 1 and os.path.isdir(os.path.join(parent, common)):
            add += os.sep
        if add:
            self.path_entry.set_text(text + add)
            self.path_entry.set_position(-1)

    def go_path(self) -> None:
        """Enter in the path field: open what it names and put the tree on it."""
        raw = self.path_entry.get_text().strip().strip("'\"")
        if raw.startswith("file://"):
            raw = GLib.filename_from_uri(raw)[0]
        target = os.path.abspath(os.path.expanduser(raw or "~"))
        if not os.path.exists(target):
            self.flash(f"no such path: {raw}")
            return
        folder = target if os.path.isdir(target) else os.path.dirname(target)
        self.reset_tree_root(folder)
        self.reveal(folder)
        if os.path.isdir(target) and not list_images(target):
            # Nothing to show here, but the tree is now open on it, so a
            # subfolder is one click or arrow key away.
            self.flash(f"no images in {os.path.basename(target) or target}"
                       " — pick a subfolder")
            self.tree.grab_focus()
            return
        self.open_target(target)
        self.entry.grab_focus()
        self.path_entry.set_text(self.folder)   # tidy: "~/x/" → the real path

    def reveal(self, target: str) -> None:
        """Expand down to `target` and select it."""
        self._selecting = True
        try:
            it = self.store.get_iter_first()
            self.tree.expand_row(self.store.get_path(it), False)
            rel = os.path.relpath(target, self.tree_root)
            parts = [] if rel == "." else rel.split(os.sep)
            for part in parts:
                child, found = self.store.iter_children(it), None
                while child is not None:
                    if self.store[child][NAME] == part:
                        found = child
                        break
                    child = self.store.iter_next(child)
                if found is None:
                    break
                it = found
                self.tree.expand_row(self.store.get_path(it), False)
            path = self.store.get_path(it)
            self.tree.get_selection().select_iter(it)
            self.tree.scroll_to_cell(path, None, True, 0.4, 0.0)
        finally:
            self._selecting = False

    def switch_folder(self, folder: str) -> None:
        if os.path.abspath(folder) == os.path.abspath(self.folder) and self.files:
            return
        if not self.commit():
            return                      # refuse to leave unsaved work behind
        files = list_images(folder)
        if not files:
            self.flash(f"no images in {os.path.basename(folder) or folder}")
            return
        self.folder = folder
        self.files = files
        self.origins = read_origins(folder)
        self.cache.clear()
        self.edits.clear()
        self.head.set_subtitle(folder)
        self.refresh_title()
        self.watch_folder()
        self.load(0)
        self.entry.grab_focus()

    def refresh_title(self) -> None:
        """Put the folder in the window title, not just the header bar.

        The capture script has to tell whether the instance it found is
        watching the folder the new screenshot landed in — an instance open on
        some other folder must not be raised in its place, or you get handed
        yesterday's images and no sign of the one you just took. The window
        title is the only handle an outside script has on that.
        """
        self.set_title(f"{APP_NAME} — {self.folder}")
        if hasattr(self, "path_entry") and not self.path_entry.has_focus():
            self.path_entry.set_text(self.folder)

    def open_target(self, target: str) -> None:
        """Show `target`, which may be a folder or a single image.

        Unconditional: whatever folder this window is on, a named file wins and
        it moves. That is the whole point — a screenshot should appear in the
        window that is already open, not in a second one beside it.
        """
        target = os.path.abspath(os.path.expanduser(target))
        if os.path.isdir(target):
            self.switch_folder(target)
            return
        if not os.path.isfile(target):
            self.flash(f"no such file: {os.path.basename(target)}")
            return

        folder, name = os.path.split(target)
        if not self.commit():
            return                      # unsaved work is not worth losing

        if os.path.abspath(folder) != os.path.abspath(self.folder):
            self.folder = folder
            self.origins = read_origins(folder)
            self.cache.clear()
            self.head.set_subtitle(folder)
            self.refresh_title()
            self.watch_folder()
            self.files = list_images(folder)
        elif name not in self.files:
            # Written moments ago: the listing this window is holding predates
            # it, and the folder watcher may not have fired yet.
            self.files = list_images(self.folder)

        self.edits.clear()
        if name in self.files:
            self.load(self.files.index(name))
        else:
            self.load(0)
            self.flash(f"not listed: {name}")
        self.entry.grab_focus()

    # ── watching the folder ───────────────────────────────────────────────

    def watch_folder(self) -> None:
        """Notice screenshots that arrive while the window is open.

        The capture hotkey is pressed *at* this app — you take a shot in order
        to annotate it — so a folder listing read once at startup would miss
        exactly the file you wanted. Rescanning is not enough either: the point
        is to land on the new shot without touching anything.
        """
        if getattr(self, "monitor", None) is not None:
            self.monitor.cancel()
            self.monitor = None
        try:
            self.monitor = Gio.File.new_for_path(self.folder).monitor_directory(
                Gio.FileMonitorFlags.NONE, None)
            self.monitor.connect("changed", self.on_folder_changed)
        except GLib.Error:
            self.monitor = None         # inotify limit or an odd filesystem

    def on_folder_changed(self, _mon, gfile, _other, event) -> None:
        if event not in (Gio.FileMonitorEvent.CREATED,
                         Gio.FileMonitorEvent.MOVED_IN):
            return
        name = gfile.get_basename()
        if os.path.splitext(name)[1].lower() not in IMAGE_EXTS:
            return
        if name in self.files:
            return                      # our own rename coming back to us
        # CREATED fires when the file appears, not when it is finished, so
        # give the writer a moment before trying to decode it.
        GLib.timeout_add(500, self.adopt, name)

    def adopt(self, name: str) -> bool:
        if name in self.files:
            return False
        if not os.path.exists(os.path.join(self.folder, name)):
            return False
        if not self.commit():
            return False                # unsaved work wins; the file is listed
        self.files.insert(0, name)      # newest first
        self.load(0)
        self.entry.grab_focus()
        self.flash("new screenshot")
        self.present()
        return False

    # ── names for the current file ────────────────────────────────────────

    def current(self) -> str | None:
        return self.files[self.idx] if self.files else None

    def path(self, basename: str | None = None) -> str:
        return os.path.join(self.folder, basename or self.current())

    def split_current(self) -> tuple[str, str, str]:
        name = self.current()
        return split_label(name, self.origins) if name else ("", "", "")

    def copy_path(self, *_args) -> None:
        """The full path of the current file onto the clipboard. store() is
        what makes it survive quitting: without it the clipboard dies with
        the app, and Esc is one keystroke away."""
        name = self.current()
        if not name:
            self.flash("no image to copy")
            return
        clip = Gtk.Clipboard.get(Gdk.SELECTION_CLIPBOARD)
        clip.set_text(self.path(name), -1)
        clip.store()
        self.flash("path copied")
        self.entry.grab_focus()

    # ── loading ───────────────────────────────────────────────────────────

    def pixbuf_for(self, basename: str) -> GdkPixbuf.Pixbuf | None:
        if basename in self.cache:
            return self.cache[basename]
        try:
            pb = GdkPixbuf.Pixbuf.new_from_file(self.path(basename))
        except GLib.Error:
            return None
        if len(self.cache) >= CACHE_MAX:
            self.cache.pop(next(iter(self.cache)))
        self.cache[basename] = pb
        return pb

    def load(self, idx: int) -> None:
        self.edits.clear()
        if not self.files:
            self.idx, self.pixbuf, self.loaded_label = 0, None, ""
            self.counter.set_text("0/0")
            self.stem_lbl.set_text("")
            self.entry.set_text("")
            self.entry.set_sensitive(False)
            self.copy_btn.set_sensitive(False)
            self.area.queue_draw()
            return

        self.entry.set_sensitive(True)
        self.copy_btn.set_sensitive(True)
        self.idx = max(0, min(idx, len(self.files) - 1))
        self.pixbuf = self.pixbuf_for(self.current())

        stem, label, _ = self.split_current()
        self.loaded_label = label
        self.entry.set_text(label)
        self.entry.select_region(0, -1)

        self.counter.set_text(f"{self.idx + 1}/{len(self.files)}")
        self.stem_lbl.set_text(stem)
        if self.pixbuf is None:
            self.flash("cannot decode this file")
        self.area.queue_draw()
        GLib.idle_add(self.prefetch)

    def pending_turns(self) -> int:
        """Net quarter turns clockwise still to be written, 0-3."""
        return sum(e["turns"] for e in self.edits
                   if e["kind"] == "rot") % 4

    def refresh_view(self) -> None:
        """Re-derive the shown image from the file's, applying any pending
        rotation. The cache keeps the file as it is on disk, so this can be
        recomputed from scratch after every press and every undo instead of
        rotating a rotated copy and accumulating resampling."""
        base = self.pixbuf_for(self.current()) if self.files else None
        turns = self.pending_turns()
        if base is not None and turns:
            base = base.rotate_simple(PIXBUF_ROTATION[turns])
        self.pixbuf = base
        self.area.queue_draw()

    def prefetch(self) -> bool:
        for j in (self.idx + 1, self.idx - 1):
            if 0 <= j < len(self.files):
                self.pixbuf_for(self.files[j])
        return False

    def flash(self, msg: str) -> None:
        self.status.set_markup(GLib.markup_escape_text(msg))
        GLib.timeout_add(2200, lambda: self.status.set_text("") or False)

    def error(self, msg: str) -> None:
        """Stays on screen. A save failure must not scroll past unnoticed."""
        self.status.set_markup(
            f"<span foreground='{SOL_RED}'><b>"
            f"{GLib.markup_escape_text(msg)}</b></span>")

    # ── tools ─────────────────────────────────────────────────────────────

    def set_tool(self, key: str) -> None:
        self.tool = key
        self._syncing = True
        for k, b in self.tool_buttons.items():
            b.set_active(k == key)
        self._syncing = False
        self.entry.grab_focus()

    def on_tool_toggled(self, button, key) -> None:
        if self._syncing:
            return
        if button.get_active():
            self.set_tool(key)
        elif key == self.tool:
            button.set_active(True)     # a group needs one member selected

    # ── saving ────────────────────────────────────────────────────────────

    def backup(self, basename: str) -> None:
        if not KEEP_ORIGINALS:
            return
        d = os.path.join(self.folder, ORIGINALS_DIR)
        os.makedirs(d, exist_ok=True)
        dest = os.path.join(d, self.origins.get(basename, basename))
        if not os.path.exists(dest):
            shutil.copy2(self.path(basename), dest)

    def burn_edits(self) -> None:
        """Write the pending marks into the file at full resolution.

        Rotation is applied first and crop last, whatever order they were made
        in. Every mark was placed in the coordinates of the rotated, uncropped
        image — that is what was on screen — so turning afterwards or cropping
        beforehand would move them all.
        """
        basename = self.current()
        src = self.path(basename)
        self.backup(basename)

        im = Image.open(src)
        fmt = im.format or "PNG"        # captured before convert(), which drops it
        was_rgb = im.mode == "RGB"
        im = im.convert("RGBA")

        turns = self.pending_turns()
        if turns:
            im = im.transpose(PIL_ROTATION[turns])

        pen = tuple(int(c * 255) for c in PEN_RGB)
        rect_col = tuple(int(c * 255) for c in RECT_RGB)
        crop_box = None

        for e in self.edits:
            kind = e["kind"]
            if kind == "pen":
                pts = [(round(x), round(y)) for x, y in e["pts"]]
                d = ImageDraw.Draw(im)
                if len(pts) == 1:
                    r = PEN_WIDTH / 2
                    x, y = pts[0]
                    d.ellipse([x - r, y - r, x + r, y + r], fill=pen)
                else:
                    d.line(pts, fill=pen, width=PEN_WIDTH, joint="curve")
            elif kind == "hl":
                # Translucent, so it goes on its own layer and is composited
                # rather than painted over the pixels underneath.
                overlay = Image.new("RGBA", im.size, (0, 0, 0, 0))
                ImageDraw.Draw(overlay).rectangle(
                    self._hl_box(e),
                    fill=tuple(int(c * 255) for c in HL_RGB)
                    + (int(HL_ALPHA * 255),))
                im = Image.alpha_composite(im, overlay)
            elif kind == "rect":
                ImageDraw.Draw(im).rectangle(
                    self._norm(e["box"]), outline=rect_col, width=RECT_WIDTH)
            elif kind == "crop":
                crop_box = self._norm(e["box"])

        if crop_box:
            x0, y0, x1, y1 = crop_box
            if x1 - x0 >= 2 and y1 - y0 >= 2:
                im = im.crop((int(x0), int(y0), int(x1), int(y1)))

        if was_rgb or fmt == "JPEG":
            im = im.convert("RGB")

        tmp = src + ".scr-tmp"
        if fmt == "JPEG":
            im.save(tmp, format="JPEG", quality=JPEG_QUALITY)
        else:
            im.save(tmp, format=fmt)
        os.replace(tmp, src)            # never a half-written image
        self.cache.pop(basename, None)

    @staticmethod
    def _norm(box) -> tuple[float, float, float, float]:
        x0, y0, x1, y1 = box
        return min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)

    @staticmethod
    def _hl_box(e) -> tuple[float, float, float, float]:
        x0, x1 = sorted((e["x0"], e["x1"]))
        return x0, e["y"] - HL_HEIGHT / 2, x1, e["y"] + HL_HEIGHT / 2

    def apply_label(self) -> None:
        label = sanitize(self.entry.get_text())
        if label == self.loaded_label:
            return
        old = self.current()
        new_base = relabel(self.folder, old, label, self.origins)
        if new_base == old:
            return
        self.files[self.idx] = new_base
        pb = self.cache.pop(old, None)
        if pb is not None:
            self.cache[new_base] = pb
        self.loaded_label = label

    def commit(self) -> bool:
        """Autosave. Marks first: they are burnt into the file the label is
        about to rename, so the other order would write to a path that no
        longer exists.

        Returns False if anything failed, and the caller must then refuse to
        move on. With no save key, silently advancing past a failed save is how
        work disappears — which is what an earlier version of this did.
        """
        if not self.files:
            return True
        try:
            if self.edits:
                self.burn_edits()
                self.edits.clear()
            self.apply_label()
            return True
        except Exception as exc:                     # noqa: BLE001
            self.error(f"NOT SAVED — {exc}")
            print(f"{APP_ID}: saving {self.current()!r} failed: {exc}",
                  file=sys.stderr, flush=True)
            return False

    # ── navigation ────────────────────────────────────────────────────────

    def go(self, delta: int) -> None:
        if not self.files:
            return
        target = self.idx + delta
        if not 0 <= target < len(self.files):
            self.flash("start of folder" if target < 0 else "end of folder")
            return
        if not self.commit():
            return              # stay put — the label and marks are still here
        self.load(target)

    def quit_app(self) -> None:
        """Quitting is a save point like any other, so it can be refused."""
        if self.commit():
            self.destroy()

    def confirm_trash(self) -> None:
        """Deleting is the one action here that asks first.

        Everything else autosaves precisely because it is reversible — a label
        is a rename, a mark has a copy in .originals/. Removing the file is
        not, so it gets the one dialog in the app. OK holds focus and is the
        default response, so the whole thing is Del then Enter.
        """
        if not self.files:
            return
        dlg = Gtk.MessageDialog(
            transient_for=self, modal=True,
            message_type=Gtk.MessageType.QUESTION,
            buttons=Gtk.ButtonsType.OK_CANCEL,
            text="Move to trash?")
        dlg.format_secondary_text(self.current())
        dlg.set_default_response(Gtk.ResponseType.OK)
        ok = dlg.get_widget_for_response(Gtk.ResponseType.OK)
        ok.set_can_default(True)
        ok.grab_default()
        ok.grab_focus()
        response = dlg.run()
        dlg.destroy()
        if response == Gtk.ResponseType.OK:
            self.trash()

    def trash(self) -> None:
        basename = self.current()
        try:
            subprocess.run(["gio", "trash", self.path(basename)],
                           check=True, capture_output=True, timeout=10)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired,
                FileNotFoundError) as exc:
            self.flash(f"trash failed: {exc}")
            return
        self.cache.pop(basename, None)
        del self.files[self.idx]
        self.load(min(self.idx, max(0, len(self.files) - 1)))
        self.flash(f"trashed {basename}")

    # ── moving to a folder ────────────────────────────────────────────────

    @staticmethod
    def move_accel() -> str:
        return "Alt+M" if TOOL_KEYS_NEED_ALT else "M"

    def refresh_move_btn(self) -> None:
        accel = self.move_accel()
        if self.move_dest:
            name = os.path.basename(self.move_dest) or self.move_dest
            self.move_btn.set_label(f"Move \u2192 {name} ({accel})")
            self.move_btn.set_tooltip_text(
                f"{accel} moves this image to {self.move_dest}\n"
                "Click to choose another folder")
        else:
            self.move_btn.set_label(f"Move \u2192 \u2026 ({accel})")
            self.move_btn.set_tooltip_text(
                f"Choose the folder {accel} moves images to")

    def choose_move_dest(self, *_args) -> bool:
        """Pick the folder the move key sends images to. True if one was."""
        dlg = Gtk.FileChooserDialog(
            title="Move images to", transient_for=self, modal=True,
            action=Gtk.FileChooserAction.SELECT_FOLDER)
        dlg.add_buttons("_Cancel", Gtk.ResponseType.CANCEL,
                        "_Select", Gtk.ResponseType.OK)
        dlg.set_default_response(Gtk.ResponseType.OK)
        dlg.set_current_folder(self.move_dest or self.folder)
        response = dlg.run()
        chosen = dlg.get_filename()
        dlg.destroy()
        self.entry.grab_focus()
        if response != Gtk.ResponseType.OK or not chosen:
            return False
        self.move_dest = chosen
        self.refresh_move_btn()
        self.flash(f"{self.move_accel()} \u2192 {os.path.basename(chosen)}")
        return True

    def move_current(self) -> None:
        """Send the image to the chosen folder and show the next one.

        This is the sorting workflow: pick a folder once, then a keystroke per
        keeper. No confirmation, because unlike the trash it is reversible with
        a plain mv — and asking would make it two keys per image, which is the
        point of having a key at all. Pending marks and the label are saved
        first, so what lands in the folder is what was on screen.
        """
        if not self.files:
            return
        if not self.move_dest and not self.choose_move_dest():
            return
        if not os.path.isdir(self.move_dest):
            self.error(f"no such folder: {self.move_dest}")
            return
        if os.path.abspath(self.move_dest) == os.path.abspath(self.folder):
            self.flash("already in that folder")
            return
        if not self.commit():
            return
        basename = self.current()
        try:
            new_base = move_file(self.folder, basename, self.move_dest,
                                 self.origins)
        except OSError as exc:
            self.error(f"NOT MOVED \u2014 {exc}")
            return
        self.cache.pop(basename, None)
        del self.files[self.idx]
        self.load(min(self.idx, max(0, len(self.files) - 1)))
        self.flash(f"\u2192 {os.path.basename(self.move_dest)}/{new_base}")

    # ── geometry ──────────────────────────────────────────────────────────

    def fit(self):
        """(scale, x offset, y offset) mapping image space to widget space."""
        a = self.area.get_allocation()
        if not self.pixbuf:
            return 1.0, 0.0, 0.0
        iw, ih = self.pixbuf.get_width(), self.pixbuf.get_height()
        # Never enlarge: a region capture blown up to the window is blurry.
        # Whole-pixel offsets, or at 1:1 every pixel is smeared into the next.
        s = min(1.0, a.width / iw, a.height / ih)
        return s, round((a.width - iw * s) / 2), round((a.height - ih * s) / 2)

    def to_image(self, wx: float, wy: float) -> tuple[float, float]:
        s, ox, oy = self.fit()
        x, y = (wx - ox) / s, (wy - oy) / s
        iw, ih = self.pixbuf.get_width(), self.pixbuf.get_height()
        return max(0, min(x, iw - 1)), max(0, min(y, ih - 1))

    # ── events ────────────────────────────────────────────────────────────

    def on_render(self, _area, cr) -> None:
        a = self.area.get_allocation()
        cr.set_source_rgb(*CANVAS_RGB)
        cr.paint()

        if not self.pixbuf:
            cr.set_source_rgb(0.35, 0.38, 0.40)
            cr.select_font_face("monospace", cairo.FONT_SLANT_NORMAL,
                                cairo.FONT_WEIGHT_NORMAL)
            cr.set_font_size(15)
            msg = ("choose a folder in the tree" if not self.files
                   else "cannot decode this file")
            cr.move_to(40, a.height / 2)
            cr.show_text(msg)
            return

        s, ox, oy = self.fit()
        iw, ih = self.pixbuf.get_width(), self.pixbuf.get_height()
        cr.save()
        cr.translate(ox, oy)
        cr.scale(s, s)
        Gdk.cairo_set_source_pixbuf(cr, self.pixbuf, 0, 0)
        # The default (bilinear) filter aliases text when shrinking.
        cr.get_source().set_filter(cairo.FILTER_BEST if s < 1 else cairo.FILTER_NEAREST)
        cr.paint()

        cr.set_line_cap(cairo.LINE_CAP_ROUND)
        cr.set_line_join(cairo.LINE_JOIN_ROUND)
        for e in self.edits:
            kind = e["kind"]
            if kind == "pen":
                cr.set_source_rgb(*PEN_RGB)
                cr.set_line_width(PEN_WIDTH)
                if len(e["pts"]) == 1:
                    x, y = e["pts"][0]
                    cr.arc(x, y, PEN_WIDTH / 2, 0, 6.2832)
                    cr.fill()
                else:
                    cr.move_to(*e["pts"][0])
                    for p in e["pts"][1:]:
                        cr.line_to(*p)
                    cr.stroke()
            elif kind == "hl":
                x0, y0, x1, y1 = self._hl_box(e)
                cr.set_source_rgba(*HL_RGB, HL_ALPHA)
                cr.rectangle(x0, y0, x1 - x0, y1 - y0)
                cr.fill()
            elif kind == "rect":
                x0, y0, x1, y1 = self._norm(e["box"])
                cr.set_source_rgb(*RECT_RGB)
                cr.set_line_width(RECT_WIDTH)
                cr.rectangle(x0, y0, x1 - x0, y1 - y0)
                cr.stroke()
            elif kind == "crop":
                x0, y0, x1, y1 = self._norm(e["box"])
                cr.set_source_rgba(0, 0, 0, CROP_DIM)
                cr.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
                cr.rectangle(0, 0, iw, ih)
                cr.rectangle(x0, y0, x1 - x0, y1 - y0)
                cr.fill()
                cr.set_fill_rule(cairo.FILL_RULE_WINDING)
                cr.set_source_rgb(*[int(SOL_CYAN[i:i + 2], 16) / 255
                                    for i in (1, 3, 5)])
                cr.set_line_width(2 / s)
                cr.rectangle(x0, y0, x1 - x0, y1 - y0)
                cr.stroke()
        cr.restore()

        if self.show_help:
            cr.set_source_rgba(0, 0, 0, 0.85)
            cr.paint()
            cr.set_source_rgb(0.9, 0.9, 0.9)
            cr.select_font_face("monospace", cairo.FONT_SLANT_NORMAL,
                                cairo.FONT_WEIGHT_NORMAL)
            cr.set_font_size(15)
            y = max(30, a.height / 2 - 230)
            for line in HELP.splitlines():
                cr.move_to(50, y)
                cr.show_text(line)
                y += 22

    def on_press(self, _w, ev) -> None:
        if not self.pixbuf:
            return
        if ev.button == 3:
            self.undo()
            return
        if ev.button != 1:
            return
        x, y = self.to_image(ev.x, ev.y)
        if self.tool == "pen":
            self.edits.append({"kind": "pen", "pts": [(x, y)]})
        elif self.tool == "hl":
            self.edits.append({"kind": "hl", "y": y, "x0": x, "x1": x})
        elif self.tool == "rect":
            self.edits.append({"kind": "rect", "box": (x, y, x, y)})
        elif self.tool == "crop":
            # One crop per image: a second drag replaces the first rather than
            # cropping a crop, which would be impossible to reason about.
            self.edits = [e for e in self.edits if e["kind"] != "crop"]
            self.edits.append({"kind": "crop", "box": (x, y, x, y)})
        self.dragging = True
        self.area.queue_draw()

    def on_motion(self, _w, ev) -> None:
        if not self.dragging or not self.edits:
            return
        x, y = self.to_image(ev.x, ev.y)
        e = self.edits[-1]
        if e["kind"] == "pen":
            e["pts"].append((x, y))
        elif e["kind"] == "hl":
            e["x1"] = x                 # horizontal only, like a highlighter
        else:
            x0, y0, _, _ = e["box"]
            e["box"] = (x0, y0, x, y)
        self.area.queue_draw()

    def on_release(self, _w, _ev) -> None:
        self.dragging = False
        self.show_pending()

    def show_pending(self) -> None:
        """Say what is unsaved, and stay saying it.

        A pending mark is invisible as *state* — you can see the drawing, but
        not that it is not yet in the file. A crop especially: you drag a box,
        nothing appears to happen, and there is no clue that Enter is what
        commits it. This message persists rather than flashing, because it is a
        condition, not an event.
        """
        parts = []
        turns = self.pending_turns()
        if turns:
            parts.append("rotated "
                         + {1: "90° cw", 2: "180°", 3: "90° ccw"}[turns])
        crop = next((e for e in self.edits if e["kind"] == "crop"), None)
        if crop:
            x0, y0, x1, y1 = self._norm(crop["box"])
            parts.append(f"crop {int(x1 - x0)}x{int(y1 - y0)}")
        n = len([e for e in self.edits if e["kind"] not in ("crop", "rot")])
        if n:
            parts.append(f"{n} mark{'s' if n != 1 else ''}")

        if not parts:
            self.status.set_text("")
            return
        self.status.set_markup(
            f"<span foreground='{SOL_CYAN}'>unsaved: "
            f"{' · '.join(parts)} — F7 apply</span>")

    def apply_now(self) -> None:
        """Burn the pending edits without leaving the image, so the result can
        be seen and undone-by-restore rather than only discovered later."""
        if not self.edits:
            self.flash("nothing to apply")
            return
        if self.flush():
            self.flash("applied")

    def flush(self) -> bool:
        """Write the pending edits and reread the file, which is now what the
        screen was already showing. False means the write failed and everything
        is still pending."""
        if not self.commit():
            return False
        self.cache.pop(self.current(), None)     # apply_label may have renamed
        self.load(self.idx)
        return True

    def rotate(self, delta: int) -> None:
        """A quarter turn, pending like a mark: four presses cost one write,
        and Ctrl+Z takes them back one at a time.

        Marks already drawn are written out first. They were placed in the
        current orientation and burn_edits turns the image before it draws, so
        letting them stay pending across a turn would land them sideways.
        """
        if not self.files or self.pixbuf is None:
            self.flash("nothing to rotate")
            return
        if any(e["kind"] != "rot" for e in self.edits) and not self.flush():
            return

        self.edits.append({"kind": "rot", "turns": delta})
        if self.pending_turns() == 0:
            # Back where it started — drop the entries rather than rewrite the
            # file with a turn that amounts to nothing.
            self.edits = [e for e in self.edits if e["kind"] != "rot"]
        self.refresh_view()
        self.show_pending()

    def undo(self) -> None:
        if self.edits:
            self.edits.pop()
            self.refresh_view()
            self.show_pending()

    def on_scroll(self, _w, ev) -> None:
        if ev.direction == Gdk.ScrollDirection.DOWN:
            self.go(1)
        elif ev.direction == Gdk.ScrollDirection.UP:
            self.go(-1)

    def on_key(self, _w, ev) -> bool:
        """Returning True keeps the key away from the entry, which is always
        focused so that typing a label needs no click and no mode."""
        key  = ev.keyval
        ctrl = ev.state & Gdk.ModifierType.CONTROL_MASK

        if self.show_help and key != Gdk.KEY_F1:
            self.show_help = False
            self.area.queue_draw()
            return True

        # The path field is a plain text field, so it comes ahead of the tool
        # letters and every other shortcut: each key is its own except the
        # three that finish with it.
        if self.path_entry.has_focus():
            if key in (Gdk.KEY_Return, Gdk.KEY_KP_Enter):
                self.go_path()
                return True
            if key == Gdk.KEY_Tab:
                self.complete_path()
                return True
            if key == Gdk.KEY_Escape:
                self.path_entry.set_text(self.folder)
                self.entry.grab_focus()
                return True
            return False

        alt = ev.state & Gdk.ModifierType.MOD1_MASK
        for tool_key, label, letter in TOOLS:
            if key not in (Gdk.keyval_from_name(letter),
                           Gdk.keyval_from_name(letter.upper())):
                continue
            if TOOL_KEYS_NEED_ALT and not alt:
                break               # a bare letter belongs to the label field
            if not TOOL_KEYS_NEED_ALT and (alt or ctrl):
                break
            self.set_tool(tool_key)
            self.flash(f"tool: {label.lower()}")
            return True

        # The move key follows the tool letters: Alt+M by default, bare M if
        # TOOL_KEYS_NEED_ALT is off, for the same reason — "m" is in "menu".
        if key in (Gdk.KEY_m, Gdk.KEY_M) and (
                alt if TOOL_KEYS_NEED_ALT else not (alt or ctrl)):
            self.move_current()
            return True

        if key in (Gdk.KEY_F3, Gdk.KEY_F9):
            self.left.set_visible(not self.left.get_visible())
            self.entry.grab_focus()
            return True
        if ctrl and key in (Gdk.KEY_l, Gdk.KEY_L):
            self.focus_path()
            return True

        if key == Gdk.KEY_F1:
            self.show_help = not self.show_help
            self.area.queue_draw()
            return True
        if key == Gdk.KEY_F11:
            (self.unfullscreen if getattr(self, "_full", False)
             else self.fullscreen)()
            self._full = not getattr(self, "_full", False)
            return True

        # With focus in the tree the arrows and Enter belong to it, or folder
        # browsing by keyboard would be impossible. PgUp/PgDn are the exception:
        # they page through images wherever the focus happens to sit, so that
        # clicking a folder does not quietly change what they do.
        if self.tree.has_focus() and key in (
                Gdk.KEY_Up, Gdk.KEY_Down, Gdk.KEY_Left, Gdk.KEY_Right,
                Gdk.KEY_Return, Gdk.KEY_KP_Enter, Gdk.KEY_space):
            return False

        if ctrl and key in (Gdk.KEY_z, Gdk.KEY_Z):
            self.undo()
            return True
        if ctrl and key in (Gdk.KEY_q, Gdk.KEY_Q):
            self.quit_app()
            return True
        # KP_Page_Up/Down are the keypad 9 and 3 with NumLock off, which is how
        # a keypad-only hand pages through a folder.
        if key in (Gdk.KEY_Return, Gdk.KEY_KP_Enter, Gdk.KEY_Down,
                   Gdk.KEY_Page_Down, Gdk.KEY_KP_Page_Down):
            self.go(1)
            return True
        if key in (Gdk.KEY_Up, Gdk.KEY_Page_Up, Gdk.KEY_KP_Page_Up):
            self.go(-1)
            return True
        if key == Gdk.KEY_Escape:
            self.quit_app()
            return True
        # Delete is claimed for the file, not the text. Backspace still deletes
        # characters, which covers editing; forward-delete is the rarer need,
        # and the confirmation makes a mistyped Del harmless.
        if key in (Gdk.KEY_Delete, Gdk.KEY_KP_Delete, Gdk.KEY_F8):
            self.confirm_trash()
            return True
        # Bare, not Alt: brackets are not letters, and a screenshot label
        # wanting one is rarer than wanting to straighten a photo. This is the
        # one exception to "a printable key goes into the label".
        if key == Gdk.KEY_bracketleft:
            self.rotate(-1)
            return True
        if key == Gdk.KEY_bracketright:
            self.rotate(1)
            return True
        if key == Gdk.KEY_F7:
            self.apply_now()
            return True
        # Shift, because a bare Ctrl+C belongs to the label field, where the
        # text is selected the moment an image loads.
        if ctrl and (ev.state & Gdk.ModifierType.SHIFT_MASK) and key in (
                Gdk.KEY_c, Gdk.KEY_C):
            self.copy_path()
            return True
        if key == Gdk.KEY_F5:
            if self.files:
                self.cache.pop(self.current(), None)
                self.load(self.idx)
                self.flash("reloaded")
            return True
        return False

    def on_delete(self, _w, _ev) -> bool:
        """Window close button. Returning True cancels the close, which is
        what an unsaved image needs."""
        return not self.commit()

    def on_destroy(self, _w) -> None:
        # Nothing to do: the window belongs to the Gtk.Application, which quits
        # of its own accord once its last window is gone.
        pass


# ── entry point ───────────────────────────────────────────────────────────

def load_icons() -> None:
    """Set the icon the window manager shows — the Alt+Tab thumbnail.

    set_default_icon_list rather than a single file so the WM picks a size it
    does not have to rescale. set_prgname must match the .desktop file's
    StartupWMClass or the launcher and the window are treated as unrelated,
    and the Alt+Tab entry falls back to a generic placeholder — GTK 3 derives
    WM_CLASS from the program name, so this is also what set_wmclass used to
    do before it was deprecated.
    """
    GLib.set_prgname(APP_ID)
    here = os.path.dirname(os.path.abspath(__file__))
    icons = []
    for name in ("icon.png", "icon-128.png", "icon-64.png",
                 "icon-48.png", "icon-32.png"):
        p = os.path.join(here, name)
        if os.path.exists(p):
            try:
                icons.append(GdkPixbuf.Pixbuf.new_from_file(p))
            except GLib.Error:
                pass
    if icons:
        Gtk.Window.set_default_icon_list(icons)


class App(Gtk.Application):
    """Single instance, so a capture always lands in the window already open.

    Launching this a second time does not start a second window: GApplication
    hands the new command line to the running instance over DBus, and it opens
    whatever was asked for — whichever folder it happened to be showing. The
    capture scripts used to match the window title against their own folder to
    decide whether to reuse it, which meant an instance left open on some other
    folder got a second window beside it and the new screenshot went unseen.
    """

    def __init__(self):
        super().__init__(application_id="local.scr-annotator",
                         flags=Gio.ApplicationFlags.HANDLES_COMMAND_LINE)
        self.win = None

    def do_command_line(self, command_line):
        args = command_line.get_arguments()[1:]
        target = args[0] if args else DEFAULT_FOLDER
        target = os.path.abspath(os.path.expanduser(target))

        if self.win is None:
            folder = (target if os.path.isdir(target)
                      else os.path.dirname(target) if os.path.isfile(target)
                      else DEFAULT_FOLDER)
            if not os.path.isdir(folder):
                folder = None           # no ~/new yet: open the tree on $HOME
            load_icons()
            self.win = Annotator(folder)
            self.add_window(self.win)
            self.win.show_all()
            if os.path.isfile(target):
                self.win.open_target(target)
            self.win.entry.grab_focus()
        else:
            self.win.open_target(target)
            self.win.present()
        return 0


def main() -> int:
    return App().run(sys.argv)


if __name__ == "__main__":
    sys.exit(main())
