# Calendar

**Calendar** (the calendar icon) shows the **Plans** and **Journal** entries from your Nibras server on a day grid, and lets you add and move them with your finger. It's on the **On this phone** side of the rail because it works offline: new entries are saved on the phone first and sent to the server when a connection appears.

!!! note
    The calendar shows server data, so it needs a [Nibras connection](sync.md). Without one, it only shows entries you add on the phone.

## Reading the grid

- The top bar shows the date (or date range) on view.
- **Previous** / **Today** / **Next** move through the days.
- **1d** / **3d** show one day or three days side by side.
- **Plans** are drawn in green and **Journal** entries in light blue. A repeating plan's future occurrences are drawn paler.
- A thin line across today's column marks the current time.
- The hours shown run from **Calendar start hour** to **Calendar end hour** in [Settings](settings.md) (5 to 22 by default). The whole range fits on screen without scrolling.
- **Slot** (15m / 30m / 60m) under the grid sets the size of a time slot, which is the step that new entries and drags snap to.

### Mini calendar

When **Mini calendar** is on in Settings (it is by default), a strip at the top shows last week and this week, each row starting with its week number (**W41**). A dot marks days with entries. Tap a day to jump there.

## Add an entry

Any of these opens the **New entry** form:

- Tap an empty slot: the entry starts there and lasts one slot.
- **Long-press and drag** down the grid to sweep a longer time range.
- Tap **+** (**Add entry**) in the top bar.

In the form:

| Field | |
|---|---|
| **Type** | **Plan** or **Journal**. The last type you used is preselected. |
| **Date** | With **Previous day** / **Next day** arrows. |
| **Start**, **End** | The times. |
| **Summary** | The title. The keyboard opens here. Arabic text is right-aligned automatically. |
| **Description** | Optional detail. |

Tap **Save**. You'll see **Saved to calendar**, or **Saved — will sync when online** if you're offline.

## Change an entry

- **Tap** an entry to open it for editing (**Edit entry**). The type can't be changed once an entry exists.
- **Drag the thin strip** at the top or bottom edge to change the start or end time.
- **Long-press and drag** the entry to move it to another time on the same day.

While you drag, a preview shows where it will land. Nothing changes until you let go.

!!! note "Repeating plans offline"
    A future occurrence of a repeating plan isn't a record on the server yet. Editing it creates one, so it can't be done offline. You'll see **Can't edit a repeat while offline — try again when connected**.
