"""Note filtering — the pure-Python model behind the left filter sidebar.

Like ``notes.py`` this module is deliberately free of GUI code. It answers two questions:

* **which** notes the board should show (:meth:`FilterState.matches`), and
* **how many** notes each individual filter option would match (:func:`compute`),

so the sidebar only has to draw the numbers it is handed. Keeping the two apart means the
counting rules (below) are readable in one place instead of scattered through widget code.

Counting rule — every option's count is computed with all the *other* dimensions filtered but
its own dimension ignored ("faceted" counting). So the number next to *Type W* always reads
"how many notes would be on the board if I also picked W", never zero just because some other
type is currently selected. An empty selection set means "no constraint on this dimension".

Dates — a note's **creation** date is read from its file-name timestamp, never from the file's
own timestamps: retagging a note rewrites and renames the file, which would otherwise drag it
to "today" and wreck the heat map. Its **updated** date is the file's mtime, which is exactly
what that bump means. See :meth:`Taxonomy.created_on` / :meth:`Taxonomy.updated_on`.
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass, field
from datetime import date

from notes import Note, is_note_file, parse_created

# The "non-standard notes" tri-state (see is_standard for what counts as non-standard).
NS_INCLUDE = "include"   # show everything — the default
NS_HIDE = "hide"         # standard notes only
NS_ONLY = "only"         # the clean-up queue: non-standard notes only
NS_MODES = (NS_INCLUDE, NS_HIDE, NS_ONLY)

# The "bad taxonomy" tri-state (see taxonomy_issues). Deliberately a SEPARATE dimension from
# non-standard: that one is about the file NAME's shape, this one about its VALUES. A note can be
# perfectly well-formed and still be tagged with a type the folder has never defined, and those two
# clean-up queues want different fixes — rename vs. retag.
TX_INCLUDE = "include"   # show everything — the default
TX_HIDE = "hide"         # only notes whose every tag is one the folder defines
TX_ONLY = "only"         # the retag queue: well-formed names carrying undefined tag values
TX_MODES = (TX_INCLUDE, TX_HIDE, TX_ONLY)

# Which of a note's two dates the calendar heat map colours and a clicked day filters on.
BY_CREATED = "created"
BY_UPDATED = "updated"
DATE_FIELDS = (BY_CREATED, BY_UPDATED)

# Filter dimensions. Also used as the ``skip`` key when counting a facet's own options.
DIM_TYPE = "type"
DIM_CATEGORY = "category"
DIM_PRIORITY = "priority"
DIM_DAY = "day"
DIM_NONSTANDARD = "nonstandard"
DIM_TAXONOMY = "taxonomy"
DIM_TAG = "tag"

# Inline `#hashtag` in a note's BODY. Distinct from type/category, which are single codes encoded in
# the file name and drawn from a configured list: a hashtag is free-form, a note can carry any number
# of them, and nothing has to be declared before it is used. That is why they get a cloud rather than
# a facet list — the value set is open-ended and interesting mainly by frequency.
#
# `\w` under Python 3 is Unicode-aware, so Arabic hashtags work without a separate alternative. The
# leading (?:^|[^\w#/]) keeps `#` from matching inside a URL fragment, a C preprocessor line, or a
# Markdown heading (`## Heading` — the second # fails the lookbehind class).
#
# Dots and hyphens are allowed after the first character because the tag bank's vocabulary uses
# them — `A.quran` is one tag, not `A`, and `آية-اليوم` is one tag too. A trailing run of them is
# trimmed off by the caller: `#A.quran.` at the end of a sentence must not swallow the full stop.
TAG_RE = re.compile(r"(?:^|[^\w#/])#(\w[\w.\-]{0,48})", re.UNICODE | re.MULTILINE)


def tags_of(note: Note) -> set[str]:
    """The distinct hashtags in a note's body, lower-cased so `#Work` and `#work` are one tag."""
    return {tag for tag in (m.group(1).rstrip(".-").lower()
                            for m in TAG_RE.finditer(note.content or "")) if tag}


