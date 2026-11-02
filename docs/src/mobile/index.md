# Nibras Mobile for Android

**Nibras Mobile** (نبراس محمول in Arabic, formerly called knotes) is the phone app of the Nibras personal-knowledge ecosystem. It is a fast note-taker first: one screen with your notes and a box to type the next one. Around that it adds the things you reach for on a phone during the day: a media player that remembers where you stopped, prayer times with the athan, alarms and a quick timer, daily checklists, a calendar, home-screen widgets, and an optional connection to a Nibras server.

## What makes it different

- **Your notes are plain text files.** Each note is one Markdown (`.md`) file in a folder you choose. There is no app database to lock them in. The same files open on your computer, sync with any folder-sync tool, and outlive the app. See [Plain-text notes](../concepts/plain-text-notes.md).
- **The file name carries the metadata.** When you wrote the note, its type, priority and category are all in the file name, so any tool can sort and filter them. See [File names and tags](../concepts/filenames-and-tags.md).
- **Capture now, classify later.** Saving a note takes one tap. You can tidy up the tags whenever you like. See [Capture, then classify](../concepts/capture-then-classify.md).
- **Works offline.** Everything in the **On this phone** part of the app works with no network at all. The server part is optional, and shows what was last synced when you are offline.
- **Three languages.** English, Arabic (عربي) and French (Français). In Arabic the whole interface switches to right-to-left.

## Install

Nibras Mobile needs Android 6.0 or newer. There are three ways to install it, and all three give the same file, signed with the same key:

- **From an F-Droid client** (F-Droid, Droid-ify, Neo Store…). Add the project's repository under the client's *Settings → Repositories*, or open this link on the phone:
  `https://mhm-fakih.github.io/nibras-fdroid/repo?fingerprint=4E6533C6F4783BD04CF7A9278CF5963A5E7DE77D486EA32FBD3AA4E5CCEDC2F3`
  Updates then arrive like any other app's.
- **With [Obtainium](https://obtainium.imranr.dev)**, which installs and updates straight from the project's GitHub releases. Each release carries a `-release.apk` and a `-debug.apk`; pick the **release** one.
- **Directly**: download the `knotes-…-release.apk` file from the [latest release](https://github.com/mhm-fakih/nibras-pim/releases/latest), open it, allow your browser or file manager to install unknown apps if Android asks, and tap **Install**.

After installing you have **two icons** on your phone: **Nibras Mobile**, which opens the app, and **Player**, which opens the [media player](player.md) straight on the track you last played.

!!! note "Updating"
    An update installs over the old version. Your notes are files in your own folder, so an update never touches them. Your settings are kept, and alarms, reminders and the athan are set again by themselves after the update. To move your settings to another phone, use **Settings → General → Backup** (see [Backing up](troubleshooting.md#backing-up)).

## First run

### 1. Grant file access

On the first screen, the app explains that it stores your notes as plain text files and asks for access to them. Tap **Grant file access**. On Android 11 and newer this opens the system's **All files access** page; switch it on for Nibras Mobile, then go back.

Full file access is needed because your notes, media and pictures live in ordinary folders that you choose, not inside the app.

### 2. The setup tour

On a fresh install, a short tour walks you through the folders the app reads and the parts of the app you want. The top of each page shows **Step N of 10**. **Next** moves on, **Back** goes back, and **Skip setup** is there on every page if you would rather explore first. Nothing in the tour is permanent: every answer can be changed in [Settings](settings.md) later.

| Page | What you choose |
|---|---|
| **Language · اللغة · Langue** | **English**, **عربي** or **Français**. The app switches straight away. Your notes and files are never translated. |
| **Welcome to Nibras Mobile** | A short introduction. |
| **Where your notes live** | The **Notes folder**. The home screen writes and lists notes here. If you already keep notes in a folder (synced from a computer, for example), point this at it and they appear immediately. |
| **Collecting files into notes** | A **File collection** switch, and where collected screenshots, photos and recordings go (**Collected notes go to**). See [Collect files](collect.md). |
| **Your media library** | A **Media player pages** switch, and the **Media folder** for audio and video. See [Media player](player.md). |
| **Pictures** | A **Gallery** switch, and the **Pictures folder** it shows. See [Gallery](gallery.md). |
| **Prayer times** | A **Prayer times** switch. Location, method and sounds are set on the [Prayer times](prayer.md) page itself. |
| **Connect to Nibras** | A **Use a Nibras server** switch, the **Server address**, **Username** and **Password**, and **Sync with Nibras in the background**. Leave the address blank to skip. See [Nibras sync](sync.md). |
| **Reminders and the chime** | Switches for **Chime** and **Keep a status notification**, plus **Allow notifications**. |
| **You're set up** | A summary of every folder the app will read and which parts are **On** or **Off**. Tap **Start using Nibras Mobile**. |

A part you switch off leaves the [rail](navigation.md) and does nothing in the background. Each one has the same switch at the top of its tab in Settings, so you can turn it back on later.

Next to each folder field, the tour checks what is actually there: **Folder found, with files in it.**, **Folder found — it's empty for now.**, **This folder doesn't exist yet.** (tap **Create** to make it), or **That path is a file, not a folder.** Tap **Choose** to pick a folder instead of typing a path.

!!! tip "A good default"
    A folder named `Notes` at the top of your phone's storage is a fine place to start. If you plan to sync with a computer, point your sync tool at the same folder.

!!! note "Upgrading from an older version"
    If the app was already set up on this phone, the tour is not shown. You can open it any time with **Show the setup tour** in **Settings → General**.

### 3. Allow notifications

On Android 13 and newer, the app asks once for permission to show notifications. Allow it. Without it, Android will not show reminders, the chime, the athan notification or the player controls.

## Changing the language later

The language list is the first item in [Settings](settings.md) → **General**. Each language is written in its own script, so you can find yours whatever language the screen is in. On Android 13 and newer you can also change it from the system's **App languages** page. More in [Languages](../concepts/languages.md).

In Arabic, notifications are laid out right to left as well, even on a phone whose own language is English. Numbers keep Western digits (0–9), so times, file names and bookmarks stay readable by other tools.

## Where to go next

- [Getting around: the left rail](navigation.md)
- [Writing and editing notes](notes.md)
- [Finding notes](search-filters.md)
- [Media player](player.md)
- [Prayer times and athan](prayer.md)
- [Alarms, the quick timer and the chime](alarms.md)
- [Daily and weekly checklists](checklists.md)
- [Home-screen widgets](widgets.md)
- [Settings reference](settings.md)
- [Backup, troubleshooting and FAQ](troubleshooting.md)
