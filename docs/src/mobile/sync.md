# Nibras sync

knotes works entirely on its own. If you also run a **Nibras server**, connecting to it adds your tasks, plans, journal, goals, writings and resources to the phone. You can read and work on them offline, and your changes go back to the server when the connection returns. See [How sync works](../concepts/sync.md) for the bigger picture.

!!! note "Your phone notes stay files"
    The connection does **not** upload the notes on your phone. They stay files in your notes folder. To sync those with a computer, use a folder-sync tool such as Syncthing. A single note can be sent to the server on purpose with [Push to Nibras](#push-a-note-to-nibras).

## Connect

You can enter the server address in the setup tour (**Connect to Nibras → Server address**), or in [Settings](settings.md) → **Nibras server**:

| Field | What to enter |
|---|---|
| **Scheme** | `http` or `https`, whichever your server uses. |
| **IP / host** | The server's address on your network, or its host name. |
| **Port** | The server's port. |
| **Context** | The path the app is served under, if any. Leave it as **(none)** otherwise. |
| **Username**, **Password** | Your Nibras account. Used to sign in for sync. |
| **Files folder** | Where record attachments live on the phone, for example a `Nibras` folder kept in step by a folder-sync tool. The app opens attached files from here; it doesn't download them. |

**Full URL** under the fields shows the address being built, so you can check it. Tap **Save** at the top of Settings.

## What comes down

The phone receives the records you have **bookmarked** on the server, for each of the seven types. They are saved on the phone, so the [server pages](server-pages.md) open instantly and work offline. When you're offline they show what was last synced.

To bring down a record you haven't bookmarked, find it with [Search server](server-pages.md#search-server) and flag it.

knotes also takes its list of note **types** and **categories** from the server, when the app starts and when you tap **Refresh notes + sync now**, so the phone files notes under the same codes as the server.

## What goes up

Changes you make on the phone are saved on the phone first, then sent when the server can be reached:

- notes you add to a record,
- highlights you make in a resource,
- records you mark complete,
- un-bookmarking (when the star is set to **Un-bookmark in Nibras**),
- calendar entries you add or move,
- notes you [push to Nibras](#push-a-note-to-nibras),
- flash-card decisions.

While offline you'll see messages like **Saved — will sync when online**. Everything waiting is sent automatically as soon as the network comes back. You don't need to do anything.

## Background sync

In [Settings](settings.md) → **Background sync**:

| Setting | Default | Meaning |
|---|---|---|
| **Sync with Nibras** | On | Periodically checks the server and pulls fresh records while online. |
| **Check every (sec)** | 30 | A lightweight online/offline check. |
| **Sync every (sec)** | 300 | A full sync: pulling records and sending what's waiting. Only runs while online. |

The current state appears on the Notes page under the title (**synced 2m ago**, **offline · last synced 1h ago**) and in the [status notification](alarms.md#the-status-notification).

To sync **right now**, tap **Refresh notes + sync now** on the Notes page, or tap the title of a server widget. When a sync you started finishes, you'll see **Synced**, **Offline — can't sync**, **Sign-in failed — check the username and password in Settings**, or **Sync failed**.

## Push a note to Nibras

Any note on your phone can be created as a record on the server:

1. On the note's card, tap **More** (⋮) → **Push to Nibras**.
2. The record is created using the note's type, priority and category from its file name, and is saved as bookmarked on the server, so it comes back down on the next sync.

While it's waiting to be sent, the menu item reads **Queued — will sync** and the card shows an upload-cloud icon (**Queued for Nibras**). Once it has arrived, the icon becomes a cloud with a tick (**Pushed to Nibras**) and the menu offers **Push again to Nibras**. Pushing works offline: the note waits and is sent when the network returns.
