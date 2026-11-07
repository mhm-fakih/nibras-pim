# Live dashboard for window_tracker.py — proposal

---

## 0. Blocker: the tracker has never produced data

Three independent checks agree, and this has to be fixed before any dashboard is
meaningful.

**The file contains two complete copies of the program.** `def main` appears at
line 151 and again at line 352; `if __name__ == "__main__"` at line 211 and again
at 408. Python executes top to bottom, so the guard at **line 211 runs copy #1** —
the older version that writes human-readable text lines into a file *named*
`.csv`. Copy #2 (the real CSV version) is dead code. It is only reached when you
press Ctrl+C: the first `main()` exits, execution continues past line 216,
redefines every function, and then **starts a second tracker**. Ctrl+C restarts
the program instead of stopping it.

**`import csv` is missing.** Copy #2 calls `csv.writer(...)` in `ensure_csv_header`
and `append_log`. Even when it does run, it dies with `NameError: name 'csv' is
not defined` on the first attempted write.

**`~/today/` contains no logs** — 16 screenshot `.jpg` files and nothing
else. No `.csv`, no `.txt`. Consistent with the above: nothing has ever been
recorded.

Everything else is in place: `xprintidle`, `wmctrl`, `xdotool` and `notify-send`
are all installed, the shebang resolves, and `psutil` imports fine.

**Recommendation:** keep copy #2 (proper CSV, `duration_sec` is directly
aggregatable), delete copy #1, add the missing import. Everything below assumes
that.

---

## 1. Changes needed in the tracker itself — regardless of dashboard choice

