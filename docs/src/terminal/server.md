# Server commands

When a note folder is linked to a Nibras server, the terminal app can check the server, log in, and
list your most recent records. Every folder has its own server (see
[Configuration](configuration.md)), and these commands always use the server of the **current**
folder — the one shown in the status bar.

## Is the server up?

The status bar checks the current server every few seconds. A **green ●** next to its address
means it's reachable; **red** means it isn't. To check on demand:

```
/heartbeat
```

The log answers with `up` or `unreachable`.

## Log in

```
/login <username> <password>
```

On success the log shows `logged in as …` with your user name and role. The login is remembered —
per server — across restarts until it expires, so you rarely need to repeat it. It's stored in
`~/.config/knotes/tokens.json`, readable only by your user.

Logging in also sends any notes that were queued with the `+` prefix while you were logged out.

!!! warning
    The password you type is visible in the command bar and kept in the session's ↑/↓ history until
    you quit. Log in when nobody is watching your screen.

If the login expires, the next command says `session expired — /login again`.

## List recent records

Each of these lists the **20 most recently updated** records of one kind:

| Command | Lists |
|---|---|
| `/tasks` | Tasks |
| `/goals` | Goals |
| `/journals` | Journals |
| `/planners` | Planners |
| `/resources` | Resources |
| `/notes` | Notes |
| `/writings` | Writings |

The output starts with the total count, then one line per record — its number, title and, if it
has one, its status:

```
tasks — 214 total, showing 20
  #812  Renew the domain  In progress
  #809  Draft the quarterly summary  Done
```

If you're not logged in, the app asks you to `/login` first.

See [Sync](../concepts/sync.md) for how the apps and the server fit together.
