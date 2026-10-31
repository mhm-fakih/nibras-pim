# Connecting to a Nibras server

Nibras Notes works fully offline. Connecting it to a Nibras server is optional, and it does two
things: it keeps your **type** and **category** codes in step with the server, so that notes you
capture now can be imported later into the right place — and it can
[import a note](#import-a-note-into-the-server) into the server as a record when you ask.

| On the desktop | Comes from the server's |
|---|---|
| Types | Enabled modules that have a code, in the server's order |
| Categories | Bookmarked departments |

This is the "capture now, classify later" workflow — see
[Capture, then classify](../concepts/capture-then-classify.md) and [Sync](../concepts/sync.md).

!!! note "Nothing is uploaded on its own"
    Syncing only *reads* codes. Your notes stay as files in your folder until you import one
    yourself — from here, through the server's import page, the
    [terminal app](../terminal/index.md)'s `+` prefix, or the [mobile app](../mobile/index.md).

## Set it up

1. Open **Settings** (**Ctrl+,**).
2. Under **Nibras backend — default**, fill in:
    - **Backend URL** — the server's base address, e.g. `https://nibras.example.com`
    - **Backend username**
    - **Backend password**
3. Click **Save**.

The app logs in and fetches the codes right away. The **Type** and **Cat** chip rows are replaced
with the server's codes, and the codes are saved in the folder's `.knotes.json`.

## When it syncs

- When the app starts.
- When you switch to another note folder.
- When you save Settings.

Each sync runs in the background and gives up quickly. If the server is unreachable, the URL is
blank or the login fails, nothing changes — you keep the codes you already have and carry on
writing. After one successful sync, the codes are cached, so they're available offline.

!!! warning "Server codes replace your own"
    While a folder is connected, its **Types** and **Categories** come from the server on every
    sync. Edits you make to those lists in Settings will be overwritten the next time it syncs.
    Leave the URL blank if you'd rather manage the codes by hand.

## Import a note into the server

When a note is ready to become a record on the server:

1. Select its card.
2. Press **Ctrl+I**, or click the import button (the arrow) in the card's action row.
3. Confirm **Import this note into the backend as a …?** — the dialog names the record type (from
   the note's type code), shows the note's title and the server's address.

The server creates the record, and the note file moves into the folder's `imported/` archive, so
the card leaves the board. The status bar confirms it with **Imported →**, the module and the new
record's number. Its attached files are copied to the record as its attachments.

- A loose file that still **needs metadata** can't be imported — the server picks the record type
  from the file name's type code, so retag it first.
- Without a server URL the app says **No backend configured**. If the import fails, a dialog gives
  the server's reason.
- The server reads the file straight from the folder, so it must be able to see the same folder —
  in practice, a server running on the same machine.

## A different server per folder

Each note folder can use its own server. In **Settings**, the **Tag options for folder “X”**
section has:

- **This folder's backend URL**
- **This folder's backend username**
- **This folder's backend password**

These apply only to the folder named in the heading, and are stored in that folder's `.knotes.json`.
Blank fields fall back to the default backend above them.

!!! warning "Passwords are stored as plain text"
    The default backend's password is saved in `config.json` next to the program, and a folder's
    own password in that folder's `.knotes.json`. If you sync note folders to other devices, the
    folder password travels with them. Use an account with only the access it needs.
