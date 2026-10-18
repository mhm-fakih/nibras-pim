# Capture inbox: importing from phone & desktop

Nibras works on a **capture now, classify later** principle: the phone and desktop apps let you jot
things down quickly — offline, as plain-text notes — and the web app is where those captures become
proper records. See [Capture then classify](../concepts/capture-then-classify.md) for the idea
behind it.

There are three ways in:

| Source | How it arrives | Where you handle it |
|---|---|---|
| **Phone app** — a note you *push* | Directly as a record in its module, already bookmarked | The module itself (look under **★ Bookmarked** or **Recently created**) |
| **Desktop app / notes folder** | As Markdown files in the notes folder the server reads | **Local notes** and **Import notes** |
| **Any files** | Dropped into a folder on the server, named as a command | **Import files** |

## From the phone

When you push a note from the phone app, it is created on the server as a record in the module its
type says (task, journal, plan, note, goal, writing or resource), with its priority and department,
and **bookmarked**. Nothing needs importing — open the module and review it. If the phone was
offline, it sends its queue when the connection returns. See [Sync](../concepts/sync.md).

## Local notes

**Local notes** shows the plain Markdown notes folder that the desktop app writes — the same folder
**Import notes** imports from. Because the server reads the folder itself, you can browse it from
any browser.

- **By day** groups the notes by when they were written; **By subject** groups them by department and
  course, using the `#tags` in each note.
- **Filter these notes — text, tag or course…** narrows the list.
- Click a note to open it: you can edit the text and **Save** it back to the file, or **Import as
  ‹module›** to turn it into a record.
- **Add note** writes a new note into the folder. Choose a **Type** (N — Note, T — Task, J —
  Journal, R — Resource, P — Plan, G — Goal, W — Writing) and a **Priority**, put the title on the
  first line, and add a tag such as `#A.subject` to file it under a department. Click **Write note**.
- The refresh icon (**Re-read the folder**) picks up changes made elsewhere.

Importing from here explains what will happen — a record is created (under the note's course, if
it has one), any companion files are copied into the record's folder, and the note is moved into the
`imported/` subfolder so it disappears from the list. Afterwards, **Open the record** takes you to
it.

!!! note "How subjects are recognised"
    A note's `#A.subject` tag is matched against the **Note tags** field of your **Courses** (the
    canonical tag first, then any aliases). Give your courses their tags and the **By subject** view
    names them properly. See [Filenames and tags](../concepts/filenames-and-tags.md).

## Import notes

**Import notes** imports the notes in the folder in bulk, card by card.

Each note's **filename** carries its metadata —
`date~TYPE~pPRIORITY~DEPT.md`, for example `2026_06_15-214006~N~p3~A.md`:

| Part | Sets |
|---|---|
| date and time | the record's creation date |
| `TYPE` | the target module, by its code (`N` note, `T` task, …) |
| `p3` | the priority |
| `A` | the department, by its letter code |

The first line of the note becomes the title and the rest the description. Files in the note's
companion folder are copied into the record's folder. See
[Filenames and tags](../concepts/filenames-and-tags.md) and
[Companion folders](../concepts/companion-folders.md).

To import:

1. Check the **Import folder** at the top (a folder on the server; **Save** if you change it).
2. Optionally narrow the cards with the **Type** and **Priority** filters.
3. Read each card: its chips show the module, priority, department and time taken from the
   filename. **Similar existing records** beside it shows records with close titles — click one to
   check it before importing a duplicate.
4. Click **Import**. The new record's card appears right under the note, and the note file moves to
   `imported/`.

A card that says **Missing type, priority, department — can't import** (or **unrecognized
filename**) needs its filename fixed first — rename it in the desktop app, then reload the page.

!!! tip "Resource notes need a resource type"
    Notes of type **R** become resources, and a resource needs a type before it can be filed. Pick a
    **Default resource type (for R notes)** at the top of the page, or click one of the type chips on
    an individual card to override it (click again to go back to the default).

### Finding duplicates

The **Find duplicates already in the database** panel scans a whole module for records entered
twice. Choose the **Module** and a **Method**, then click **Scan**:

| Method | What it does |
|---|---|
| **Fast — title match (instant)** | Compares titles. |
| **Cheap AI — embeddings, same language** | Compares meaning, within one language (about a minute). |
| **Smart AI — any language** | Judges candidate pairs with a language model, across languages (a few minutes). |

The AI methods are only available when the server has a local AI service set up; otherwise they are
greyed out. Each pair found shows a similarity score and a **Merge** button, which opens the same
[merge dialog](records.md#merging-duplicates) as dragging in a list.

## Import files

**Import files** turns files and folders into records in one go. Its upper part is
[Pending sync](writings-fulltext.md#pending-sync); the lower part, **Import files / folders by
name**, works like this:

1. Put files or folders in a **drop folder** on the server and name each one as a
   [batch command](batch-commands.md) — for example `t ?done Final report.pdf`.
2. Enter the **Drop folder** (remembered for next time) and choose the **Default module** for names
   that don't start with a module code.
3. Click **Scan / preview**. Each item shows what it will create, where it will go, or why it can't
   be imported.
4. Click **Import** on one item, or **Import all**.

A **file** is moved into the new record's repository-1 folder and renamed to its title; a **folder**
becomes the record's folder.

## Image renamer

**Image renamer** helps you tidy a folder of pictures (screenshots, scans, photos) on the server
before filing them.

1. Enter the **Directory path** (a folder on the server) and click **Load**.
2. Step through the images and give each a meaningful **File name** — the extension is kept for you.
   Renaming moves on to the next image.

It is built for the keyboard:

| Key | Action |
|---|---|
| **←** / **h** | Previous image |
| **→** / **l** / **Space** | Next image |
| **r** / **F2** / **Enter** | Edit the name |
| **Enter** (while editing) | Rename and go to the next image |
| **Esc** | Cancel editing |
| **d** / **Del** | Delete — no confirmation |
| **u** | Undo the last delete or rename |

Deleted images are moved aside rather than destroyed, which is what makes **u** possible.
