# capture/

Three small tools that pull material from outside into **knotes** — plain-text notes named
`<yyyy_mm_dd-hhmmss>~<type>~p<priority>~<category>.md`, with an optional companion folder
`<same name>.d/` for files. That is exactly what the backend's **Import page** reads
(`NoteImportService`, folder `NIBRAS_KNOTES_DIR`): the type picks the module, the `.d` files become
the record's attachments. So the integration is one shared folder — capture writes, Import reads.

| Tool | Source | Note | Companion `.d/` |
|---|---|---|---|
| `./run.sh telegram` | the latest N messages of a group, channel or Saved Messages | `N` — one transcript per run, titled `Telegram <chat> export` | the messages' photos/videos/files |
| `./run.sh firefox` | a Firefox profile's bookmarks (`places.sqlite`) | `R` — one list per run, titled `Firefox bookmarks export`, grouped by date added | — |
| `./run.sh media URL…` | anything yt-dlp reads (YouTube, Aparat, …) | `R` — one per video: title, duration/site/resolution, URL | mp4 + 128k mp3 + `.srt` in the audio's language |

`R` notes need a resource type when imported — the Import page asks for it. Each Telegram and
Firefox run can also **delete** what it exported (`--delete`), which turns a chat or the bookmarks
toolbar into an inbox that empties into knotes.

## Setup
```bash
cd capture
./setup.sh                 # .venv with telethon, cryptg, yt-dlp; creates config.env
$EDITOR config.env         # NIBRAS_KNOTES_DIR, Telegram API id/hash/phone
./run.sh telegram          # first run is interactive: Telegram sends a login code
```
`config.env` and `telegram/*.session` are gitignored — the session file is a live login, keep it
private. ffmpeg must be on PATH for `media`. Re-run `./setup.sh` now and then: it upgrades yt-dlp,
whose site extractors go stale.

Every tool takes `--knotes DIR` (`-o DIR` for media), `--priority N` (`-P`) and `--category X`
(`-k`); priority and category default to the knotes folder's `.knotes.json`. Notes never
overwrite one another: a name already taken moves on by a second.

## Unattended
```bash
# in config.env:
CAPTURE_TELEGRAM_ARGS='--chat saved --limit 200 --delete'
CAPTURE_FIREFOX_ARGS='--profile "~/.mozilla/firefox/xxxxxxxx.default-release" --delete --if-closed'

./systemd/install.sh       # enables the timers whose line is set
```
Telegram runs nightly; Firefox hourly, doing nothing while Firefox has the profile open
(`--if-closed`) — it must be closed for a deletion to stick. `./run.sh firefox --list-profiles` shows
which profile is which. Logs: `journalctl --user -u capture-telegram`.

## Two things worth knowing
- **Firefox deletions write Sync tombstones.** A bare `DELETE` from `moz_bookmarks` is undone by
  the next Firefox Sync, which finds the bookmarks on the server and restores them as new.
  `delete_bookmarks()` does what Firefox's own remove does — a `moz_bookmarks_deleted` row per
  synced bookmark, parents' `syncChangeCounter` bumped — so the deletion propagates instead.
  A backup `places.sqlite.backup-<timestamp>` is made first, every time.
- **Telegram media references expire mid-run.** Long exports re-fetch a message for a fresh file
  reference and retry once (`download_media`).
