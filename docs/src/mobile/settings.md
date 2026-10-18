# Settings reference

Open **Settings** from the gear at the bottom of the rail.

!!! warning "Tap Save"
    Changes on this page take effect when you tap **Save** at the top right. **Back** leaves without saving. The one exception is **Language**, which switches the moment you tap it.

Prayer-time settings are on the [Prayer times](prayer.md) page itself, and player settings are in the [player](player.md#player-settings). This page covers everything else, in the order it appears.

## Language

**English**, **عربي** or **Français**. Applies immediately. In Arabic the whole interface is right-to-left. Your notes and files are never translated.

## Notes

| Setting | Default | Meaning |
|---|---|---|
| **App name** | knotes | The title at the top of the Notes page. |
| **Default type code (new notes)** | first type | The type chip preselected for new notes. Stays selected after each save. Blank means the first type in the list. |
| **Default category / dept code (new notes)** | first category | The category preselected for new notes. Blank means the first category. |
| **Default priority (new notes)** | 2 | The priority preselected for new notes. |
| **Top priority (widgets)** | 4 | Which priority the **Top priority** and **Today's tasks** widgets list, and the KPI widget counts. Tasks added from the widget get this priority. |
| **Notes directory** | `Notes` at the top of your storage | Where your notes live. Use the folder button to choose one. |
| **AI answers directory** | notes folder + `-ai` | The read-only [AI answers](ai-answers.md) folder. Blank uses the default. |
| **Timestamp format** | `yyyy_MM_dd-HHmmss` | How the time at the start of a new note's file name is written. **Now:** shows an example, or **invalid format**. |
| **Preview lines** | 3 | Lines of text shown on each note card. |
| **Priority min**, **Priority max** | 1, 5 | The range of priorities offered. |

!!! tip "Timestamp letters"
    In the timestamp format, `HH` is the hour, `mm` is minutes and `MM` is the **month**. Mixing them up gives odd file names. Changing the format only affects new notes. Existing file names are kept.

## Nibras server

Read-only browser for your Nibras desktop database. See [Nibras sync](sync.md#connect) for each field.

| Setting | Meaning |
|---|---|
| **Scheme** | `http` or `https` |
| **IP / host**, **Port**, **Context** | The server's address. **Full URL** shows the result. |
| **Username**, **Password** | Your Nibras account, used to sign in for sync. |
| **Files folder** | Where record attachments live on the phone. |

### Star button

What the star on server records does:

- **Bookmark on this device**: a private shortlist. Nothing is sent to the server. (Default.)
- **Un-bookmark in Nibras**: tapping the star takes the record off your server bookmarks. See [Server pages](server-pages.md#bookmark-the-star).

**Font size N×** sets the text size of Resources full text (1.5× by default).

## Media

| Setting | Meaning |
|---|---|
| **Audio library folders** | The folders the [Media](player.md) page scans. Add several with **Add folder**. |
| **Gallery folder** | The folder the [Gallery](gallery.md) shows. |
| **Calendar start hour**, **Calendar end hour** | The hours the [Calendar](calendar.md) grid shows (5 and 22 by default). |

## Plan reminders

Reminders before upcoming Plans/Journal events from the server. See [Plan reminders](alarms.md#plan-reminders).

| Setting | Default |
|---|---|
| **Lead times (minutes)** | `60, 5` |
| **Reminder sound** (**Choose sound**) | Default |
| **Require dismissal** | Off |
| **Repeat sound (times)** | 4 |

## Background sync

| Setting | Default |
|---|---|
| **Sync with Nibras** | On |
| **Check every (sec)** | 30 |
| **Sync every (sec)** | 300 |

See [Background sync](sync.md#background-sync).

## Status & chime

| Setting | Default | Meaning |
|---|---|---|
| **Persistent notification** | On | Ongoing notification with today's note count. It also keeps the app running so alarms ring on time. |
| **Mini calendar** | On | The two-week strip at the top of the Calendar page. |
| **Recent notes widget** | 3 days back, 40 words, text size 15 sp | How far back the [Recent notes widget](widgets.md#recent-notes) reaches, how many words before **Read more**, and its text size. |
| **Prayer times are hidden** | (only shown when off) | Brings back the Prayer times page after it was switched off on that page. |
| **File collection** | On | The Collect page and its daily run. Off removes both. |
| **15-minute chime** | On | A chime at :00, :15, :30 and :45. |
| **Choose chime file** | built-in chime | Any audio file. |
| **Chime start hour**, **Chime end hour** | 8, 22 | The window the chime sounds in. |

## File collection

See [Collect files](collect.md).

| Setting | Meaning |
|---|---|
| **Source folders** | Folders to gather files from. Untick a folder to skip it without removing it. |
| **Destination (New) folder** | Where collections are written. Your notes folder by default. |
| **Collect automatically every day** | Runs the collection on its own each day, **At** the time you set (15:00 by default). Files are moved. |

## Types and Categories

The lists of note types and categories, each entry a short **Code** and a **Name**.

- **Types**: one type per note. The code goes first in the file name.
- **Categories**: one category per note. The code goes last in the file name.

Use **Add type** / **Add category** to add one and the bin icon (**Remove**) to delete one. Keep codes short (one or two letters). The characters `~ / \ : * ? " < > |` can't be used.

The defaults are types `W` Work, `H` Home, `I` Ideas, `T` Todo and categories `P` Personal, `W` Work, `M` Misc.

!!! note "When connected to Nibras"
    With a Nibras server set up, both lists are replaced by the server's own modules and departments when the app starts and whenever you tap **Refresh notes + sync now**, so the phone and the server use the same codes. Changes you make here may be overwritten then.

Removing a code doesn't change existing notes. Notes that still use it show up under the **Bad taxonomy** filter on the Notes page.

## Setup tour

**Show the setup tour** walks through the folders again, explaining what each one is for.
