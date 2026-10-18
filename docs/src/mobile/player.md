# Media player

knotes includes an audio and video player built for working through long recordings: lectures, audiobooks, voice memos. It remembers where you stopped in every file, lets you drop timestamped bookmarks with a note on each, and can pick what to play next for you.

## The media library

Open **Media** (the headset icon) from the rail. It browses every audio and video file under your **Audio library folders** (set in the setup tour or in [Settings](settings.md); you can add several).

Three tabs:

| Tab | How it lists your files |
|---|---|
| **Library** | By the files' own tags: **Genres**, then artist, then album. Untagged files go under **Unknown genre**, **Unknown artist** or **Unknown album**, never hidden. |
| **Folders** | The folder tree, one level at a time. Each folder shows a peek at its first few tracks (**…and N more**), and **Recently bookmarked here** at the top. |
| **Bookmarked** | Every file that has bookmarks, newest bookmark first, with how many marks it has. The quickest way to pick up a half-finished listen. |

**Filter by name…** at the top searches the whole library as one flat list. It matches file names, artist, album and genre. **Rescan the library** (circular arrow) re-reads the folders after you've added files.

Tap any file to play it. It opens in the player and resumes where you last stopped.

Supported files include MP3, M4A/M4B, AAC, Opus, OGG, WAV, FLAC and AMR audio, and MP4, MKV, WebM, AVI, MOV and 3GP video.

!!! tip "Other ways to play"
    - Audio or video **attached to a note** plays here when you tap it on the note card.
    - From another app (a file manager, for example), choose **Open with → knotes** on an audio or video file.

## The player screen

From top to bottom:

- **Progress**: how far through the file you are, as a percentage, and how far through the folder (files sorted by name).
- **Transcript** (only when the file has one). See [Subtitles and transcripts](#subtitles-and-transcripts).
- **Seek bar** with the current time and the total length. Time runs left to right in every language.
- **The file name**, and under it a small red **Delete this file** link.
- **Bookmark note (optional)**: type a label for the next bookmark here.
- The large **Add bookmark** button.
- The **transport buttons**:

    | Top row | | |
    |---|---|---|
    | **+10** skip forward | **Play** / **Pause** | **−10** skip back |

    | Bottom row | | | |
    |---|---|---|---|
    | **Next** (⏭) | **In this folder** list | **Audio only** toggle | **−5** skip back |

    The **+10/−10** skip length is adjustable (see [Player settings](#player-settings)). **Audio only** turns off a video's picture, for example while driving, and shows **audio** while on. The ⏭ button carries a small icon showing what **Play next** will do.

Playback continues in the background and with the screen off. The usual controls appear in the notification, on the lock screen and on Bluetooth headsets. Back returns you to the app and leaves the [mini player](navigation.md#the-mini-player) at the bottom.

## Bookmarks

A bookmark is a moment in the file plus an optional line of text.

1. While listening, optionally type a label in **Bookmark note (optional)**.
2. Tap **Add bookmark**.

The bookmark is placed a few seconds **before** the current moment (7 seconds by default), because you usually want the line you just heard. The player jumps back to that point and confirms with **✓ Bookmarked −7s**.

- **With subtitles on**, a bookmark with no typed label takes the subtitle line being spoken as its label (**✓ Bookmarked −7s · line captured**).
- **Tap again quickly to raise its priority.** A second tap within a few seconds (3 by default), while still at the same spot, doesn't add another bookmark. It adds a priority label instead: `p2`, then `p3` on a third tap, and so on (**✓ priority added p2**).
- **Bookmark on pause**: when this is ticked in Player settings, every pause (on screen, from a headset cable or from Bluetooth) drops a bookmark. The pause button shows a small bookmark badge while it's on. Handy when you might fall asleep listening.

Open the **Bookmarks** panel (bookmark icon, below the controls) to see the file's bookmarks, newest first. Tap one to jump to it, or tap its bin icon to delete it.

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

## The panels

A row of icons under the controls opens one panel at a time. Tap the same icon again to close it.

| Icon | Panel |
|---|---|
| Bookmark | **Bookmarks**: this file's bookmarks (open by default) |
| Folder | **In this folder**: the other files in the same folder; tap one to play it |
| Open folder | **Recent folders**: folders you've played from; tap one to resume its most recent file |
| Clock | **Recently played**: files you've played recently |
| Gear | **Player settings** |

The app remembers which panel you had open.

## Player settings

| Setting | Range | What it does |
|---|---|---|
| **Skip Ns** | 5–60 s (default 10) | Length of the large skip buttons. |
| **Speed** | 0.75×–1.25× | Playback speed. |
| **Bookmark −Ns** | 0–20 s (default 7) | How far back a bookmark is placed. |
| **2-tap prio Ns** | 1–10 s (default 3) | How quickly a second tap must follow to raise priority instead of adding a bookmark. |
| **Volume** | −20 to +50 dB | Boost or lower this player's volume, for quiet recordings. |
| **Play next** | see below | What happens when a file ends, and what ⏭ does. |
| **Sleep timer** | **Off**, 15m, 30m, 45m, 60m | Pauses playback after that long. Shows **pauses in …** while running. Works with the screen off. |
| **Bookmark on pause** | on/off | Every pause adds a bookmark. |

Every slider also has − and + buttons for fine steps. These settings are kept between sessions.

### Play next

| Mode | What plays next |
|---|---|
| **Off** | Nothing starts on its own when a file ends. |
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

Tap **Delete this file** under the file name. The dialog **Delete this file?** lists everything that goes with it: its transcript files and its bookmarks. There is no recycle bin, so the file can't be brought back. After deleting, the player moves on.
