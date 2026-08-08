# knotes

A fast, minimal Android note-taker. One screen: a scrolling list of your notes
with a quick-entry box pinned to the bottom — type and hit send, no dialogs.
Notes are stored as **plain `.md` files** in a directory you choose (legacy
2-field `.txt` notes from older versions are still read, and auto-migrate to
`.md` the next time they're edited).

## Features

- **Single-screen capture** — list of notes (newest at the bottom, chat-style)
  with an always-ready input box above the keyboard.
- **Plain-text files** in a user-chosen directory (set in Settings).
- **Configurable timestamped file names** — default pattern `yyyy_MM_dd-HHmmss`.
- **Type, priority** (configurable range, default 1–5) **and category tags**
  (configurable short codes) are picked per note and **encoded into the file name**.
- **Arabic / RTL** note bodies are automatically right-aligned (in both the list
  and the editor).
- **Search** across note content and the type/category codes (not the resolved
  tag names — matches the raw codes stored in the file name).
- **Push to Nibras** — ⋮ on any note → *Push to Nibras* creates it as a record on the
  backend (`/sync/mobilePushCreate`), typed by the note's filename metadata and saved
  bookmarked so it syncs back on the next pull. Works offline: pushes wait in a
  persistent outbox and are sent automatically when the network returns (the same
  app-level drain also flushes offline-created calendar events).
- **2–3 line preview** per note (configurable).
- Tap a note to edit it in place; ⋮ → **Delete** to remove it.

## File-name scheme

```
<timestamp>~<type>~p<priority>~<category>.md
2026_06_10-143025~W~p3~A.md
```

- `~` is the field separator; the `p<priority>` token is located first (its `p`
  prefix makes it unambiguous regardless of timestamp format), the token before
  it is the type, the token after it is the category — either may be blank
  (`...~~p3~.md`).
- This is the same scheme the desktop app uses, so a note captured on either
  device is readable by both (e.g. over a Syncthing-synced folder).
- On a same-second name collision, a `-N` suffix is appended to the timestamp.
- Legacy 2-field `.txt` files (`<timestamp>~p<priority>~<subjectCodes>.txt`,
  multiple subjects joined with `-`) are still read and listed, and silently
  migrate to the current `.md` scheme the next time they're edited.
- Plain `.md`/`.txt` files dropped into the directory by other apps still show
  up (with no type/priority/category) and become managed once you edit them.

> Note on the timestamp pattern: time uses `HH` (hour) and `mm` (minutes);
> `MM` means **month**. The default is therefore `yyyy_MM_dd-HHmmss`
> (the requested `HHMMss` would have repeated the month instead of minutes).

## Storage permission

To read/write files in an arbitrary directory the app requests **All files
access** (`MANAGE_EXTERNAL_STORAGE`) on Android 11+, or the legacy storage
permission on Android 8–10. You grant this once on first launch.

## Nibras browser (read-mostly)

A built-in browser for a [Nibras](https://192.168.0.3:1441/nibras) desktop database on
the LAN. It's a **persistent left rail** (one letter-cell per type, each with a live
count badge) always visible beside the content — not a button you open. Configure the
**Base URL** and **Username** under Settings → *Nibras server*.

- Lists the 7 data types — **T**asks, **P**lans, **J**ournal, **G**oals, **N**otes,
  **W**ritings, **R**esources — each with a live count, then a **dense list** per type
  (one line per record, bookmarked first). Tap a row to expand it; **only one row is
  open at a time**.
- **Works offline:** every fetched type is cached on disk (`filesDir/nibras_cache/<TYPE>.json`),
  so browsing, reading, bookmarking, and highlighting all work without the server. The UI loads
  from cache instantly and refreshes when the server is reachable (all types are synced on
  connect).
- **Read** and **bookmark** state is tracked **only on the phone** (never written back).
- **Offline-first writes:** notes, highlights, and completions are saved to a local **outbox**
  first and pushed to the server when it's reachable (on connect/refresh, or immediately if
  online).
