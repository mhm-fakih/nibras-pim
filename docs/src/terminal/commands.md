# Commands

A line that starts with `/` is a command. As you type, the command bar suggests the matching
command in grey — press **Tab**, **→** or **End** to accept it.

## Command reference

| Command | Also | What it does |
|---|---|---|
| `/help` | `/h`, `/?` | Show the note-line format and the list of commands |
| `/repos` | `/repo` | Refresh the dashboard of note folders |
| `/theme` | | Show the current theme |
| `/theme light` | `/theme l` | Switch to the light theme (saved for next time) |
| `/theme dark` | `/theme d` | Switch to the dark theme (saved for next time) |
| `/heartbeat` | | Check whether the current folder's server is reachable |
| `/login <user> <password>` | | Log in to the current folder's server |
| `/tasks` | | List the 20 most recently updated tasks |
| `/goals` | | … goals |
| `/journals` | | … journals |
| `/planners` | | … planners |
| `/resources` | | … resources |
| `/notes` | | … notes |
| `/writings` | | … writings |
| `/quit` | `/exit`, `/q` | Close the app |

The record-listing commands need a login first — see [Server commands](server.md).

## Line prefixes

| Starts with | Meaning |
|---|---|
| `/` | A command (above) |
| `+` | Save the note **and** send it to the server — see [Writing notes](writing-notes.md#send-a-note-to-the-server-the-prefix) |
| anything else | Save a note — see [Writing notes](writing-notes.md) |

## Keys

| Key | Action |
|---|---|
| **Enter** | Save the note or run the command |
| **↑** / **↓** | Step through the lines you entered this session |
| **Tab**, **→**, **End** | Accept the suggested command |
| **Ctrl+C**, **Ctrl+Q** | Ignored on purpose — type `/quit` to exit |

The command bar always keeps the keyboard focus, so you can type at any time.
