#!/usr/bin/env python3

"""
Active Window Time Tracker for Linux Mint Cinnamon (X11)
Logs daily window usage to ~/shr/dev-test/scr-logs/YYYY-MM-DD.csv

Features:
  - Tracks active window (app name + title) with time spent
  - Detects inactivity after IDLE_TIMEOUT_SEC and records the away period as a
    "break" entry, back-dated to the moment activity actually stopped
  - Resumes tracking when the user returns
  - Optionally minimizes all windows on idle (MINIMIZE_ON_IDLE, off by default)
  - Flushes the in-progress session on exit (Ctrl+C, SIGTERM, systemd restart)
"""

import csv
import os
import re
import signal
import subprocess
import time
from datetime import datetime, date

import psutil

LOG_DIR          = os.path.expanduser("~/shr/dev-test/scr-logs")
POLL_INTERVAL    = 1        # seconds between checks
IDLE_TIMEOUT_SEC = 5 * 60   # 5 minutes of inactivity → break

# Going idle only records a break. Minimising every window on idle is kept
# available (it is a plausible escalation action for a distraction threshold)
# but is off by default: it fires during video calls and presentations, where
# it is genuinely destructive.
MINIMIZE_ON_IDLE = False

# A window change is only believed if the user has touched the keyboard or mouse
# within this many seconds. Beyond it, a focus change cannot have been initiated
# by the user — it is an app grabbing focus while they are away (SafeEyes,
# screensavers, updaters, notification popups).
#
# Without this guard those app-driven switches are logged as real sessions, and
# the break that is later back-dated to the moment input stopped overlaps them:
# the same wall-clock minutes get counted twice, once as active and once as
# break, which inflates active time and fragments the focus blocks.
FOCUS_CHANGE_GRACE_SEC = 10

# How long a window has to hold focus before it counts as a thing you switched
# to. Below this it is a glance, not a context switch: checking a file manager
# for three seconds and coming back is part of the activity you were already in.
#
# Without it the log thrashes — 2026-07-22 recorded 174 sessions in 32 minutes,
# 75 of them nemo, averaging under 4 seconds each, which makes every
# session-derived number (mean session, switches per 10 min) meaningless.
#
# A glance is NOT discarded, it is absorbed into the session in progress. That
# matters: dropping the rows instead would delete real minutes from the day and
# leave gaps that split one block of work into several.
MIN_SESSION_SEC = 10

BREAK_APP   = "break"
BREAK_TITLE = "Away from computer"

# Apps that take focus without the user choosing to work in them: break
# overlays, script dialogs, file-chooser portals, the desktop background. They
# are never the thing you are doing, but they are on top when they appear, so
# the tracker logged each one as a session — yesterday that was 9 file-roller
# sessions totalling 21 seconds and 7 safeeyes sessions, each one cutting a
# focus block in two.
#
# When one of these is focused the session in progress is simply held open,
# exactly as it is while the user is away (see FOCUS_CHANGE_GRACE_SEC). If they
# are genuinely gone the idle timeout still fires and back-dates a break over
# the held-open stretch, so nothing is over-counted.
#
# Matched against the process name for sessions and against WM_CLASS for the
# window count, lowercased on both sides.
IGNORED_APPS = {
    "safeeyes",                 # break-reminder overlay; steals focus by design
    "zenity",                   # one-off dialogs spawned by shell scripts
    "xdg-desktop-portal-gtk",   # the GTK file-chooser portal
    "file-roller",              # archive viewer — opened to peek, then closed
    "nemo-desktop",             # the desktop background itself
    "cinnamon-screensaver",
    "gnome-screensaver",
    "notify-osd",
    "xfce4-notifyd",
}

# A minimised window is still an open piece of work you have not finished with,
# and it is still in the window list, which is what "how many windows do I have
# open" means to the person reading the dashboard.
#
# This was False at first, on the theory that an iconified window is not
# competing for attention. It agreed with a hand count of 9 on the morning it
# was written — but only because a single window happened to be minimised just
# then. Later the same day four were, and the dashboard read 3 against a real 8.
# An attention metric that swings by 5 depending on how tidy the desktop is
# measures tidiness, not attention.
COUNT_MINIMIZED = True

CSV_HEADERS = ["date", "start", "end", "duration_sec", "duration_hms",
               "app", "title", "windows_open"]

# Many window titles carry an animated or counting component that changes every
# second or two: braille spinners in terminals, "(3)" unread badges, media
# timers. Keying a session on the raw title makes each tick a separate session,
# which shreds a 40-minute focus block into 2400 one-second rows and makes
# "longest unbroken focus" meaningless. Normalise those out before comparing.
SPINNER_RE  = re.compile(
    r"["
    r"⠀-⣿"      # braille — most CLI spinners
    r"✖✱✲✳✴✵✶✻✽✹✢"  # ✳ ✻ ✽ …
    r"◢-◥◐-◓◰-◳"  # quarter-circle / triangle spinners
    r"←-↓↻↺"                 # arrow spinners
    r"]+"
)
UNREAD_RE   = re.compile(r"^\(\d+\)\s*")          # "(3) Inbox"
MEDIA_TIME_RE = re.compile(r"^\d{1,2}:\d{2}(:\d{2})?\s*[-–|]\s*")  # "12:34 - video"