- **Add a note** to any record → `POST {base}/sync/mobilePush`.
- **Resources only**: the record's `fullText` is shown as tappable sentences; tapping one makes
  it **bold** and queues it → `POST {base}/sync/mobilePushHighlight` (already-highlighted
  sentences stay bold and aren't re-sent).
- **Mark a record complete** (checkbox on the row) → `POST {base}/sync/mobilePushComplete`,
  timestamped locally for a same-day "completed today" count.
- The note/highlight writes use the server's append shape: `{"data":[{"ecode","id","textToAppend"}]}`,
  which appends `textToAppend` to the record's `notes` / `highlights` field.
- It only ever issues `GET` heartbeat/`exportJson` reads and the three `POST`s above (plus the
  separate "Push to Nibras" per-note record creation described under Features, `mobilePushCreate`).
  **No file/attachment downloads** (Syncthing handles those) and no other record mutation.

### Server-side requirements

The endpoint paths live as constants in `nibras/NibrasApi.kt` and match the Grails
`SyncController` actions:

- `mobilePush` — appends `textToAppend` to a record's `notes`.
- `mobilePushHighlight` — appends `textToAppend` to a record's `highlights`.
- `mobilePushComplete` — marks a record complete.
- `exportJsonR` includes a `fullText` field per Resources record (used for highlighting).

The Nibras server uses a self-signed certificate on the LAN, so the OkHttp client in
`NibrasApi.kt` is intentionally configured to trust all certificates — fine for a personal
LAN tool; don't reuse that client for public-internet traffic.

## Collect files

Open from the **folder icon** in the top bar. Gathers files from a configurable set of folders
(Settings → *File collection*; defaults include `DCIM/Screenshots`, `DCIM/Camera`, `Pictures`,
`Download`, `Recordings`, `Music/Voice Recorder`) into a single **timestamped subfolder** of the
**New** folder (default `/storage/emulated/0/New`).

Each file is **renamed with a standardized leading timestamp** (the main timestamp format from
Settings), so everything sorts chronologically regardless of source:

- If the original name contains a recognizable date/time (many camera/screenshot/recorder
  formats), that timestamp is parsed, reformatted, and moved to the front.
- If it has no timestamp, the file's **last-modified time** is used instead.
- e.g. `Screenshot_2024-01-15-12-30-45_Chrome.png` → `2024_01_15-123045_Screenshot_Chrome.png`;
  `notes.pdf` → `2026_06_11-095700_notes.pdf`.

The screen **scans first and lists the files found** (old → new name); pressing **Collect**
moves them and shows the results. Files are **moved** (removed from the source). Requires the
same All-files-access permission as notes.

## Tech stack

- Kotlin 2.0.21, Jetpack Compose (Material 3), OkHttp 4.12
- Android Gradle Plugin 8.7.3, Gradle 8.14.4, JDK 21
- `compileSdk`/`targetSdk` 35, `minSdk` 23
- No database — filesystem + `SharedPreferences` (local notes, settings, and Nibras
  read/bookmark/highlight tracking).

## Project layout

```
app/src/main/java/com/knotes/
  MainActivity.kt     // entry point + storage-permission gate + navigation
  MainViewModel.kt    // note list state + add/update/delete
  KnotesApp.kt        // Application subclass — app-wide connectivity callback (drains outboxes)
  NoteRepository.kt   // reads/writes note files (.md, legacy .txt) in the notes directory
  SettingsStore.kt    // SharedPreferences-backed settings (StateFlow)
  Model.kt            // Note models + file-name build/parse + RTL detection

  ui/        // screens — MainScreen (capture+list), AppRail (Nibras type rail),
             // NibrasBrowserScreen, CalendarScreen, AudiobookScreen (Library/Folders
             // tabs), GalleryScreen, CollectScreen, SettingsScreen, Theme

  player/    // audio/video player — PlayerController + PlayerScreen, PlaybackService
             // (media3), Bookmark (.tmk sidecars), Subtitles, VolumeController

  nibras/    // Nibras browser data layer — NibrasApi (HTTP client), NibrasCache
             // (offline JSON cache), NibrasLocalStore (read/bookmark/highlight/complete
             // state + outbox), RecordOutbox + PushCenter ("Push to Nibras"),
             // CalendarStore/CalendarViewModel (calendar events)

  reminder/  // ReminderScheduler (AlarmManager), PlansRepository/PlansStore (upcoming
             // plans + task-due items), TaskOverdueNotifier, BootReceiver, and the
             // home-screen widgets (KPI, Bookmarks, Upcoming-plans, Phone-status)

  status/    // StatusService (persistent note-count notification), quarter-hour
             // chime (ChimeScheduler/ChimeReceiver)

  collect/   // FileCollector — the "Collect files" feature
```

## Build & run

Open the folder in **Android Studio** (Ladybug or newer) and press Run — it
provisions the SDK and the Gradle wrapper automatically.

From the command line you need **JDK 21** and the **Android SDK** (set
`ANDROID_HOME` / `local.properties`). If the Gradle wrapper jar is missing,
generate it once:

```bash
gradle wrapper --gradle-version 8.14.4
./gradlew assembleDebug
./gradlew installDebug   # to a connected device/emulator
```
