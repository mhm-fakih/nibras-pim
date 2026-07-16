# audio-player

A GTK3 desktop **audio & video player** with a folder library browser, tag editing, subtitles, and
strong per-file **bookmarking** — the Linux companion to the phone's Media3 player, and a sibling of
`desktop/knotes` (same visual language, separate app). Bookmarks live in plain-text `.tmk` sidecars
in the **same format the phone writes**, so a file bookmarked on the phone shows its ticks here (and
vice versa) when the folder is synced (e.g. Syncthing).

```bash
./run.sh                 # checks deps, then launches
python3 player.py FILE    # open a specific file and play it
./install.sh             # menu entry + pinnable panel launcher
```

---

## General features

**One unified window**

```
┌───────────────┬─────────────────────────────────────────────┐
│ FOLDERS       │  Genres │ Artists │ Albums   (3-pane browser) │
│ (⚙ Root tree) ├──────────────────────┬──────────────────────┤
│               │  PLAYLIST            │  PLAYER               │
├───────────────┤  (sortable columns)  │  video · title · seek │
│ RECENTLY      │                      │  transport · bookmarks│
│ BOOKMARKED    │                      │  speed / gain / sleep │
└───────────────┴──────────────────────┴──────────────────────┘
                          status bar: Scanning… / N files found
```

- **Folder library** — a permanent tree rooted at one **configurable folder** (default: home, not the
  whole filesystem). Double-click a folder to scan it (recursively) for audio and video. A status
  bar with a spinner shows progress and the final media count.
- **3-pane column browser** — Genres → Artists → Albums (iTunes/Rhythmbox style); selecting narrows
  the next pane and the playlist. An **Untagged** page lists files with no metadata.
- **Playlist** — sortable, reorderable, persistent columns (`#`, Title, Artist, Album, Length, Bkm);
  the **playing track is bold** and stays centered in view; the **Bkm** column shows each file's
  bookmark count. Double-click to play (the list becomes the queue).
- **Player** — video renders **in-window**; an editable **Title** field; a **waveform seek bar with
  bookmark ticks**; wide transport buttons; `0.75–1.25×` pitch-preserving speed; per-file **resume**;
  folder-as-queue **auto-advance**; gain up to 3×; an optional **sleep timer**.
- **Waveform seek bar** — the track's **amplitude spectrum** is painted behind the seek bar (loud
  stretches show as tall bars, quiet ones stay flat), so you can *see* where the peaks are and scrub
  straight to them. The played portion is tinted as you go. The envelope is decoded in the background
  the moment a file's length is known, so opening a track never blocks — and it works for video too.
- **Bookmarking** — `Ctrl+B`/`B` drops a mark a few seconds back, **rewinds & plays** from it, and
  writes the `.tmk` sidecar. Ticks on the bar, a list with inline **label editing** (click the “no
  label” placeholder), click-to-seek, delete.
- **Tag editing** — set a file's **Title** inline; **bulk-tag a whole folder** (genre / artist /
  album) in one action (handy for the Untagged page).
- **Subtitles** — sidecar `.srt` languages appear as chips; the active line shows over the video (or
  over the seek bar for audio).
- **Delete to Trash** — the trash button / `Ctrl+Delete` moves the file (and its `.tmk`) to the
  freedesktop **Trash** (recoverable), after a confirm dialog.
- **Persistence** — theme (light default), pane sizes, window size, column order, tree root, recents,
  resume positions, and last-played file all persist across launches.

---

## Technical details

### Stack & libraries

| Concern | Uses |
|---|---|
| Language | **Python 3** (no build step) |
| GUI toolkit | **GTK 3** via **PyGObject** (`gi`) |
| Audio/video playback | **GStreamer 1.x** `playbin` (`gi` → `Gst`) — pitch-preserving speed via **`scaletempo`**, software **`gtksink`** for in-window video (avoids GL/DMABuf artifacts; `AUDIO_PLAYER_GL=1` opts into `gtkglsink`) |
| Tag **reading** (library scan) | **`GstPbutils.Discoverer`** (part of GStreamer) — reads audio *and* video tags |
| Tag **writing** | **`mutagen`** (optional; only needed to write tags) |
| Subtitles | in-house `.srt` parser (stdlib `re`, `bisect`) |
| Move to Trash | **`Gio.File.trash()`** (freedesktop Trash spec) |
| Everything else | Python stdlib only |

No third-party Python dependency except **`mutagen`** (optional). No network use.

### Media types (scanned & playable)

`.mp3` `.m4a` `.m4b` `.aac` `.ogg` `.oga` `.opus` `.flac` `.wav` `.wma` `.mka` `.webm` `.mp4` `.m4v`
`.mkv` `.mov` `.avi`

(Actual decoding depends on the GStreamer plugins installed — good/bad/ugly cover the above.)

### Metadata — read

Read during a library scan via `GstPbutils.Discoverer`, using the standard, cross-format tag keys:

