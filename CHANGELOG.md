# Changelog

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
