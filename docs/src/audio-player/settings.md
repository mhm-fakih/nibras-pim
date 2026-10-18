# Settings

Open **Settings** with **Ctrl+,** or the gear button in the title bar. Click **Save** to apply.

| Setting | What it does | Default |
|---|---|---|
| **Folder-tree root** | The folder the **FOLDERS** tree starts from. Type or paste a path (`~` is allowed), or use **Browse…**. Keep it narrow — your recordings folder, not your whole home folder | a built-in default, else your home folder |
| **Skip forward / back (seconds)** | How far the wide back/forward buttons, **←**/**→** and **Ctrl+←**/**Ctrl+→** jump | 10 |
| **Bookmark rewind (seconds)** | How far back a new bookmark is placed (and playback rewinds) | 5 |
| **Waveform limit (MB)** | Files larger than this get no waveform. 0 = no limit | 10 |
| **Sleep timer** → **Show the sleep timer** | Show the **Sleep** menu next to the volume | off |
| **Sleep presets (minutes)** | Times offered by the sleep timer, comma-separated | `15, 30, 45, 60` |

You can also change the tree's root folder with the ⚙ button above the tree.

## Remembered automatically

You don't need to save these — the player keeps them for you:

- Light or dark theme (**Ctrl+T**)
- Playback speed and volume
- Window size and the sizes of all panes
- Playlist column order
- The last folder you loaded and the last file you played
- Your position in each file (to resume)
- Recently played and recently bookmarked files

## The config file

Everything is stored in `config.json` **next to the program**. Close the player before editing it
by hand. Deleting it resets the player to its defaults — your bookmarks are not affected, because
they live in the `.tmk` files next to your recordings.
