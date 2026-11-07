#!/usr/bin/env python3
"""Attention-grabbing alerts for threshold breaches.

    python3 alert.py                  # flash the screen, to test it
    python3 alert.py --demo           # what a real breach looks like
    python3 alert.py --flash-now      # internal: does the GTK work in-process

Wired into the threshold engine later: only on an ok -> breach transition,
never repeatedly while in breach. A flash that fires every poll is a flash you
learn to ignore within a day.

Why GTK and not `xrefresh -solid`: under Cinnamon's compositor the root-window
paint xrefresh performs is recomposited away before it is visible. An
override-redirect window drawn over everything is not.
"""
import subprocess
import sys
import time

FLASH_COLOR   = "#ff2222"
FLASH_TIMES   = 3
FLASH_ON_MS   = 90
FLASH_OFF_MS  = 70
FLASH_OPACITY = 0.55      # solid red at full opacity is genuinely unpleasant


def _flash_gtk(times=FLASH_TIMES, color=FLASH_COLOR, on_ms=FLASH_ON_MS,
               off_ms=FLASH_OFF_MS, opacity=FLASH_OPACITY) -> None:
    """Flash every monitor. Runs GTK directly — call via flash_screen()."""
    import gi
    gi.require_version("Gtk", "3.0")
    from gi.repository import Gdk, Gtk

    display = Gdk.Display.get_default()
    if display is None:
        raise RuntimeError("no display")

    # Span the union of all monitors: with a dashboard on a second screen, a
    # flash on only one of them is a flash you can miss.
    x0 = y0 = 10**9
    x1 = y1 = -10**9
    for i in range(display.get_n_monitors()):
        g = display.get_monitor(i).get_geometry()
        x0, y0 = min(x0, g.x), min(y0, g.y)
        x1, y1 = max(x1, g.x + g.width), max(y1, g.y + g.height)

    # POPUP is override-redirect: it bypasses the window manager, so it appears
    # instantly, never takes focus, and cannot steal a keystroke mid-flash.
    win = Gtk.Window(type=Gtk.WindowType.POPUP)
    win.set_app_paintable(True)
    win.set_decorated(False)
    win.set_keep_above(True)
    win.set_accept_focus(False)
    win.set_focus_on_map(False)
    win.set_skip_taskbar_hint(True)
    win.set_skip_pager_hint(True)

    screen = win.get_screen()
    visual = screen.get_rgba_visual()
    if visual is not None:                 # needed for the opacity to apply
        win.set_visual(visual)

    rgba = Gdk.RGBA()
    rgba.parse(color)

    def on_draw(_widget, cr):
        cr.set_source_rgba(rgba.red, rgba.green, rgba.blue, opacity)
        cr.paint()
        return False

    win.connect("draw", on_draw)
    win.move(x0, y0)
    win.resize(x1 - x0, y1 - y0)

    def pump(ms: int) -> None:
        end = time.monotonic() + ms / 1000.0
        while time.monotonic() < end:
            while Gtk.events_pending():
                Gtk.main_iteration_do(False)
            time.sleep(0.004)

    for _ in range(times):
        win.show_all()
        pump(on_ms)
        win.hide()
        pump(off_ms)

    win.destroy()
    while Gtk.events_pending():
        Gtk.main_iteration_do(False)


def flash_screen(in_process: bool = False) -> bool:
    """Flash the screen.

    By default this re-executes this file as a short-lived subprocess, so the
    caller (the rich TUI dashboard) never has to import GTK into its own
    process. Pass in_process=True to skip the fork.
    """
    if in_process:
        try:
            _flash_gtk()
            return True
        except Exception:
            return False
    try:
        r = subprocess.run([sys.executable, __file__, "--flash-now"],
                           capture_output=True, timeout=10)
        return r.returncode == 0
    except (OSError, subprocess.TimeoutExpired):
        return False


def notify(title: str, body: str, urgency: str = "normal",
           icon: str = "dialog-warning") -> None:
    try:
        subprocess.run(
            ["notify-send", "-u", urgency, "-i", icon, title, body],
            capture_output=True, timeout=3,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass


def breach(rule: str, value: str, limit: str, demo: bool = False) -> None:
    """Full attention-grab for one breached rule: what broke, plus the flash.

    `demo` marks the notification as a test. A sample alert that looks exactly
    like a real one teaches you to distrust the real ones.
    """
    prefix = "[TEST] " if demo else ""
    notify(f"{prefix}Limit exceeded — {rule}", f"{value}   (limit {limit})",
           urgency="critical")
    flash_screen()


if __name__ == "__main__":
    if "--flash-now" in sys.argv:
        _flash_gtk()
    elif "--demo" in sys.argv:
        breach("continuous distraction", "10m 12s", "10m", demo=True)
        print("sent: critical notification + screen flash")
    else:
        print("flashed" if flash_screen() else "flash failed")
