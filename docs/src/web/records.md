# Modules & records

Everything you keep in the web app is a **record** in a **module**. Each module is its own record
type with its own fields, but they all share the same list, detail pane and form, so once you know
one you know them all.

## The modules

| Module | What it is for | Notable fields |
|---|---|---|
| **Tasks** | Things to do | Summary, Description, Status, **Goal**, % complete, Planned duration (min), Start, End, Urgent, Order #, **Repeats**, **People** |
| **Journal** | What happened | Summary, Description, Type, Start, End, Energy |
| **Planner** | What you plan to happen, at a time | Summary, **Plan**, **Reality**, Type, Status, Start, End, % complete, **Repeats** |
| **Resources** | Books, articles, videos… | Type, Title, Author, Publisher, Edition, ISBN, Short description, Description, Full text, Highlights, Comments, URL, Status, Year, Pages, Read, Read on, % complete |
| **Notes** | Ideas and reading notes | Summary, Short description, Content, Reaction, Type, Status, **Resource** (the book the note is about), Pages |
| **Goals** | Outcomes you work towards | Summary, Description, Code, Status, Type, % complete, Start, End, Completed, **Show habit heatmap on dashboards** |
| **Writings** | Longer pieces you write | Summary, Body, Short description, Slug, Source, Status, Type, Born on |
| **Contacts** | People | Name, Code, Job title, Telephones, Email, Address, About |
| **Departments** | Top-level areas of life or work | Name, Code, Summary |
| **Courses** | Subjects inside a department | Title, Code, Department, **Note tags** |
| **Excerpts** | Passages from a book | Summary, Book, Chapter(s), Chapter #, Title, Content |

Most content modules also carry **Highlights**, **Notes**, **Department**, **Course**, **Priority**,
**Bookmarked** and **Tags**.

