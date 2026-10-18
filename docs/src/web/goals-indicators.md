# Goals, habits & indicators

**Goals** say what you are working towards; **indicators** measure things over time — from a habit
you log by hand to a number the server works out for you every night.

## Goals

A goal is a record like any other (see [Modules & records](records.md)), with a **Code**, **Status**,
**Type**, **% complete**, **Start**, **End** and **Completed** date.

What makes goals special:

- **Bookmark** the goals you are actively pursuing. Only bookmarked goals are offered in a task's
  **Goal** picker, on the [Kanban](tasks-kanban-sprints.md#kanban) board and in the calendar's
  **Link to goal** box.
- A goal's detail pane lists its tasks under **Related**, and has a **+ Add a task to this goal…**
  box — type a summary and press **Enter**.
- Click the **✓** circle to mark the goal complete (this also removes its bookmark).

## Habit tracking on a goal

A goal can carry one or more **habits** — daily indicators linked to it.

1. Open the goal.
2. If it has no habit yet, click **Track this goal**. This adds a daily habit indicator linked to the
   goal.
3. Each day, enter a **Value** (and optionally a **Date** and a **Note** — *What did you do
   today?*), then click **Log**. Use **Log another** for more entries on the same day.

The goal then shows a heatmap of your days, coloured by how much you logged — red for no entry,
orange for a little, greens for more — with a day-by-day log underneath.

!!! tip "Show habits on a screen"
    Switch on **Show habit heatmap on dashboards** in the goal's form, then add a **Goals habit
    heatmap** region to a [dashboard](dashboards.md).

## Indicators

Open **Indicators** from the sidebar (or **g i**). The page has four parts:

1. **KPI cards** — standard at-a-glance figures across your records.
2. **Needs data** — monitored indicators that have no value yet for the current period (day, week…).
   Each card shows the last value and date; enter a value (or accept the **Proposed value**) and click
   **Confirm**.
3. **Monitored indicators** — a line chart per indicator. Indicators that share a **Category** are
   drawn together on one chart.
4. **All indicators** — every defined indicator; ★ marks monitored ones. Click any of them to edit.

### Creating an indicator

Click **New indicator** and fill in:

| Field | Meaning |
|---|---|
| **Name**, **Code**, **Description** | What it measures |
| **Frequency (daily / weekly)** | How often a value is expected |
| **Scale / unit** | e.g. *minutes*, *pages*, *kg* |
| **Category** | A short code; indicators with the same category share a chart |
| **Colour** | Line colour (filled in automatically if blank) |
| **Actively monitored** | Watch it on this page and in **Needs data** |
| **Goal** | Link it to a goal to use it as a habit tracker |

An indicator gets its numbers in one of four ways, shown as a chip on each card:

| Kind | Where the value comes from |
|---|---|
| **MANUAL** | You type it in. |
| **QUERY** | A query over your records, set in the **Query** field. |
| **FILE** | A count of files in a server folder (**Folder path**, filtered by **Extensions**). |
| **SCRIPT** | A small shell command on the server that prints a number. |

For QUERY, FILE and SCRIPT indicators the app **proposes** a value and waits for you to confirm it.
Switch on **Auto-collect** to have the server compute and save the value by itself once a day, at
the time set in [Settings → Indicator auto-collection](settings.md#indicator-auto-collection).

!!! note
    Query, folder and script indicators run on the server, so they are usually set up by whoever
    looks after it.
