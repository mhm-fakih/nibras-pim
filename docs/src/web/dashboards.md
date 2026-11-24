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
    | **Kanban board** | The [Kanban](tasks-kanban-sprints.md#kanban) | **Show the description with each task** and **Show the full description** (both on by default) |
    | **File content** | The text of a file in one of the repositories | **Repo** (1–3), **Relative path** |
    | **Quick tasks (quick-tasks.md, open ones)** | The desktop app's `quick-tasks.md` (at the top of the notes folder) with the ticked tasks left out | — |

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

## The weekly checklists

**Checklists (week)** (sidebar → **Library**) shows the daily and weekly checklists you keep on the
phone, one week at a time, as a read-only wall page. It opens in a new tab with nothing around it,
and fits the whole week onto the screen — the text gets smaller rather than the page scrolling.
The checklists are kept in the database, which the phone syncs with (see
[Ticking on the web](#ticking-on-the-web)); a phone edit shows here once the phone has synced.

- Each group of items is a block with its own score. Daily groups have a column per day; weekly
  groups have a single **Week** column.
- A mark is ✓ for done, ◐ for partly done, or an empty box. Days still to come are faded, today's
  column is shaded, and the row under the mouse is highlighted so you can follow it across.
- The header shows the week number and dates, the share of daily items done on each day, and the
  arrows **Previous week** / **Next week**.
- Items in Arabic are set right to left.

The page looks after itself: when the phone's edit reaches the server it re-reads the week within
about 15 seconds, and it reloads entirely every few minutes (4 unless changed). Click the small
reload button in the header to choose another interval, or **Never reload**; the choice is saved
for every screen. Adding `?reload=N` to the page's address sets the interval (in minutes) for that
one screen only.

!!! note
    Unlike a dashboard's **View** link, this page needs a sign-in. Sign in once on the screen's
    browser: an open page renews its sign-in by itself, so it stays signed in.

### Ticking on the web { #ticking-on-the-web }

**Checklists** (sidebar → **Library**, just above the wall page) shows the same lists inside the
app, to tick and annotate:

- **Click** a box to cycle its mark: done (✓), then partly done (◐), then clear — as on the phone.
- **Right-click** a box to set its mark and write a **note** for that day. A box with a note has a
  small yellow corner; hover over it to read the note.
- **Click an item** to see its history: every day it was ticked or noted, newest first.
- **Edit lists** adds groups and items, renames them, switches a group between daily and
  **Weekly**, and takes groups or items off the list. Changes apply from the week on screen: an
  item added in week 46 starts that week, and one taken off in week 46 is last shown in week 45.
  Nothing is deleted — the item's history stays, and **Not on this week's list** at the bottom
  offers **Reopen**. A time in front of a group's name (`07:30 Start of day`) sets the phone's
  reminder.
- The arrows move between weeks; **This week** comes back. The box icon opens the wall page.
- The **printer** icon prints **paper checklists**: one A4 sheet listing the week's groups and
  items once, each with **three squares, one per day**, to tick by pen over three days, and a notes
  box per day at the foot. Pick the first day (today by default) and press **Print**; the browser's
  print dialog opens on your current printer (choose *Save as PDF* there for a file). A weekly
  item has one square across the three days. A long list is laid out in two columns, and the type
  shrinks until everything fits one page.

**Sync with the phone.** The phone keeps its own copy and exchanges changes with the server each
time it syncs, so it works offline too. If the same day of the same item was changed on both the
phone and the web between two syncs, the phone's change stands by default. To make the web's
change stand instead, open the **⋮** menu on the Checklists page and pick **The web's change
stands**.

The week files the phone used to write (in the notes folder's `checklists` folder) were read in
once, the first time the page opened; they are left in place. **⋮ → Import the week files again**
reads them again, adding only what the database doesn't have yet.

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
