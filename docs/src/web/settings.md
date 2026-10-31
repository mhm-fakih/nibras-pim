# Settings

**Settings** (sidebar, or **g s**) configures the web app for everyone using this installation. You
need the **Settings** capability to open it — usually only administrators have it. Most changes save
as soon as you flip a switch, press **Enter** or leave a field; sections with a **Save** button save
when you click it.

!!! note "Some preferences belong to your browser"
    A few things are remembered per browser rather than in Settings: the width of the sidebar and
    list, whether the sidebar is collapsed, records **Per page**, the mini calendar's size, the
    Calendar page's view, the **Quick note** bar's folded state, text sizes on the reading pages,
    Insights' periods — and whether *you* receive desktop notifications.

## Application

**App name** is the name shown at the top of the sidebar and in the browser tab (default
*Nibras*).

## Session

**Stay signed in for (hours)** is how long a sign-in lasts in a browser that has been closed or
asleep (12 unless changed; from 1 hour up to a year). A page that stays open renews its sign-in by
itself and never runs out — which is what keeps a wall screen showing the
[weekly checklists](dashboards.md#the-weekly-checklists) signed in. Only administrators can change
the value; it applies from the next sign-in. Press **Enter** or leave the field to save.

## Pages and features

**Pages** shows or hides tool pages in the sidebar: Calendar, Kanban, Dashboards, All bookmarks,
Indicators, Insights, Sprints, Reports, Maintenance, Parameters, Admin, Batch entry, Import files,
Import notes, Image renamer, Static website and Audit log. Settings and Help are always available.
Hiding a page only removes it from the sidebar.

**Features** switches off parts of the interface that aren't pages:

| Feature | What it controls |
|---|---|
| **RSS feeds** | The **RSS (bookmarked)** link in the sidebar. |
| **Standard reports per module** | The built-in report shortcuts in the sidebar (your saved searches stay). |
| **Release codename** | The release nickname next to the version number. |
| **Resource detail fields** | Full text, Highlights, Comments, URL and Read on, on resources. Hiding them changes no data. |
| **Repository 2** / **Repository 3** | The **Repo2** / **Repo3** buttons on records. |
| **New resource from a URL** | The link button beside **New** on Resources. |

## Notifications

- **Enable desktop notifications (overdue tasks/planners, missing journal entry)** — turns on your
  browser's notifications for this app. The first time, the browser asks for permission. If it says
  **Notifications are blocked for this site**, allow them in the browser's site settings.
- **Journal reminder hour** — after this hour (24-hour clock, default 17), you are reminded once a
  day if you haven't written a journal entry. Click **Save**.

With notifications on, the app checks every few minutes for **overdue** or **due-within-a-week**
tasks and plans, and nudges you every 15 minutes when no [timer](tasks-kanban-sprints.md#time-tracking)
is running.

!!! note
    Notifications only work while the web app is open in a browser tab. The on/off switch is stored
    in your browser, so each person (and each browser) chooses separately.

## Indicator auto-collection

**Run time** is when indicators with **Auto-collect** switched on compute and save their value each
day (default 23:50). See [Goals, habits & indicators](goals-indicators.md#creating-an-indicator).

## Append to description

The **Prefix** and **Suffix** wrapped around text added with the **Append to…** box under a record's
description or notes. Write `\n` for a new line; the default prefix starts a new paragraph.

## Modules

The **Modules** table lists every record type. For each one:

| Column | Meaning |
|---|---|
| **Module** | Its internal key (fixed). |
| **Label** | The name shown in the sidebar and lists. |
| **Code** | A short code of up to 4 letters (e.g. `T`) used in commands, filenames and on the apps. Must be unique. |
| **Color** | The module's accent colour, shown as a bar on list rows. |
| **Order** | Position in the sidebar. |
| **Sync order** | The order in which the module's parameters are handed to the phone and desktop apps. |
| **Enabled** | Show or hide the module everywhere. |
| **Bucket /100**, **Bucket by type** | How the module's record folders are laid out in the repositories (grouped by hundreds of IDs, and/or by type). |

Edit **Label**, **Code**, **Order** or **Sync order**, then press **Enter** or click the row's
**Save**. Switches and colours save immediately.

!!! warning "Switching a module off"
    A disabled module disappears from the sidebar, the Today page and the apps' type lists.
    Its records are not deleted — turn it back on and they return. When **Journal** is off, clicking
    a day in the mini calendar no longer creates a day journal.

Statuses, types and contexts are not set here but on the [Parameters](administration.md#parameters)
page.

## Repositories

The folders on the server where record files live:

- **Repo 1 — sandbox / portable**, **Repo 2 — main**, **Repo 3 — dismissed**.
- **Checkout folder — bookmark symlinks** — where links to bookmarked records' folders go.
- **File-explorer command** — the program the **Repo** buttons launch on the server's desktop.

Click **Save**. These are paths on the server machine, so they are normally set once by whoever
installed it.

## Local notes

**Notes folder** is the folder of Markdown notes shown on [Local notes](import-capture.md#local-notes)
— a path on the server; leave it blank for the default. The hint under the field says how many notes
were found, or that the folder doesn't exist. **Tag bank file (fallback only)** is only needed if
your courses don't yet carry **Note tags**.

## File search

What the [Search page](search.md#files) finds among files. The table lists the sources —
**Repo 1**, **Repo 2**, **Repo 3** and **Note companion folders** — with the folder each one is and
how many files are **Indexed**. For each source tick:

- **Names** — find its files by name (every file);
- **Contents** — also read the text of the file types listed below. Ticking **Contents** ticks
  **Names** too.

Below the table:

- **Contents of these types** — the file extensions whose text is read, comma-separated (PDF,
  Office, DjVu…). Other files are found by name only.
- **Largest file to read (MB)** — bigger files are found by name only.

Click **Save**; the index then updates itself. Folders and files whose names start with a dot are
skipped, and links are not followed. From then on the index keeps itself up to date every
10 minutes, reading only what changed.
**Re-read all files** starts over from scratch. The line under the buttons says how many files are
indexed and when the last scan ran; **Show files whose text could not be read** lists the files that
failed, grouped by the kind of problem — scanned pages with no text layer, empty files,
password-protected or damaged files, and so on.

!!! note
    Reading DjVu text needs the `djvutxt` program on the server; until it is installed, `.djvu`
    files are found by name only, and the page says so.

## Export

- **Open the file after "Export to file"** — open the checkout file on the server's desktop after a
  manual export.
- **Open the file after a new calendar-day journal is auto-exported** — the same, when clicking a day
  creates a new day journal.

## On bookmarking

Two optional actions when a record is bookmarked (both **Off** by default):

- **Create a symbolic link in the checkout folder** — a link to the record's repository-1 folder,
  named from its module code and ID (e.g. `W18`), with its checkout text written alongside.
- **Open the record's checkout text** — write the record's text file and open it.

Removing a bookmark always removes its link.

## Tasks and other preferences

| Setting | Meaning |
|---|---|
| **Auto-stop a timer after (minutes)** | Maximum length of a time-tracking session (default 60). |
| **Language codes** | Comma-separated codes usable as `'xx` tags in commands and search (default `ar,en,fr,fa,de`). See [Languages](../concepts/languages.md). |
| **Today page: records per column** | How many created and modified records each module shows on Today (default 5, at most 50). |
| **Status on completion** | The status a task moves to when its completion box is ticked — on the record, in a sprint review or from the phone. **Leave unchanged** to keep the status as it is. |