| Field | GStreamer tag | Notes |
|---|---|---|
| Title | `GST_TAG_TITLE` | falls back to the file's base name for display |
| Artist | `GST_TAG_ALBUM_ARTIST` → `GST_TAG_ARTIST` | album-artist preferred for grouping |
| Album | `GST_TAG_ALBUM` | |
| Genre | `GST_TAG_GENRE` | |
| Track no. | `GST_TAG_TRACK_NUMBER` | |
| Duration | container duration | for the Length column & seek bar |

Files with none of genre/artist/album are treated as **untagged**.

### Metadata — written

Written via `mutagen`'s **“easy” interface**, i.e. the most interoperable tags, so any common player
reads them back:

| Field | ID3v2 (MP3) | Vorbis comment (FLAC/OGG/Opus) | MP4/M4A atom |
|---|---|---|---|
| `title` | TIT2 | TITLE | ©nam |
| `artist` | TPE1 | ARTIST | ©ART |
| `album` | TALB | ALBUM | ©alb |
| `genre` | TCON | GENRE | ©gen |

Two write paths: **per-file Title** edit, and **bulk folder** genre/artist/album. Only non-blank
fields are written; formats mutagen can't tag (some video/exotic containers) are reported as skipped,
never fatal.

### `.tmk` bookmark sidecar (interop contract)

One bracketed timestamp per line, optionally followed by a label — human-readable & diff-friendly:

```
[1:09]
[12:04] p2 key point on refraction
[1:03:20] auto
```

- Timestamp is `M:SS` or `H:MM:SS` (a bare `SS` or trailing `.mmm` are also accepted on read).
- The bracket is the position; anything after `]` is the label (the phone may put a `pN` priority
  token or the literal `auto` there — preserved verbatim).
- Sidecar path = the media's base name with the extension swapped to `.tmk`
  (`lecture.mp3` → `lecture.tmk`). Written atomically; an emptied list removes the file.
- `tmk.py` is the reference implementation and has a self-test (`python3 tmk.py`).

> **Phone side:** the Android app references `Bookmarks.load/save` + `data class Bookmark(positionMs,
> text)` but doesn't implement them yet, so `tmk.py` here is the canonical spec. Implementing
> `mobile/.../player/Bookmarks.kt` against the same lines keeps phone ↔ desktop in lockstep.

### `.srt` subtitle sidecar

`<base>.<lang>.srt` → language chip (`lecture.en.srt` → **en**); a plain `<base>.srt` → **sub**.
Parsed tolerantly (BOM, CRLF, inline `<i>`/`<b>` tags stripped).

### Modules

| File | Responsibility |
|---|---|
| `tmk.py` | The `.tmk` reader/writer — the interop contract. Pure Python, self-tested. |
| `audio.py` | GStreamer `playbin` engine: play/seek/speed/gain, embeddable video widget. No GUI. |
| `waveform.py` | Amplitude-envelope extraction for the seek bar spectrum: a background GStreamer `level` decode, cancellable. No GUI. |
| `library.py` | Folder scan + tag read (`Discoverer`), aggregation, and tag write (`mutagen`). |
| `subtitles.py` | `.srt` sidecar discovery + parsing. Self-tested. |
| `config.py` | `Settings` (all persisted state) as `config.json` next to the program. |
| `player.py` | The GTK3 window: library + playlist + player, tying it all together. |

### Configuration

All settings persist to **`config.json`** beside the program: theme, skip/rewind seconds, sleep
timer enable + presets, volume, tree root, column order, pane sizes, window size, recents, resume
positions, and last-played file. Playback defaults mirror the phone.

---

## Requirements

```bash
# Debian / Ubuntu
sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-gst-1.0 gir1.2-gstpbutils-1.0 python3-mutagen \
     gstreamer1.0-plugins-good gstreamer1.0-plugins-bad gstreamer1.0-plugins-ugly gstreamer1.0-gtk3
# Fedora
sudo dnf install python3-gobject gtk3 gstreamer1 gstreamer1-plugins-base python3-mutagen \
     gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free
# Arch
sudo pacman -S python-gobject gtk3 gstreamer gst-plugins-base python-mutagen \
     gst-plugins-good gst-plugins-bad gst-plugins-ugly
```

`mutagen` is only needed to **write** tags; everything else works without it.

---

## Keyboard

| Keys | Action |
|------|--------|
| `Space` · `Ctrl+P` | Play / pause |
| `←` / `→` · `Ctrl+←` / `Ctrl+→` | Skip back / forward (Ctrl works even in lists/fields) |
| `B` · `Ctrl+B` | Add bookmark (rewinds & plays from the mark) |
| `N` | Focus the bookmark-note field |
| `Ctrl+O` | Open a media file |
| `Ctrl+L` | Focus the folder tree |
| `Ctrl+Delete` | Move the playing file to Trash (confirms) |
| `Ctrl+,` | Settings |
| `Ctrl+T` | Toggle light / dark |
| `Ctrl+Q` | Quit |

## Settings (Ctrl+, or the ⚙ button)

Skip seconds, bookmark-rewind seconds, and the sleep timer (off by default — enable it and set its
preset minutes). The tree root, column order, theme, and window/pane layout persist automatically.