def normalize_title(title: str) -> str:
    """Strip the volatile parts of a window title used for session identity."""
    t = SPINNER_RE.sub("", title)
    t = UNREAD_RE.sub("", t)
    t = MEDIA_TIME_RE.sub("", t)
    return re.sub(r"\s+", " ", t).strip()


# Apps where every window is one activity, whatever it is spread across. This
# has two consequences, and they have to agree or the dashboard contradicts
# itself:
#
#   sessions — the title must NOT end the session, so hopping between terminal
#     windows stays one continuous stretch of work instead of being shredded
#     into fragments (see session_identity)
#   windows  — all of an app's windows count as ONE open window, because four
#     Tilix windows are one activity competing for attention, not four (see
#     count_open_windows)
#
# Everything not listed here splits on the title and counts per window — which
# is what you want for a browser, where each tab genuinely is a different task.
#
# Note this is about separate top-level windows. Splits and tabs *inside* one
# window were never counted separately: they are not X11 windows at all, so
# neither wmctrl nor xdotool can see them.
SAME_WORK_APPS = {
    "tilix", "gnome-terminal", "gnome-terminal-server",
    "xfce4-terminal", "konsole", "alacritty", "kitty", "xterm",
}


def session_identity(app: str, title: str) -> tuple[tuple, str]:
    """Return (identity, display_title) for the active window.

    `identity` decides where one session ends and the next begins.
    `display_title` is only what gets written to the log.
    """
    norm = normalize_title(title or "")
    if app in SAME_WORK_APPS:
        return (app, ""), norm      # all windows of this app == one work session
    return (app, norm), norm        # e.g. each browser tab is its own session


# "no candidate window", distinct from None, which is itself a legitimate
# observation meaning "no window is active". Conflating the two let a single
# failed xdotool call — a timeout, or the instant a window closes — match a
# stale candidate and end the session with no debounce at all.
NO_CANDIDATE = object()


class Shutdown(Exception):
    """Raised from a signal handler so the main loop can flush before exiting."""


def _on_signal(signum, _frame):
    raise Shutdown(signal.Signals(signum).name)


# ──────────────────────────────────────────────
# Window / idle helpers
# ──────────────────────────────────────────────

def get_idle_ms() -> int:
    """Milliseconds since the last mouse/keyboard event, via xprintidle."""
    try:
        out = subprocess.run(
            ["xprintidle"], capture_output=True, text=True, timeout=2
        ).stdout.strip()
        return int(out) if out.isdigit() else 0
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return 0


def minimize_all_windows() -> None:
    """Minimize (iconify) every open window using wmctrl + xdotool."""
    try:
        result = subprocess.run(
            ["wmctrl", "-l"], capture_output=True, text=True, timeout=3
        )
        for line in result.stdout.splitlines():
            parts = line.split()
            if parts:
                subprocess.run(
                    ["xdotool", "windowminimize", parts[0]],
                    capture_output=True, timeout=2
                )
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass


def _listed_windows() -> list[tuple[str, str]]:
    """(window_id, WM_CLASS) for every window the WM lists."""
    result = subprocess.run(
        ["wmctrl", "-lx"], capture_output=True, text=True, timeout=3
    )
    out = []
    for line in result.stdout.splitlines():
        parts = line.split(None, 3)          # id  desktop  WM_CLASS  host+title
        if len(parts) >= 3:
            out.append((parts[0], parts[2]))
    return out


def _is_countable(win_id: str, wm_class: str) -> bool:
    """Whether a listed window is one the user would call an open window.

    `wmctrl -l` lists everything the WM manages, which includes things nobody
    counts: the desktop background (one per monitor), modal preference sheets,
    and the overlays in IGNORED_APPS. Left unfiltered the count ran 4-6 high —
    enough to trip a multitasking threshold on its own.
    """
    if any(part.lower() in IGNORED_APPS for part in wm_class.split(".")):
        return False

    props = subprocess.run(
        ["xprop", "-id", win_id, "_NET_WM_WINDOW_TYPE", "_NET_WM_STATE"],
        capture_output=True, text=True, timeout=2
    ).stdout

    if "_NET_WM_WINDOW_TYPE_DESKTOP" in props:      # the desktop background
        return False
    if "_NET_WM_STATE_SKIP_TASKBAR" in props:       # dialogs the WM won't list
        return False
    if not COUNT_MINIMIZED and "_NET_WM_STATE_HIDDEN" in props:
        return False
    return True


