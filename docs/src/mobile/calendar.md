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
- The hours shown run from **Calendar start hour** to **Calendar end hour** in [Settings](settings.md) → **Calendar & widgets** (5 to 22 by default). The whole range fits on screen without scrolling.
- **Slot** (15m / 30m / 60m) under the grid sets the size of a time slot, which is the step that new entries and drags snap to.

### Mini calendar

When **Mini calendar** is on in Settings → **Calendar & widgets** (it is by default), a strip at the top shows last week and this week, each row starting with its week number (**W41**). A dot marks days with entries. Tap a day to jump there.

To see further ahead, tap the small arrow at the end of the second row (**Show the whole month**). The strip unfolds into the whole month, with arrows to page to the previous or next month; days outside the month are drawn paler. Tap the arrow at the end of the month's title row (**Back to two weeks**) to fold it again.

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

## Add a week at once

For a timetable, typing entries one by one is slow. Tap the list-with-plus icon in the top bar (**Add a week**) and type one entry per line:

```
p mon 10:00 Calculus
p mon,wed 8:00-9:30 Physics
p tue-thu 14 Lab
j fri 2:30pm Group meeting
```

- **`p` or `j`** at the start makes a plan or a journal entry. Leave it out and the line takes the **Type** chosen under the box.
- **Days**: a day (`mon`), a list (`mon,wed`) or a run (`mon-fri`). English, French and Arabic day names all work.
- **Times**: `8`, `8:30` or `2:30pm`, optionally with an end (`8:00-9:30`). Without an end, an entry lasts an hour.
- Everything after the time is the title.

Under the box, each line shows what it will become (type, date, times, title), or what's wrong with it, for example **Line 2: no time (8, 8:30, 2:30pm…)**.

- **From** is the first day the lines are placed from: each weekday lands on its first date on or after it. Tap the date to change it.
- **Repeat**: **Once (this week only)**, or use **+** to repeat **Weekly for N weeks, until …** (up to 52), so a term's timetable is typed once. Only plans repeat; **Journal entries are added once; only planners repeat.**

Tap **Add N** to save them all. Like single entries, they're saved on the phone first and sent when the server can be reached.

## Change an entry

- **Tap** an entry to open it for editing (**Edit entry**). The type can't be changed once an entry exists.
- **Drag the thin strip** at the top or bottom edge to change the start or end time.
- **Long-press and drag** the entry to move it to another time on the same day.

While you drag, a preview shows where it will land. Nothing changes until you let go.

!!! note "Repeating plans offline"
    A future occurrence of a repeating plan isn't a record on the server yet. Editing it creates one, so it can't be done offline. You'll see **Can't edit a repeat while offline — try again when connected**.
