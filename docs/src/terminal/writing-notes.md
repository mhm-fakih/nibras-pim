# Writing notes

Anything you type that doesn't start with `/` or `+` is saved as a note when you press **Enter**.

## The note line

A note line can start with an optional folder letter and an optional three-character tag code:

```
[<folder>] [<type><priority><category>] <text>
```

| Part | Looks like | Meaning |
|---|---|---|
| Folder | one letter, e.g. `s` | Which configured note folder to save in |
| Tag code | letter + digit + letter, e.g. `t3d` | Type **T**, priority **3**, category **D** |
| Text | everything else | The note itself |

Letters are case-insensitive; codes are saved in upper case. For example:

| You type | Saved as |
|---|---|
| `s t3d buy new filters` | Folder **S**, type **T**, priority **3**, category **D**, text "buy new filters" |
| `t3d buy new filters` | Current folder, type **T** / priority **3** / category **D** |
| `just a thought` | Current folder, default tags, text "just a thought" |
| `b x3 note` | `b` is not a folder and `x3` is not a full tag code, so the **whole line** is the text, with the current folder and default tags |

The folder letter only counts when it's followed by a valid tag code. If the line doesn't start
with a valid tag code, nothing is stripped — the whole line is kept as the note, so nothing you type
is ever lost.

!!! note
    The priority in the tag code is a single digit, 0–9.

## Sticky defaults

The app remembers what you used last:

- **Folder** — naming a folder makes it the current folder. It stays current for the following
  notes and is remembered across restarts. The status bar shows it.
- **Tags** — a tag code becomes the default for the following plain notes in this session.

So you can set the context once and then just type:

```
w t3d first point about the plan
second point
third point
```

All three notes go to folder **W** with type **T**, priority **3**, category **D**.

Each folder can have its own server, so switching folders also switches which server the status bar
checks. See [Configuration](configuration.md).

## Send a note to the server: the `+` prefix

Start a line with `+` to save the note **and** create a record for it on the Nibras server:

```
+ s t3d call the supplier about the delivery
```

What happens:

1. The note is always saved to the folder first, so it's never lost.
2. If you're logged in, the app asks the folder's server to import that file. The server creates
   the record and moves the file into the folder's `imported/` subfolder. The log shows
   `✓ pushed`, with the module and record number.
3. If you're not logged in yet, the note is **queued** and pushed automatically after `/login`.

!!! warning "Before you use `+`"
    - The server reads the file straight from the folder, so the note folder must be one the server
      can reach (typically the server's import folder).
    - The queue lives only while the app is running. If you quit before logging in, queued notes
      stay saved in the folder but aren't sent — import them later from the server.

See [Server commands](server.md) for logging in, and
[Capture, then classify](../concepts/capture-then-classify.md) for the idea behind sending notes
later.

## Handy keys while typing

| Key | Action |
|---|---|
| **Enter** | Save the note (or run the command) |
| **↑** / **↓** | Recall earlier lines from this session |
| **Tab**, **→** or **End** | Accept the grey command suggestion |
