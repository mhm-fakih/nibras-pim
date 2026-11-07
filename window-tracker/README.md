# window-tracker

Tracks the active window on Linux Mint Cinnamon (X11) and logs how long each
piece of work actually lasted. Left half of a two-column terminal dashboard;
the right half is [`file-watch`](../file-watch/).

## Files

| File | Purpose |
|---|---|
| `window_tracker.py` | the tracker daemon — one CSV row per session |
| `alert.py` | fullscreen flash + critical notification for threshold breaches |
| `dashboard.py` | the two-column dashboard — time on the left, files on the right |
| `test_sessions.py` | drives the main loop against a fake clock — `python3 test_sessions.py` |
| `DASHBOARD-PROPOSAL.md` | design: indicators, thresholds, notification discipline |

## Running

Installed as a **systemd user service**. The unit is version-controlled in
`systemd/` — that copy is the source of truth; re-run the installer after
editing it:

```bash
./systemd/install.sh
```


```bash
systemctl --user status window-tracker
systemctl --user restart window-tracker     # after editing the script
journalctl --user -u window-tracker -f      # live log
```

The unit is anchored to `default.target`, **not** `graphical-session.target` —
that target is never activated on this Cinnamon session, so a unit wanting it
would never start. `ExecStartPre=/usr/bin/xprintidle` gates startup on X being
up, since lingering is enabled and `default.target` is reached at boot.

## Output

One CSV per day in `~/.local/share/nibras/window-tracker/YYYY-MM-DD.csv`:

```
date,start,end,duration_sec,duration_hms,app,title,windows_open
```

`windows_open` is a cheap multitasking signal, sampled once per session rather
than once per poll. It counts only windows you would actually call open — see
the design note below.

## Design notes

These exist because the obvious implementation is wrong in a way that is
invisible until you look at real data.

**Session identity is not the window title.** Titles carry animated content —
braille spinners in terminals, `(3)` unread badges, media timers. Keying a
session on the raw title turns a 40-minute focus block into ~2400 one-second
rows and makes "longest unbroken focus" read as 1 second. `normalize_title()`
strips the volatile parts before comparing.

**All terminals are one activity.** Apps in `SAME_WORK_APPS` use the app name
alone as identity, so moving between terminal windows never breaks a focus
block — and for the same reason they collapse to a single entry in
`windows_open`: four Tilix windows are one activity competing for attention,
not four. Everything else splits on the title and counts per window, which is
what you want for a browser, where each tab genuinely is a different task.

Splits and tabs *inside* one window were never counted separately — they are
not X11 windows at all, so nothing in the toolchain can see them.

**A glance is not a switch.** A window has to hold focus for `MIN_SESSION_SEC`
before the tracker believes you switched to it. Below that it is absorbed into
the session already in progress, not discarded: dropping it would delete real
minutes from the day and leave gaps that split one block of work into several.
Without this the log thrashed — 2026-07-22 logged 174 sessions in 32 minutes,
75 of them `nemo` averaging under 4 seconds, which makes every session-derived
number meaningless.

**Window changes are ignored while the user is away.** A focus change after
`FOCUS_CHANGE_GRACE_SEC` of no input cannot have been the user — it is an app
grabbing focus (SafeEyes, screensavers, updaters). Logging those creates
sessions that the back-dated break later overlaps, double-counting the same
minutes as both active and break.

**Overlays are not sessions.** Apps in `IGNORED_APPS` — SafeEyes break screens,
`zenity` dialogs, the GTK file-chooser portal, the desktop background — take
focus without the user choosing them. Logging each one as a session cut a focus
block in two every time one appeared: on 2026-07-21 that was 9 `file-roller`
sessions totalling 21 seconds. While one is focused the session in progress is
held open instead, and if the user really has gone the idle timeout still
back-dates a break over it.

**`wmctrl -l` is not a list of open windows.** It lists everything the WM
manages, which includes the desktop background (one per monitor), modal
preference sheets carrying `_NET_WM_STATE_SKIP_TASKBAR`, and the overlays
above. Unfiltered, the count ran 4-6 high — enough to trip a multitasking
threshold on its own. `_is_countable()` drops those. Note it costs one `xprop`
per window; it is affordable only because the count is sampled once per
session.

**Minimised windows count.** `COUNT_MINIMIZED` was `False` at first, on the
theory that an iconified window is not competing for attention. It agreed with
a hand count of 9 on the morning it was written — but only because one window
happened to be minimised just then. Later the same day four were, and the
dashboard read **3** against a real **8**. A number that swings by five
depending on how tidy the desktop is measures tidiness, not attention. A
minimised window is still open work you have not finished with, and it is still
in the window list, which is what the question means to whoever reads the
dashboard.

