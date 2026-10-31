# Media player

Nibras Mobile includes an audio and video player built for working through long recordings: lectures, audiobooks, voice memos. It remembers where you stopped in every file, lets you drop timestamped bookmarks with a note on each, and can pick what to play next for you.

## The media library

Open **Media** (the headset icon) from the rail. It browses every audio and video file under your **Audio library folders** (set in the setup tour or in [Settings](settings.md) → **Media**; you can add several).

Three tabs:

| Tab | How it lists your files |
|---|---|
| **Library** | By the files' own tags: **Genres**, then artist, then album. Untagged files go under **Unknown genre**, **Unknown artist** or **Unknown album**, never hidden. |
| **Folders** | The folder tree, one level at a time. Each folder shows a peek at its first few tracks (**…and N more**), and **Recently bookmarked here** at the top. |
| **Bookmarked** | Every file that has bookmarks, newest bookmark first, with how many marks it has. The quickest way to pick up a half-finished listen. |

**Filter by name…** at the top searches the whole library as one flat list. It matches file names, artist, album and genre. **Rescan the library** (circular arrow) re-reads the folders after you've added files. The file you played last is highlighted in every list.

Tap any file to play it. It opens in the player and resumes where you last stopped.

Supported files include MP3, M4A/M4B, AAC, Opus, OGG, WAV, FLAC and AMR audio, and MP4, MKV, WebM, AVI, MOV and 3GP video.

!!! tip "Other ways to play"
    - The **Player** icon on your home screen, installed next to the Nibras Mobile icon, opens the player straight on the track that is loaded or was played last.
    - Audio or video **attached to a note** plays here when you tap it on the note card.
    - From another app (a file manager, for example), choose **Open with → Nibras Mobile** on an audio or video file.

**Media player pages** in [Settings](settings.md) → **Media** switches the **Media** and **Videos** pages off. They leave the rail, but files opened from notes or from another app still play.

## Videos

**Videos** (the film icon, just under Media in the rail) shows every video in the **Videos folder** and its subfolders, newest first, as cards. Each card has a frame from the video with its length, a bar along the bottom showing how far you've watched, and under it the folder, how long ago the file arrived, its size and its number of bookmarks.

- When the videos sit in several folders, a row of chips narrows the page to one folder. **All** shows them all again.
- **Rescan the folder** (circular arrow) looks again after new files arrive.
- Tap a card to play it in the player, with the picture on. Bookmarks, transcripts and resume work as for any other file.

The **Videos folder** is set in [Settings](settings.md) → **Media**. By default it's a `Nibras` folder at the top of your storage.

## The player screen

The player fills the screen. The top bar shows how much of the file you've played, as a percentage, then a bar and a count (**3/12**) for how far through the folder you are (files sorted by name), and a star. Under it is a row of [tabs](#the-tabs). The first tab, **Player**, holds the controls. From top to bottom:

