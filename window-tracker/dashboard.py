#!/usr/bin/env python3
"""The terminal dashboard.

Run it:                          python3 dashboard.py
Export an image of it:           python3 dashboard.py --svg out.svg

Both columns read real data: the left from the tracker's CSV for today plus a
live window count, the right from file_watch's snapshot.

The focus/neutral/distraction split is NOT shown, because the classifier that
would produce it does not exist yet (see DASHBOARD-PROPOSAL.md). Everything
here is measured; nothing is a placeholder. That matters more than it sounds:
the previous version hard-coded "25 open windows", which read as a live
breach for a day after it stopped being true.
"""
import json
import os
import re
import sys
from collections import defaultdict
from datetime import date, datetime, time as dtime, timedelta

from rich import box
from rich.console import Console, Group
from rich.panel import Panel
from rich.table import Table
from rich.text import Text

import window_tracker as wt

COL_W = 104                      # each column
WIDTH = COL_W * 2 + 4            # both columns plus the gutter

REPO_DIR      = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILE_SNAPSHOT = os.path.join(REPO_DIR, "file-watch", "data", "snapshot.json")

# ── top notes ─────────────────────────────────────────────────────────────
# knotes filenames carry their own metadata:
#     2026_07_22-102037~T~p3~Dev.md
#     └ datetime ────┘ └type └prio └tag
KNOTES_DIR    = os.path.expanduser("~/new/knotes")
TOP_PRIORITY  = "p4"
TOP_NOTES_MAX = 12          # 6 per column

# Deliberate order, not alphabetical: this is how you rank the kinds, so the
# list reads top-down in the order you would work it. A type not listed here
# sorts after all of these, grouped with its own kind rather than scattered.
TYPE_ORDER = "PTGJRWN"

# Matches file_watch's MIN_FILE_BYTES: a 0/1/2-byte note has no title to show
# and nothing to act on. It is debris to delete, not work to list.
MIN_NOTE_BYTES = 3
NOTE_RE = re.compile(
    r"^\d{4}_\d{2}_\d{2}-\d{6}~(?P<type>[^~]*)~(?P<prio>p\d+)~(?P<tag>[^~]*)\.md$")

# Arabic, Arabic Supplement, and the presentation-form blocks a shaper emits.
ARABIC_RE = re.compile(r"[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]")

# VTE reorders Arabic itself; see rtl(). Only set this False for a terminal
# that genuinely does not, or Arabic comes out reversed.
TERMINAL_DOES_BIDI = True

# A block ends at a break row, or at a gap this long between one session ending
# and the next starting — a gap that size means the tracker was stopped or the
# user was away below the idle threshold.
BLOCK_GAP_SEC = 120

FOCUS_TARGET_MIN   = 45      # what a good unbroken block looks like
WINDOW_LIMIT       = 12      # open windows before it counts as multitasking
APPS_PER_10MIN_LIM = 8
BREAK_INTERVAL_MIN = 90      # go this long without a break and you are told

FOCUS, NEUTRAL, DISTRACT = "green", "yellow", "red"
GREEN = "green"


def bar(frac: float, width: int = 30, color: str = "green") -> Text:
    frac = max(0.0, min(1.0, frac))
    filled = round(frac * width)
    t = Text()
    t.append("█" * filled, style=color)
    t.append("░" * (width - filled), style="grey37")
    return t


def sparkline(values, color="cyan") -> Text:
    blocks = "▁▂▃▄▅▆▇█"
    hi = max(values) or 1
    return Text("".join(blocks[min(7, int(v / hi * 7))] for v in values), style=color)


def kpi(label, value, sub=None, color="white"):
    t = Text()
    t.append(f"{label}\n", style="grey62")
    t.append(f"{value}\n", style=f"bold {color}")
    if sub:
        t.append(sub, style="grey50")
    return t


def hms(seconds: float) -> str:
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s = divmod(rem, 60)
    if h:
        return f"{h}h {m:02d}m"
    if m:
        return f"{m}m {s:02d}s"
    return f"{s}s"


