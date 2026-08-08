"""The left filter sidebar — a collapsible navigation bar of note filters.

Widgets only: *which* notes match and *how many* match each option is decided by
:mod:`filters`; this module draws those numbers and reports clicks back. The window calls
:meth:`FilterSidebar.refresh` on every reload and reads :attr:`FilterSidebar.state`.

The panel holds, top to bottom:

* a **calendar heat map** — one cell per day, shaded by how many notes were written (or
  last touched) that day, so a month of note-taking is legible at a glance; clicking a day
  filters the board to it, shift-clicking picks a range;
* **priority**, **type** and **category** facets — every option the folder defines, plus any
  *foreign* code the notes actually use;
* the **non-standard** tri-state — include / hide / show only the visible files and folders whose
  name isn't the canonical note format yet, i.e. the "needs metadata" clean-up queue;
* a **tag cloud** of the ``#hashtags`` found in note bodies, sized by how often each is used —
  clicking one narrows the board to the notes carrying it (picking several means "carries all of
  them"), which is what makes free-form tags usable alongside the fixed type/category codes;
* the **bad taxonomy** tri-state — the other clean-up queue: names that ARE the canonical format
  but carry a tag value the folder never defined (type ``Y`` where no type ``Y`` exists). Separate
  from non-standard because the fix is different — retag, not rename.

Every row carries the number of notes behind it. Collapsed, the whole thing shrinks to a thin
rail with a re-open chevron, so it costs ~30px when it isn't wanted (Ctrl+B toggles it).
"""

from __future__ import annotations

import calendar
from datetime import date, timedelta

import gi

gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, Gdk, GLib  # noqa: E402

import filters as F  # noqa: E402
from filters import FilterState, Taxonomy  # noqa: E402
from notes import Note  # noqa: E402

PANEL_WIDTH = 250
RAIL_WIDTH = 26

# Monday-first, matching the ISO week that date.weekday() already counts in.
WEEKDAY_INITIALS = ("M", "T", "W", "T", "F", "S", "S")

# Six rows always span a month, whatever weekday it starts on.
CAL_CELLS = 42

# The sidebar's sections, in the order they're stacked. The keys double as the persisted
# "which sections are collapsed" identifiers (Settings.sidebar_sections_collapsed).
SEC_CALENDAR = "calendar"


def _first_of_month(day: date) -> date:
    return day.replace(day=1)