Your administrator decides which modules are switched on (and their labels and order) in
[Settings → Modules](settings.md#modules), so your sidebar may show fewer.

!!! note "Departments and courses classify everything"
    A record's **Department** and **Course** say where it belongs. When you pick a **Course** on a
    form, its department is filled in for you. Departments and courses are modules of their own, so
    you can bookmark them, give them files and notes, and search them like any other record.

## Browsing a module

Click a module in the sidebar. The middle pane lists its records, newest first, 25 to a page. Each
row shows a ★ if bookmarked, the title, the creation date, and chips for code, status, type, the
linked resource and up to three tags. A cover thumbnail appears when the record has a cover image.

Use the search box and filters at the top of the list to narrow it down — see
[Search, filters & reports](search.md).

## Creating a record

1. Click **New** at the top of the list (or press **n**).
2. Fill in the form. Text areas marked **(Markdown)** accept [Markdown](#formatting-text).
3. Click **Save** — or **Cancel** to throw the draft away.

The new record opens in the detail pane with a **✓ Saved** card above it.

!!! tip "Faster ways in"
    - The **Quick note** bar at the bottom of the detail pane creates a note in one step: type, then
      **Ctrl+Enter** or **Add**. If the first line is short it becomes the title and the rest the
      content. Click the bar's header to fold it away.
    - [Batch entry](batch-commands.md#batch-entry) creates many records from one line each.
    - On **Resources**, the link icon beside **New** (**New resource from a URL**) lets you paste a
      web address and click **Fetch**: the server reads the page and opens a pre-filled form —
      title, author, publisher, description, year and the article text. Check the fields, then
      **Save**; the page's main image is saved as the cover. The panel stays open so you can fetch
      several links in a row.

## The detail pane

Select a record to see it on the right. Along the top:

| Control | What it does |
|---|---|
| ☆ / ★ | **Bookmark** or remove the bookmark. Bookmarks drive the Kanban, sprints, the bookmarked TOC, RSS feeds and what your phone receives. |
| 🚩 (tasks) | Mark or clear **Urgent**. |
| ◯ / ✓ (tasks, goals) | Mark **complete** or reopen. Completing a bookmarked record also removes its bookmark. |
| **Track** (tasks) | Start a time-tracking session — see [Time tracking](tasks-kanban-sprints.md#time-tracking). |
| **+ Subtask** (tasks with a goal) | Create a follow-on task under the same goal. |
| **Edit** | Open the form (or press **e**). |
| ⤢ | Open the record in the full [reading page](#the-reading-page) in a new tab. |
| **Repo1**, **Repo2**, **Repo3** | Open the record's folder in each file repository — see [Files](#files-and-folders). The badge is the number of files. |
| **Delete** | Move the record to **Trash** (after confirming). |

Below the buttons, chips show the record's **ID**, repeat rule, status, type, context, department,
course, the linked resource (click to open it) and when it was **Created**.

Further down you'll find:

- **Tags** — click **+ add tag…** to attach an existing tag or type a new one; click a tag's × to
  remove it.
- **People** — on tasks (when Contacts is enabled): **+ add person…**.
- **The fields**, rendered as formatted text. Checklists (`- [ ] item`) can be ticked right here.
- **Append to Description… / Append to Notes…** — a box under those fields that adds text to the end
  without opening the form. Press **Ctrl+Enter** to add.
- **Related** — linked records in other modules (a goal's tasks, a task's time-tracking journals, a
  resource's notes…). Click one to open it.
- **Edit full text in your editor** — see [Writings & checkout files](writings-fulltext.md).
- **Files** — see below.
- **History** — every change to this record, field by field (visible if you may see the audit
  log).

On a **contact**, an **Open tasks** list shows what is still open with that person. On a
**resource**, **Add a note about this resource** creates a real note linked to the resource, with the
course pre-filled from the resource's own.

### Formatting text

Long text fields are written in Markdown and shown formatted. The [Help](keyboard-shortcuts.md)
page has a cheatsheet. Highlights with `==double equals==` are shown as highlighted text. Text in
Arabic and other right-to-left scripts is aligned automatically from its first letters — see
[Languages](../concepts/languages.md).

## Editing, quick edits and bookmarks

- **Edit** (or **e**) opens the full form. Save or Cancel as usual.
- With a record selected you can change one field from the keyboard without the form: **p**
  priority, **#** type, **?** status, **@** context, **d** department, **c** course, **\*** toggle
  bookmark. A small picker appears; choose a value and it is applied at once. See
  [Keyboard shortcuts](keyboard-shortcuts.md#quick-edit).
- To change many records at once, tick them in the list and type a command — see
  [Batch edit](batch-commands.md#batch-edit).

## Repeating tasks and plans

**Tasks** and **Planner** entries have a **Repeats** switch on the form. Pick **Daily**,
**Weekly**, **Monthly**, **Yearly** with an **Every N** interval, or **Custom (cron)**.

=== "Tasks"

    A repeating task exists one occurrence at a time. When you complete it, the next one is
    created. Turn on **Fixed schedule — repeats on the date even if not completed** to have the next
    occurrence appear by date regardless.

=== "Planner"

    A repeating plan is a series: the record holds the rule and the calendar draws every occurrence.
    Choose when it **Ends** — **Never**, **On date** or **After N occurrences**. An occurrence only
    becomes a record of its own once you open, move or annotate it. See
    [Calendar](calendar.md#repeating-plans).

## Merging duplicates

In Tasks, Journal, Planner, Resources, Notes, Goals and Writings you can merge two records: **drag
one row in the list onto another**. A **Merge** dialog explains what will happen — text fields are
joined, empty fields on the survivor are filled from the other, tags are combined, linked records
follow the survivor, and files move into a `<id>-files` folder. Use **Swap** to choose which record
survives, then **Merge**. The absorbed record goes to Trash.

The [Import notes](import-capture.md#finding-duplicates) page can also scan a whole module for likely
duplicates.

## Deleting and restoring

**Delete** never destroys a record outright: it is moved to **Trash** (sidebar → **Trash**), which
lists deleted and merged records across every module. Click the restore icon to put a record back
where it was.

## Files and folders

Each record can have files kept in up to three **repositories** — folders on the server:

| Repository | Meaning |
|---|---|
| **Repo 1 · sandbox** | The working, portable copy. Checkout files and covers live here. |
| **Repo 2 · main** | The archive. |
| **Repo 3 · dismissed** | Things set aside. |

In the **Files** section of the detail pane, choose **Upload to**, click **Upload** and pick a file.
Click a file name to download it; the bin icon deletes it. A file named `cover.jpg` (or `.png`,
`.webp`…) is shown as the record's cover.

!!! note "Open-folder buttons"
    **Repo1 / Repo2 / Repo3** (and the keys **f 1/2/3**) open the folder in a file manager *on the
    computer running the server*. They are useful when the server runs on your own desktop; from
    another device, use **Upload** and the file links instead. The Repo 2 and Repo 3 buttons can be
    hidden in [Settings → Features](settings.md#pages-and-features).

## The reading page

The ⤢ button opens a record on its own narrow, distraction-free tab. From there:

- **Edit** sends you back to the main window with the form open.
- **Highlight** (when the record has body text) lets you click sentences to highlight them. Each
  click marks the text and adds it to the record's highlights; click again to remove it. Choose
  **Sentence** or **Clause** granularity — the choice is remembered per record. Click **Done
  highlighting** when finished.

Calendar entries open in this reading page too.
