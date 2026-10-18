# Dashboards & Insights

## Dashboards

A **dashboard** is a page you assemble from ready-made **regions** and leave open — on a wall
screen, a TV or a spare monitor.

### Building a dashboard

1. Open **Dashboards** from the sidebar and click **New dashboard**.
2. Enter a **Dashboard name**, the number of grid **Columns**, and **Auto-refresh (min)** (0 turns it
   off).
3. Click **Add region** for each block you want, and choose its **Component**:

    | Component | Shows | Settings |
    |---|---|---|
    | **Quote of the day** | The most recent bookmarked note of a given type | **Note type code**, e.g. `quote` |
    | **Calendar (filtered by type)** | A week of journal and planner entries | **Event type name** (blank for all), **Start hour**, **End hour** |
    | **Goals habit heatmap** | Habit heatmaps of goals marked *Show habit heatmap on dashboards* | — |
    | **Kanban board** | The [Kanban](tasks-kanban-sprints.md#kanban) | — |
    | **File content** | The text of a file in one of the repositories | **Repo** (1–3), **Relative path** |

4. Set how many grid **Columns** each region spans, and use the arrows to **Move up** / **Move
   down**. Each region shows a live **Preview**.
5. Click **Save**.

In the dashboard list, use the eye icon to **View** a dashboard, the pencil to **Edit**, and the bin
to **Delete**.

!!! tip "Matching calendar types"
    The calendar region's **Event type name** matches the type *name* in both Journal and Planner, so
    you can define the same name (say "family") in both on the [Parameters](administration.md#parameters)
    page — each may have its own colour.

### Showing it on a screen

**View** opens the dashboard on its own, without the sidebar or any other app chrome, in a new tab.
It refreshes its data every couple of minutes and reloads itself on the **Auto-refresh** interval,
so it can run unattended for days.

!!! warning "Dashboard view links don't need a sign-in"
    The view page is made for a TV with nobody logged in, so **anyone who can reach the server and
    knows the link can see that dashboard's content**. Only put regions on a dashboard that you are
    comfortable showing that way, and don't share its link widely.

## Insights

**Insights** helps you keep on top of reviewing recent work. Choose a **period** (from *Last week*
to *Last year*; default the last 3 months) and a **reviewed in** window (from *Last day* to *Last
month*; default the last week). Both are remembered by your browser.

The page shows:

- **Where your records live** — a doughnut chart of the records created in the period, by module.
- **Needs review first** — for each module, the share of the period's records you have updated
  within the review window, lowest first.
- **Activity by type** — a card per module with weekly bars of **Created** and **Updated** records,
  and records waiting for review. Click a record to open it.

"Reviewed" simply means *edited* within the review window — open a record, improve it, save it.
