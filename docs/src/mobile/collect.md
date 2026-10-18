# Collect files

Screenshots, camera photos, downloads and voice recordings pile up in the folders other apps write to. **Collect files** gathers them into a single note, with the files attached and renamed so they sort by date. A loose screenshot becomes something you can title, tag and find again.

Open it from the rail (the folder-with-arrow icon, last in **On this phone**).

## How it works

1. When the page opens, it scans your source folders (**Scanning source folders…**) and lists what it found, each file with its new name.
2. The banner says where the files will go: **They will be moved into a new timestamped subfolder of … and renamed with the standard timestamp.**
3. Tap **Collect N files**. The files are moved and the result is shown (**Moved N of N files**).
4. Tap **Scan again** (or the **Rescan** arrow at the top) to look again.

!!! warning "Files are moved, not copied"
    Collected files are removed from their original folders. They now live in the collection.

## What you get

Each collection is an ordinary [note with attachments](notes.md#attachments):

```
2026_06_11-095700~W~p2~P.md        the note: "Collected 3 files" and a list
2026_06_11-095700~W~p2~P.d/        the files
    2024_01_15-123045_Screenshot_Chrome.png
    2026_06_11-095700_notes.pdf
    …
```

- The note uses your default **type, priority and category** for new notes, just like a quick note. Retag it like any other note.
- Each file is renamed so a **timestamp leads its name**. If the original name contains a recognisable date and time (most camera, screenshot and recorder names do), that time is used. Otherwise the file's last-modified time is used, and the list marks it **mtime**.

For example, `Screenshot_2024-01-15-12-30-45_Chrome.png` becomes `2024_01_15-123045_Screenshot_Chrome.png`.

!!! tip "Put collections in your notes folder"
    By default, collections go into your notes folder, so they appear on the Notes page and your computer reads them as notes. If you choose a different destination, the setup tour warns: *Collected notes will not appear on the home screen.*

## Choosing the folders

In [Settings](settings.md), under **File collection**:

- **Source folders**: the folders to scan. By default these are the usual places for screenshots, camera photos, pictures, downloads and recordings. Add your own with **Add folder**. Untick a folder to skip it while keeping it in the list.
- **Destination (New) folder**: where collections are written.

## Collect automatically every day

Switch on **Collect automatically every day** and pick a time (**At 15:00** by default). The same collection then runs on its own each day. You get a notification (**Collected N files**) only when something was actually collected.

## Switching the feature off

The **File collection** switch in Settings removes the Collect page from the rail and stops the daily run, whatever the automatic setting says.
