# Subtitles & transcripts

The player picks up two kinds of text files that sit next to a recording and share its name:

| Kind | File name | Timed? | Shown |
|---|---|---|---|
| Subtitles | `talk.en.srt`, `talk.srt` | Yes | One line at a time, in sync with playback |
| Transcripts | `talk.ar.ai.txt`, `talk.en.ai.txt`, `talk.ai.txt` | No | The whole text in the **TRANSCRIPT** side panel |

When a file has either, a row of chips appears under its title.

## Subtitles

Subtitles are standard `.srt` files. The part between the recording's name and `.srt` is the
language: `talk.en.srt` gives an **en** chip. A plain `talk.srt` gives a **sub** chip.

1. Open the recording. The **Subtitles:** chips appear, with **Off** selected.
2. Click a language chip.

The current line appears over the video, or — for audio — just above the seek bar. Click **Off** to
hide subtitles.

!!! tip
    With subtitles on, a bookmark you add without typing a label is labelled with the line being
    spoken. See [Bookmarks](bookmarks.md#with-a-label).

## Transcripts

A transcript is a plain UTF-8 text file ending in `.ai.txt` — for example the output of a speech-to-
text tool. It has no timings, so the player shows the whole text in the **TRANSCRIPT** panel to the
right of the player, like song lyrics.

- The **Transcript:** chips show each language found (`talk.ar.ai.txt` → **ar**; a plain
  `talk.ai.txt` → **txt**).
- The panel **opens by itself** when a file has a transcript. If you picked a language for an
  earlier file, that language is chosen again when available.
- Arabic transcripts are laid out right-to-left. Other languages follow the direction of their text.
  See [Languages](../concepts/languages.md).

### Correct a transcript

The panel is editable — fix whatever the transcriber got wrong. Your changes are saved back to the
`.ai.txt` file a moment after you stop typing, and always before you switch language, open another
file or quit. Press **Ctrl+S** to save right away. The message line confirms each save.

### Start a new transcript

For each of **ar** and **en** that the file doesn't have yet, a **+ ar** / **+ en** button appears.
Click one to create an empty `talk.ar.ai.txt` (or `.en.ai.txt`) and start typing in the panel. The
button you press decides the language; the file keeps that name.

### Delete a transcript

Each transcript chip has its own small trash button. Clicking it moves that file to your desktop's
Trash **immediately, without asking** — restore it from the Trash if you change your mind.

!!! note "Where transcripts come from"
    The player only reads, edits and creates these files — it doesn't transcribe audio itself. Any
    tool that writes `<recording>.<lang>.ai.txt` next to the recording will do.
