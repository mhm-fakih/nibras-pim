# Playback

The **PLAYER** pane holds everything for the file that's playing.

## Open something to play

- Click a track in the playlist (see [Library](library.md)).
- Press **Ctrl+O** (or the open button in the title bar) and pick a file.
- Paste a file's path into **Go to path…** (**Ctrl+L**) and press **Enter**.
- Start the player with a file: `./run.sh ~/Music/talk.mp3`.
- Click a file under **RECENTLY BOOKMARKED**.

When you open a file on its own (not from the playlist), the other media files in its folder become
the queue, in name order.

## Controls

From top to bottom:

| Control | What it does |
|---|---|
| Video area | Shown only for files with video |
| **Title** field and trash button | The file's title — edit it and press **Enter** to save it as a tag (see [Tag editing](tag-editing.md)). Hover it to see the file name. The trash button moves the file to the Trash |
| Subtitles / Transcript chips | Shown when the file has them — see [Subtitles & transcripts](subtitles-and-transcripts.md) |
| Waveform seek bar | Click or drag to jump; bookmarks show as ticks |
| Times | Elapsed, remaining and total |
| Transport | **Previous track**, back, **Play / Pause**, forward, **Next track** |
| Bookmark field and **＋ Bookmark** | See [Bookmarks](bookmarks.md) |
| **Speed** | 0.75× to 1.25×, without changing pitch |
| **Sleep** | Pause after a set time (only when enabled in Settings) |
| **Vol** | 0 to 3× — above the middle mark boosts quiet recordings |

The wide back and forward buttons jump by the **Skip forward / back** time (10 seconds by default;
change it in [Settings](settings.md)).

## The waveform seek bar

The seek bar is drawn over the recording's loudness profile: loud passages show as tall bars, quiet
ones stay flat, and the part already played is tinted. It's a quick way to spot where someone starts
speaking, or to skip a long silence.

The waveform is worked out in the background after a file opens, so playback never waits for it.
For very large files it's skipped — the player tells you so — because it has to read the whole
file. Raise or remove the limit with **Waveform limit (MB)** in [Settings](settings.md).

## Resume where you left off

The player remembers your position in every file you've listened to for more than a few seconds.
Open the file again and it continues from there. On the next start, the last file you played is
opened again, paused.

## End of file

When a file ends, the player **stops**. It doesn't roll on into the next recording — press
**Next track** or click the next one in the playlist when you're ready.

## Sleep timer

The sleep timer is hidden until you turn on **Sleep timer** in [Settings](settings.md). Then a
**Sleep** menu appears next to the volume: choose a time (15, 30, 45 or 60 minutes by default) and
playback pauses when it runs out. Choose **Off** to cancel.

## Move a file to the Trash

Press **Ctrl+Delete** or the trash button next to the title, and confirm with **Move to Trash**.
The file and its `.tmk` bookmarks go to your desktop's Trash (so you can restore them), and the
player moves on to the next file in the queue.

## Light and dark

**Ctrl+T** or the moon button in the title bar switches between the light (default) and dark theme.
