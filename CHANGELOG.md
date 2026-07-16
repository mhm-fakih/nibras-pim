# Changelog

## 2026-07-16

### Bookmarked-records checkout + master TOC (`repo1/TOC.md`)

The record-checkout feature grew from "export one record's text" into a full editing workflow
around the Zettlr-style writer fork (format contract: `nbr-zettlr/docs/MASTER-TOC-FORMAT.md`).

- **Per-record checkout file names.** A record's editable text exports to
  `<CODE><id>.md` (e.g. `W18.md`, `T1795.md`) in its repo-1 folder instead of the shared
  `_record.md` — editor tabs are finally tellable apart. Legacy `_record.md` checkouts are still
  read/synced, and are retired (or parked, see below) on the next export.
- **"Bookmarked TOC" button** (Import files page, pending-sync section →
  `POST /api/fulltext/bookmarked-toc`): checks out **every bookmarked record across all seven
  modules** (T, P, G, J, N, W, R) and rebuilds the master index at **`repo1/TOC.md`**:
  department (`#`, "No dept." last) → course (`##`, when set; course-less records first so their
  embeds don't nest under a stray course heading) → `### <CODE><id> — <title>` + a structural
  embed (`![](W/18/W18.md)`, relative to the TOC). Records order within a group: module
  (T→P→G→J→N→W→R), then priority (desc), then id.
- **Checkouts never clobber edits.** If a checkout file already exists with content that differs
  from the fresh DB export (i.e. unsynced editor changes), the old file is parked beside it as
  `<CODE><id>.<yyyyMMdd-HHmmss>.md` — a name the pending-sync scan deliberately ignores — and
  the response lists these conflicts; the UI shows them as a warning with the backup file names,
  for manual comparing/merging.
- **Round-trip**: editing any embedded checkout in the writer shows up in the fulltext
  pending-sync list (Import files page) exactly like before; syncing applies the `@@ field @@`
  blocks back to the record with audit tracking.

### Capture apps

- **Terminal: robust `notes` import.** The TUI shares the desktop app's `notes.py` (there is no
  `terminal/notes.py` in the repo — see `terminal/README.md`). A local copy now takes precedence
  (standalone `terminal/` folders work by dropping `desktop/notes.py` beside the script), the
  repo-layout `../desktop` fallback stays, and a missing module exits with a clear message.
- **Desktop: kanban hides empty category columns** — the board only spends width on panels that
  have notes (the "—" uncategorized column already behaved this way).
- **Mobile player: panel chips span the full width**, bookmarks first and open by default; the
  bookmark button row is 30% shorter with a 3-line filename cap. (Panels — bookmarks / in this
  folder / recently played / settings — replaced the old long stacked page.)
- **Desktop: view mode + sorting on the toolbar.** The minimal-mode toggle grew into a linked
  kanban / list / minimal radio group, joined by a sort dropdown (Date / Category / Type /
  Priority) — both persisted and applied immediately; removed from the Settings dialog.
- **Desktop: loud launch failure.** A crash while opening the window (classically a nonexistent
  or unwritable notes folder) used to be swallowed inside GTK's activate vfunc, leaving a
  windowless ghost process holding the D-Bus name — later launches showed nothing. Now it prints
  the traceback, shows an error dialog pointing at config.json, and quits.
- **Terminal: half-created venv recovery** — `run.sh` rebuilds `.venv` when `bin/pip` is missing
  (an interrupted first run used to break every launch); the command-bar cursor is a solid
  reverse-video block again instead of the underline.