def _same_work_key(wm_class: str) -> str | None:
    """The SAME_WORK_APPS bucket this window belongs to, if any."""
    return next((part.lower() for part in wm_class.split(".")
                 if part.lower() in SAME_WORK_APPS), None)


def count_open_windows() -> int:
    """Number of open activities the user would actually count.

    Not a raw window count. Windows are dropped if they are not really windows
    (see _is_countable), and every SAME_WORK_APPS app collapses to one however
    many windows it has open — four Tilix windows are one activity, and the
    session log already treats them that way.

    Sampled once per session rather than once per poll: this spawns one
    subprocess per window on top of the three already spawned every second.
    """
    try:
        collapsed: set[str] = set()
        count = 0
        for win_id, wm_class in _listed_windows():
            if not _is_countable(win_id, wm_class):
                continue
            bucket = _same_work_key(wm_class)
            if bucket:
                if bucket in collapsed:
                    continue
                collapsed.add(bucket)
            count += 1
        return count
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return -1


def get_active_window_info() -> tuple[str | None, str | None]:
    """Return (app_name, window_title) of the currently active window."""
    try:
        win_id = subprocess.run(
            ["xdotool", "getactivewindow"],
            capture_output=True, text=True, timeout=2
        ).stdout.strip()

        if not win_id:
            return None, None

        title = subprocess.run(
            ["xdotool", "getwindowname", win_id],
            capture_output=True, text=True, timeout=2
        ).stdout.strip()

        pid_out = subprocess.run(
            ["xdotool", "getwindowpid", win_id],
            capture_output=True, text=True, timeout=2
        ).stdout.strip()

        app_name = None
        if pid_out.isdigit():
            try:
                app_name = psutil.Process(int(pid_out)).name()
            except (psutil.NoSuchProcess, psutil.AccessDenied):
                pass

        return app_name or "unknown", title

    except (subprocess.TimeoutExpired, FileNotFoundError):
        return None, None


# ──────────────────────────────────────────────
# Logging helpers
# ──────────────────────────────────────────────

def get_log_path(for_date: date | None = None) -> str:
    os.makedirs(LOG_DIR, exist_ok=True)
    d = (for_date or date.today()).strftime("%Y-%m-%d")
    return os.path.join(LOG_DIR, f"{d}.csv")


def ensure_csv_header(log_path: str) -> None:
    """Write the CSV header only if the file is new/empty."""
    if not os.path.exists(log_path) or os.path.getsize(log_path) == 0:
        with open(log_path, "w", newline="", encoding="utf-8") as f:
            csv.writer(f).writerow(CSV_HEADERS)


def format_duration(seconds: float) -> str:
    seconds = int(seconds)
    h, rem = divmod(seconds, 3600)
    m, s   = divmod(rem, 60)
    if h: return f"{h}h {m}m {s}s"
    if m: return f"{m}m {s}s"
    return f"{s}s"


def append_log(
    log_path: str, app: str, title: str,
    start: datetime, end: datetime
) -> None:
    duration_sec = (end - start).total_seconds()
    # Not the session threshold — that is MIN_SESSION_SEC, enforced in the main
    # loop before a switch is ever believed. This only suppresses zero-length
    # rows, which a break or a shutdown landing just after a switch can produce.
    if duration_sec < 1:
        return

    ensure_csv_header(log_path)

    row = [
        start.strftime("%Y-%m-%d"),      # date
        start.strftime("%H:%M:%S"),      # start
        end.strftime("%H:%M:%S"),        # end
        int(duration_sec),               # duration_sec  ← easy to SUM/filter
        format_duration(duration_sec),   # duration_hms  ← human readable
        app,                             # app
        title,                           # title
        count_open_windows(),            # windows_open  ← multitasking signal
    ]

    with open(log_path, "a", newline="", encoding="utf-8") as f:
        csv.writer(f).writerow(row)

    print(f"{row[1]} → {row[2]}  [{row[4]}]  {app}  —  {title}", flush=True)


def flush_current(
    current_key: tuple | None,
    current_title: str,
    window_start: datetime | None,
    log_date: date,
    now: datetime,
) -> None:
    """Write the session in progress. `current_key` is identity; the title
    logged is `current_title`, which for SAME_WORK_APPS is the window the
    session began in."""
    if current_key and window_start:
        append_log(
            get_log_path(log_date),
            current_key[0], current_title,
            window_start, now,
        )


# ──────────────────────────────────────────────
# Main loop
# ──────────────────────────────────────────────