Tiling panes and tabs inside one window — Tilix splits, browser tabs — are not
separate X11 windows and were never counted separately. A four-pane Tilix
window is one window; two Tilix windows fold into one activity via
`SAME_WORK_APPS`.

**Breaks are back-dated.** When idle passes `IDLE_TIMEOUT_SEC`, the break starts
at the moment input actually stopped, not when the threshold tripped — so break
durations are accurate rather than five minutes short.

**The in-progress session is flushed on exit.** Without the SIGTERM handler,
every `systemctl restart` would silently discard the window you were on.

## Tests

```bash
python3 test_sessions.py
```

Runs the real `main()` loop against a fake clock and a scripted focus sequence,
asserting the property the whole design rests on: **every second the user was
present belongs to exactly one session — no gaps, no double counting** — while
sub-threshold glances produce no rows of their own. Session accounting fails
silently, so a regression here is invisible in the CSV until you go looking.

## Configuration

At the top of `window_tracker.py`:

| Setting | Default | Meaning |
|---|---|---|
| `LOG_DIR` | `~/.local/share/nibras/window-tracker` | where CSVs go |
| `POLL_INTERVAL` | `1` | seconds between checks |
| `IDLE_TIMEOUT_SEC` | `300` | idle before a break is recorded |
| `MINIMIZE_ON_IDLE` | `False` | minimise all windows on idle — off; destructive during calls |
| `FOCUS_CHANGE_GRACE_SEC` | `10` | ignore focus changes after this much idle |
| `MIN_SESSION_SEC` | `10` | focus time before a window counts as a switch |
| `SAME_WORK_APPS` | terminals | apps that are one activity: one session, one window |
| `IGNORED_APPS` | overlays, dialogs | never start a session; never counted as open |
| `COUNT_MINIMIZED` | `True` | count iconified windows in `windows_open` |

## Dashboard

```bash
python3 dashboard.py            # static
python3 dashboard.py --live     # redraws in place; alternate screen, Ctrl+C restores
python3 dashboard.py --svg out.svg
```

Needs roughly **48 lines x 208 columns**.

Both columns now read real data — the left from today's CSV plus a live window
count, the right from `file-watch`'s snapshot. Nothing on it is a placeholder,
which is the point: the earlier mock hard-coded `25 open windows`, and that
number went on reading as a live threshold breach for a day after it stopped
being true.

The unit the left column reports is the **block**, not the session. A session
ends every time you change window; a block only ends at a break or a gap over
`BLOCK_GAP_SEC`. "Longest today" on sessions would report seconds.

### TOP NOTES

The foot of the right column lists priority-`p4` notes from `KNOTES_DIR`
(`~/new/knotes`), titled by each note's own first line, in two columns because
a note title is nowhere near 98 characters wide.

Recursive, but never into a hidden directory: knotes keeps `.imported`,
`.trash` and `.deleted` beside the live notes, and those are exactly the ones
that are not yours to act on. Notes under `MIN_NOTE_BYTES` are skipped as
debris, and an empty one reports `(empty)` rather than `(unreadable)` — the
latter sends you hunting for a permissions problem that does not exist.

Ordered by `TYPE_ORDER` (`PTGJRWN`), not alphabetically, so the list reads in
the order you would work it. An unrecognised type sorts last, still grouped.

**Arabic is passed through untouched.** VTE implements the Unicode bidi
algorithm itself and hands Arabic to the font already reordered, so running
python-bidi's `get_display` over it first reverses a line that is about to be
reversed again:

```
raw          مراجعة عقد الخدمة قبل نهاية الشهر     correct
get_display  رهشلا ةياهن لبق ةمدخلا دقع ةعجارم     backwards
```

The first version did exactly that. It took printing both in a real tilix
window and reading the result to settle — shipped unchecked it would have
garbled every Arabic note while looking deliberate. `TERMINAL_DOES_BIDI = False`
is there for a terminal that really is passive; the right-alignment applies
either way.

### Reading the file column

`unreviewed backlog` is judged as a **share of the folder**, not an absolute
count: green to 25%, yellow to 50%, red beyond. The thresholds it replaced
(red over 50, bar scaled to 200) were read off a 225-file folder that was 95%
behind, and against 47 notes they painted 6 outstanding — a good state — in
alarm colours. The bar was hard-coded red at every value, so it carried no
information at all.

## Not built yet

The focus/neutral/distraction classifier, `state.json` and the threshold
engine. Designed in `DASHBOARD-PROPOSAL.md`, waiting on a couple of days of
real data — category rules written from guessed app/title strings match
nothing. Until it exists the dashboard shows active vs break, and no
three-way split.

## Requirements

`xdotool`, `xprintidle`, `wmctrl`, `psutil`, `rich`; `notify-send` and
PyGObject/GTK3 for `alert.py`.