def is_standard(note: Note) -> bool:
    """True when the item's FILE NAME is the canonical note format —
    ``<timestamp>~<type>~p<priority>~<subject>.md``.

    That's the whole rule, and it's deliberately the same one ``notes.is_note_file`` applies when
    deciding what counts as a note at all, so "non-standard" here means exactly the set that
    ``NoteRepository.list_unrecognized()`` surfaces: the visible files and folders sitting in the
    note folder that aren't notes yet (hidden dotfiles, companion ``.d/`` folders and the app's own
    ``.deleted``/``.merged``/``.imported`` housekeeping folders never reach this point).

    Note what this is NOT: it says nothing about whether a note's tags are ones the folder defines.
    A note named ``…~T~p3~Dev.md`` is perfectly standard even where ``Dev`` isn't a configured
    category — that case shows up as a *foreign code* row in the type/category facets instead.
    """
    return is_note_file(os.path.basename(note.path.rstrip(os.sep + "/")))


def _today_if_unsettled() -> date:
    """The day a non-standard item is filed under: **today**, on both date metrics.

    It has no date of its own to be placed by — there's no file-name timestamp to read, and its
    mtime is whatever the copy/sync/download that dropped it there happened to set, which may be
    months old. Filed on that mtime it would sit on a day nobody visits, and the board's default
    view (today, see Settings.start_filter_today) would never surface it.

    Pinning it to today makes it outstanding work that keeps presenting itself: it stays on today,
    every day, until `NoteRepository.promote` settles it into a real note — at which point it gets a
    genuine timestamp and moves to its rightful day.
    """
    return date.today()


@dataclass(frozen=True)
class Taxonomy:
    """The active folder's tag options, as the filters need to see them.

    A flat snapshot of the per-folder :class:`~config.RepoConfig` — rebuilt whenever that
    changes (a folder switch, a settings edit, a backend taxonomy sync), so "is this note's
    category one we actually define?" is always answered against the current folder.
    """

    types: tuple[str, ...] = ()
    categories: tuple[str, ...] = ()
    priorities: tuple[int, ...] = ()
    timestamp_format: str = "%Y_%m_%d-%H%M%S"

    @classmethod
    def from_repo_config(cls, cfg, timestamp_format: str) -> "Taxonomy":
        return cls(
            types=tuple(code for code, _name in cfg.types),
            categories=tuple(code for code, _name in cfg.categories),
            priorities=tuple(cfg.priorities()),
            timestamp_format=timestamp_format,
        )

    def defines(self, dim: str, key) -> bool:
        """Is this tag code one the folder actually configures, or one only its notes use? Drives
        the *foreign code* styling in the facet lists — which is a separate question from whether
        the note's file name is standard (see :func:`is_standard`)."""
        if key is None:
            return False
        return key in {DIM_TYPE: self.types, DIM_CATEGORY: self.categories,
                       DIM_PRIORITY: self.priorities}[dim]

    def created_on(self, note: Note) -> date:
        """The day the note was written, from its FILE-NAME timestamp — stable across metadata
        edits (which rewrite the file and would otherwise move it to today). Falls back to the
        file's mtime when the name doesn't parse; see notes.parse_created.

        A non-standard item is pinned to TODAY — see _today_if_unsettled."""
        if not is_standard(note):
            return _today_if_unsettled()
        return date.fromtimestamp(parse_created(note.timestamp, self.timestamp_format, note.mtime))

    @staticmethod
    def updated_on(note: Note) -> date:
        """The day the note's file was last written — the "revisited it" signal. A non-standard item
        is pinned to TODAY on this metric too, so the Created/Updated switch can't hide it."""
        if not is_standard(note):
            return _today_if_unsettled()
        return date.fromtimestamp(note.mtime)

    def day_of(self, note: Note, date_field: str) -> date:
        return self.updated_on(note) if date_field == BY_UPDATED else self.created_on(note)

    def issues(self, note: Note) -> tuple[str, ...]:
        """Which of the note's tag dimensions carry a value this folder doesn't define — the
        dimensions behind the *bad taxonomy* filter. See :func:`taxonomy_issues`."""
        return taxonomy_issues(note, self)


def taxonomy_issues(note: Note, tax: Taxonomy) -> tuple[str, ...]:
    """The dimensions where [note] is tagged with a value [tax] doesn't define — e.g. ``("type",)``
    for ``2026_08_08-101500~Y~p3~A.md`` in a folder with no type ``Y``. Empty means the note is
    fine.

    Two exclusions, both deliberate:

    * **Non-standard names are never reported.** They have no parsed tags to judge; their problem
      is the name itself, and that is what the non-standard filter is for. Reporting them here too
      would put the same files in both clean-up queues and make each one's count a lie.
    * **A blank tag is not a bad tag.** ``…~~p3~.md`` is a legal name (see NoteFiles.buildName) and
      an empty type/category is the "untagged" bucket, which already has its own row in the type and
      category facets. Only a value that is *present* and undefined counts.
    """
    if not is_standard(note):
        return ()
    bad = []
    for dim, value in ((DIM_TYPE, note.type), (DIM_PRIORITY, note.priority),
                       (DIM_CATEGORY, note.category)):
        if value is None or value == "":
            continue
        if not tax.defines(dim, value):
            bad.append(dim)
    return tuple(bad)


