# Backup, troubleshooting and FAQ

## Backing up

### Your notes are already files

Everything you write lives in ordinary files in folders you chose:

| What | Where |
|---|---|
| Notes | One `.md` file each, in your **Notes directory** |
| Note attachments and collections | The `.d` folder next to each note |
| Highlights in notes | Inside the note file itself (`==…==`) |
| Media bookmarks | A `.tmk` file next to each audio or video file |
| Marked-up pictures | The picture itself; originals in a hidden `.scr-backup` folder next to it |

To back these up, copy the folders to a computer or keep them in step with a folder-sync tool such as Syncthing. Uninstalling knotes does not delete them.

### What lives inside the app

Some things are stored by the app rather than in your folders:

- your settings, including folder paths, types and categories, and server details,
- alarms, and prayer-time settings,
- player settings and the last position in each media file,
- phone-only state for server records: read/unread and the private star shortlist,
- the saved copy of server records, and changes still waiting to be sent.

These are lost if you uninstall the app or clear its data. Before doing either, let any waiting changes sync to the server, and note down your settings. Everything on the server is re-downloaded when you connect again.

## Troubleshooting

### The Notes page is empty

- The list starts on **today**. If you haven't written anything today, it's empty. Open the **Filters** bar and tap **Clear all**.
- Check **Settings → Notes directory**. A path that looks right but doesn't exist (a typo, `notes` instead of `Notes`, a memory card that isn't inserted) gives an empty app with no error. The setup tour (**Settings → Show the setup tour**) shows whether each folder exists and what's in it.
- Make sure file access is still granted (see below).

### "Could not write the note. Check that file access is still granted."

knotes needs **All files access**. Open Android's settings → Apps → knotes → Permissions (or **Special app access → All files access**) and switch it on. If it's off when the app starts, the app shows **Grant file access** again.

### Alarms, reminders or the chime come late, or not at all

- If the Alarms page says **Exact alarms are not permitted for this app**, allow **Alarms & reminders** for knotes in Android's settings.
- Allow notifications for knotes. Without them Android hides reminders, the chime and the athan notification.
- Keep **Persistent notification** on (Settings → **Status & chime**). It keeps the app running so alarms fire on time.
- Some phones stop background apps to save battery. If alarms still come late, exclude knotes from battery optimisation in Android's settings.

### The athan didn't sound

On the [Prayer times](prayer.md) page, open **Athan** and check:

- **Alarms on** is switched on. Otherwise the page shows **Alarms are switched off**.
- That prayer is ticked under **Call the athan at**.
- Its volume under **Volume for each** isn't 0, and the phone's **alarm** volume isn't at zero. The athan can be quieter than the alarm volume, never louder.
- Tap **Hear it** to test the sound.

### The phone wasn't silenced for prayer

**Silence → Go silent at these times** needs Do Not Disturb access. If the panel shows **Android will not let an app change the ringer without Do Not Disturb access.**, tap **Grant access**. Also check the prayer is ticked under **At these times**.

### Prayer times don't match my mosque

Work through **Method**: set **Where you are**, then the **School** (Asr), then the **Calculation method**. Fine-tune the rest with **Times → Adjust each time**. A difference of a minute or two in each time is normal between methods.

### "Can't delete this note"

The note has files in its attachment folder. Open it with **Open files folder**, move or delete the files, then delete the note. See [Delete a note](notes.md#delete-a-note).

### Server pages are empty or out of date

- Check the connection state at the top of a record page (**Online** / **Offline**).
- **Sign-in failed — check the username and password in Settings**: fix them in **Settings → Nibras server**.
- Only **bookmarked** records come to the phone. To get another one, use [Search server](server-pages.md#search-server) and flag it.
- Tap **Refresh server pages** at the bottom of the rail to reload everything.

### Search server shows an error

| Message mentions | Meaning |
|---|---|
| **401** | The username or password is wrong. |
| **403** | You're signed in, but the account isn't allowed to search. |
| **404** | The server is running an older version without search. Update the server. |
| **Couldn't reach** | Check the address in Settings and that the server is running. |

### The AI answers page says the folder is missing

The folder hasn't arrived on the phone yet, or it's somewhere else. Check your folder-sync tool, or set **Settings → AI answers directory** to where it actually is.

### "Nothing left here" in the player

**Play next** couldn't find a file to play: everything in that folder is already bookmarked or played. Choose another mode, or open a file from the Media page.

## FAQ

**Does knotes need an internet connection?**
No. Notes, the player, gallery, collection, prayer times, alarms and widgets all work offline. Only the server pages and sync need your Nibras server, and they show the last synced copy when it can't be reached.

**Can I use a notes folder I already have?**
Yes. Point **Notes directory** at it. Plain `.md` and `.txt` files appear straight away, and they become fully tagged notes once you give them a type, priority and category.

**Can I edit my notes on a computer?**
Yes. They are ordinary text files. Keep the folder in step with a sync tool, and the same notes open in the Nibras desktop app or any text editor. See [Plain-text notes](../concepts/plain-text-notes.md).

**Does anything leave my phone?**
Only what you send to your own Nibras server, if you set one up. Location for prayer times is read only when you tap **Use my location**, and nothing is looked up online.

**Why do numbers stay 0–9 in Arabic?**
On purpose. Times, file names and bookmark files are shared with other tools, and keeping Western digits keeps them readable everywhere.

**Why isn't there a recycle bin?**
A deleted note, picture or recording is a deleted file. If you sync your folders to a computer, your sync tool may keep old versions. Check its settings.

**Can I switch off features I don't use?**
**Prayer times** (on its own page, under **Athan**) and **File collection** (in Settings) can be switched off, and they leave the rail. Server pages simply stay empty if no server is set up.