### 1.1 Fix the foundation
- Delete lines 1–215 (copy #1), keep the CSV implementation.
- Add `import csv`.
- **Flush the in-progress session on exit.** Currently SIGTERM/Ctrl+C discards
  whatever window you were on — under systemd, every restart silently loses the
  current session. Install a signal handler that calls `flush_current()`.

### 1.2 The key addition: a live state file

The CSV is an *append-on-transition* log. It learns about a session only when the
session **ends**. A dashboard reading only the CSV therefore cannot see what you
are doing right now — during a 40-minute focused stretch it would show nothing at
all, which is exactly the moment you most want it to be right.

So the tracker should also write a small **`state.json` every poll** (1 s),
written atomically (`write to .tmp` → `os.replace`) so a reader never sees a
half-written file.

```json
{
  "updated": "2026-07-21T16:20:03",
  "current": {
    "app": "firefox", "title": "YouTube — Home",
    "category": "distraction",
    "started": "16:14:52", "elapsed_sec": 311
  },
  "today": {
    "active_sec": 18240, "break_sec": 2400,
    "by_category": {"focus": 12100, "neutral": 3200, "distraction": 2940},
    "by_app": {"code": 9800, "firefox": 5100, "...": 0},
    "switches": 214,
    "switches_last_hour": 38,
    "longest_focus_sec": 2760,
    "mean_focus_session_sec": 412,
    "distraction_streak_sec": 311
  },
  "thresholds": [
    {"name": "daily_distraction", "value": 2940, "limit": 2700,
     "state": "breach", "since": "16:18:41"}
  ]
}
```

**This is the architectural decision that matters:** the tracker owns all
computation and writes one small file; every dashboard is a dumb reader. You can
then run a tray icon, a web page and a terminal view simultaneously, swap any of
them out, and never touch the tracker again. The alternative — each dashboard
re-parsing the CSV and recomputing — duplicates the category rules in every
front-end and guarantees they drift.

### 1.3 Extend the CSV schema

Add one column, `category`, so historical analysis doesn't require re-running the
classifier over old rows (and so changing your rules later doesn't silently
rewrite history):

```
date,start,end,duration_sec,duration_hms,app,title,category
```

### 1.4 Move configuration out of the code

A `config.yaml` (or `.json`) next to the script holding category rules, thresholds
and notification settings. You will tune these weekly at first; editing Python to
do it will stop you from doing it.

---

## 2. Defining the two things you actually asked to measure

### 2.1 Distraction — requires classification, and the title is essential

App name alone is not enough. `firefox` is a work tool and the single largest
distraction, often within the same minute. Classification must consider **app +
window title**:

```yaml
categories:
  focus:
    - app: [code, jetbrains-*, xed, gnome-terminal, tilix]
    - app: firefox
      title: ["GitHub", "Stack Overflow", "localhost", "docs\\."]
  distraction:
    - app: firefox
      title: ["YouTube", "Reddit", "Twitter", "X\\.com", "Netflix"]
    - app: [steam, discord, spotify]
  neutral:
    - app: [nemo, thunderbird, slack]
```

Order matters: first match wins, unmatched → `neutral`.

**Include an "unclassified" report.** Anything that fell through to `neutral`
with more than ~5 min/day should be listed on the dashboard so you can classify
it. Without this feedback loop the ruleset never improves and the numbers slowly
become meaningless.

### 2.2 Multitasking — do NOT measure it as raw switch count

The obvious metric is switches per hour. It is misleading: alternating
editor → terminal → editor twenty times while debugging is *concentrated work*,
and it produces a higher switch count than twenty minutes of drifting through
Reddit. Counting raw switches would flag your most productive hour as your worst.

Three better signals, in order of usefulness:

| Metric | Definition | What it catches |
|---|---|---|
| **Category-changing switches** | switches where the category differs from the previous window | genuine attention breaks, ignoring editor↔terminal churn |
| **Mean focus-session length** | average uninterrupted stretch within `focus` | fragmentation — 12 × 5 min is not 60 min of work |
| **Distinct apps per 10 min** | rolling unique count | scattered "juggling" states |

Suggested framing for the dashboard: report **fragmentation** (mean focus-session
length, and the longest unbroken stretch today) rather than a raw switch tally.
The number you want to move is "longest unbroken focus block", and it is
motivating in a way a switch counter is not.

---

## 3. Thresholds and notifications

### 3.1 Four shapes of threshold — you need all four

| Type | Example | Why it exists |
|---|---|---|
| **Cumulative** | > 45 min distraction today | total budget |
| **Streak** | > 10 min continuous distraction | catches the rabbit hole *while you're in it* |
| **Rate** | > 12 category-switches in 10 min | catches scattered states |
| **Ratio** | distraction > 25% of active time | scale-free; works on a short day |

Streak is the one that changes behaviour — cumulative limits are usually breached
long after the damage is done, and by then the notification is just an accusation.

### 3.2 Proposed starting defaults

| Rule | Limit | Action |
|---|---|---|
| Continuous distraction | 10 min | notify (normal) |
| Continuous distraction | 20 min | notify (critical) |
| Daily distraction total | 45 min | notify (normal) |
| Category switches / 10 min | 12 | notify (low) |
| Mean focus session < | 8 min, after 1 h of work | notify (low), suggest a block |
| No break in | 90 min | notify — you already have the idle/break machinery |

### 3.3 Notification design — the part that decides whether you keep it on

`notify-send` is installed, so delivery is trivial. The discipline is in *when
not* to fire:

- **Fire on transition only** (ok → breach), never repeatedly while in breach.
  A rule that re-fires every poll trains you to dismiss it within a day.
- **Cooldown per rule** (e.g. 15 min) before the same rule may fire again.
- **Global cap** — at most ~6 notifications/hour, whatever the rules say.
- **Hysteresis** — clear the breach only when the metric drops meaningfully below
  the limit (e.g. 80%), not the instant it dips under.
- **Escalation ladder** rather than repetition: `low → normal → critical` urgency,
  and only at the top of the ladder consider something physical. You already have
  `minimize_all_windows()`; using it as a 20-minute-rabbit-hole intervention is
  reasonable, but it should be opt-in in config, because doing it during a video
  call would be genuinely bad.
- **Quiet hours / pause switch.** A "snooze 30 min" that writes to config, so
  that when you're deliberately watching a tutorial the system stops nagging
  rather than being killed entirely and never restarted.

---

## 4. The dashboard — five ways, compared

All five read `state.json`; they differ only in surface. Effort assumes the
tracker changes in §1 are done.

| # | Approach | Live? | Visibility | Deps | Effort | Best for |
|---|---|---|---|---|---|---|
| **A** | **Tray indicator** (AppIndicator/GTK) — icon + colour + menu | yes, 1 s | always visible, zero effort to check | `gir1.2-appindicator3` | ~half day | ambient awareness + it *is* the notifier |
| **B** | **Terminal TUI** (`rich`/`textual`) | yes, 1 s | only when you look | `pip install rich` | ~2 h | you already live in a terminal |
| **C** | **Local web page** (FastAPI/Flask + SSE, or plain HTML polling `state.json`) | yes | a browser tab, also reachable from phone | `flask` or none | ~half day | charts, timeline, history browsing |
| **D** | **Always-on overlay** (Conky, or a small GTK window pinned) | yes | permanently on screen | conky | ~2 h | hard accountability, no hiding it |
| **E** | **End-of-day static report** (regenerate HTML from CSV) | no | on demand | none | ~2 h | trends, weekly review |

### Recommendation

**A + C, in that order, with E later.**

- **A (tray indicator) first.** It is the highest-value-per-line item, because the
  glanceable state and the notifications are the same component — the icon turns
  amber at warn and red at breach, and the menu shows today's totals and a snooze
  toggle. A dashboard you must open is a dashboard you check after the damage;
  a tray icon changes colour in your peripheral vision.
- **C (web page) second,** when you want the timeline: a horizontal band of the
  day coloured by category makes fragmentation immediately obvious in a way no
  number does. Serve it locally; no server needed if the page just polls
  `state.json` and today's CSV.
- **E (static report)** once a few weeks of CSV exist and week-over-week trend
  becomes the interesting question.

**Skip D unless you want the confrontational version** — an always-visible
overlay is effective and quickly resented.

---

## 5. Suggested build order

1. **Fix the tracker** (§1.1) — delete the duplicate, add `import csv`, add the
   exit flush. *Nothing works before this, and it produces no visible feature.*
2. **Run it for two days** and classify whatever shows up. You need real app/title
   strings before writing category rules; guessing them produces a ruleset that
   matches nothing.
3. **Add categories + `state.json`** (§1.2–1.4).
4. **Tray indicator with thresholds and notifications** (option A).
5. **Web timeline** (option C).
6. **Weekly report** (option E).

Steps 1–2 are unavoidable, and step 2 is why this can't all be built in one pass:
the category rules are the core of "distraction tracking" and they cannot be
written without your actual data.

---

## 6. Two things to decide before implementation

**Privacy.** The CSV stores every window title verbatim — document names, email
subjects, URLs, client names. That file is a detailed record of your working life
in plaintext. Options: keep as-is, store a redacted title for apps you nominate
(password managers, banking), or hash titles and keep only the category. Worth
deciding now rather than after six months of accumulation.

**Retention.** One CSV per day grows without bound. A monthly roll-up (per-app,
per-category daily totals) plus deletion of raw rows older than N days keeps
trends while shrinking the exposure above.
