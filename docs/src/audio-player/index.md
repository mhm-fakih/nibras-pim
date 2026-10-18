# Audio player

The **audio player** is a desktop player for audio and video recordings — lectures, talks, voice
memos, podcasts. Its strength is **bookmarking**: one key marks the moment you just heard, and the
marks are saved in a small `.tmk` text file next to the recording, in the same format the
[mobile app](../mobile/index.md) uses. Sync the folder, and bookmarks made on the phone show up
here, and the other way round.

Around that it offers a folder library, a waveform seek bar, subtitles, editable transcripts and
tag editing.

## The window at a glance

```
┌───────────────┬──────────────────────────────────────────────────┐
│ FOLDERS       │  Genres │ Artists │ Albums                         │
│ Go to path…   ├─────────────────┬──────────────────┬─────────────┤
│ ⚙ root folder │  PLAYLIST       │  PLAYER          │ TRANSCRIPT  │
│               │                 │  title · waveform│ (when the   │
├───────────────┤                 │  transport       │  file has   │
│ RECENTLY      │  Tag → …        │  BOOKMARKS       │  one)       │
│ BOOKMARKED    │                 │  Speed · Vol     │             │
└───────────────┴─────────────────┴──────────────────┴─────────────┘
  status bar
```

- **FOLDERS** — browse your recordings folder; double-click a folder to load it. See
  [Library](library.md).
- **Genres / Artists / Albums** and **PLAYLIST** — narrow down and pick a track.
- **PLAYER** — video, waveform seek bar, controls and the bookmark list. See
  [Playback](playback.md) and [Bookmarks](bookmarks.md).
- **TRANSCRIPT** — an editable transcript, when the file has one. See
  [Subtitles & transcripts](subtitles-and-transcripts.md).
- **RECENTLY BOOKMARKED** — files you bookmarked lately; click one to play it.

## Install

The player is a Python 3 program using GTK 3 for the window and GStreamer for playback. `mutagen`
is optional — it's only needed to **write** tags.

=== "Debian / Ubuntu / Mint"

    ```bash
    sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-gst-1.0 gir1.2-gstpbutils-1.0 \
         python3-mutagen gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
         gstreamer1.0-plugins-ugly gstreamer1.0-gtk3
    ```

=== "Fedora"

    ```bash
    sudo dnf install python3-gobject gtk3 gstreamer1 gstreamer1-plugins-base python3-mutagen \
         gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free
    ```

=== "Arch"

    ```bash
    sudo pacman -S python-gobject gtk3 gstreamer gst-plugins-base python-mutagen \
         gst-plugins-good gst-plugins-bad gst-plugins-ugly
    ```

Which formats actually play depends on the GStreamer plugins you have; the packages above cover
all the formats listed under [Library](library.md#supported-formats).

!!! note "Linux only"
    The audio player is made for Linux desktops. There is no Windows launcher.

## Run

```bash
cd audio-player
./run.sh                    # checks the requirements, then starts
./run.sh ~/Music/talk.mp3   # open a file and start playing it
```

`run.sh` tells you which packages to install if something is missing.

**Menu entry.** Run `./install.sh` once. It adds **Audio Player** to your application menu; launch
it from there, then right-click its panel icon and choose **Pin**. Re-run `install.sh` if you move
the folder.

## First run

1. Press **Ctrl+,** to open **Settings**, and set **Folder-tree root** to the folder that holds your
   recordings (use **Browse…** to pick it). Keep it narrow — your recordings folder, not your whole
   home folder.
2. In **FOLDERS**, double-click a folder. Its media files (including subfolders) load into the
   playlist.
3. Click a track to play it.
4. While it plays, press **B** to bookmark the moment you just heard.

The player opens maximized and remembers its layout, theme, last folder and last file. Next time it
opens that file paused, at the position where you stopped.

## Where things are stored

| What | Where |
|---|---|
| Bookmarks | `<recording>.tmk` next to each recording — see [Bookmarks](bookmarks.md) |
| Subtitles | `<recording>.<lang>.srt` next to the recording |
| Transcripts | `<recording>.<lang>.ai.txt` next to the recording |
| Settings, resume positions, recent files | `config.json` next to the program |

The player doesn't use the network.
