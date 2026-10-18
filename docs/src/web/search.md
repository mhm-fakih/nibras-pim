# Search, filters & reports

There are four ways to find records, from quickest to most powerful:

1. The **list filters** of the module you are in.
2. The **report shortcuts** and **saved searches** in the sidebar.
3. The **global search**, across every module.
4. The **Reports** page, for column-picked tables you can print.

## Filtering a module's list

At the top of every module list:

| Control | What it does |
|---|---|
| **Search …** box | Finds records whose text contains what you type. While a search is active, each row shows *where* it matched (e.g. **Description:** …) with the term in bold. |
| **Status**, **Type** | Show one status or type (modules that have them). **Any** clears it. |
| **Priority** | Show one priority, **P0**–**P5**. |
| ☆ (**Bookmarked only**) | Show only bookmarked records. |
| **On day** | Show records belonging to one date. Journals and plans use their own date; other modules use the creation date. |
| **Per page** | 10, 25, 50, 100 or 200 rows — remembered by your browser. |

Active report and day filters appear as chips under the filters; click a chip's × to remove it.
The total number of matching records is shown at the bottom, next to the pager.

## Report shortcuts in the sidebar

Under **‹Module› reports** the sidebar offers ready-made filters for the current module. Each shows
how many records match; empty ones are greyed out.

| Report | Shows |
|---|---|
| **★ Bookmarked** | Bookmarked records |
| **🚩 Urgent** | Urgent tasks |
| **No status** / **No type** | Records still missing that classification |
| **No department** / **No priority** | Records not yet filed or prioritised |
| **With tags** | Records with at least one tag |
| **Overdue** / **Due soon (7d)** | Records whose end date has passed / falls within a week |
| **High priority (≥3)** | Priority 3 and above |
| **Recently created** / **Recently updated** | Fresh activity |

Only the reports that make sense for the module are listed (for example, **Overdue** only where
records have an end date). Your administrator can hide this section in
[Settings → Features](settings.md#pages-and-features).

The **RSS (bookmarked)** link under the reports is described in [RSS feeds](rss.md).

## Saved searches

Below the reports, **Saved searches** lists your own searches for the current module. Click one to
run it. Click the gear (**Manage saved searches**) to add, edit or delete them:

1. Click **New report**.
2. Give it a **Name** and a **Priority** (used for ordering).
3. Choose a **Query type**:
    - **Search form** — a set of list filters, written as parameters, e.g.
      `bookmarked=true&highPriority=true`. Running it simply filters the list.
    - **HQL** — a query over the app's records, e.g. `from Task where bookmarked = true`.
    - **SQL (native)** — a raw database query.
4. Click **Preview** to try it, then **Save**.

Queries that return records show them as cards in the middle pane — click one to open it, or the ×
to go back to the list. Other results (counts, columns) open in a table dialog.

!!! warning "SQL is powerful"
    A native SQL saved search runs exactly what you write. A statement that changes data reports
    "*n* row(s) affected" — it has really changed them. Use **Preview** with care, and prefer
    **Search form** or **HQL** when you only want to look.

## Global search

Type in **Search everything…** at the top of the sidebar (or press **/**), then **Enter**. The
**Search** page shows matches from every module, grouped by module, with the matching words
highlighted. Matches from your [local notes folder](import-capture.md#local-notes) appear under
**Local notes**. Click a result to open it.

You can mix free text with **filter tokens**:

| Token | Meaning | Example |
|---|---|---|
| `p4` | priority 4 | `p4 linux` |
| `#code` | type code | `#art budget` |
| `?code` | status code | `?done report` |
| `@code` | context code | `@home` |
| `dCODE` | department code | `dA` |
| `cCODE` | course code | `cALG` |
| `*` / `*-` | bookmarked / not bookmarked | `* travel` |
| `!` / `!-` | urgent / not urgent | `! invoice` |
| `'xx` | language (codes from Settings) | `prayer 'ar` |

`d…` and `c…` are only treated as filters when the code really exists, so ordinary words starting
with those letters still search as text.

!!! tip "Check for duplicates before adding"
    Start a search with `~` to find **near-duplicates** instead of exact matches, e.g.
    `~ hand over the old laptop`. Each result shows a similarity percentage — useful before you
    create a record that may already exist.

!!! note "No results at all?"
    If global search finds nothing and shows a warning that **the search index is empty**, an
    administrator needs to click **Rebuild search index** on the [Admin](administration.md#admin-page)
    page once.

## A day across all modules

Click a date in the sidebar's mini calendar. With the Journal module on, this opens that day's
journal (see [Calendar](calendar.md#the-mini-calendar)). With Journal switched off, the middle pane
instead shows every record from that day across all enabled modules.

## The Reports page

**Reports** (sidebar) builds tables you can run again and print.

1. Click **New report**.
2. Enter a **Report name**, choose a **Record type** and **Rows per page**.
3. Under **Tick what to show**, pick the columns; put them in order with the up and down
   arrows under **Order, left to right**.
4. Under **Which records**, choose:
    - **Point and click** — **Contains text**, **Status**, **Type**, **Tag**, **Created from** /
      **Created to**, plus any of the sidebar report presets; or
    - **Query** — write an HQL query yourself, e.g. `from Book where type.code = 'art' order by id
      desc`. The query's own `order by` decides the sort.
5. Pick **Sort by** and the direction, add optional **Notes**, check the preview and **Save**.

In the report list, use **Run** to view it in the app, **Printable view** to open a clean
black-on-white page in a new tab (then click **Print**), **Edit** or **Delete**.

!!! note
    Values appear by their full names (a resource type reads "Article", not its code). Very large
    reports are cut off at a safe size in the printable view, and the page says so.