@dataclass
class FilterState:
    """What the user has picked in the sidebar. An empty set = that dimension is unconstrained.

    ``None`` is a real, selectable key in the tag sets: it stands for the "untyped" /
    "uncategorized" / "no priority" bucket, which is how ``list_unrecognized()`` placeholders and
    blank-tagged notes show up.
    """

    types: set[str | None] = field(default_factory=set)
    categories: set[str | None] = field(default_factory=set)
    priorities: set[int | None] = field(default_factory=set)
    days: set[date] = field(default_factory=set)
    tags: set[str] = field(default_factory=set)
    nonstandard: str = NS_INCLUDE
    taxonomy: str = TX_INCLUDE
    date_field: str = BY_CREATED

    def selection(self, dim: str) -> set:
        return {DIM_TYPE: self.types, DIM_CATEGORY: self.categories,
                DIM_PRIORITY: self.priorities, DIM_DAY: self.days, DIM_TAG: self.tags}[dim]

    def active(self) -> bool:
        """True when anything at all is filtering the board (the date field alone doesn't)."""
        return bool(self.types or self.categories or self.priorities or self.days or self.tags
                    or self.nonstandard != NS_INCLUDE or self.taxonomy != TX_INCLUDE)

    def active_count(self, dim: str) -> int:
        """How many options are picked in one dimension — the little badge on its section header."""
        if dim == DIM_NONSTANDARD:
            return 0 if self.nonstandard == NS_INCLUDE else 1
        if dim == DIM_TAXONOMY:
            return 0 if self.taxonomy == TX_INCLUDE else 1
        return len(self.selection(dim))

    def clear(self) -> None:
        for dim in (DIM_TYPE, DIM_CATEGORY, DIM_PRIORITY, DIM_DAY, DIM_TAG):
            self.selection(dim).clear()
        self.nonstandard = NS_INCLUDE
        self.taxonomy = TX_INCLUDE

    def set_option(self, dim: str, key, on: bool) -> None:
        sel = self.selection(dim)
        sel.add(key) if on else sel.discard(key)

    def select_range(self, first: date, last: date) -> None:
        """Replace the day selection with an inclusive range (shift-click in the calendar)."""
        if last < first:
            first, last = last, first
        self.days = {date.fromordinal(o) for o in range(first.toordinal(), last.toordinal() + 1)}

    def matches(self, note: Note, tax: Taxonomy, skip: str | None = None) -> bool:
        """Does [note] pass the filters? ``skip`` ignores one dimension, which is what makes a
        facet's own counts stay meaningful while that facet has a selection (see module docstring)."""
        if skip != DIM_TYPE and self.types and note.type not in self.types:
            return False
        if skip != DIM_CATEGORY and self.categories and note.category not in self.categories:
            return False
        if skip != DIM_PRIORITY and self.priorities and note.priority not in self.priorities:
            return False
        if skip != DIM_NONSTANDARD and self.nonstandard != NS_INCLUDE:
            standard = is_standard(note)
            if standard if self.nonstandard == NS_ONLY else not standard:
                return False
        # Picking two tags means "notes carrying BOTH", unlike the type/category facets where two
        # picks widen. A tag set is open-ended and a note can hold many, so intersecting is what
        # makes a cloud useful for narrowing down; unioning would only ever grow the result.
        if skip != DIM_TAG and self.tags and not self.tags.issubset(tags_of(note)):
            return False
        if skip != DIM_TAXONOMY and self.taxonomy != TX_INCLUDE:
            bad = bool(taxonomy_issues(note, tax))
            if (not bad) if self.taxonomy == TX_ONLY else bad:
                return False
        if skip != DIM_DAY and self.days and tax.day_of(note, self.date_field) not in self.days:
            return False
        return True


