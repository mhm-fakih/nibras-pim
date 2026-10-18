# Calendar

Nibras has two calendars: the small one in the sidebar of every module, for jumping to a day, and
the full **Calendar** page, for planning your week.

## The mini calendar

In any module view, the sidebar shows a compact calendar under the search box.

- By default it shows **Last 2 weeks** (last week and this week, Monday first). Click the expand
  arrow to see **the full month** with ISO week numbers; your choice is remembered.
- A small **dot** under a date means records of the current module were created that day.
- A **bold** date has a journal entry.
- Today is circled.

**Clicking a date** opens that day's **summary journal** in the Journal module — and creates it if
it doesn't exist yet, so there is always exactly one per day to write in. If the Journal module is
switched off, clicking a date creates nothing; instead the middle pane lists every record from that
day across your enabled modules.

!!! tip
    To filter the current list to one day without opening a journal, use the **On day** filter at
    the top of the list instead.

## The Calendar page

Open **Calendar** from the sidebar (or press **g c**). It shows a week (or a day) of **Journal** and
**Planner** entries, plus your **sprints**, on a time grid.

### Moving around

- **‹** and **›** step back or forward a week (or a day); **Today** jumps back.
- The heading shows the date range and the **ISO week** number.
- The month calendar on the right jumps to any date.
- A red line marks the current time.

### Adjusting the view

| Control | Choices |
|---|---|
| **Week** / **Day** | Seven days from Monday, or a single day |
| **Slot** | 15, 30 or 60 minutes per row |
| **From** / **To** | The first and last hour shown (default 07:00–21:00) |

These settings are remembered by your browser.

### How entries look

Each entry is coloured by its **type** (the colours are set on the
[Parameters](administration.md#parameters) page). The border tells modules apart: **planner** entries
have a dashed border, **journal** entries a solid one, and **sprints** a double one. Hover an entry
to see its time and description; repeating plans are marked **⟳**.

### Adding an entry

1. Click a slot — or press and drag down across several slots to choose a time range.
2. In **New calendar entry**, choose the **Module** (**Planner** or **Journal**) and optionally a
   **Type**. The module you used last is remembered.
3. Enter a **Summary** and optional **Description**; adjust **Start** and **End** if needed.
4. Optionally **Link to goal** or **Link to task** (bookmarked ones are offered).
5. Click **Create**.

Entries created here are bookmarked automatically.

### Changing an entry

- **Click** an entry to open it in the reading page in a new tab (a sprint opens the
  [Sprints](tasks-kanban-sprints.md#sprints) page). Use **Edit** there to change it.
- **Drag the top or bottom edge** of an entry to change its start or end time.

Read-only users can look but not add, resize or remove.

## Repeating plans

A planner entry whose **Repeats** switch is on is a *series*: the calendar draws every occurrence at
the same time of day without storing each one. An occurrence becomes a record of its own only when
you open, move or annotate it — so a year of a daily habit costs nothing until you touch it.

To remove a single occurrence, hover it and click the **×** in its corner (**Remove just this
occurrence**). To change or end the whole series, edit the original planner record — see
[Repeating tasks and plans](records.md#repeating-tasks-and-plans).

## Calendars elsewhere

- A [dashboard](dashboards.md) can include a **Calendar (filtered by type)** region — for example
  only "family" events — to show on a spare screen.
- Planner entries whose end date has passed, or is near, show up in the Planner module's
  **Overdue** and **Due soon (7d)** reports, and in
  [desktop notifications](settings.md#notifications) if you turn them on.
