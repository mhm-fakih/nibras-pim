# knotes for Android

**knotes** is the phone app of the Nibras personal-knowledge ecosystem. It is a fast note-taker first: one screen with your notes and a box to type the next one. Around that it adds the things you reach for on a phone during the day: a media player that remembers where you stopped, prayer times with the athan, alarms, a calendar, home-screen widgets, and an optional connection to a Nibras server.

## What makes it different

- **Your notes are plain text files.** Each note is one Markdown (`.md`) file in a folder you choose. There is no app database to lock them in. The same files open on your computer, sync with any folder-sync tool, and outlive the app. See [Plain-text notes](../concepts/plain-text-notes.md).
- **The file name carries the metadata.** When you wrote the note, its type, priority and category are all in the file name, so any tool can sort and filter them. See [File names and tags](../concepts/filenames-and-tags.md).
- **Capture now, classify later.** Saving a note takes one tap. You can tidy up the tags whenever you like. See [Capture, then classify](../concepts/capture-then-classify.md).
- **Works offline.** Everything in the **On this phone** part of the app works with no network at all. The server part is optional, and shows what was last synced when you are offline.
- **Three languages.** English, Arabic (عربي) and French (Français). In Arabic the whole interface switches to right-to-left.

## Install

knotes is distributed as an Android app package (`.apk`). It needs Android 6.0 or newer.

1. Download the `.apk` file to your phone.
2. Open it. If Android asks, allow your browser or file manager to install unknown apps.
3. Tap **Install**, then **Open**.

!!! note "Updating"
    To update, install the newer `.apk` over the old one. Your notes are files in your own folder, so an update never touches them. Your settings are kept too.

## First run

### 1. Grant file access

On the first screen, knotes explains that it stores your notes as plain text files and asks for access to them. Tap **Grant file access**. On Android 11 and newer this opens the system's **All files access** page; switch it on for knotes, then go back.

Full file access is needed because your notes, media and pictures live in ordinary folders that you choose, not inside the app.

### 2. The setup tour

On a fresh install, a short tour walks you through the folders the app reads. The top of each page shows **Step N of 9**. **Next** moves on, **Back** goes back, and **Skip setup** is there on every page if you would rather explore first. Nothing in the tour is permanent: every answer can be changed in [Settings](settings.md) later.

| Page | What you choose |
|---|---|
| **Language · اللغة · Langue** | **English**, **عربي** or **Français**. The app switches straight away. Your notes and files are never translated. |
| **Welcome to knotes** | A short introduction. |
| **Where your notes live** | The **Notes folder**. The home screen writes and lists notes here. If you already keep notes in a folder (synced from a computer, for example), point this at it and they appear immediately. |
| **Collecting files into notes** | Where collected screenshots, photos and recordings go (**Collected notes go to**). See [Collect files](collect.md). |
| **Your media library** | The **Media folder** for audio and video. See [Media player](player.md). |
| **Pictures** | The **Pictures folder** shown by the Gallery. See [Gallery](gallery.md). |
| **Connect to Nibras** | Optional **Server address** of a Nibras server. Leave it blank to skip. See [Nibras sync](sync.md). |
| **Reminders and the chime** | Switches for **Quarter-hour chime**, **Keep a status notification**, **Sync with Nibras in the background**, plus **Allow notifications**. |
| **You're set up** | A summary of every folder the app will read. Tap **Start using knotes**. |

Next to each folder field, the tour checks what is actually there: **Folder found, with files in it.**, **Folder found — it's empty for now.**, **This folder doesn't exist yet.** (tap **Create** to make it), or **That path is a file, not a folder.** Tap **Choose** to pick a folder instead of typing a path.

!!! tip "A good default"
    A folder named `Notes` at the top of your phone's storage is a fine place to start. If you plan to sync with a computer, point your sync tool at the same folder.

!!! note "Upgrading from an older version"
    If knotes was already set up on this phone, the tour is not shown. You can open it any time from the bottom of Settings with **Show the setup tour**.

### 3. Allow notifications

On Android 13 and newer, knotes asks once for permission to show notifications. Allow it. Without it, Android will not show reminders, the chime, the athan notification or the player controls.

## Changing the language later

The language list is the first item in [Settings](settings.md). Each language is written in its own script, so you can find yours whatever language the screen is in. On Android 13 and newer you can also change it from the system's **App languages** page. More in [Languages](../concepts/languages.md).

In Arabic, numbers keep Western digits (0–9), so times, file names and bookmarks stay readable by other tools.

## Where to go next

- [Getting around: the left rail](navigation.md)
- [Writing and editing notes](notes.md)
- [Finding notes](search-filters.md)
- [Media player](player.md)
- [Prayer times and athan](prayer.md)
- [Home-screen widgets](widgets.md)
- [Settings reference](settings.md)
- [Backup, troubleshooting and FAQ](troubleshooting.md)