# ──────────────────────────────────────────────
# Reading the tracker's own output
# ──────────────────────────────────────────────

def read_sessions(day: date | None = None) -> list[dict]:
    """Today's rows, parsed, oldest first. Missing file → no rows, not a crash:
    the dashboard has to render on a machine where the tracker never ran."""
    import csv

    day = day or date.today()
    path = wt.get_log_path(day)
    rows = []
    try:
        with open(path, encoding="utf-8") as f:
            for r in csv.DictReader(f):
                try:
                    start = datetime.combine(day, dtime.fromisoformat(r["start"]))
                    end = datetime.combine(day, dtime.fromisoformat(r["end"]))
                    dur = int(r["duration_sec"])
                except (ValueError, KeyError, TypeError):
                    continue
                rows.append({
                    "start": start, "end": end, "dur": dur,
                    "app": r.get("app", ""), "title": r.get("title", ""),
                    "is_break": r.get("app") == wt.BREAK_APP,
                })
    except OSError:
        return []
    rows.sort(key=lambda r: r["start"])
    return rows


def blocks(sessions: list[dict]) -> list[tuple[datetime, datetime]]:
    """Contiguous stretches of work, split by breaks and by long gaps.

    This is the unit the dashboard is about — not the session. A session ends
    every time you change window; a block only ends when you stop working.
    """
    out: list[list[datetime]] = []
    for s in sessions:
        if s["is_break"]:
            continue
        if out and (s["start"] - out[-1][1]).total_seconds() <= BLOCK_GAP_SEC:
            out[-1][1] = s["end"]
        else:
            out.append([s["start"], s["end"]])
    return [(a, b) for a, b in out]


