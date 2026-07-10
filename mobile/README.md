# knotes

A fast, minimal Android note-taker. One screen: a scrolling list of your notes
with a quick-entry box pinned to the bottom — type and hit send, no dialogs.
Notes are stored as **plain `.txt` files** in a directory you choose.

## Features

- **Single-screen capture** — list of notes (newest at the bottom, chat-style)
  with an always-ready input box above the keyboard.
- **Plain-text files** in a user-chosen directory (set in Settings).
- **Configurable timestamped file names** — default pattern `yyyy_MM_dd-HHmmss`.
- **Priority** (configurable range, default 1–5) and **subject tags**
  (configurable short codes) are picked per note and **encoded into the file name**.
- **Arabic / RTL** note bodies are automatically right-aligned (in both the list
  and the editor).
- **Search** across note contents and subject tags.
- **2–3 line preview** per note (configurable).
- Tap a note to edit it in place; ⋮ → **Delete** to remove it.

## File-name scheme

```
<timestamp>~p<priority>~<subjectCodes>.txt
2026_06_10-143025~p3~W-I.txt
```

- `~` and `-` are used as separators and never appear in a date pattern, so the
  priority and subjects can always be parsed back out of the file name —
  regardless of the timestamp format you configure.
- Multiple subjects are joined with `-` (e.g. `W-I`).
- Plain `.txt` files dropped into the directory by other apps still show up
  (with no priority/subject) and become managed once you edit them.

> Note on the timestamp pattern: time uses `HH` (hour) and `mm` (minutes);
> `MM` means **month**. The default is therefore `yyyy_MM_dd-HHmmss`
> (the requested `HHMMss` would have repeated the month instead of minutes).

## Storage permission

To read/write files in an arbitrary directory the app requests **All files
access** (`MANAGE_EXTERNAL_STORAGE`) on Android 11+, or the legacy storage
permission on Android 8–10. You grant this once on first launch.

## Nibras browser (read-mostly)

A built-in browser for a [Nibras](https://192.168.0.3:1441/nibras) desktop database on
the LAN. Open it from the **cloud icon** in the top bar. Configure the **Base URL** and
**Username** under Settings → *Nibras server*.

- Lists the data types (Tasks, Events, Plans, Journal, Goals, Notes, Writings, Resources)
  with live counts, then a **dense table** per type. Tap a row to expand it; **only one row is
  open at a time**.
- **Works offline:** every fetched type is cached on disk (`filesDir/nibras_cache/<TYPE>.json`),
  so browsing, reading, bookmarking, and highlighting all work without the server. The UI loads
  from cache instantly and refreshes when the server is reachable (all types are synced on
  connect).
- **Read** and **bookmark** state is tracked **only on the phone** (never written back).
- **Offline-first writes:** notes and highlights are saved to a local **outbox** first and
  pushed to the server when it's reachable (on connect/refresh, or immediately if online).
- **Add a note** to any record → `POST {base}/sync/mobilePush`.
- **Resources only**: the record's `fullText` is shown as tappable sentences; tapping one makes
  it **bold** and queues it → `POST {base}/sync/mobilePushHighlight` (already-highlighted
  sentences stay bold and aren't re-sent).
- Both writes use the server's append shape: `{"data":[{"ecode","id","textToAppend"}]}`, which
  appends `textToAppend` to the record's `notes` / `highlights` field.
- It only ever issues `GET` heartbeat/`exportJson` reads and the two `POST`s above.
  **No file/attachment downloads** (Syncthing handles those) and **no record creation**.

### Server-side requirements

The endpoint paths live as constants in `nibras/NibrasApi.kt` and match the Grails
`SyncController` actions:

- `mobilePush` — appends `textToAppend` to a record's `notes`.
- `mobilePushHighlight` — appends `textToAppend` to a record's `highlights`.
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
- `compileSdk`/`targetSdk` 35, `minSdk` 26
- No database — filesystem + `SharedPreferences` (local notes, settings, and Nibras
  read/bookmark/highlight tracking).

## Project layout

```
app/src/main/java/com/knotes/
  MainActivity.kt      // entry point + storage-permission gate + navigation
  MainViewModel.kt     // note list state + add/update/delete
  NoteRepository.kt    // reads/writes .txt files in the notes directory
  SettingsStore.kt     // SharedPreferences-backed settings (StateFlow)
  Model.kt             // Note / Subject models + file-name build/parse + RTL detection
  ui/MainScreen.kt     // list + bottom input bar (subjects, priority, send)
  ui/SettingsScreen.kt // directory, format, preview lines, priority range, subjects
  ui/Theme.kt          // Material 3 (dynamic color on Android 12+)
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
