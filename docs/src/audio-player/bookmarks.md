# Bookmarks

A bookmark marks a moment in a recording, with an optional label. The audio player is built around
making them quickly — while you listen, without stopping.

## Add a bookmark

Press **B** (or **Ctrl+B**, which works even while you're typing), or click **＋ Bookmark**.

The bookmark is placed a few seconds **back** — at the moment you just heard, not the moment you
reacted — and playback jumps back there so you hear it again. The message line confirms it, e.g.
`✓ Bookmarked 12:04 (−5s)`. Change how far back with **Bookmark rewind (seconds)** in
[Settings](settings.md).

### With a label

1. Press **N** to jump to the **Optional note for the next bookmark…** field.
2. Type a label.
3. Press **Enter** (or **Ctrl+B**) to add the bookmark with that label.

If you don't type a label and a subtitle track is switched on, the bookmark is labelled with the
subtitle line at that moment.

## Use your bookmarks

The **BOOKMARKS** list under the controls shows every bookmark of the current file, in time order.
Bookmarks also appear as ticks on the [waveform seek bar](playback.md#the-waveform-seek-bar).

| To… | Do this |
|---|---|
| Jump to a bookmark | Click its time (or anywhere on the row outside the label) |
| Edit a label | Click the label (or **no label**), type, then press **Enter** or click away to save |
| Delete a bookmark | Click its trash button |

The **Bkm** column in the playlist shows how many bookmarks each file has, and files you bookmark
appear under **RECENTLY BOOKMARKED**.

## The .tmk file

Bookmarks are saved immediately to a plain-text file next to the recording, with the same name and
a `.tmk` extension:

```
lecture.mp3
lecture.tmk
```

One bookmark per line — the time in brackets, then the label:

```
[1:09]
[12:04] key point on refraction
[1:03:20] auto
```

- Times are `M:SS`, or `H:MM:SS` past the hour.
- Labels written by other apps (for example a `p2` priority or the word `auto` from the phone) are
  kept exactly as they are.
- Deleting the last bookmark removes the `.tmk` file.

Because it's plain text, you can read or edit a `.tmk` in any editor, and it syncs like any other
file. The phone writes the same format, so bookmarks made on either side appear on both. See
[Bookmarks (.tmk)](../concepts/bookmarks-tmk.md).

!!! tip "Bookmarks in your notes"
    If a recording sits in a note's [companion folder](../concepts/companion-folders.md), knotes
    desktop shows the number of bookmarks as **time marks** on the note's card.
