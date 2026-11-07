"""Drive window_tracker.main() with a fake clock and a scripted focus sequence.

Run: python3 test_sessions.py

Checks the property that matters: every second the user was present is
attributed to exactly one session — no gaps, no double counting — while
sub-threshold glances do not produce rows of their own.
"""
import sys
from datetime import datetime, timedelta

import window_tracker as wt

BASE = datetime(2026, 7, 22, 10, 0, 0)
CLOCK = [0.0]


class FakeDT(datetime):
    @classmethod
    def now(cls, tz=None):
        return BASE + timedelta(seconds=CLOCK[0])


def run(script, end_at):
    """script: list of (t_seconds, app, title). Returns logged rows."""
    CLOCK[0] = 0.0
    rows = []

    def active():
        cur = (None, None)
        for t, app, title in script:
            if CLOCK[0] >= t:
                cur = (app, title)
        return cur

    def sleep(_):
        CLOCK[0] += wt.POLL_INTERVAL
        if CLOCK[0] > end_at:
            raise wt.Shutdown("test-end")

    wt.datetime = FakeDT
    wt.get_idle_ms = lambda: 0
    wt.get_active_window_info = active
    wt.time.sleep = sleep
    wt.append_log = lambda path, app, title, start, end: rows.append(
        (app, (start - BASE).total_seconds(), (end - BASE).total_seconds())
    )
    wt.main()
    return rows


def check(name, rows, expect_apps, span_end):
    ok = True
    got = [r[0] for r in rows]
    if got != expect_apps:
        print(f"  FAIL apps: got {got}, want {expect_apps}")
        ok = False
    # contiguity: each session starts where the previous ended
    for (a, s0, e0), (b, s1, e1) in zip(rows, rows[1:]):
        if abs(s1 - e0) > 1.01:
            print(f"  FAIL gap of {s1 - e0:.0f}s between {a} and {b}")
            ok = False
    if rows and abs(rows[-1][2] - span_end) > 1.5:
        print(f"  FAIL end: last session ends {rows[-1][2]:.0f}s, want ~{span_end}")
        ok = False
    total = sum(e - s for _, s, e in rows)
    print(f"  {'ok  ' if ok else 'FAIL'} {name}")
    for app, s, e in rows:
        print(f"        {app:<8} {s:6.0f}s → {e:6.0f}s  ({e - s:.0f}s)")
    print(f"        total accounted: {total:.0f}s of {span_end}s")
    return ok


print(f"MIN_SESSION_SEC = {wt.MIN_SESSION_SEC}\n")
all_ok = True

# 1. A glance is absorbed: 5s in nemo in the middle of a tilix stretch.
all_ok &= check(
    "glance under threshold leaves no row and no gap",
    run([(0, "tilix", "t"), (30, "nemo", "n"), (35, "tilix", "t")], 70),
    ["tilix"], 70)

# 2. A real switch is recorded, and the outgoing session ends when focus left.
all_ok &= check(
    "genuine switch splits cleanly with no gap",
    run([(0, "tilix", "t"), (30, "xed", "x")], 70),
    ["tilix", "xed"], 70)

# 3. Churn: four 3s glances then a real switch. All time must still land
#    somewhere, and none of the glances may produce a row.
all_ok &= check(
    "rapid churn is absorbed, no time lost",
    run([(0, "tilix", "t"), (30, "nemo", "n"), (33, "xed", "x"),
         (36, "nemo", "n"), (39, "firefox", "f"), (42, "xed", "x")], 80),
    ["tilix", "xed"], 80)

# 4. All tilix windows are one activity: switching between them is not a switch.
all_ok &= check(
    "different tilix windows do not split the session",
    run([(0, "tilix", "win A"), (20, "tilix", "win B"), (40, "tilix", "win C")], 70),
    ["tilix"], 70)

# 5. A transient failure of the window query (xdotool timeout, or the instant a
#    window closes) must NOT end the session. This is the NO_CANDIDATE bug:
#    None meant both "no candidate" and "no active window", so a single failed
#    poll matched a stale candidate and committed immediately.
all_ok &= check(
    "transient loss of the active window does not end the session",
    run([(0, "tilix", "t"), (30, None, None), (32, "tilix", "t")], 70),
    ["tilix"], 70)

# 6. But a sustained one does — the user really is on no window.
rows6 = run([(0, "tilix", "t"), (30, None, None)], 70)
print(f"  {'ok  ' if [r[0] for r in rows6] == ['tilix'] and abs(rows6[0][2] - 30) < 1.5 else 'FAIL'}"
      f" sustained loss ends the session at the moment focus left")
for app, s, e in rows6:
    print(f"        {app:<8} {s:6.0f}s → {e:6.0f}s  ({e - s:.0f}s)")
all_ok &= ([r[0] for r in rows6] == ["tilix"] and abs(rows6[0][2] - 30) < 1.5)

print("\nPASS" if all_ok else "\nFAIL")
if __name__ == "__main__":
    sys.exit(0 if all_ok else 1)
