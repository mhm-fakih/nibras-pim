# Connecting to a Nibras server

knotes desktop works fully offline. Connecting it to a Nibras server is optional, and it does one
thing: it keeps your **type** and **category** codes in step with the server, so that notes you
capture now can be imported later into the right place.

| On the desktop | Comes from the server's |
|---|---|
| Types | Enabled modules that have a code, in the server's order |
| Categories | Bookmarked departments |

This is the "capture now, classify later" workflow — see
[Capture, then classify](../concepts/capture-then-classify.md) and [Sync](../concepts/sync.md).

!!! note "The desktop app doesn't upload notes"
    The connection only *reads* codes. Your notes stay as files in your folder; getting them into
    the server happens through the server's import, the [terminal app](../terminal/index.md)'s `+`
    prefix, or the [mobile app](../mobile/index.md).

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
