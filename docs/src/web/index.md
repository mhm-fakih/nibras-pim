# The Nibras web app

The web app is the browser side of Nibras: the place where your tasks, journals, plans, resources,
notes, goals and writings live as **records** on a server, ready to be searched, linked, scheduled
and reviewed. The phone and desktop apps capture things quickly — often offline — and the web app is
where those captures end up, get classified and get worked on.

If you are new to how Nibras thinks about notes, records and filenames, read
[Concepts](../concepts/index.md) first; this guide is about using the web app itself.

## Opening the app and signing in

Open the web app at the address your administrator gives you. You will see a **Sign in** card.

1. Enter your **Username** and **Password**.
2. Click **Sign in**.

After signing in you land on the page you were trying to reach, or on **Tasks** if you came straight
to the sign-in page.

!!! note "First sign-in"
    If your administrator has asked you to choose your own password, a **Change your password**
    dialog appears straight away. Enter the **Current password**, then the **New password** twice,
    and click **Change password**. You can't use the app until this is done.

To sign out, click the **Sign out** icon next to your name at the bottom of the left sidebar.
Your session also ends on its own after some hours (12 by default); just sign in again.

!!! warning "Sign-in problems"
    - **Invalid username or password.** — check the spelling; usernames and passwords are
      case-sensitive.
    - **Can't reach the server — the backend may be down.** — the app itself is not answering. This
      is not a password problem; contact your administrator.

## The layout: three panes

Every module (Tasks, Notes, Goals, …) uses the same Outlook-style layout:

| Pane | What it holds |
|---|---|
| **Left sidebar** | App name and version, the global search box, a mini calendar, report shortcuts and saved searches for the current module, the module buttons, the tool pages, and your name with **Sign out**. |
| **Middle pane (list)** | The records of the current module, with a search box, filters, paging and the **New** button. |
| **Right pane (detail)** | The selected record — or the form when you create or edit one. A **Quick note** bar sits at its bottom. |

A thin **status bar** runs along the bottom with version information, a **check for updates** link
and a **Help** link.

You can rearrange the layout to suit you; each of these is remembered by your browser:

- **Resize** the sidebar or the list by dragging its right edge.
- **Collapse** the sidebar with the **‹** chevron in its header; click the thin bar that remains to
  bring it back.
- When the sidebar is short of room, the tool buttons shrink to icons; hover an icon to see its
  name.

!!! tip "On a phone or narrow window"
    The sidebar becomes a drawer behind a **☰** menu button in the top-left corner, and the module
    view shows either the list or the record — never both. Use **Back to list** to return.

## Finding your way

=== "Modules"

    The **module buttons** in the sidebar (Tasks, Journal, Planner, Resources, Notes, Goals,
    Writings, Departments, Courses, …) open each record type. Which modules appear, in what order,
    and with which short code (shown beside the name, e.g. `T`) is set in
    [Settings → Modules](settings.md#modules).

=== "Tool pages"

    Below the modules are the **tool pages**: Today, Calendar, Kanban, Dashboards, Insights,
    Sprints, Reports, All bookmarks, Flash cards, Highlights, Local notes, and — depending on your
    permissions — Parameters, Admin, Batch entry, Import files, Import notes, Image renamer, Static
    website, Maintenance, Audit log, Trash, Settings, Users, Roles & permissions, and **Help &
    shortcuts**. An administrator can hide pages you don't use (see
    [Settings → Pages](settings.md#pages-and-features)).

=== "Keyboard"

    Press **?** for the shortcut overlay, **/** to jump into the global search, and **g** followed
    by a letter to go to a module — for example **g t** for Tasks. See
    [Keyboard shortcuts](keyboard-shortcuts.md).

=== "Search"

    Type in **Search everything…** at the top of the sidebar and press **Enter** to search every
    module (and your local notes folder) at once. See [Search, filters & reports](search.md).

Clicking the button of the page you are already on refreshes its data.

## The Today page

The app's home page, **Today**, shows one card per main module (Tasks, Journal, Planner, Resources,
Notes, Writings, Goals) with two short lists:

- **Created** — the newest records, with the time (today) or date they were created.
- **Modified** — records edited after they were first written.

Click a title to open that record in its module. The number of records per list is set in
[Settings → Tasks](settings.md#tasks-and-other-preferences) (**Today page: records per column**).

## What you can do depends on your role

Your role is shown under your name in the sidebar. Most people are **Data entry** (create and edit
records) or **Read only** (look, search and read, but no **New**, **Edit**, **Delete** or quick
note). Administrators can grant or withhold each capability — see
[Accounts & permissions](administration.md). If you open a page you aren't allowed to use, you'll see
**You don't have permission to view this page.**

## Where to go next

- [Modules & records](records.md) — creating, editing and organising records.
- [Search, filters & reports](search.md) — finding things.
- [Calendar](calendar.md), [Tasks, Kanban & Sprints](tasks-kanban-sprints.md),
  [Goals, habits & indicators](goals-indicators.md).
- [Capture inbox: importing from phone and desktop](import-capture.md).
- [FAQ & troubleshooting](faq.md).
