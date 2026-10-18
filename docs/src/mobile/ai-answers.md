# AI answers

**AI answers** (the robot icon, just under Notes in the rail) is a second, **read-only** notes page. It shows a separate folder of notes that arrives on the phone from elsewhere, typically answers prepared on your computer and synced over with a folder-sync tool such as Syncthing.

## Where the folder is

By default the app looks for a folder named like your notes folder with `-ai` added. If your notes folder is `/storage/emulated/0/Notes`, the AI answers folder is `/storage/emulated/0/Notes-ai`.

To use a different folder, set **AI answers directory** in [Settings](settings.md). Leave it blank to use the default.

The app never creates this folder itself. If it isn't there yet, the page says **No AI answers folder on this phone yet.** and shows the path it expected. If the folder exists but holds nothing, it says **The folder is there but empty.**

## What you can do

- **Browse** the answers. The cards look like the ones on the Notes page, and attached files can be opened or played the same way.
- **Search** them with **Search these answers**, and narrow them with the same [filter panel](search-filters.md#the-filter-panel) as the Notes page.
- **Read** one by tapping it. The header says **AI answer**. Markdown is formatted.

What you can't do here is write: there is no box for new notes, no **Edit**, no delete, no push to Nibras and no tag changes. The folder is filled by the sync, and anything changed here would be overwritten.

!!! note "Dates"
    Each answer is dated by the time in its file name, when it was written, not by when it reached your phone. Sync tools change a file's modified time when they copy it, which would otherwise date every answer to the moment it arrived.

If the page shows **No answer matches.**, the answers are there but your search or filters hide them all. Clear them to see everything.
