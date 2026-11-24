# Capture inbox: importing from phone & desktop

Nibras works on a **capture now, classify later** principle: the phone and desktop apps let you jot
things down quickly — offline, as plain-text notes — and the web app is where those captures become
proper records. See [Capture then classify](../concepts/capture-then-classify.md) for the idea
behind it.

There are four ways in:

| Source | How it arrives | Where you handle it |
|---|---|---|
| **Phone app** — a note you *push* | Directly as a record in its module, already bookmarked | The module itself (look under **★ Bookmarked** or **Recently created**) |
| **Desktop app / notes folder** | As Markdown files in the notes folder the server reads | **Local notes** and **Import notes** |
| **Capture tools** — Telegram, Firefox bookmarks, online videos | As notes written into the same notes folder | **Local notes** and **Import notes** |
| **Any files** | Dropped into a folder on the server, named as a command | **Import files** |
| **Any files**, one by one | In a folder on the server (`~/new`), looked at before filing | **File processor** |

## From the phone

When you push a note from the phone app, it is created on the server as a record in the module its
type says (task, journal, plan, note, goal, writing or resource), with its priority and department,
and **bookmarked**. Nothing needs importing — open the module and review it. If the phone was
offline, it sends its queue when the connection returns. See [Sync](../concepts/sync.md).

## From the capture tools

Three small command-line tools bring material from outside into the notes folder, as ordinary
notes ready for **Import notes**:

| Tool | What it writes |
|---|---|
| **Telegram** | One **N** note per run, *Telegram ‹chat› export*, with the messages' photos, videos and files in its companion folder. |
| **Firefox bookmarks** | One **R** note per run, *Firefox bookmarks export*, the bookmarks grouped by the date they were added. |
| **Media** (via yt-dlp) | One **R** note per run: a line of your own, then each video's title, length, site and address, with the video, an MP3 and the subtitles in the companion folder. |

Their priority and department come from the notes folder's own defaults; when the folder sets no
department, the notes get department **X**, so they are never left without one. Give them their
real department while classifying, or at import. Remember that **R** notes need a resource type —
see the tip under [Import notes](#import-notes). The tools themselves are set up on the computer
that runs them; see the `capture/` folder of the Nibras source.

**YouTube download** (sidebar → **Import & export**, for admins) runs the Media tool from the web,
on the server, when the capture tools are set up there. Paste one or more YouTube ids or links
(other sites yt-dlp reads work too), then choose:

- **Both** (an MP4 and an MP3 of each), **Video** (the MP4 only) or **Audio** (the MP3 only);
  **Voice quality** makes the MP3 mono 64 kbps.
- **Subtitles** on or off: an `.srt` in the spoken language, the uploader's if there are any, else
  the automatic ones, cleaned of their repeated lines; **Inside the video** also embeds them in the
  MP4.
- An optional **note text**; empty uses the video's title.

**Download** starts it in the background; the page shows its progress and log, and **Stop** ends
it. **Recent downloads** lists the last 50 with their logs, kept across server restarts; one a
restart cut short shows as *interrupted*, and any failed one has **Retry**. When it is done, the note's path is shown with **Import as a record**, which imports it at once
(an **R** note still needs its resource type — use **Import notes** to set one). The tools' folder
is the setting `capture.dir` (default `~/prj/nibras-pkm/capture`).

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

!!! note "Nightly tidy"
    Every night the server tidies the notes folder: a note left empty for more than a day is moved
    to the `.deleted` folder (the desktop app's trash) unless its companion folder holds files, and
    empty companion folders are removed.

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
3. Click **Scan / preview**. Each item shows what it will create (**Create ‹module›: “title”**),
   what will happen to it, and the folder it will go to — or why it can't be imported. The folder
   is the module's real layout in repository 1, by type and by hundreds of IDs as set in
   [Settings → Modules](settings.md#modules).
4. Click **Import** on one item, or **Import all**.

A **file** is moved into the new record's repository-1 folder and renamed to its title; a **folder**
becomes the record's folder. Imported items stay on the page with a ✓ line naming the new record and
where its files went, until you scan again.

## File processor

**File processor** settles a folder one entry at a time — the files you haven't named as commands,
each looked at before it becomes a record.

- **Folder to settle** is `~/new` at first; type another folder and press Enter (it's remembered).
  The list shows one level of it, folders first; click a folder to open it, **↑** or the path to go
  back. Hidden entries and the `DELETED` folder aren't listed.
- Click a file to see it: an image, a text file (Markdown, plain text, CSV…), a PDF, or a recording
  plays in the page. **‹ ›** step through the folder's files.
- On the right, say what record to make of it:
  - **Module** and **Title** — the title starts as the file name without its extension.
  - **Its text into** (text files) — which field the file's text goes to: **Full text** for a
    resource, **Description** otherwise, or any other long field.
  - **Day** and **Goes to** — the day the file name holds (`IMG_20261114_…`, `scan 20261114….pdf`,
    `2026-11-14`), else the day the file was last changed, and the date field it goes to
    (**Published on** for a resource), or **Nowhere**.
  - **Then the file** moves into the new record's repository-1 folder, or — an image — becomes its
    cover (`cover.jpg`), or stays where it is.
- **Create record** makes it and moves on to the next file; the green line links to the record.
  **Skip** leaves the file for later; **Put aside** moves it into `DELETED/` in the folder being
  settled — nothing is deleted.
- **Delete** asks first, then moves the file (or folder) to the desktop's trash, where the file
  manager can restore it until the trash is emptied.
- **A whole folder as one record:** the ✓ button on a folder's row selects it instead of opening
  it. Its contents are listed, the title is the folder name, the day comes from that name, and
  **Text from** can take the text of one of its text files. Creating the record moves everything
  in the folder into the record's repository-1 folder, and the emptied folder goes.

The module, and per module the text and date fields you chose, are remembered in this browser.

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