def _shift_months(month: date, delta: int) -> date:
    index = month.year * 12 + (month.month - 1) + delta
    return date(index // 12, index % 12 + 1, 1)


def _month_days(month: date) -> list[date]:
    _first_weekday, length = calendar.monthrange(month.year, month.month)
    return [month.replace(day=d) for d in range(1, length + 1)]


class FilterSidebar(Gtk.Box):
    """The sidebar itself. Construct it with the shared :class:`~filters.FilterState` the window
    filters with, the :class:`~config.Settings` it persists its own layout in, and a callback to
    run whenever a filter changes (the window re-renders the board from there)."""

    def __init__(self, state: FilterState, settings, on_changed):
        super().__init__(orientation=Gtk.Orientation.HORIZONTAL)
        self.state = state
        self.settings = settings
        self._on_changed = on_changed

        self._suppress = False              # guards programmatic toggle updates (see _refresh_rows)
        self._tax = Taxonomy()
        self._type_names: dict[str, str] = {}
        self._category_names: dict[str, str] = {}
        self._counts: F.FacetCounts | None = None
        self._month = _first_of_month(date.today())
        self._month_pinned = False          # True once the user (or the first refresh) chose a month
        self._day_anchor: date | None = None  # shift-click range start
        self._day_mods = 0                  # modifiers of the in-flight day click

        self._sections: dict[str, tuple] = {}   # key → (chevron, content widget, badge label)
        self._lists: dict[str, Gtk.Box] = {}    # facet dimension → its row container
        self._rows: dict[str, dict] = {}        # facet dimension → {option key: (button, count)}
        self._collapsed_sections = set(settings.sidebar_sections_collapsed)

        if state.date_field not in F.DATE_FIELDS:
            state.date_field = F.BY_CREATED

        self._build()
        self.set_collapsed(not settings.sidebar_visible)

    # ------------------------------------------------------------------ build

    def _build(self) -> None:
        # Collapsed rail and expanded panel are siblings; exactly one is visible. Both carry
        # no_show_all so the window's show_all() can't override the collapsed state — their
        # children are shown explicitly below instead.
        self.rail = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        self.rail.get_style_context().add_class("sidebar-rail")
        self.rail.set_size_request(RAIL_WIDTH, -1)
        self.rail.set_no_show_all(True)
        open_button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        open_button.add(Gtk.Image.new_from_icon_name("pan-end-symbolic", Gtk.IconSize.MENU))
        open_button.set_tooltip_text("Show the filter sidebar (Ctrl+B)")
        open_button.connect("clicked", lambda _b: self.set_collapsed(False))
        self.rail.pack_start(open_button, False, False, 0)
        self.rail_badge = Gtk.Label()
        self.rail_badge.set_angle(270)      # reads top-to-bottom down the rail
        self.rail_badge.get_style_context().add_class("sidebar-rail-badge")
        self.rail.pack_start(self.rail_badge, False, False, 0)
        self.pack_start(self.rail, False, False, 0)

        self.panel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.panel.get_style_context().add_class("sidebar-panel")
        self.panel.set_size_request(PANEL_WIDTH, -1)
        self.panel.set_no_show_all(True)

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        header.get_style_context().add_class("sidebar-header")
        header.set_border_width(6)
        title = Gtk.Label(label="Filters", xalign=0)
        title.get_style_context().add_class("sidebar-title")
        header.pack_start(title, True, True, 0)
        # "shown / total" — the one number that says what the filters are currently doing.
        self.shown_label = Gtk.Label()
        self.shown_label.get_style_context().add_class("sidebar-shown")
        header.pack_start(self.shown_label, False, False, 0)
        close_button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        close_button.add(Gtk.Image.new_from_icon_name("pan-start-symbolic", Gtk.IconSize.MENU))
        close_button.set_tooltip_text("Hide the filter sidebar (Ctrl+B)")
        close_button.connect("clicked", lambda _b: self.set_collapsed(True))
        header.pack_end(close_button, False, False, 0)
        self.panel.pack_start(header, False, False, 0)

        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        # Give the scrollbar its own gutter rather than letting it float over the panel's right
        # edge, where it would sit on top of the per-option counts (the same reason the note editor
        # turns overlay scrolling off). The counts also carry right padding, in .filter-count.
        scroller.set_overlay_scrolling(False)
        body = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        body.set_border_width(6)
        scroller.add(body)
        self.panel.pack_start(scroller, True, True, 0)

        body.pack_start(self._section(SEC_CALENDAR, "Calendar", self._build_calendar()),
                        False, False, 0)
        for dim, caption in ((F.DIM_PRIORITY, "Priority"), (F.DIM_TYPE, "Type"),
                             (F.DIM_CATEGORY, "Category")):
            container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
            container.set_no_show_all(True)
            self._lists[dim] = container
            self._rows[dim] = {}
            body.pack_start(self._section(dim, caption, container), False, False, 0)
        body.pack_start(
            self._section(F.DIM_NONSTANDARD, "Non-standard", self._build_nonstandard()),
            False, False, 0)
        self._tag_cloud = Gtk.FlowBox()
        self._tag_cloud.set_selection_mode(Gtk.SelectionMode.NONE)
        self._tag_cloud.set_max_children_per_line(99)   # let width, not a count, decide the wrap
        self._tag_cloud.set_row_spacing(2)
        self._tag_cloud.set_column_spacing(2)
        self._tag_cloud.set_no_show_all(True)
        self._tag_buttons: dict[str, Gtk.ToggleButton] = {}
        body.pack_start(self._section(F.DIM_TAG, "Tags", self._tag_cloud), False, False, 0)

        body.pack_start(
            self._section(F.DIM_TAXONOMY, "Bad taxonomy", self._build_taxonomy()),
            False, False, 0)

        footer = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        footer.set_border_width(6)
        self.clear_button = Gtk.Button(label="Clear all filters")
        self.clear_button.get_style_context().add_class("sidebar-clear")
        self.clear_button.connect("clicked", lambda _b: self._clear_all())
        footer.pack_start(self.clear_button, True, True, 0)
        self.panel.pack_start(footer, False, False, 0)

        self.pack_start(self.panel, True, True, 0)

        # Show the children of both (no_show_all only stops the parents themselves), so flipping
        # the parent's visibility is all that collapsing/expanding has to do.
        for parent in (self.rail, self.panel):
            for child in parent.get_children():
                child.show_all()

    def _section(self, key: str, caption: str, content: Gtk.Widget) -> Gtk.Widget:
        """A collapsible section — the same chevron + name + count header the kanban subgroups use
        (see KnotesWindow._build_subgroup), so the two read as one idiom."""
        collapsed = key in self._collapsed_sections
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)

        header = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
        header.get_style_context().add_class("group-header")
        hbox = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        chevron = Gtk.Image.new_from_icon_name(
            "pan-end-symbolic" if collapsed else "pan-down-symbolic", Gtk.IconSize.MENU)
        hbox.pack_start(chevron, False, False, 0)
        hbox.pack_start(Gtk.Label(label=caption, xalign=0), True, True, 0)
        badge = Gtk.Label()   # how many options are picked in this section, blank when none
        badge.get_style_context().add_class("sidebar-section-badge")
        hbox.pack_end(badge, False, False, 0)
        header.add(hbox)
        header.connect("clicked", lambda _b, k=key: self._toggle_section(k))
        box.pack_start(header, False, False, 0)

        content.set_visible(not collapsed)
        box.pack_start(content, False, False, 0)
        self._sections[key] = (chevron, content, badge)
        return box

    def _toggle_section(self, key: str) -> None:
        chevron, content, _badge = self._sections[key]
        collapsed = key not in self._collapsed_sections
        if collapsed:
            self._collapsed_sections.add(key)
        else:
            self._collapsed_sections.discard(key)
        chevron.set_from_icon_name(
            "pan-end-symbolic" if collapsed else "pan-down-symbolic", Gtk.IconSize.MENU)
        content.set_visible(not collapsed)
        self.settings.sidebar_sections_collapsed = sorted(self._collapsed_sections)
        self.settings.save()

    # --------------------------------------------------------------- calendar

    def _build_calendar(self) -> Gtk.Widget:
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        box.set_no_show_all(True)

        # Which date the heat map shades AND a clicked day filters on — one switch drives both, so
        # "show me what I wrote in June" and "show me what I revised in June" are one click apart.
        metric = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL)
        metric.get_style_context().add_class("linked")
        self._metric_buttons: dict[str, Gtk.ToggleButton] = {}
        for field, label, tip in (
                (F.BY_CREATED, "Created",
                 "Heat map and day filter read the note's creation date — taken from its FILE NAME, "
                 "so retagging a note never moves it in time"),
                (F.BY_UPDATED, "Updated",
                 "Heat map and day filter read the file's last-modified date — the 'revisited it' signal")):
            button = Gtk.ToggleButton(label=label)
            button.get_style_context().add_class("chip")
            button.set_tooltip_text(tip)
            button.connect("toggled", lambda b, f=field: self._on_metric(b, f))
            metric.pack_start(button, True, True, 0)
            self._metric_buttons[field] = button
        box.pack_start(metric, False, False, 0)

        nav = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=2)

        def _month_button(icon: str, delta: int, tip: str) -> Gtk.Button:
            button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
            button.add(Gtk.Image.new_from_icon_name(icon, Gtk.IconSize.MENU))
            button.set_tooltip_text(tip)
            button.connect("clicked", lambda _b: self._shift_month(delta))
            return button

        nav.pack_start(_month_button("pan-start-symbolic", -1, "Previous month"), False, False, 0)
        nav.pack_end(_month_button("pan-end-symbolic", 1, "Next month"), False, False, 0)
        self.month_label = Gtk.Label()
        self.month_label.get_style_context().add_class("cal-month")
        nav.set_center_widget(self.month_label)
        box.pack_start(nav, False, False, 0)

        self.day_grid = Gtk.Grid(column_homogeneous=True, row_spacing=2, column_spacing=2)
        box.pack_start(self.day_grid, False, False, 0)

        actions = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        today_button = Gtk.Button(label="Today")
        today_button.get_style_context().add_class("sidebar-clear")
        today_button.set_tooltip_text("Jump the calendar back to this month")
        today_button.connect("clicked", lambda _b: self._go_today())
        actions.pack_start(today_button, True, True, 0)
        self.clear_days_button = Gtk.Button(label="Clear days")
        self.clear_days_button.get_style_context().add_class("sidebar-clear")
        self.clear_days_button.set_tooltip_text("Drop the day selection (keeps the other filters)")
        self.clear_days_button.connect("clicked", lambda _b: self._clear_days())
        actions.pack_start(self.clear_days_button, True, True, 0)
        box.pack_start(actions, False, False, 0)
        # `box` carries no_show_all so the section's collapsed state survives the window's
        # show_all() — which also means show_all() never reaches INTO it. Show its children here
        # (the day cells do it for themselves in _rebuild_days, which replaces them).
        for child in box.get_children():
            child.show_all()
        return box

    def _rebuild_days(self) -> None:
        """Redraw the visible month. Cheap (43 widgets) and it has to happen whenever the month,
        the metric or any count changes — which is nearly every refresh — so it isn't cached."""
        for child in self.day_grid.get_children():
            self.day_grid.remove(child)

        created = self._counts.created_days if self._counts else {}
        updated = self._counts.updated_days if self._counts else {}
        by_updated = self.state.date_field == F.BY_UPDATED
        heat = updated if by_updated else created
        ramp = "u" if by_updated else "c"

        self.month_label.set_markup(f'<b>{self._month:%B %Y}</b>')
        for column, initial in enumerate(WEEKDAY_INITIALS):
            label = Gtk.Label(label=initial)
            label.get_style_context().add_class("cal-weekday")
            self.day_grid.attach(label, column, 0, 1, 1)

        # Scale the shading to the busiest day of THIS month, so a quiet month still reads as a
        # gradient rather than one flat wash.
        peak = max([heat.get(d, 0) for d in _month_days(self._month)] or [0])
        start = self._month - timedelta(days=self._month.weekday())   # back to that week's Monday
        today = date.today()
        for cell in range(CAL_CELLS):
            day = start + timedelta(days=cell)
            n_created, n_updated = created.get(day, 0), updated.get(day, 0)
            count = n_updated if by_updated else n_created
            in_month = day.month == self._month.month and day.year == self._month.year

            button = Gtk.Button(relief=Gtk.ReliefStyle.NONE)
            style = button.get_style_context()
            style.add_class("cal-day")
            if not in_month:
                style.add_class("cal-other-month")
            level = F.heat_level(count, peak) if in_month else 0
            if level:
                style.add_class(f"heat-{ramp}{level}")
            if day == today:
                style.add_class("cal-today")
            if day in self.state.days:
                style.add_class("cal-selected")

            inner = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
            number = Gtk.Label(label=str(day.day))
            number.get_style_context().add_class("cal-day-num")
            inner.pack_start(number, False, False, 0)
            # The per-day count — the calendar is a filter like any other, so it shows its numbers.
            tally = Gtk.Label(label=str(count) if count else "·")
            tally.get_style_context().add_class("cal-day-count")
            inner.pack_start(tally, False, False, 0)
            button.add(inner)
            button.set_tooltip_text(
                f"{day:%a %d %b %Y}\n{n_created} created · {n_updated} updated"
                "\nClick to filter · Shift+click for a range")
            button.connect("button-press-event", self._remember_day_mods)
            button.connect("clicked", lambda _b, d=day: self._on_day(d))
            self.day_grid.attach(button, cell % 7, cell // 7 + 1, 1, 1)

        for child in self.day_grid.get_children():
            child.show_all()

    def _remember_day_mods(self, _button, event) -> bool:
        """"clicked" carries no modifiers, so stash them from the press that precedes it."""
        self._day_mods = event.state & Gtk.accelerator_get_default_mod_mask()
        return False   # let the press become a normal click

    def _on_day(self, day: date) -> None:
        if self._suppress:
            return
        if self._day_mods & Gdk.ModifierType.SHIFT_MASK and self._day_anchor is not None:
            self.state.select_range(self._day_anchor, day)
        else:
            self.state.set_option(F.DIM_DAY, day, day not in self.state.days)
            self._day_anchor = day
        self._day_mods = 0
        self._emit()

    def _shift_month(self, delta: int) -> None:
        self._month = _shift_months(self._month, delta)
        self._month_pinned = True
        self._rebuild_days()

    def _go_today(self) -> None:
        self._month = _first_of_month(date.today())
        self._month_pinned = True
        self._rebuild_days()

    def _clear_days(self) -> None:
        if not self.state.days:
            return
        self.state.days.clear()
        self._day_anchor = None
        self._emit()

    def _on_metric(self, button: Gtk.ToggleButton, field: str) -> None:
        """The Created/Updated pair behaves like a radio: exactly one is always active, so clicking
        the active one re-asserts it instead of leaving the pair with nothing selected."""
        if self._suppress:
            return
        if self.state.date_field == field:
            self._reassert(button)
            return
        if not button.get_active():
            return
        self.state.date_field = field
        self.settings.heatmap_metric = field
        self.settings.save()
        # A day selection means "created on X" or "updated on X" — the switch changes the question,
        # so the old answer has to go rather than silently mean something else.
        self.state.days.clear()
        self._day_anchor = None
        self._emit()

    # ------------------------------------------------- the two clean-up queues

    def _build_tristate(self, dim: str, modes: tuple) -> Gtk.Widget:
        """An include / hide / only radio group — the shape both clean-up queues take."""
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        box.set_no_show_all(True)
        self._rows[dim] = {}
        for mode, label, tip in modes:
            button, count = self._build_row(dim, mode, label, tip)
            box.pack_start(button, False, False, 0)
            self._rows[dim][mode] = (button, count)
            button.show_all()
        return box

    def _build_nonstandard(self) -> Gtk.Widget:
        return self._build_tristate(F.DIM_NONSTANDARD, (
            (F.NS_INCLUDE, "Include", "Show everything in the folder (the default)"),
            (F.NS_HIDE, "Hide",
             "Real notes only — hide the visible files and folders whose name isn't the "
             "<timestamp>~<type>~p<n>~<subject>.md format yet"),
            (F.NS_ONLY, "Only",
             "Show ONLY those non-standard items — the 'needs metadata' clean-up queue. "
             "Select one and press Ctrl+Shift+<code> to tag it and file it in"),
        ))

    def _build_taxonomy(self) -> Gtk.Widget:
        return self._build_tristate(F.DIM_TAXONOMY, (
            (F.TX_INCLUDE, "Include", "Show everything, whatever it's tagged with (the default)"),
            (F.TX_HIDE, "Hide",
             "Hide notes tagged with a type, priority or category this folder doesn't define"),
            (F.TX_ONLY, "Only",
             "Show ONLY those — the retag queue: the file name is the right shape, but a value in "
             "it matches no configured choice (type Y where no type Y exists). Non-standard names "
             "are not counted here; they're the section above"),
        ))

    # ---------------------------------------------------------------- tag cloud

    # Relative Pango sizes for the least→most used tag. Deliberately a short, coarse ladder rather
    # than a continuous scale: the point is "these few are the big ones", and finer gradations read
    # as noise at sidebar width. The smallest is still legible on its own.
    _TAG_SCALE = (0.85, 0.95, 1.05, 1.2, 1.4)

    def _sync_tag_cloud(self, counts: dict) -> None:
        """Rebuild the cloud when its tag set changes; otherwise just restyle in place.

        Rebuilding on every refresh would drop the button the user is about to click — the cloud is
        recomputed on each keystroke in the search box, and every note in the folder is re-read to
        do it — so the widget list is reused whenever the tags themselves are unchanged.
        """
        if list(self._tag_buttons) != list(counts):
            for child in self._tag_cloud.get_children():
                self._tag_cloud.remove(child)
            self._tag_buttons.clear()
            for tag in counts:
                button = Gtk.ToggleButton()
                button.get_style_context().add_class("tag-chip")
                button.add(Gtk.Label())
                button.connect("toggled", lambda b, t=tag: self._on_row(b, F.DIM_TAG, t))
                self._tag_cloud.add(button)
                self._tag_buttons[tag] = button
            self._tag_cloud.show_all()

        peak = max(counts.values(), default=0)
        for tag, button in self._tag_buttons.items():
            n = counts.get(tag, 0)
            step = 0 if peak <= 1 else int((n - 1) / max(1, peak - 1) * (len(self._TAG_SCALE) - 1))
            label = button.get_child()
            label.set_markup(
                f'<span size="{int(self._TAG_SCALE[min(step, len(self._TAG_SCALE) - 1)] * 100)}%">'
                f'#{GLib.markup_escape_text(tag)}</span> '
                f'<span size="80%" alpha="60%">{n}</span>')
            button.set_tooltip_text(f"{n} note(s) tagged #{tag}")
            self._suppress = True
            button.set_active(tag in self.state.tags)
            self._suppress = False
            # A picked tag stays clickable at 0 so it can be switched off; see FacetCounts.tags.
            button.set_sensitive(n > 0 or tag in self.state.tags)
        self._tag_cloud.set_visible(bool(counts))

    # -------------------------------------------------------------- facet rows

    def _build_row(self, dim: str, key, label: str, tooltip: str) -> tuple[Gtk.ToggleButton, Gtk.Label]:
        """One filter option: its name on the left, the number of notes behind it on the right."""
        button = Gtk.ToggleButton()
        button.get_style_context().add_class("filter-row")
        button.set_tooltip_text(tooltip)
        row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        name = Gtk.Label(label=label, xalign=0)
        name.set_ellipsize(3)   # END
        row.pack_start(name, True, True, 0)
        count = Gtk.Label()
        count.get_style_context().add_class("filter-count")
        row.pack_end(count, False, False, 0)
        button.add(row)
        button.connect("toggled", lambda b, d=dim, k=key: self._on_row(b, d, k))
        return button, count

    def _on_row(self, button: Gtk.ToggleButton, dim: str, key) -> None:
        if self._suppress:
            return
        if dim in (F.DIM_NONSTANDARD, F.DIM_TAXONOMY):
            # Tri-state, so it's a radio: exactly one mode is always on.
            attr = "nonstandard" if dim == F.DIM_NONSTANDARD else "taxonomy"
            if getattr(self.state, attr) == key:
                self._reassert(button)
                return
            if not button.get_active():
                return
            setattr(self.state, attr, key)
        else:
            self.state.set_option(dim, key, button.get_active())
        self._emit()

    def _sync_rows(self, dim: str, keys: list, label_of, tooltip_of) -> None:
        """Rebuild a facet's rows only when its option list actually changed — a folder switch, a
        settings edit, a backend taxonomy sync, or a foreign code appearing in the notes. Reusing
        the widgets otherwise keeps the sidebar from flickering on every keystroke in search."""
        if list(self._rows[dim]) == keys:
            return
        container = self._lists[dim]
        for child in container.get_children():
            container.remove(child)
        self._rows[dim] = {}
        for key in keys:
            button, count = self._build_row(dim, key, label_of(key), tooltip_of(key))
            if not self._tax.defines(dim, key):
                button.get_style_context().add_class("filter-foreign")
            container.pack_start(button, False, False, 0)
            self._rows[dim][key] = (button, count)
            button.show_all()   # the container carries no_show_all; its rows must show themselves

    def _label_for(self, dim: str, key) -> str:
        if key is None:
            return {F.DIM_TYPE: "Untyped", F.DIM_CATEGORY: "Uncategorized",
                    F.DIM_PRIORITY: "No priority"}[dim]
        if dim == F.DIM_PRIORITY:
            return f"P{key}"
        name = (self._type_names if dim == F.DIM_TYPE else self._category_names).get(key, "")
        return f"{key} · {name}" if name else str(key)

    def _tooltip_for(self, dim: str, key) -> str:
        what = {F.DIM_TYPE: "type", F.DIM_CATEGORY: "category", F.DIM_PRIORITY: "priority"}[dim]
        if key is None:
            return f"Notes with no {what} at all — including files that still need metadata"
        if not self._tax.defines(dim, key):
            return (f"{what.capitalize()} '{key}' — used by notes in this folder but NOT one of its "
                    f"configured options (e.g. synced from a folder with a different taxonomy)")
        return f"Notes with {what} '{key}'"

    # ------------------------------------------------------------- public API

    @property
    def collapsed(self) -> bool:
        return not self.settings.sidebar_visible

    def toggle_collapsed(self) -> None:
        self.set_collapsed(not self.collapsed)

    def set_collapsed(self, collapsed: bool) -> None:
        self.panel.set_visible(not collapsed)
        self.rail.set_visible(collapsed)
        if self.settings.sidebar_visible == collapsed:
            self.settings.sidebar_visible = not collapsed
            self.settings.save()

    def set_taxonomy(self, tax: Taxonomy, types, categories) -> None:
        """Point the sidebar at the active folder's tag options. Selections are deliberately kept:
        a code the new taxonomy doesn't define is still a perfectly good filter (it just moves to
        the 'foreign' styling) — the window clears them outright on a folder switch instead."""
        self._tax = tax
        self._type_names = dict(types)
        self._category_names = dict(categories)

    def refresh(self, notes: list[Note]) -> None:
        """Recount every option against [notes] — the folder's notes after the search box but
        before the filters — and mirror the state into the widgets. Called on every reload."""
        self._counts = F.compute(notes, self.state, self._tax)
        if not self._month_pinned:
            self._jump_to_latest_activity()
        self._suppress = True
        try:
            self._refresh_rows()
            self._rebuild_days()
            self._refresh_header()
        finally:
            self._suppress = False

    def _jump_to_latest_activity(self) -> None:
        """On the first refresh, open the calendar on the most recent month that has any notes —
        a folder last touched in March shouldn't greet you with an empty current month. Only until
        the user navigates the calendar themselves, after which their choice stands."""
        self._month_pinned = True
        days = self._counts.updated_days if self.state.date_field == F.BY_UPDATED \
            else self._counts.created_days
        if days and max(days) < _first_of_month(date.today()):
            self._month = _first_of_month(max(days))

    def _refresh_rows(self) -> None:
        counts = self._counts
        for dim, table in ((F.DIM_PRIORITY, counts.priorities), (F.DIM_TYPE, counts.types),
                           (F.DIM_CATEGORY, counts.categories)):
            keys = list(table)
            self._sync_rows(dim, keys,
                            lambda k, d=dim: self._label_for(d, k),
                            lambda k, d=dim: self._tooltip_for(d, k))
            selection = self.state.selection(dim)
            for key, (button, count) in self._rows[dim].items():
                n = table.get(key, 0)
                count.set_text(str(n))
                button.set_active(key in selection)
                # An option no remaining note could match is dead weight — grey it out, unless it's
                # the one currently selected (which must stay clickable to switch off).
                button.set_sensitive(n > 0 or key in selection)

        for dim, table, picked in ((F.DIM_NONSTANDARD, counts.nonstandard, self.state.nonstandard),
                                   (F.DIM_TAXONOMY, counts.taxonomy, self.state.taxonomy)):
            for mode, (button, count) in self._rows[dim].items():
                count.set_text(str(table.get(mode, 0)))
                button.set_active(picked == mode)

        self._sync_tag_cloud(counts.tags)

        for field, button in self._metric_buttons.items():
            button.set_active(self.state.date_field == field)

    def _refresh_header(self) -> None:
        counts = self._counts
        active = self.state.active()
        self.shown_label.set_markup(
            f'<b>{counts.shown}</b>/{counts.total}' if active else f'{counts.total}')
        self.shown_label.set_tooltip_text(
            f"{counts.shown} of {counts.total} notes pass the filters" if active
            else f"{counts.total} notes — no filters set")
        self.clear_button.set_sensitive(active)
        self.clear_days_button.set_sensitive(bool(self.state.days))

        for key, (_chevron, _content, badge) in self._sections.items():
            dim = F.DIM_DAY if key == SEC_CALENDAR else key
            picked = self.state.active_count(dim)
            badge.set_text(str(picked) if picked else "")

        picked_total = sum(self.state.active_count(d) for d in
                           (F.DIM_TYPE, F.DIM_CATEGORY, F.DIM_PRIORITY, F.DIM_DAY,
                            F.DIM_NONSTANDARD, F.DIM_TAXONOMY, F.DIM_TAG))
        self.rail_badge.set_text(f"FILTERS {picked_total}" if picked_total else "FILTERS")

    # --------------------------------------------------------------- plumbing

    def _reassert(self, button: Gtk.ToggleButton) -> None:
        """Put a radio-style toggle back on after a click that would have switched it off."""
        self._suppress = True
        button.set_active(True)
        self._suppress = False

    def _clear_all(self) -> None:
        if not self.state.active():
            return
        self.state.clear()
        self._day_anchor = None
        self._emit()

    def _emit(self) -> None:
        """Hand back to the window, which re-renders the board and calls refresh() straight back."""
        GLib.idle_add(self._on_changed)