def window_column(t: float = 0.0):
    """Left column — real data from the tracker's CSV for today."""
    sessions = read_sessions()
    now = datetime.now()

    if not sessions:
        return Group(Panel(
            Text("no sessions logged today\n\n"
                 "  systemctl --user status window-tracker",
                 style="grey50"),
            title="[bold]TIME[/]", title_align="left",
            border_style="grey30", box=box.ROUNDED, padding=(1, 2)))

    work = [s for s in sessions if not s["is_break"]]
    blks = blocks(sessions)

    active_sec = sum(s["dur"] for s in work)
    break_sec = sum(s["dur"] for s in sessions if s["is_break"])

    # ── now bar ────────────────────────────────────────────────────────────
    # The live window, not the last logged row: the tracker only writes a row
    # when a session ends, so the CSV never contains what you are doing now.
    live_app, live_title = wt.get_active_window_info()
    last_end = sessions[-1]["end"]

    # The current block runs from the end of the last break, or from the start
    # of the last block of work if no break has happened since. Measuring from
    # blks[-1][0] unconditionally was wrong: after a break the last block is
    # closed, and the dashboard claimed a 40-minute unbroken block six seconds
    # after the user got back.
    last_break_end = next((s["end"] for s in reversed(sessions) if s["is_break"]),
                          None)
    if blks and (last_break_end is None or last_break_end <= blks[-1][0]):
        cur_start = blks[-1][0]
    else:
        cur_start = last_break_end or now
    cur_sec = max(0.0, (now - cur_start).total_seconds())

    stale = (now - last_end).total_seconds() > 300
    if stale:
        cur_sec = 0.0

    nowbar = Text()
    nowbar.append("  NOW   " if not stale else " STALE  ",
                  style="bold white on dark_green" if not stale
                  else "bold white on grey30")
    nowbar.append(f"  {live_app or '—'}", style="bold white")
    nowbar.append(f"  —  {(live_title or '')[:60]}", style="grey70")
    if stale:
        nowbar.append(f"      last row {last_end:%H:%M}", style="grey50")
    else:
        nowbar.append(f"      {hms(cur_sec)}", style="bold white")

    # ── focus blocks ───────────────────────────────────────────────────────
    durs = [(b - a).total_seconds() for a, b in blks]
    longest = max(durs, default=0)
    mean = sum(durs) / len(durs) if durs else 0
    over_25 = sum(1 for d in durs if d >= 25 * 60)

    focus = Table.grid(padding=(0, 2))
    focus.add_column(width=26)
    focus.add_column(width=12, justify="right")
    focus.add_column()
    focus.add_row("current unbroken block",
                  Text(hms(cur_sec), style=f"bold {FOCUS}"),
                  Text.assemble(bar(cur_sec / (FOCUS_TARGET_MIN * 60), 34, FOCUS),
                                (f"  target {FOCUS_TARGET_MIN}m", "grey50")))
    focus.add_row("longest today",
                  Text(hms(longest), style="bold white"),
                  Text.assemble(bar(longest / (FOCUS_TARGET_MIN * 60), 34, "dark_green"),
                                (f"  of {len(durs)} blocks", "grey50")))
    focus.add_row("mean block",
                  Text(hms(mean), style="bold white"),
                  Text.assemble(bar(mean / (FOCUS_TARGET_MIN * 60), 34, "dark_green"),
                                ("  fragmentation", "grey50")))
    focus.add_row("blocks over 25m",
                  Text(str(over_25), style="bold white"),
                  Text(f"{'▇' * over_25 or '—'} of {len(durs)} today", style="grey50"))

    # ── multitasking ───────────────────────────────────────────────────────
    open_now = wt.count_open_windows()

    # Sessions *overlapping* the last 10 minutes, not sessions that started in
    # it, plus whatever is focused right now. Keying on the start time alone
    # reported "0 distinct apps" whenever the current session was long enough to
    # have begun more than 10 minutes ago — which is precisely the good case.
    cutoff = now - timedelta(seconds=600)
    recent = [s for s in work if s["end"] >= cutoff]
    apps_recent = {s["app"] for s in recent}
    if live_app and not stale:
        apps_recent.add(live_app)
    distinct = len(apps_recent)
    switches = len(recent)

    win_col = "red" if open_now > WINDOW_LIMIT else "white"
    app_col = "yellow" if distinct > APPS_PER_10MIN_LIM else "white"
    win = Table.grid(padding=(0, 2))
    win.add_column(width=26)
    win.add_column(width=12, justify="right")
    win.add_column()
    win.add_row("open windows",
                Text(str(open_now) if open_now >= 0 else "?", style=f"bold {win_col}"),
                Text.assemble(bar(open_now / (WINDOW_LIMIT * 2.5), 34, win_col),
                              (f"  limit {WINDOW_LIMIT}"
                               f"{' — BREACH' if open_now > WINDOW_LIMIT else ''}",
                               f"bold {win_col}" if open_now > WINDOW_LIMIT else "grey50")))
    win.add_row("distinct apps / 10 min",
                Text(str(distinct), style=f"bold {app_col}"),
                Text.assemble(bar(distinct / (APPS_PER_10MIN_LIM * 1.5), 34, app_col),
                              (f"  limit {APPS_PER_10MIN_LIM}", "grey50")))

    # ── today ──────────────────────────────────────────────────────────────
    # Active vs break only. The three-way focus/neutral/distraction split needs
    # the classifier; inventing it here is what made the old mock misleading.
    split = Table.grid(padding=(0, 2))
    split.add_column(width=26)
    split.add_column(width=12, justify="right")
    split.add_column()
    total = active_sec + break_sec or 1
    for name, val, col in (("active", active_sec, FOCUS),
                           ("break", break_sec, "grey50")):
        split.add_row(name, Text(hms(val), style=f"bold {col}"),
                      Text.assemble(bar(val / total, 34, col),
                                    (f"  {val / total:.0%}", "grey50")))
    split.add_row("sessions logged", Text(str(len(work)), style="bold white"),
                  Text(f"across {len(durs)} blocks", style="grey50"))

    # ── timeline ───────────────────────────────────────────────────────────
    # One cell per 5 minutes from the first session to now.
    day_start = sessions[0]["start"]
    cells = max(1, int((now - day_start).total_seconds() // 300) + 1)
    cells = min(cells, 96)
    span = (now - day_start).total_seconds() / cells
    state = ["·"] * cells
    for s in sessions:
        i0 = int((s["start"] - day_start).total_seconds() / span)
        i1 = int((s["end"] - day_start).total_seconds() / span)
        for i in range(max(0, i0), min(cells, i1 + 1)):
            if s["is_break"]:
                if state[i] == "·":
                    state[i] = "B"
            else:
                state[i] = "F"
    palette = {"F": FOCUS, "B": "grey30", "·": "grey19"}
    tl = Text()
    for ch in state:
        tl.append("▉", style=palette[ch])
    # Early in the day the bar is narrower than the two timestamps, so they run
    # together — fall back to an arrow instead of padding to a negative width.
    gap = cells - 10
    labels = (f"{day_start:%H:%M}" + " " * gap + f"{now:%H:%M}" if gap >= 1
              else f"{day_start:%H:%M} → {now:%H:%M}")
    timeline = Group(
        tl, Text(labels, style="grey50"),
        Text.assemble(("  ▉ active   ", FOCUS), ("▉ break   ", "grey30"),
                      ("▉ tracker down", "grey19")),
    )

    # ── thresholds ─────────────────────────────────────────────────────────
    since_break = (now - (last_break_end or day_start)).total_seconds()

    def state_cell(value, limit):
        if value > limit:
            return Text("BREACH", style="bold white on red")
        if value > limit * 0.85:
            return Text("WARN", style="bold yellow")
        return Text("ok", style="green")

    th = Table(box=box.SIMPLE_HEAD, show_edge=False, pad_edge=False,
               header_style="grey62")
    th.add_column("rule", width=30)
    th.add_column("value", width=10, justify="right")
    th.add_column("limit", width=8, justify="right")
    th.add_column("state", width=10)
    th.add_column("since", style="grey50")
    th.add_row("open windows", str(open_now), str(WINDOW_LIMIT),
               state_cell(open_now, WINDOW_LIMIT), "—")
    th.add_row("time since last break", hms(since_break),
               f"{BREAK_INTERVAL_MIN}m",
               state_cell(since_break, BREAK_INTERVAL_MIN * 60),
               f"{last_break_end:%H:%M}" if last_break_end else "none yet")
    th.add_row("distinct apps / 10 min", str(distinct), str(APPS_PER_10MIN_LIM),
               state_cell(distinct, APPS_PER_10MIN_LIM), "—")

    # ── switches sparkline + top apps ──────────────────────────────────────
    foot = Table.grid(padding=(0, 4))
    foot.add_column(width=48)
    foot.add_column()

    buckets: dict[int, int] = defaultdict(int)
    for s in work:
        buckets[int((s["start"] - day_start).total_seconds() // 600)] += 1
    hi_b = max(buckets or {0: 0}, default=0)
    series = [buckets[i] for i in range(max(0, hi_b - 17), hi_b + 1)] or [0]
    spark = Group(
        Text("window switches / 10 min", style="grey62"),
        Text.assemble(sparkline(series), (f"   now {switches}", "bold white")),
    )

    by_app: dict[str, int] = defaultdict(int)
    for s in work:
        by_app[s["app"]] += s["dur"]
    top = sorted(by_app.items(), key=lambda kv: -kv[1])[:5]
    hi_a = top[0][1] if top else 1
    tops = Table(box=None, show_header=False, pad_edge=False)
    tops.add_column(width=18)
    tops.add_column(width=9, justify="right")
    tops.add_column()
    for app, secs in top:
        tops.add_row(app[:18], Text(hms(secs), style="white"),
                     bar(secs / hi_a, 10, FOCUS))

    foot.add_row(spark, tops)

    return Group(
        Panel(nowbar, box=box.HEAVY, border_style="grey30", padding=(0, 0)),
        Panel(focus, title="[bold]FOCUS[/]", title_align="left",
              border_style="green", box=box.ROUNDED, padding=(1, 2)),
        Panel(win, title="[bold]MULTITASKING[/]", title_align="left",
              border_style="red" if open_now > WINDOW_LIMIT else "grey30",
              box=box.ROUNDED, padding=(1, 2)),
        Panel(split,
              title=f"[bold]TODAY[/]  active {hms(active_sec)} · break {hms(break_sec)}",
              title_align="left", border_style="grey30", box=box.ROUNDED,
              padding=(1, 2)),
        Panel(timeline, title="[bold]TIMELINE[/]", title_align="left",
              border_style="grey30", box=box.ROUNDED, padding=(1, 2)),
        Panel(th, title="[bold]THRESHOLDS[/]", title_align="left",
              border_style="yellow", box=box.ROUNDED, padding=(0, 2)),
        Panel(foot, border_style="grey30", box=box.ROUNDED, padding=(1, 2)),
    )


def rtl(text: str) -> str:
    """Arabic is passed through untouched, on purpose.

    VTE — so tilix, gnome-terminal, xfce4-terminal — implements the Unicode
    bidi algorithm itself, and hands Arabic to the font already reordered and
    shaped. Running python-bidi's get_display over it first reverses a line
    that was about to be reversed again, and what lands on screen is garbled:

        raw          مراجعة عقد الخدمة قبل نهاية الشهر     <- correct
        get_display  رهشلا ةياهن لبق ةمدخلا دقع ةعجارم     <- backwards

    Checked by printing both in a real tilix window and reading the result,
    which is the only way to settle it — the library is not wrong, the
    assumption that the terminal is passive was.

    Set TERMINAL_DOES_BIDI = False for a terminal that really is passive (some
    minimal emulators, and most SSH sessions into ancient boxes); then this
    reorders, and the alignment below still applies either way.
    """
    if TERMINAL_DOES_BIDI or not ARABIC_RE.search(text):
        return text
    try:
        from bidi import get_display
    except ImportError:
        try:
            from bidi.algorithm import get_display      # older python-bidi
        except ImportError:
            return text                                 # unreordered beats crashing
    try:
        return get_display(text)
    except Exception:                                    # noqa: BLE001
        return text


def first_line(path: str, limit: int = 62) -> str:
    """The note's own title: its first non-empty line.

    An empty file and one that cannot be opened are reported differently on
    purpose — "(unreadable)" on a note that is merely blank sends you hunting
    for a permissions problem that does not exist.
    """
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip().lstrip("#").strip()
                if line:
                    return line[:limit]
    except OSError:
        return "(unreadable)"
    return "(empty)"


def type_rank(kind: str) -> tuple:
    """Position in TYPE_ORDER; anything unrecognised goes last, still grouped."""
    k = (kind or "").upper()
    i = TYPE_ORDER.find(k) if len(k) == 1 else -1
    return (i, "") if i >= 0 else (len(TYPE_ORDER), k)


def top_notes() -> list[tuple[str, str]]:
    """(type, title) for every visible priority-TOP_PRIORITY note, by type.

    Walks recursively but never into a hidden directory: knotes keeps
    .imported, .trash and .deleted alongside the live notes, and those are
    exactly the ones that are not yours to act on.
    """
    found: list[tuple[str, str]] = []
    for dirpath, dirnames, filenames in os.walk(KNOTES_DIR):
        dirnames[:] = [d for d in dirnames if not d.startswith(".")]
        for name in filenames:
            if name.startswith("."):
                continue
            m = NOTE_RE.match(name)
            if not m or m.group("prio") != TOP_PRIORITY:
                continue
            full = os.path.join(dirpath, name)
            try:
                if os.path.getsize(full) < MIN_NOTE_BYTES:
                    continue
            except OSError:
                continue
            found.append((m.group("type"), first_line(full)))
    found.sort(key=lambda r: (type_rank(r[0]), r[1].lower()))
    return found


def top_notes_panel():
    notes = top_notes()
    if not notes:
        body = Text(f"no {TOP_PRIORITY} notes in {os.path.basename(KNOTES_DIR)}",
                    style="grey42")
    else:
        shown = notes[:TOP_NOTES_MAX]
        # Two columns: the panel is ~98 wide and a note title is nowhere near
        # that, so one column would waste half the row and push the list down
        # the screen for no reason. Numbering runs down the left column and
        # continues in the right, the way a printed list reads.
        half = (len(shown) + 1) // 2
        left, right = shown[:half], shown[half:]

        body = Table.grid(padding=(0, 1))
        for _ in range(2):
            body.add_column(width=3, justify="right")   # 1, 2, 3
            body.add_column(width=2)                    # type
            body.add_column(width=39)                   # title
        body.add_column(width=2)                        # gutter between halves

        def cells(idx, entry):
            if entry is None:
                return [Text(""), Text(""), Text("")]
            kind, title = entry
            # A right-to-left line is right-aligned as well as reordered:
            # left-aligning it puts the start of the sentence in the middle of
            # the column, which is not where the eye looks for it.
            is_rtl = bool(ARABIC_RE.search(title))
            return [Text(f"{idx}.", style="grey50"),
                    Text(kind, style=NEUTRAL),
                    Text(rtl(title), style="white",
                         justify="right" if is_rtl else "left")]

        for row in range(half):
            a = cells(row + 1, left[row] if row < len(left) else None)
            b = cells(half + row + 1, right[row] if row < len(right) else None)
            body.add_row(*a[:2], a[2], *b[:2], b[2], Text(""))

        if len(notes) > TOP_NOTES_MAX:
            body.add_row("", "", Text(f"+{len(notes) - TOP_NOTES_MAX} more",
                                      style="grey42"), "", "", "", "")
    return Panel(body, title=f"[bold]TOP NOTES[/]  {TOP_PRIORITY}",
                 title_align="left", border_style="grey30",
                 box=box.ROUNDED, padding=(1, 2))


def file_column():
    """Right column — REAL data from file_watch's snapshot, when present."""
    try:
        with open(FILE_SNAPSHOT, encoding="utf-8") as f:
            s = json.load(f)["summary"]
    except (OSError, KeyError, json.JSONDecodeError):
        return Panel(Text("no snapshot yet — run:\n"
                          f"  python3 {REPO_DIR}/file-watch/file_watch.py --once",
                          style="grey50"),
                     title="[bold]FILES[/]", title_align="left",
                     border_style="grey30", box=box.ROUNDED, padding=(1, 2))

    k, buckets = s["kinds"], s["untouched_by_age"]
    backlog = sum(buckets.values())
    per_day = s.get("created_per_day", {})
    today_key = date.today().isoformat()

    # ── header ────────────────────────────────────────────────────────────
    head = Text()
    head.append("  WATCHING  ", style="bold white on grey23")
    head.append(f"  {s['watch_dir']}", style="bold white")
    head.append(f"      {s['total_files']} files", style="grey70")

    # ── the number that should create urgency ─────────────────────────────
    # Judged as a share of the folder, not as an absolute count. The old
    # thresholds (red over 50, bar scaled to 200) were read off a 225-file
    # folder that was 95% unreviewed; against 47 notes they made 6 outstanding
    # — a good state — render in alarm colours, and a bar that was red whatever
    # the value said nothing at all.
    total = max(1, s["total_files"])
    share = backlog / total
    if share <= 0.25:
        backlog_col = GREEN
    elif share <= 0.50:
        backlog_col = NEUTRAL
    else:
        backlog_col = DISTRACT

    oldest = s.get("oldest_untouched")
    urgent = Table.grid(padding=(0, 2))
    urgent.add_column(width=26)
    urgent.add_column(width=12, justify="right")
    urgent.add_column()
    urgent.add_row("unreviewed backlog",
                   Text(str(backlog), style=f"bold {backlog_col}"),
                   Text.assemble(bar(share, 34, backlog_col),
                                 (f"  {share:.0%} of {s['total_files']} files",
                                  "grey50")))
    urgent.add_row("settle rate",
                   Text(f"{s['settle_rate']}%",
                        style="bold green" if s["settle_rate"] >= 50 else "bold red"),
                   Text.assemble(bar(s["settle_rate"] / 100, 34,
                                     GREEN if s["settle_rate"] >= 50 else "red"),
                                 ("  edited after arriving", "grey50")))
    urgent.add_row("created today",
                   Text(str(per_day.get(today_key, 0)), style="bold white"),
                   Text(f"{k['revisited']} revisited all-time", style="grey50"))

    # ── backlog by age — the guilt gradient ───────────────────────────────
    ages = Table.grid(padding=(0, 2))
    ages.add_column(width=26)
    ages.add_column(width=12, justify="right")
    ages.add_column()
    hi = max(max(buckets.values()), 1)
    for label, colour in (("today", GREEN), ("2-7d", NEUTRAL),
                          ("8-30d", "dark_orange"), ("30d+", DISTRACT)):
        n = buckets[label]
        ages.add_row(f"untouched · {label}",
                     Text(str(n), style=f"bold {colour}" if n else "grey42"),
                     bar(n / hi, 34, colour) if n else Text("—", style="grey30"))

    # ── name one file: a count does not make you act, a name does ─────────
    if oldest:
        pick = Text()
        pick.append("REVIEW THIS NEXT\n", style="bold grey62")
        pick.append(f"{oldest['path']}\n", style="bold white")
        pick.append(f"sitting untouched for {oldest['age_days']}d",
                    style=DISTRACT if oldest["age_days"] > 7 else "grey50")
    else:
        pick = Text("nothing untouched — inbox clear", style=f"bold {GREEN}")

    # ── intake per day ────────────────────────────────────────────────────
    days = sorted(per_day.items())[-10:]
    hi_d = max((n for _, n in days), default=1)
    intake = Table.grid(padding=(0, 2))
    intake.add_column(width=14)
    intake.add_column(width=6, justify="right")
    intake.add_column()
    for d, n in days:
        intake.add_row(d, Text(str(n), style="bold white"),
                       bar(n / hi_d, 40, "cyan"))

    return Group(
        Panel(head, box=box.HEAVY, border_style="grey30", padding=(0, 0)),
        Panel(urgent, title="[bold]FILE REVIEW[/]", title_align="left",
              border_style=backlog_col if share > 0.25 else "grey30",
              box=box.ROUNDED, padding=(1, 2)),
        Panel(ages, title="[bold]BACKLOG BY AGE[/]", title_align="left",
              border_style="grey30", box=box.ROUNDED, padding=(1, 2)),
        Panel(pick, border_style=DISTRACT, box=box.ROUNDED, padding=(1, 2)),
        Panel(intake, title="[bold]CREATED PER DAY[/]", title_align="left",
              border_style="grey30", box=box.ROUNDED, padding=(1, 2)),
        top_notes_panel(),
    )


def build(t: float = 0.0):
    """Render one frame. `t` is unused: every number here is read fresh from the
    tracker's CSV and from the live window list, so a redraw is already current.
    It is kept because --live passes it."""
    cols = Table.grid(padding=(0, 2))
    cols.add_column(width=COL_W)
    cols.add_column(width=COL_W)
    cols.add_row(window_column(t), file_column())
    return cols


if __name__ == "__main__":
    if "--svg" in sys.argv:
        out = sys.argv[sys.argv.index("--svg") + 1]
        c = Console(record=True, width=WIDTH)
        c.print(build())
        c.save_svg(out, title="dashboard — time + files")
        print(f"wrote {out}")

    elif "--live" in sys.argv:
        # screen=True uses the terminal's alternate buffer, like vim/less: the
        # dashboard owns the screen with no leftover shell output above it, and
        # the previous contents are restored on exit.
        import time
        from rich.live import Live

        console = Console(width=WIDTH)
        t0 = time.monotonic()
        try:
            with Live(build(), console=console, refresh_per_second=4,
                      screen=True) as live:
                while True:
                    time.sleep(0.25)
                    live.update(build(time.monotonic() - t0))
        except KeyboardInterrupt:
            console.print("[grey50]stopped[/]")

    else:
        console = Console(width=WIDTH)
        if "--no-clear" not in sys.argv:
            console.clear()
        console.print(build())