- **Mobile widgets rework.** KPI widget: notes today/total · free storage (GB, moved from the
  volumes widget) · `/knotes` vs `/new` recursive file counts (hidden files/folders ignored) as
  a ratio — the tasks-today and top-priority indicators are gone (the latter now lives in the
  Upcoming-plans widget's second-section header, "Top priority list (N)"). The phone-status
  widget is now **"Volumes"**: six streams (Ring, Notification, Alarm, Music, Call, System —
  accessibility deliberately skipped) in three columns as icon + bar (no numeric levels),
  coloured green / orange >60% / red >80% (API 31+). All widgets share the bookmarks widget's
  typography (12dp frame, 14sp bold title, 13sp rows).
- **dist/build.sh: no more PyInstaller single-binary step** — the desktop app ships as plain
  `.py` sources (`desktop-app/` + `run-desktop.sh`) only.
- **audio-player joins the repo** (`audio-player/`, GTK/Python — the sixth app, also synced to
  GitHub). New feature: sidecar **`.ai.txt` transcriptions as "lyrics"** — `x.ar.ai.txt` /
  `x.en.ai.txt` beside `x.mp3` appear as `Transcript: Off/ar/en` chips; picking a language opens
  a scrollable side panel with the whole text (no timestamps, unlike `.srt` subtitles; Arabic
  right-aligned), and a trash button deletes the shown transcription file (with confirmation).

## 2026-07-10

### Mobile ↔ backend sync (records authored on the phone)

- **Backend: `POST /sync/mobilePushCreate`** — the generic companion to `mobilePushCalendar`.
  Creates a record of **any** exportable type from the phone. Item shape:
  `{"data":[{ecode, summary, description, priority, department, startDate?, endDate?}]}` where
  `ecode` is the module type code (`T/J/P/N/G/W/R`) and `department` is the single-letter
  Department code (same vocabulary as note file names). Every record is saved with
  **`bookmarked = true`** so it round-trips on the next `exportJson<CODE>` pull. Plain-text
  descriptions become minimal HTML (`<br>` line breaks — no literal newline, so the export's own
  `\n → <br/>` mapping doesn't double them). Requires `content.write` (same gate as the other
  `mobilePush*` endpoints).
- **Mobile: "Push to Nibras" on every note card** (⋮ menu). Builds a record from the note —
  first line → summary, body → description, filename metadata → type/priority/department — and
  queues it in a new persistent **record outbox** (`RecordOutbox`, offline-first). The card shows
  an up-cloud icon while queued and a filled done-cloud once the server accepts it; re-pushing an
  edited note replaces its pending entry (no duplicates).
- **Process-wide outbox drain** (`KnotesApp` + `PushCenter`). A connectivity callback now lives in
  the `Application`, not in a screen's ViewModel — so the record outbox **and** the calendar
  outbox drain as soon as a network appears, regardless of which screen (if any) is open.
  Previously the calendar drain died with `CalendarViewModel`.
- **Calendar events stay first-class before sync** (unchanged, now guaranteed by the new drain):
  offline-created events feed the Upcoming-plans widget and fire reminders while still queued;
  once pushed they come back bookmarked from the server and the plans store switches to the
  server copies without duplicating.

## 2026-07-03

### Web — backend + frontend
- **Import folder is configurable.** The knotes import folder is read from settings (key
  `import.notes-dir`), defaulting to `$HOME/new/knotes` instead of a hardcoded path. The Import
  Notes page shows the current folder in an editable field (`PUT /api/import/notes/dir`), and the
  list response now returns `{ dir, notes }`.
- **Reusable `RecordCard` component.** A single-record card (coloured entity bar + summary,
  description preview, status, type, department, course, bookmarked) for showing one record outside
  the detail pane. Shown as a "✓ Saved" card right after a record is created; reusable elsewhere
  (e.g. batch add).
- **Real save-error messages.** The entity form now surfaces the backend's actual error (missing
  required field, etc.) instead of a generic message. Blank lookup fields (type/status/…) are still
  auto-filled with their admin-configured defaults on create (`LookupDefaultsService`).
- **Repository folder buttons redesigned.** The detail-pane "1/2/3" buttons now show a folder icon,
  an `R1/R2/R3` label, and a **live count of files** in each repository (shares the file-list cache).

### Mobile (Android)
- **Player priority bump.** Tapping bookmark again within the priority window bumps the priority
  *without* moving the playhead and flashes "✓ priority added pN" (previously it always flashed
  "Bookmarked −Ns", which looked like a rewind).
- **KPI widget "completed today" comes from the server.** Counts tasks **and** goals whose
  `completedOn` (server field, reused from the existing schema) is today — so server-side
  completions count, not just on-device checkbox ticks. `exportJson` for T and G now carries
  `completedOn`.
- **Quarter-hour chime volume.** The chime now plays through a `Ringtone` (notification usage), so
  it follows the device's notification-volume slider instead of always playing at full volume.
  Still uses the user's chosen sound, else the default notification sound.
- **Audio-only mode on the player.** A toggle disables the video track (e.g. while driving);
  persists across files.