def main():
    print(f"Window tracker started. Logging to {LOG_DIR}/")
    print(f"Idle timeout: {IDLE_TIMEOUT_SEC // 60} min  |  Poll: {POLL_INTERVAL}s")
    print("Press Ctrl+C to stop.\n", flush=True)

    current_key:   tuple | None    = None
    current_title: str             = ""
    window_start:  datetime | None = None
    last_log_date: date            = date.today()
    on_break:      bool            = False

    # The window focus has moved to but which has not yet held it for
    # MIN_SESSION_SEC. Until it does, it is a glance and current_* still owns
    # the clock.
    pending_key:   object          = NO_CANDIDATE
    pending_title: str             = ""
    pending_since: datetime        = datetime.now()

    # Nothing before this process existed is ours to log. Without this, a
    # restart during a break double-logs it: the outgoing process flushes the
    # break so far on SIGTERM, then the incoming one sees xprintidle still high
    # and back-dates a second break to the same moment. The two rows overlap and
    # the day's break total is inflated by the length of the first.
    started_at:    datetime        = datetime.now()

    try:
        while True:
            now   = datetime.now()
            today = now.date()

            # ── Midnight rollover ──────────────────────────────────────
            if today != last_log_date and current_key and window_start:
                midnight = datetime.combine(today, datetime.min.time())
                flush_current(current_key, current_title, window_start,
                              last_log_date, midnight)
                window_start  = midnight
                last_log_date = today

            idle_sec = get_idle_ms() / 1000.0

            # ── Idle → start break ─────────────────────────────────────
            if not on_break and idle_sec >= IDLE_TIMEOUT_SEC:
                went_idle_at = max(
                    datetime.fromtimestamp(now.timestamp() - idle_sec),
                    started_at,          # see started_at — no overlapping breaks
                )
                flush_current(current_key, current_title, window_start,
                              last_log_date, went_idle_at)

                print(f"[{now.strftime('%H:%M:%S')}] Idle {int(idle_sec)}s — "
                      f"logging break since {went_idle_at.strftime('%H:%M:%S')}.",
                      flush=True)
                if MINIMIZE_ON_IDLE:
                    minimize_all_windows()

                current_key   = (BREAK_APP, BREAK_TITLE)
                current_title = BREAK_TITLE
                window_start  = went_idle_at
                on_break      = True
                pending_key   = NO_CANDIDATE   # no candidate survives a break

            # ── User returned → end break ──────────────────────────────
            elif on_break and idle_sec < IDLE_TIMEOUT_SEC:
                flush_current(current_key, current_title, window_start,
                              last_log_date, now)
                print(f"[{now.strftime('%H:%M:%S')}] User returned — resuming tracking.",
                      flush=True)
                current_key   = None
                current_title = ""
                window_start  = None
                on_break      = False
                pending_key   = NO_CANDIDATE

            # ── Normal tracking ────────────────────────────────────────
            # Only while the user is demonstrably present: see
            # FOCUS_CHANGE_GRACE_SEC. When they are not, the session in
            # progress is simply held open, and the break that follows trims it
            # back to the moment input stopped.
            elif not on_break and idle_sec <= FOCUS_CHANGE_GRACE_SEC:
                app, title = get_active_window_info()

                # An overlay or dialog on top is not a change of what the user
                # is doing: hold the session in progress open. See IGNORED_APPS.
                if app and app.lower() in IGNORED_APPS:
                    pass
                else:
                    # Identity decides session boundaries; see
                    # session_identity(). Tracked on app alone where a title is
                    # absent, since requiring one silently discarded that time.
                    if app:
                        new_key, display = session_identity(app, title or "")
                    else:
                        new_key, display = None, ""

                    # A switch is only believed once the new window has held
                    # focus for MIN_SESSION_SEC. Until then it is a candidate:
                    # the session in progress stays open and keeps accruing the
                    # time, so a glance costs nothing and leaves no gap.
                    if new_key == current_key:
                        pending_key = NO_CANDIDATE  # came back — never left
                    elif new_key != pending_key:
                        pending_key   = new_key     # a different candidate
                        pending_title = display
                        pending_since = now
                    elif (now - pending_since).total_seconds() >= MIN_SESSION_SEC:
                        # Held long enough to be real. The outgoing session ends
                        # when focus actually left it, not now, so no time is
                        # invented or lost.
                        flush_current(current_key, current_title, window_start,
                                      last_log_date, pending_since)
                        current_key   = pending_key
                        current_title = pending_title
                        window_start  = pending_since if pending_key else None
                        pending_key   = NO_CANDIDATE

            time.sleep(POLL_INTERVAL)

    except (Shutdown, KeyboardInterrupt) as exc:
        # Without this the session in progress is discarded — every restart
        # under systemd would silently lose the window you were on.
        flush_current(current_key, current_title, window_start,
                      last_log_date, datetime.now())
        reason = exc.args[0] if exc.args else "KeyboardInterrupt"
        print(f"\nTracker stopped ({reason}); current session flushed.", flush=True)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGHUP, _on_signal)
    main()