- **The video**, for video files. **Enlarge video** gives it most of the screen and keeps the seek bar and the play and skip buttons under it. **Shrink video** brings the full controls back.
- **Transcript** (only when the file has one). See [Subtitles and transcripts](#subtitles-and-transcripts).
- **Seek bar** with the current time and the total length. Bookmarks are marked along it. Time runs left to right in every language.
- **The file name** and the folder it plays from. Under them are four small actions: a red bin (**Delete this file**), **Folder** (opens the folder in your file manager), a share icon (**Share** sends the file to another app) and **Star** (see [Starred files](#starred-files)).
- **Bookmark note (optional)**: type a label for the next bookmark here.
- The large **Add bookmark** button, beside the name.
- The **transport buttons**:

    | Top row | | |
    |---|---|---|
    | **+10** skip forward | **Play** / **Pause** | **−10** skip back |

    | Bottom row | | | |
    |---|---|---|---|
    | **Next** (⏭) | **In this folder** list | **Audio only** toggle | **−5** skip back |

    The **+10/−10** skip length is adjustable (see [Player settings](#player-settings)). **Audio only** turns off a video's picture, for example while driving, and shows **audio** while on. The ⏭ button carries a small icon showing what **Play next** will do.

- **Subtitles**, when the file has any.
- **Bookmarks (N)**: this file's bookmarks. See [Bookmarks](#bookmarks).

Playback continues in the background and with the screen off. The usual controls appear in the notification, on the lock screen and on Bluetooth headsets. Back returns you to the app and leaves the [mini player](navigation.md#the-mini-player) at the bottom.

### Before the next file starts

When a file ends and **Play next** is about to start another, a banner says **Playing next file in 5s**, with a **Stop** button. It shows on every tab and in the mini player. Tap **Stop** to stay on the finished file. Opening a file, or pressing play or ⏭ yourself, also cancels it.

## Bookmarks

A bookmark is a moment in the file plus an optional line of text.

1. While listening, optionally type a label in **Bookmark note (optional)**.
2. Tap **Add bookmark**.

The bookmark is placed a few seconds **before** the current moment (7 seconds by default), because you usually want the line you just heard. The player jumps back to that point and confirms with **✓ Bookmarked −7s**.

- **With subtitles on**, a bookmark with no typed label takes the subtitle line being spoken as its label (**✓ Bookmarked −7s · line captured**).
- **Tap again quickly to raise its priority.** A second tap within a few seconds (3 by default), while still at the same spot, doesn't add another bookmark or jump back again. It adds a priority label instead: `p2`, then `p3` on a third tap, and so on (**✓ priority added p2**).
- **Bookmark on pause**: when this is on, every pause (on screen, from a headset cable or from Bluetooth) drops a bookmark. The pause button shows a small bookmark badge while it's on. Handy when you might fall asleep listening. By default it follows your Bluetooth connection: see [Player settings](#player-settings).

The file's bookmarks are listed on the **Player** tab under the controls, as **Bookmarks (N)**, the latest point in the file first. Tap one to jump to it, or tap its bin icon to delete it.

### Where bookmarks are stored

Bookmarks are saved in a small text file next to the media file, with the same name and the extension `.tmk`:

```
lecture.mp3
lecture.tmk
```

```
[12:04] p2 the key definition
[1:03:15] summary starts here
```

Because it's a plain file next to the recording, the bookmarks travel with it to your computer and back. Lines the app doesn't recognise are kept, never overwritten. See [Bookmarks (.tmk)](../concepts/bookmarks-tmk.md).

## Starred files

A bookmark marks a moment. A **star** marks the whole file, so you can find it again quickly. Tap **Star** under the file name, or the star in the top bar, to star the file that's playing. Tap it again (**Remove the star**) to take it off.

Starred files are listed on the **Starred** tab, newest star first. Tap one to play it, or tap its star to remove it. A starred file that has been moved away is hidden until it's back, and deleting a file from the player drops its star.

## The tabs

Each tab is an icon, most with a count. The app remembers which tab you had open.

| Icon | Tab |
|---|---|
| Play | **Player**: the controls and this file's bookmarks |
| Star | **Starred**: your [starred files](#starred-files) |
| Folder | **In this folder**: the other files in the same folder |
| Open folder | **Recent folders**: folders you've played from; tap one to resume its most recent file |
| Clock | **Recently played**: files you've played recently |
| Gear | **Player settings** |

Opening a file from any of these lists plays it and takes you back to the **Player** tab.

## Player settings

| Setting | Range | What it does |
|---|---|---|
| **Skip Ns** | 5–60 s (default 10) | Length of the large skip buttons. |
| **Speed** | 0.75×–1.25× | Playback speed. |
| **Bookmark −Ns** | 0–20 s (default 7) | How far back a bookmark is placed. |
| **2-tap prio Ns** | 1–10 s (default 3) | How quickly a second tap must follow to raise priority instead of adding a bookmark. |
| **Volume** | −20 to +50 dB | Boost or lower this player's volume, for quiet recordings. |
| **Recent files kept** | 10–200 (default 50) | How many files **Recently played** remembers. |
| **Play next** | see below | What happens when a file ends, and what ⏭ does. |
| **Play next after a video ends** | on/off (default off) | Off: a finished video stays paused at its start, whatever **Play next** says. ⏭ still plays the next one. |
| **Sleep timer** | **Off**, 15m, 30m, 45m, 60m | Pauses playback after that long. Shows **pauses in …** while running. Works with the screen off. |
| **Night sleep timer** | on/off (default on) | Playback started at night pauses on its own. See below. |
| **Bookmark on pause** | on/off | Every pause adds a bookmark. |
| **Follow Bluetooth** | on/off (default on) | **Bookmark on pause** turns itself on while Bluetooth audio (headphones, a car, a speaker) is connected, and off when it isn't. While this is on, the box above shows what the connection decided and can't be ticked by hand. |

Every slider also has − and + buttons for fine steps. These settings are kept between sessions.

### Night sleep timer

With **Night sleep timer** on, anything you start playing between **From** and **Until** (21:00 and 05:00 by default) pauses after **Timer** minutes (20 by default, 5–90). While it runs, the sleep timer reads **night timer: pauses in …**. Switching the night timer off keeps it off until that night is over.

### Play next

| Mode | What plays next |
|---|---|
| **Off** | The next file in the folder is cued, paused. Nothing starts on its own. |
| **Same folder** | The next file in the folder plays automatically. |
| **Random old** | A file from anywhere in your library folders that you have never bookmarked and haven't touched in 90 days. If nothing is that old yet, the oldest ones are used. |
| **Random here** | The same kind of pick, but only from the folder around what's playing, including its subfolders. Inside a note's attachment folder, it searches the folder that holds all the notes, so it works through every note's attachments. |
| **Newest here** | The same folder as **Random here**, but the newest unbookmarked files instead of the oldest, for catching up on what just arrived. |

If there's nothing left to pick, the player says so, for example **Nothing left here — every file in this folder is bookmarked or already played.**

## Subtitles and transcripts

The player picks up two kinds of companion text files that sit next to a media file with the same name:

- **Subtitles** (`.srt`): `lecture.en.srt`, `lecture.fr.srt` or plain `lecture.srt`. A **Subtitles** row shows one chip per language plus **Off**, and the current line is shown under the seek bar, for audio as well as video.
- **Transcripts** (`.ai.txt`): `lecture.en.ai.txt`, `lecture.ar.ai.txt` or plain `lecture.ai.txt`, for example machine transcripts made on your computer. They have no timings, so the whole text is shown in a scrollable **Transcript** box above the controls, one chip per language. Use **Hide transcript** / **Show transcript** to fold it.

Transcripts are read-only on the phone, but you can select and copy text from them. A transcript can be removed with its delete button (**Delete the … transcript**).

## Deleting a recording

Tap the red bin (**Delete this file**) under the file name. The dialog **Delete this file?** lists everything that goes with it: its transcript files and its bookmarks. There is no recycle bin, so the file can't be brought back. After deleting, the player moves on.
