# Tag editing

The player can write the most common tags — title, artist, album and genre — straight into your
media files, so any other player shows them too.

!!! note "Requires mutagen"
    Writing tags needs the `mutagen` Python package (`python3-mutagen` on Debian/Ubuntu,
    `python-mutagen` on Arch). Without it, everything else works, the **Apply** button is disabled,
    and title edits report that they couldn't be saved.

## Rename the playing file's title

1. Click into the **Title** field at the top of the player.
2. Type the new title.
3. Press **Enter**.

The message line shows `✓ Title saved` and the playlist updates. The file name itself doesn't
change — hover the field to see it.

## Tag a whole folder

The **Tag →** bar under the playlist writes the same **Genre**, **Artist** and **Album** to every
file currently listed in the playlist.

1. Load a folder (see [Library](library.md)) and narrow the playlist to the files you want —
   **Untagged only** is often the right starting point.
2. Fill in any of **Genre**, **Artist** and **Album**. Empty fields are left alone.
3. Click **Apply** and confirm (`Write these tags to N file(s)?`).

The folder is re-read afterwards, so the Genres / Artists / Albums browser reflects the new tags.

## Which formats can be tagged

| Format | Tags written as |
|---|---|
| MP3 | ID3v2 (title, artist, album, genre) |
| FLAC, Ogg, Opus | Vorbis comments |
| M4A / MP4 | Standard MP4 tags |

Files that can't be tagged — some video and less common formats — are skipped and counted in the
result message (e.g. `Tagged 12 file(s), 2 unsupported`). Nothing else is changed.
