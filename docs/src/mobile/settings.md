# Settings reference

Open **Settings** from the gear at the bottom of the rail.

Settings is split into tabs, one per feature, shown as a row of chips at the top: **General**, **Server**, **Prayer times**, **Media**, **File collection**, **Checklists**, **Alerts** and **Calendar & widgets**. Each tab remembers where you had scrolled to.

Tabs for an optional feature start with a switch that turns the whole feature on or off. Switched off, its pages leave the rail and nothing about it runs in the background; the rest of that tab's settings are hidden until you switch it back on.

!!! warning "Tap Save"
    Changes take effect when you tap **Save** at the top right. One **Save** covers every tab. **Back** leaves without saving. The exceptions are **Language** and **Theme colour**, which apply the moment you tap them.

Prayer-time settings are on the [Prayer times](prayer.md) page itself, and player settings are in the [player](player.md#player-settings). This page covers everything else, tab by tab.

## General

### Language

**English**, **عربي** or **Français**. Applies immediately. In Arabic the whole interface is right-to-left. Your notes and files are never translated.

### Theme colour

The app's base colour. Tap one to apply it straight away:

- **Navy**, the default and the colour of the app icons, or one of seven other swatches (teal, green, purple, burgundy, orange, brown, graphite).
- The wallpaper button (**The phone's wallpaper colours**) follows the phone's own palette, as Android 12 and newer pick it from your wallpaper.
- **Other colour (hex)**: type any six-digit colour, such as `2E6B3A`. It applies as soon as the sixth digit is in.

Light and dark versions are made from the colour you pick, and follow the phone's dark mode.

### Notes

| Setting | Default | Meaning |
|---|---|---|
| **App name** | Nibras Mobile | The title at the top of the Notes page. Blank shows the app's own name in the current language. |
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

### Types and Categories

The lists of note types and categories, each entry a short **Code** and a **Name**.

- **Types**: one type per note. The code goes first in the file name.
- **Categories**: one category per note. The code goes last in the file name.

Use **Add type** / **Add category** to add one and the bin icon (**Remove**) to delete one. Keep codes short (one or two letters). The characters `~ / \ : * ? " < > |` can't be used.

The defaults are types `W` Work, `H` Home, `I` Ideas, `T` Todo and categories `P` Personal, `W` Work, `M` Misc.

!!! note "When connected to Nibras"
    With a Nibras server set up, both lists are replaced by the server's own modules and departments when the app starts and whenever you tap **Refresh notes + sync now**, so the phone and the server use the same codes. Changes you make here may be overwritten then.

Removing a code doesn't change existing notes. Notes that still use it show up under the **Bad taxonomy** filter on the Notes page.

### Setup tour

**Show the setup tour** walks through the folders again, explaining what each one is for.

### Backup

**Export settings** saves every setting on this phone to one JSON file that you choose where to put; **Import settings** loads such a file back, for a reinstall or a second phone. The settings on screen are saved first, so what you see is what goes into the file. The server password is not included. Loading a file restarts the app (**Imported N settings — restarting**).

## Server

**Use a Nibras server** (on by default): off, nothing connects anywhere, and the **From the server** band leaves the rail.

### Nibras server

Read-only browser for your Nibras desktop database. See [Nibras sync](sync.md#connect) for each field.

| Setting | Meaning |
|---|---|
| **Scheme** | `http` or `https` |
| **IP / host**, **Port**, **Context** | The server's address. **Full URL** shows the result. |
| **Username**, **Password** | Your Nibras account, used to sign in for sync. |
| **Files folder** | Where record attachments live on the phone (`Nibras` at the top of your storage by default). |

### Star button

What the star on server records does:

- **Bookmark on this device**: a private shortlist. Nothing is sent to the server. (Default.)
- **Un-bookmark in Nibras**: tapping the star takes the record off your server bookmarks. See [Server pages](server-pages.md#bookmark-the-star).

**Font size N×** sets the text size of Resources full text (1.5× by default).

### Background sync

| Setting | Default |
|---|---|
| **Sync with Nibras** | On |
| **Check every (sec)** | 30 |
| **Sync every (sec)** | 300 |

See [Background sync](sync.md#background-sync).

## Prayer times

A single switch, **Prayer times** (on by default): the prayer page, the athan, silencing and the widget. Off, the page leaves the rail and nothing is scheduled. Location, method and sounds are set on the [Prayer times](prayer.md) page itself. This switch is the way back after the page was switched off from the prayer page.

## Media

| Setting | Default | Meaning |
|---|---|---|
| **Media player pages** | On | The **Media** (audio library) and **Videos** pages. Off: both leave the rail; files opened from notes still play. |
| **Audio library folders** | | The folders the [Media](player.md) page scans. Add several with **Add folder**. |
| **Videos folder** | `Nibras` at the top of your storage | The folder the [Videos](player.md#videos) page shows, subfolders included. |
| **Gallery** | On | The [Gallery](gallery.md) page. Off: it leaves the rail. |
| **Gallery folder** | `Pictures` | The folder the Gallery shows. |

## File collection

See [Collect files](collect.md).

| Setting | Meaning |
|---|---|
| **File collection** | The Collect page and its daily run (on by default). Off removes both, whatever the automatic setting below says. |
| **Source folders** | Folders to gather files from. Untick a folder to skip it without removing it. |
| **Destination (New) folder** | Where collections are written. Your notes folder by default. |
| **Collect automatically every day** | Runs the collection on its own each day, **At** the time you set (15:00 by default). Files are moved. Off by default. |

## Checklists

See [Checklists](checklists.md).

| Setting | Default | Meaning |
|---|---|---|
| **Checklists** | On | Daily and weekly checklists, ticked per day and kept one file per week. Off: the page leaves the rail. |
| **Checklists folder** | notes folder + `/checklists` | One `.md` file per week. Blank uses the default. |

## Alerts

### Plan reminders

Reminders before upcoming Plans/Journal events from the server. See [Plan reminders](alarms.md#plan-reminders).

| Setting | Default |
|---|---|
| **Lead times (minutes)** | `60, 5` |
| **Reminder sound** (**Choose sound**) | Default |
| **Require dismissal** | Off |
| **Remind even on silent or vibrate** | Off |
| **Repeat sound (times)** | 4 |

### Status & chime

| Setting | Default | Meaning |
|---|---|---|
| **Persistent notification** | On | Ongoing notification with today's note count. It also keeps the app running so alarms ring on time. See [The status notification](alarms.md#the-status-notification). |
| **Chime** | On | Plays a sound at the interval you pick, within the window below. |
| **Chime every** | 15 min | **15 min**, **30 min**, **1 h** or **2 h**, counted from the start hour. |
| **Choose chime file** | phone's notification sound | Any audio file. |
| **Chime start hour**, **Chime end hour** | 8, 22 | The window the chime sounds in. |

See [The chime](alarms.md#the-chime).

### Quick timer

What the [quick timer](alarms.md#quick-timer) at the top of the Alarms page plays when it ends.

| Setting | Default |
|---|---|
| **Choose sound file** | **Default alarm sound** |
| **Volume N%** | 80% |
| **Plays N×** | 2× (1–10) |

## Calendar & widgets

| Setting | Default | Meaning |
|---|---|---|
| **Mini calendar** | On | The week strip at the top of the [Calendar](calendar.md) page. |
| **Calendar start hour**, **Calendar end hour** | 5, 22 | The hours the Calendar grid shows. |

### Recent notes widget

How far back the [Recent notes widget](widgets.md#recent-notes) reaches (**today only** up to 30 days, 3 by default), how many words before **Read more** (10–200, 40 by default), and its **Text size N sp** (10–28, 15 by default).

### Tasks board widget

The [Tasks board widget](widgets.md#tasks-board)'s **Text size N sp** (10–24, 13 by default) and how many **columns** it uses (1–4, 2 by default).

The widgets redraw when you tap **Save**, so a new size shows straight away.
