# Library

The left and top parts of the window are for finding what to play: a folder tree, a location bar,
a three-column browser and the playlist.

## Browse folders

The **FOLDERS** tree starts at your **root folder** — set it in [Settings](settings.md) or with the
⚙ button above the tree. If the folder doesn't exist, the tree falls back to your home folder.

- Only folders that contain media somewhere inside them are listed, so folders of notes or
  documents stay out of the way.
- Expanding a folder never loads anything; **double-click** it to load it into the library.
- Loading reads the folder **and all its subfolders**. The status bar at the bottom shows a spinner
  while it works, then the number of files found.

The folder button in the title bar puts the keyboard in the folder tree.

## Go to a path

The **Go to path…** field above the tree (**Ctrl+L**) takes a typed or pasted path. Press **Enter**
or click the arrow:

| You enter | What happens |
|---|---|
| A folder | It's selected in the tree and loaded |
| A media file | Its folder is loaded and the file starts playing |
| Any other file (say a `.srt`) | Its folder is loaded |
| Something that doesn't exist | The field turns red and the player says so |

Paths can be quoted, start with `~`, contain `$VARIABLES`, or be `file://` links. As you type,
matching subfolders are offered for completion.

## Narrow down: Genres, Artists, Albums

The three columns above the playlist work like a classic music library: pick a genre to narrow the
artists, an artist to narrow the albums, and the playlist follows. Tags are read from the files
themselves when the folder is loaded.

**Untagged only** (top right) shows just the files that have no genre, artist or album — handy for
[tagging a folder in one go](tag-editing.md#tag-a-whole-folder).

## The playlist

| Column | Shows |
|---|---|
| **#** | Track number |
| **Title** | Title tag, or the file name |
| **Artist**, **Album** | From the file's tags |
| **Length** | Duration |
| **Bkm** | Number of [bookmarks](bookmarks.md) the file has |

- **Click** a track to play it. The playlist as currently shown becomes the queue for
  **Previous track** / **Next track**.
- **Enter** plays the selected row.
- Click a column header to sort; drag headers to reorder them. The order is remembered.
- The playing track is shown in bold and kept in view.

## Recently bookmarked

Under the folder tree, **RECENTLY BOOKMARKED** lists the files you added bookmarks to most recently
(newest first). Click one to play it.

## Supported formats

`.mp3` `.m4a` `.m4b` `.aac` `.ogg` `.oga` `.opus` `.flac` `.wav` `.wma` `.mka` `.webm` `.mp4`
`.m4v` `.mkv` `.mov` `.avi`

Whether each one plays depends on the GStreamer plugins installed (see
[Install](index.md#install)).
