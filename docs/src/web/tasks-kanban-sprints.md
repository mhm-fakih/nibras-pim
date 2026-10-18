# Tasks, Kanban & Sprints

Tasks are where work gets done. This page covers the task-specific features, the **Kanban** board
that shows tasks under their goals, **time tracking**, and **Sprints** for committing to a block of
work.

## Tasks in brief

Besides the common fields, a task has:

| Field | Use |
|---|---|
| **Status** | Where it stands (your own list of statuses — see [Parameters](administration.md#parameters)). |
| **Goal** | The bookmarked goal it serves. |
| **% complete** | Progress. |
| **Planned duration (min)** | Your estimate in minutes; sprints add these up. |
| **Start**, **End** | Dates. A task past its end date shows in the **Overdue** report. |
| **Urgent** | A flag separate from priority. |
| **Order #** | Position among a goal's tasks. |
| **Repeats** | See [Repeating tasks](records.md#repeating-tasks-and-plans). |
| **People** | The contacts the task is with (when Contacts is enabled). |

### Completing a task

Click the **✓** circle in the detail pane (**Click to mark complete**). This stamps the completion
date and removes the task's bookmark. If your administrator has set a **Status on completion** in
[Settings](settings.md#tasks-and-other-preferences), the task also moves to that status. Click again
to reopen.

### Linking tasks to goals

Choose a goal in the task form's **Goal** picker (only bookmarked goals are offered). You can also
add tasks straight from a goal: open the goal and type in **+ Add a task to this goal…**, then press
**Enter**.

A task that has a goal gets a **+ Subtask** button. It asks for a **Summary** and creates a new task
on the same goal, ordered right after its parent.

## Time tracking

On a task's detail pane:

1. Click **Track** to start a session. A running clock appears.
2. Optionally type a **Summary (optional)** of what you did.
3. Click **Stop** (or press **Enter** in the summary box).

Stopping creates a **Journal** entry linked to the task, covering the session — it appears under the
task's **Related** records and on the calendar. Starting a timer on another task stops and saves the
current one first.

!!! note "Forgotten timers"
    A session stops on its own after a maximum length (60 minutes unless changed in
    [Settings](settings.md#tasks-and-other-preferences)), and is saved as ending at that cut-off.
    With [desktop notifications](settings.md#notifications) on, the app also reminds you every
    15 minutes when no timer is running.

## Kanban

**Kanban** (sidebar, or **g k**) shows **bookmarked goals and their tasks**, one column per
**department**.

- Each column lists its **Goals**, with each goal's open tasks nested under the goal card.
- **Tasks — no goal** lists tasks in that department that aren't attached to a shown goal.
- Tasks appear if they are bookmarked *or* belong to a bookmarked goal.
- Finished tasks stay for 7 days, struck through, then drop off.
- Within a list, open tasks come first, then by priority and newest.

Click a card to go to its module. If the board is empty, bookmark some goals or tasks.

!!! tip "Keep tasks attached to goals"
    The board is designed to make goal-less tasks stand out. Linking a task to a goal moves it under
    that goal's card.

## Sprints

A **sprint** is a dated block of committed work — say *Tuesday morning* — drawn from your bookmarked
tasks. Open **Sprints** from the sidebar.

### Creating a sprint

1. Click **New sprint**.
2. Give it a **Name** (optional), a **Start** and an **End**.
3. Click **Create**.

The sprint also appears on the [Calendar](calendar.md) as a block.

### Committing tasks

The right-hand panel, **Bookmarked tasks**, lists unfinished bookmarked tasks not yet in the sprint,
most-slipped first. Tick the ones you want and click **Commit n (time)**.

The sprint board then shows:

- **Planned** — the total planned duration of the committed tasks.
- **Window** — the length of the sprint. If the plan is longer than the window, a warning says so.
- **n/m done** and a progress bar.
- A note listing any tasks **counted at the default** because they have no planned duration.
- **slipped n×** on tasks that were committed to earlier sprints and not finished.

Each committed task has a **Track** button for time tracking and an × to **Remove from this
sprint**.

### Reviewing

Tick a task's checkbox when it is done — ticking *is* the review, and it completes the task. When the
sprint is over, click **Close review** to lock it (**Reviewed**). **Re-open** unlocks it.

!!! warning
    The bin icon (**Delete this sprint**) deletes the sprint immediately, without asking.
