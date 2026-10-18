# Writing and editing notes

The **Notes** page is the app's home screen. It lists the notes in your notes folder and has a box at the bottom for writing the next one.

!!! tip "Why is the list nearly empty?"
    The list opens showing **today's notes only**. The phone is mostly for capturing, so "what I wrote today" is the default view. The **Filters** header above the list shows the active filter. Tap it and use **Clear all**, or pick other days on the calendar, to see more. See [Finding notes](search-filters.md).

## The top bar

- The app name and version, and under them **N notes · M today**: notes in the folder, and how many were written today.
- When background sync is on, a third line shows the sync state, for example **synced 5m ago** or **offline · last synced yesterday**.
- **Sort notes** (the lines icon) chooses the order and grouping. See [Finding notes](search-filters.md).
- **Search** (magnifier) opens a search box.

If you are connected to a Nibras server and have plans coming up, a collapsible **Upcoming plans (N)** panel appears above the list with the next five.

## Write a note

1. At the bottom of the screen, tap **New note — tap to expand** if the form is folded away.
2. Type in **Write a quick note…**.
3. Check the three rows of chips above the box:
    - **Type**: what kind of note it is (for example Work, Ideas, Todo).
    - **Prio**: priority, a number (1–5 by default).
    - **Cat**: the category.
4. Tap the send arrow (**Save note**).

The note is saved straight away as a new file. The chips keep their values for the next note, because the next note is usually the same kind. A note always has a type and a category: tapping a chip switches to it but never clears it.

The default type, priority and category for new notes are set in [Settings](settings.md). Tap the arrow on the **New note** header to fold the form away while you read. The app remembers whether it is folded.

!!! note "Drafts are safe"
    What you type is kept as a draft while you type. If you close the app, swipe it away or the phone restarts, the text is back in the box next time.

### How the note is named

Every note is a file named after the moment you saved it, followed by its type, priority and category:

```
2026_06_10-143025~W~p3~P.md
```

That is 10 June 2026 at 14:30:25, type `W`, priority 3, category `P`. If two notes are saved in the same second, the second one gets `-2` added to the time. The time format can be changed in Settings (**Timestamp format**). The full scheme is explained in [File names and tags](../concepts/filenames-and-tags.md).

## Note cards

Each note in the list is a card showing:

- The first few lines of the note (3 by default, see **Preview lines** in Settings). Arabic lines are right-aligned automatically, line by line.
- **Attached files**, if any (see below).
- Badges for type, priority and category, and the time.
- A cloud icon if the note has been sent to Nibras: an upload cloud while it is waiting (**Queued for Nibras**), a cloud with a tick once it has arrived (**Pushed to Nibras**).
- **Edit**, a delete icon, and **More** (⋮).

Tap a card to **read** it.

### The More (⋮) menu

| Item | What it does |
|---|---|
| **Push to Nibras** | Creates this note as a record on your Nibras server. Shows **Queued — will sync** while waiting, and **Push again to Nibras** once sent. See [Nibras sync](sync.md#push-a-note-to-nibras). |
| **Open files folder (N)** | Opens the note's attachment folder in a file manager. Only shown when there are attachments. |
| **Edit in the composer below** | Loads the note into the box at the bottom (the header says **Editing note**), so you can change its text and tags without leaving the list. Tap **Cancel** to stop editing. |
| **Delete** | Deletes the note, after asking. |

## Read and edit a note

Tapping a card opens it full-screen. Two chips at the top switch between **Read** and **Edit**.

**Read**

- Markdown is shown formatted: headings, bold, lists and so on.
- **Tap a clause to highlight it.** Tap again to remove the highlight. The highlight is written into the note itself as `==…==`, so it shows up on the computer and the server as well. The header counts how many clauses are highlighted.

**Edit**

- The whole screen is the editor. Your changes are saved when you pause typing and again when you leave (Back, switching to **Read**, or opening another note). There is no save button.

**Tags at the bottom**

In both modes, the **Type**, **Prio** and **Cat** chips stay pinned under the text. Changing one renames the note's file to match. The note keeps its original time.

## Attachments

A note can have files attached. They live in a **companion folder** next to the note, with the same name plus `.d`:

```
2026_06_10-143025~W~p3~P.md
2026_06_10-143025~W~p3~P.d/
    photo.jpg
    recording.m4a
```

The card lists up to five attached file names. Tap one to open it: audio and video play in the app's own [player](player.md) (their icon is tinted), and anything else opens in whichever app handles it. If there are more, a **… +N more** row opens the whole folder. Attachments are usually created by [Collect files](collect.md), or by syncing from your computer. See [Companion folders](../concepts/companion-folders.md).

## Delete a note

Tap the delete icon on the card, or **Delete** in the ⋮ menu. You are asked **Delete this note?** before anything happens.

!!! warning "Notes with attachments can't be deleted here"
    If the note's companion folder still holds files, the dialog says **Can't delete this note** and lists them. Deleting the note would leave those files with nothing naming them. Empty the folder first (**Open files folder**), then delete the note.

A deleted note is a deleted file. There is no recycle bin.

## Files that aren't notes yet

Anything else in your notes folder still appears in the list:

- Plain `.md` or `.txt` files from other apps show with no type, priority or category. Give them tags and they become ordinary notes.
- Files whose names don't follow the note scheme are listed by file name, so you can find them and deal with them. The **Non-standard names** filter finds them all.
- Old two-field `.txt` notes from earlier versions are still read, and are converted to the current `.md` format the next time you edit them.

## The floating buttons

At the bottom right of the list:

- **Refresh notes + sync now** (circular arrow) re-reads the folder, refreshes the type and category lists from the server, and runs a full sync straight away.
- On long lists, **Go to top** and **Go to bottom** appear as well. When the list is grouped, there is a button to **Collapse all groups** / **Expand all groups**.