@dataclass
class FacetCounts:
    """Every number the sidebar draws, counted in one pass over the notes.

    The four tag dicts are ordered: the folder's own options first (in the order it defines
    them), then any *foreign* code found in the notes but not defined here, then the ``None``
    bucket — so the sidebar can render them top-to-bottom without re-deciding the order.
    """

    types: dict
    categories: dict
    priorities: dict
    nonstandard: dict
    taxonomy: dict
    tags: dict      # tag -> matching notes, most frequent first (the cloud)
    created_days: dict
    updated_days: dict
    total: int      # notes handed in (i.e. after the search box, before the filters)
    shown: int      # notes that pass every filter — what the board will render


def _ordered_counts(notes: list[Note], state: FilterState, tax: Taxonomy,
                    dim: str, key_of, defined: tuple) -> dict:
    """Count one tag dimension: the folder's defined options (always listed, even at 0) followed
    by any foreign key the notes actually use, ``None`` last."""
    counts = {key: 0 for key in defined}
    foreign: dict = {}
    for note in notes:
        if not state.matches(note, tax, skip=dim):
            continue
        key = key_of(note)
        if key in counts:
            counts[key] += 1
        else:
            foreign[key] = foreign.get(key, 0) + 1
    for key in sorted(foreign, key=lambda k: (k is None, str(k))):
        counts[key] = foreign[key]
    return counts


def compute(notes: list[Note], state: FilterState, tax: Taxonomy) -> FacetCounts:
    """Count every filter option in [notes] under the current [state] (see the module docstring
    for the faceting rule). [notes] is the folder's notes after the search box but before the
    filters, so the sidebar's numbers follow the search and the board follows both."""
    nonstandard = {NS_INCLUDE: 0, NS_HIDE: 0, NS_ONLY: 0}
    taxonomy = {TX_INCLUDE: 0, TX_HIDE: 0, TX_ONLY: 0}
    tag_counts: dict[str, int] = {}
    created_days: dict[date, int] = {}
    updated_days: dict[date, int] = {}
    shown = 0
    for note in notes:
        if state.matches(note, tax, skip=DIM_NONSTANDARD):
            nonstandard[NS_INCLUDE] += 1
            nonstandard[NS_HIDE if is_standard(note) else NS_ONLY] += 1
        if state.matches(note, tax, skip=DIM_TAXONOMY):
            taxonomy[TX_INCLUDE] += 1
            taxonomy[TX_ONLY if taxonomy_issues(note, tax) else TX_HIDE] += 1
        # Faceted like the rest: a tag's count is "how many notes would remain if I also picked
        # this one", so an already-picked tag doesn't zero out its neighbours.
        if state.matches(note, tax, skip=DIM_TAG):
            for tag in tags_of(note):
                tag_counts[tag] = tag_counts.get(tag, 0) + 1
        # The heat map is a facet like any other: it shows the activity that the *rest* of the
        # filters leave standing, so narrowing to e.g. priority 4 recolours the calendar.
        if state.matches(note, tax, skip=DIM_DAY):
            created = tax.created_on(note)
            updated = tax.updated_on(note)
            created_days[created] = created_days.get(created, 0) + 1
            updated_days[updated] = updated_days.get(updated, 0) + 1
        if state.matches(note, tax):
            shown += 1
    return FacetCounts(
        types=_ordered_counts(notes, state, tax, DIM_TYPE, lambda n: n.type, tax.types),
        categories=_ordered_counts(notes, state, tax, DIM_CATEGORY, lambda n: n.category,
                                   tax.categories),
        priorities=_ordered_counts(notes, state, tax, DIM_PRIORITY, lambda n: n.priority,
                                   tax.priorities),
        nonstandard=nonstandard,
        taxonomy=taxonomy,
        # Most-used first, then alphabetically, so the cloud has a stable reading order and a
        # newly-typed tag doesn't reshuffle the ones around it on every keystroke. A picked tag is
        # always present even at 0, or deselecting it would be impossible once it left the cloud.
        tags=dict(sorted(
            ({**{t: 0 for t in state.tags}, **tag_counts}).items(),
            key=lambda kv: (-kv[1], kv[0]))),
        created_days=created_days,
        updated_days=updated_days,
        total=len(notes),
        shown=shown,
    )


def heat_level(count: int, peak: int, levels: int = 4) -> int:
    """Bucket a day's note count into 0 (none) or 1…``levels`` shades, scaled to the busiest day
    of the month on show — so a quiet month still reads as a gradient instead of one faint wash."""
    if count <= 0:
        return 0
    if peak <= 1:
        return levels
    step = (count - 1) / (peak - 1)
    return min(levels, 1 + int(step * (levels - 1) + 0.999))
