# Nibras PIM

A Spring Boot REST API + React web client over the existing `db_nibras` personal-information
database. Outlook-style 3-pane UI, per-entity RSS feeds, and a mini-calendar that filters records
by creation date. The API is plain REST so it can be reused later from a CLI, desktop, or mobile
app.

The seven original entities below are the core content types; the app has since grown several more
modules (Goals, Indicators, Departments, Courses, Excerpts, custom Dashboards, …) — Settings →
Modules is the current, authoritative list.

## Entities (API name → DB table)

| API path        | DB table     | display field |
|-----------------|--------------|---------------|
| `/api/tasks`    | `task`       | summary       |
| `/api/journals` | `journal`    | summary       |
| `/api/planners` | `planner`    | summary       |
| `/api/resources`| `book`       | title         |
| `/api/notes`    | `index_card` | summary       |
| `/api/goals`    | `goal`       | title         |
| `/api/writings` | `writing`    | summary       |

## Prerequisites
- Java 21, Node 18+ (tested on Node 22), MySQL with the `db_nibras` database and a `<db-user>/<db-password>`
  account. Gradle is provided via the wrapper.

## Quick start (after a reboot)
```bash
./launch.sh            # starts backend (:8081) + frontend (:5173), detached, LAN-accessible
./launch.sh status     # what's running   ·   ./launch.sh logs   ·   ./launch.sh stop
./resume.sh            # resume this Claude Code conversation from the repo
```
First-time setup (DB + deps) is below.

## 1. Create the working-copy database
Development runs against a copy so the live data is untouched:
```bash
./db/clone-dev-db.sh                                   # creates db_nibras_dev
mysql -u<db-user> -p<db-password> db_nibras_dev < db/extra-schema.sql   # adds change_log + app_module
```
`extra-schema.sql` only **adds** new tables (`change_log` audit trail, `app_module`
settings) — it never alters the legacy schema. It is idempotent (safe to re-run).
To point at the live database instead, edit `spring.datasource.url` in
`backend/src/main/resources/application.yml` (and apply `extra-schema.sql` there too).

## 2. Run the backend (port 8081, localhost only)
```bash
cd backend
./gradlew bootRun
```
- Swagger UI: http://127.0.0.1:8081/swagger-ui.html
- Example: `curl 'http://127.0.0.1:8081/api/tasks?size=5'`
- **Hot reload**: `spring-boot-devtools` restarts the app when compiled classes change.
  An IDE recompiles on save automatically; for a CLI workflow, run a continuous compile in
  a second terminal so saves trigger the restart (~1–2s vs a ~7s cold start):
  ```bash
  cd backend && ./gradlew -t classes
  ```
  Devtools is `developmentOnly`, so it is excluded from the production WAR.

## 3. Run the frontend (port 5173)
```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173 — it proxies `/api` and `/feeds` to the backend.

## Single-artifact build (backend + UI in one WAR)
```bash
cd backend
./gradlew clean bootWar           # also runs the frontend build and embeds it
java -jar build/libs/nibras-pim-1.2.0.war
```
Everything (UI, `/api`, `/feeds`, Swagger) is then served from http://127.0.0.1:8081 on one port.
Full instructions for a fresh Linux machine — incl. systemd and reverse-proxy — are in
[DEPLOY.md](DEPLOY.md).

## Journal nudge — install on a new machine

`journal-nudge.sh` and its systemd timer are per-workstation (each desktop that should nag you
runs its own copy, pointed at whichever backend it can reach — usually `http://127.0.0.1:8081` if
the backend also runs locally, otherwise the server's URL). Repeat this on every machine you want
nudges on:

1. **Copy or clone the repo** onto the machine (only `journal-nudge.sh` and `systemd/` are
   actually needed — the rest of the monorepo doesn't have to be present, but cloning the whole
   thing is simplest and keeps the script/units in sync with future edits here).
2. **Create the credentials file** — not tracked in git, one per machine:
   ```bash
   mkdir -p ~/.config/nibras
   cat > ~/.config/nibras/journal-nudge.conf <<'EOF'
   NIBRAS_URL=http://127.0.0.1:8081
   NIBRAS_USER=<your username>
   NIBRAS_PASS=<your password>
   EOF
   chmod 600 ~/.config/nibras/journal-nudge.conf
   ```
   Omit `NIBRAS_URL` if the backend is local on the default port. Add `WORK_START_HOUR` /
   `WORK_END_HOUR` / `WORK_DAYS` here too if this machine's hours differ from the 8am–8pm Mon–Fri
   default (see `journal-nudge.sh`'s header for the exact keys).
3. **Install the timer**:
   ```bash
   ./systemd/install-journal-nudge.sh
   ```
   This copies the two unit files to `~/.config/systemd/user/`, then
   `systemctl --user enable --now journal-nudge.timer`. Re-run it any time you edit
   `journal-nudge.sh` or the unit files — it's idempotent.
4. **Verify**:
   ```bash
   systemctl --user list-timers journal-nudge.timer   # confirm it's scheduled
   journalctl --user -u journal-nudge.service -f       # watch it fire live
   ```
   No notification during a run just means you're "covered" (both a recent journal entry and a
   running timer) or you're outside work hours — not a failure.
5. **Uninstall**, if a machine no longer needs it:
   ```bash
   systemctl --user disable --now journal-nudge.timer
   rm ~/.config/systemd/user/journal-nudge.{timer,service}
   systemctl --user daemon-reload
   ```

Requires `notify-send` (from `libnotify-bin`/`libnotify` — already present on most desktop Linux
installs) and a running `systemd --user` instance with an active graphical session (for the
notification's D-Bus session bus); headless/server machines can still install it, it'll just have
nowhere to display the popup.

## Configuration

### Change the database name / credentials
The connection lives in `backend/src/main/resources/application.yml` under
`spring.datasource` (defaults: db `db_nibras_dev`, user `<db-user>`, password `<db-password>`):
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db_nibras_dev?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC
    username: <db-user>
    password: <db-password>
```
Edit `db_nibras_dev` → `db_nibras` to use the live database. For servers, prefer overriding
at runtime (no rebuild) with environment variables:
```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/db_nibras?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME=<db-user>
export SPRING_DATASOURCE_PASSWORD=<db-password>
```

### Expose on the network (beyond localhost)
The app binds to `server.address: 127.0.0.1` (localhost only) by default. To make it reachable
from other machines, override the bind address and (optionally) port at runtime:
```bash
# WAR:
java -jar build/libs/nibras-pim-1.2.0.war --server.address=0.0.0.0 --server.port=8081
# or via env vars (bootRun or WAR):
SERVER_ADDRESS=0.0.0.0 SERVER_PORT=8081 java -jar build/libs/nibras-pim-1.2.0.war
```
JWT auth + role-based permissions gate every `/api` and `/sync` call (`ADMIN`, `DATA_ENTRY`,
`PARAMETERS_DATA_ENTRY`, `READ_ONLY` — see `SecurityConfig`/`PermissionService`). For LAN/production
exposure, put it behind HTTPS anyway: browsers refuse to grant the desktop-notifications permission
over plain HTTP on anything but `localhost`/`127.0.0.1`. See [DEPLOY.md](DEPLOY.md) for systemd,
nginx, and a Caddy-based HTTPS setup.

## API summary (every entity)
- `GET    /api/{entity}` — paged list. Filters: `q`, `createdOn=YYYY-MM-DD`, `from`, `to`,
  `bookmarked`, `statusId`, `typeId`, `tagId`, plus `page`, `size`, `sort`.
- `GET    /api/{entity}/{id}`
- `POST   /api/{entity}` · `PUT /api/{entity}/{id}` · `DELETE /api/{entity}/{id}` (soft delete)
- `GET    /api/{entity}/lookups` — status/type/tag options for forms
- `GET    /api/calendar?from=&to=[&entity=]` — per-day creation counts (mini-calendar badges)
- `GET    /feeds/{entity}.xml` — RSS 2.0 feed of the latest items

## Features beyond CRUD
- **Audit trail** (`/audit` page · `GET /api/audit`, `GET /api/audit/{Class}/{id}`): a Hibernate
  listener records every insert, per-field update (old→new), and delete into the `change_log`
  table — viewable per record (History panel) and globally with filters.
- **Full-page calendar** (`/calendar`): week/day grid of Journal + Planner entries with ISO week
  numbers, configurable slot size (15/30/60 min) and start/end hours (saved per browser), a
  mini-calendar overview, and click-a-slot to add an entry. `GET /api/calendar/events?from=&to=&modules=`.
  Events are coloured by their own **type** (the chip colour set on Parameters), with planners drawn
  dashed and journals solid so the two stay distinguishable when a type name is shared.
- **Recurring planner events** — habits at a fixed time of day, with a start *and* end time. A
  recurring planner is a **rule, not a chain**: the record holds it, and the calendar computes the
  occurrences inside whatever window it is drawing, so a year of a daily habit costs 365 events and
  **zero extra rows**. A real record appears only when an occurrence is *touched* — opened, moved or
  annotated (`POST /api/calendar/occurrences/materialise`); `…/skip` drops a single occurrence, and
  deleting the first one moves the rule's anchor rather than destroying the series. Ends never / on a
  date / after N. Same model as iCalendar's `RRULE` + `RECURRENCE-ID` + `EXDATE`. Because occurrences
  are expanded rather than stored, a 07:00 habit stays 07:00 across a DST change.
- **Build a resource from a URL** (`POST /api/scrape`): paste a link beside the New button on the
  resources list and get a prefilled New-resource form — title, author, publisher, description, year
  and the article text as Markdown, with the nav/header/footer/comments stripped. Metadata comes from
  Open Graph, then schema.org JSON-LD, then the document title; a page with no semantic markup falls
  back to scoring blocks by *text minus link text*. It prefills a form rather than saving a record,
  and names whatever it could not extract. **The server fetches the URL you give it**, so the
  endpoint refuses non-http(s) schemes and any host resolving to a loopback/private/link-local
  address, re-checking after every redirect; it is a POST so `content.write` is required.
- **Settings → Modules** (`/settings` · `GET/PUT /api/modules`): enable/disable each module in the
  nav and assign a unique short **code** (e.g. `T`) for later command-style use.
- **Settings → Pages / Features**: show or hide each tool page, and switch off parts of the UI that
  aren't pages of their own — the per-module RSS link and the standard report shortcuts. Both default
  on; your own saved searches are never hidden by the reports toggle.
- **One vocabulary for the clients** (`GET /sync/clientTaxonomy`): the enabled modules with their
  types, and departments of **priority >= 2**, ordered by the Settings "Order" column. The mobile app
  and the Admin page's *synced to clients* list read the **same** service, so a difference between
  what the server offers and what a device shows is real drift rather than two filters disagreeing.
- **Search from the phone** (`GET /sync/mobileSearch`, `POST /sync/mobilePushBookmark`): the mobile
  app can search the server directly — including records it has never synced — and flag any result.
  The exports carry bookmarked records only, so flagging *is* "send this to my phone".
- **Attachments** (`GET/POST/DELETE /api/attachments/{module}/{id}`, `/file?path=`): per-record
  files under `<base>/<module>/<id>` — list/upload/download/delete from the detail pane. Base dir is
  `nibras.attachments.base-dir` (default `<repo>/attachments`, override `NIBRAS_ATTACHMENTS_DIR`).
- **Import notes** (`/import` · `GET /api/import/notes`, `POST` with `{filename}`): one-click import
  of plain-text knotes whose filename encodes metadata —
  `YYYY_MM_DD-HHMMSS~TYPE~pN~DEPT.md` (e.g. `2026_06_15-214006~N~p3~Z.md`). The **type code**
  selects the target module via its Settings code (N→note, T→task, …), priority/department/creation
  date come from the filename (department resolves to the legacy `department` lookup by its letter),
  the first suitable line becomes the title and the body the description. Imported files move to
  `imported/`. Folder: `nibras.import.notes-dir` (default `<your-home>/knotes`, override
  `NIBRAS_KNOTES_DIR`). Every entity now also exposes its **Department** field for view/edit.
  **'R' notes need a resource type before import** (it places the type-bucketed repo folder,
  `R/<type>/…`): the page offers a session-wide default select plus per-note override chips, and
  the request carries `resourceTypeId`.
- **Edit full text in your editor** (`POST /api/{module}/{id}/fulltext/export`,
  `GET …/changes`, `POST …/apply`): export a record's editable long-text fields to a single
  **checkout file** — `<CODE><id>.md` (e.g. `W18.md`) in the record's repo-1 folder, fields
  delimited by `@@ field @@` markers — edit with any editor, then **Review & sync** — a
  word-level diff (additions green, deletions red) shows what changed; Apply writes it back
  through the audit-tracked save. Resources include `full_text` + `highlights`; others carry
  title/summary, description, notes. Long-text fields render as **Markdown** in the detail view,
  including `==highlight==` → highlighted text, and are edited in-app as plain **Markdown
  textareas** (no rich-text editor).
- **Bookmarked master TOC** (`POST /api/fulltext/bookmarked-toc`, button on the Import files
  page): checks out EVERY bookmarked record across all modules and rebuilds `repo1/TOC.md`
  (department → course → `CODE+id — title` + a manuscript-index embed per record, for the
  Zettlr-style writer). A checkout that already exists with different content is never
  overwritten — it's parked beside as `<CODE><id>.<timestamp>.md` and reported as a conflict.
- **Task time tracking + status-bar probe** (`/api/timer`): Track/Stop on a task creates a
  Journal linked to it; the running session is also flagged server-side, and the root-level
  `timer-status.sh` prints `🟢 <task, 20 chars>` / `🔴` / `🔴 offline` for a status bar
  (creds in `~/.config/nibras/timer-probe.conf`, JWT cached).
- **Version status-bar probe** (`/api/info`): the root-level `version-status.sh` prints
  `v1.2.0 "Maitham"` / `offline` — same config file and cached JWT as `timer-status.sh`.
- **Journal nudge (true desktop notification, no browser tab needed)**: the root-level
  `journal-nudge.sh`, run every 15 minutes by `systemd/journal-nudge.timer`
  (`systemd/install-journal-nudge.sh` installs it as a `systemctl --user` timer), checks — during
  configurable work hours/days (default 8am–8pm, Mon–Fri) — whether a journal entry overlaps the
  last hour (`/api/calendar/events`) AND a task timer is currently running (`/api/timer`). Both are
  required; either alone still fires a `notify-send` alert (with a message naming which is missing).
  Separate from the web app's own once-a-day browser notification below (only fires while a tab is
  open); config in `~/.config/nibras/journal-nudge.conf`, JWT cached like `timer-status.sh`. Setup
  steps for a new machine: [Journal nudge — install on a new machine](#journal-nudge--install-on-a-new-machine).
- **Recurring tasks**: a task can repeat **on completion** (the default — marking it done spawns
  the next occurrence) or on a **fixed schedule** (a daily background sweep spawns the next
  occurrence by date regardless of completion) — daily/weekly/monthly/yearly presets, or an
  advanced cron expression. The recurrence rule "moves" to each new occurrence; the source task
  is never touched again once it's spawned its successor.
- **Urgent flag**: a Task-only boolean, independent of `priority` — surfaced as its own report
  preset and desktop-notification signal, not folded into the priority scale.
- **Subtasks** (`POST /api/tasks/{id}/subtask`): only on a task that already has a Goal — creates
  a new task on the same Goal at `orderNumber = parent + 1` (parent backfilled to `1` if unset).
- **Desktop notifications**: the web app polls for overdue/due-soon tasks & planners (reusing the
  `overdue`/`dueSoon` report filters) and for a missing-journal-entry alert past a configurable
  hour (Settings → Notifications), firing native browser notifications — opt-in, permission
  requested from an explicit Settings toggle, never auto-prompted.
- **Global full-text search** (`/search` · `GET /api/search?q=`): MySQL FULLTEXT over an additive
  `search_doc` index (kept in sync on every create/update/delete; rebuild via
  `POST /api/search/reindex` or the Admin page). Ranked, cross-module results. See
  [db/MIGRATION.md](db/MIGRATION.md).
- **Kanban** (`/kanban` · `GET /api/dashboard/kanban`): bookmarked goals + tasks as columns by
  **department**, each with a Goals block (by type) and a Tasks block (by status).
- **Dashboard builder** (`/dashboards` · `GET/POST/PUT/DELETE /api/custom-dashboards`): build
  named dashboards from typed regions — quote-of-the-day, a filtered week calendar, the goals habit
  heatmap, the kanban board, or a file's plain-text content — arranged in a grid, each region
  spanning 1+ columns. **`/dashboards/{id}/view`** is a chrome-free, **unauthenticated** rendering
  meant for a TV/kiosk with no login at all: it calls one aggregate, read-only snapshot endpoint
  (`GET /api/public/custom-dashboards/{id}`, `permitAll` in `SecurityConfig`) that resolves only the
  exact data that dashboard's regions need server-side — not the generic content API — auto-refreshes
  on a configurable interval, and reloads the whole page periodically so it never runs stale JS
  unattended for days.
- **All bookmarks** (`/bookmarks` · `GET /api/dashboard/bookmarks`): every bookmarked record across
  all modules in one list, grouped by module in a fixed order (departments, courses, planners,
  journals, goals, tasks, resources, notes, writings).
- **Departments & Courses as modules** (`/api/departments`, `/api/courses`): the legacy `department`
  and `course` tables are now first-class modules (own list/detail/edit, bookmark, folders/covers,
  search, reports — codes D, C). Each **course belongs to a department**; every record is
  categorized by both **department** and **course** (`department_id`, `course_id`). On a record's
  form the course is a searchable picker that **auto-fills the department** from the chosen course.
  The batch command resolves them by code: `c<code>` = course, `d<code>` = department
  (e.g. `cIUL_SOC210 dU`). They are seeded into `app_module` by `db/extra-schema.sql` (no table
  changes — the tables already existed).
- **Admin dashboard** (`/admin` · `GET /api/admin/stats`): per table, total records and, for each
  parametric field, how many distinct values are **used / available**.
- **Reports + Outlook rail**: the left rail is calendar → per-module **reports** (bookmarked, no
  status/type/department/priority, with tags, overdue, due-soon, high-priority, recently
  created/updated) → module + tool buttons. Reports are filter presets (`GET /api/{m}?overdue=true`
  etc.); the list shows an active-report chip.
- **Batch edit** (`POST /api/{module}/batch {ids, command}`): multi-select records in the list, then
  a compact command — `p3` priority, `#code` type, `?code` status, `@code` department, `=code` code
  field, `'en` / `'ar` language, `*` bookmark, `*-` un-bookmark, `!` urgent, `!-` un-urgent —
  applies to all selected (audit-tracked).
- **Language: a leading apostrophe** (`'en`, `'ar`, `'pt-br`). One convention across batch edit,
  batch entry, **search** (there it filters: `prayer 'ar` finds Arabic records matching "prayer")
  and **note import** (a tag in a captured note's first line sets the record's language and is
  dropped from the title). Case-insensitive, so `'AR` and `'ar` are one language. It needs at
  least one letter after the apostrophe and lets nothing but letters and a dash follow, so ordinary
  prose — `don't`, `'quoted'`, a bare `'` — is never mistaken for a language tag.
- **Covers**: a `cover.{jpg,png,webp,…}` in a record's folder shows as a thumbnail in list rows and
  larger in the detail pane (`GET /api/attachments/{module}/{id}/cover`).
- **Indicators** (`/indicators` · module over the `indicators` table + `indicator_data` series):
  a monitoring dashboard. Bookmarked indicators are watched; by `frequency` (daily/weekly/monthly)
  the app shows which are **missing the current period's data**. Values are **proposed** (never
  auto-saved) — a `query`-type runs its stored SQL read-only (`GET /api/indicators/{id}/propose`),
  a file-type counts files under `path` matching `extensions` (hidden files/folders excluded) —
  and you **confirm** to append a data point (`POST …/{id}/data`). Each indicator shows its
  time-series chart (`GET …/{id}/series`). `GET /api/indicators/due` drives the reminders.
- **Insights** (`/insights` · `GET /api/insights/summary?from=&to=&reviewDays=`): a review-progress
  dashboard for the 7 core record types — a pie chart of where records live, a "needs review
  first" ranked list, and a per-module created-vs-updated weekly bar chart with a stale-records
  mini-list. Covers a **configurable past period** (default last 3 months) and a separate
  **configurable review window** (default last week), both dropdowns remembered per-browser —
  `total` is scoped to records *created* in the period, not the module's all-time count, so the
  page motivates reviewing recent work instead of years of backlog.
- **Highlights gallery** (`/highlights` · `GET /api/highlights[?module=&limit=]`): your records'
  key points, read one card at a time. **Live, not a snapshot** — a record whose highlights are
  filled in today is in the gallery today, which is why it reads the records themselves rather than
  a table of its own. Every module that carries the field feeds it: tasks, planners, journals,
  goals, notes and writings keep theirs in `mainHighlights`, and **resources in `highlights`** — the
  `book` table has both columns and only the latter is ever written, so the two names are not
  interchangeable. The server splits the field into bullets (stripping `-`, `*`, `•`, `1.`, `2)`)
  and decides direction from the first strong letter, once, so the web page and the phone cannot
  drift apart about either. Read-only; the card names its record and links to `/read/<module>/<id>`.
- **Flash cards** (`/flashcards` · `GET /api/flashcards`, `GET …/summary`, `PUT …/{id}/state`): the
  counterpart — a **frozen one-time extract** of passages (≤100 words) from notes with a description
  of 200 words or more, favouring religious and psychological material, `aya` notes excluded. Three
  actions per card — **reviewed**, **repeat**, **keep** — synced to the backend so the phone and the
  browser share one deck. Unreviewed cards are capped at 40 in the queue and the rest hidden;
  repeat/kept are uncapped. A gallery, not a list: one passage at a time, shrunk to fit rather than
  scrolled, justified, direction from the passage itself.
- **Status bar** (`GET /api/info`): a thin bar under the list/detail panes showing server IP
  (prefers the LAN `192.*` address), port and DB name. **Collapsible left rail**: a chevron
  collapses it to a thick bar (state remembered).
- **Responsive / mobile**: on small screens the left rail becomes a hamburger **drawer** and the
  3-pane view shows the **list or the detail** (with a "Back to list"), one at a time.
- **Image renamer** (`/images` · `GET /api/images?dir=`, `/file`, `DELETE`, `POST /restore`,
  `PUT /rename`): paste a directory path, step through its images one by one, and rename or
  delete (no confirmation) each — fully keyboard-driven (←/→ navigate, r/F2/Enter rename,
  d/Del delete, u undo). Rename auto-advances to the next image. Delete moves the file to a
  `.image_trash/` subfolder so the last delete/rename can be **undone**. Operations are bounded
  to the chosen folder (basenames only, traversal blocked).

## Design notes
- **Soft delete**: deletes set `deleted_on`; a Hibernate `@SQLRestriction` hides those rows from
  every read. Nothing is hard-deleted.
- **Legacy schema is never altered**: `spring.jpa.hibernate.ddl-auto=none`. Only a curated subset
  of each (40–80 column) table is mapped; the REST DTO is the full curated surface.
- **JWT auth + role-based permissions**: stateless tokens (`/api/auth/login`), a DB-backed
  role→capability matrix editable at runtime (`/permissions`) so who-can-do-what doesn't need a
  redeploy. Reads are open to any authenticated user; writes and admin tools are gated per capability.
- **RTL/Arabic** content is handled with `dir="auto"` throughout the UI.

## Mobile app (knotes)

`mobile/` is a Kotlin/Compose Android client (`com.knotes`) over the same API. Two things about it
are worth knowing before reading the code:

- **The left rail is split into two bands, and only one is open at a time**: what works **on this
  phone** (prayer times, notes, image gallery, media player, file collector) and what comes **from
  the server** (calendar, the record browsers, flash cards, highlights, server search). The
  distinction is the one that matters when the network is not there. Prayer times and file
  collection each have a switch in Settings that removes the page *and cancels what it had
  scheduled* — checked inside `PrayerScheduler.sync` / `CollectScheduler.sync`, at the single point
  either arms an alarm, so no caller can arm one around the switch.
- **The galleries work offline.** `FlashCache` keeps the server's raw response on disk and is read
  *before* the network is tried; `FlashOutbox` holds flash-card decisions taken offline and
  `PushCenter` drains it when connectivity returns. Highlights is read-only and just needs the
  cache.

Prayer times are computed on the device (`prayer/PrayerTimes.kt` — declination, equation of time,
hour-angle solving) with a tabular Hijri calendar and a per-user offset, so nothing about that page
needs a network.

## Media sidecars

A recording carries its extras as files beside it, sharing its base name. The phone
(`mobile/.../player/`) and the desktop player (`audio-player/`) read the same conventions, so a
folder works identically in both:

```
talk.mp3
talk.srt          subtitles        (talk.en.srt for a tagged language)
talk.ar.ai.txt    transcription    note the order: language BEFORE .ai.txt
talk.tmk          bookmarks        [m:ss] optional note, one per line
```

Two things worth knowing before changing any of this:

- **With an `.srt` loaded, an unlabelled bookmark takes the sentence being spoken as its label** —
  read at the *bookmarked* position, not the playhead, since the mark is deliberately placed a few
  seconds back. A typed note always wins. Both clients do it; on mobile it happens in two places,
  the player screen and `PlaybackService` (which bookmarks automatically on pause).
- **Ordering media by "newest" uses the date in the FILE NAME, not mtime.** These folders are
  Syncthing shares and Syncthing rewrites mtimes on transfer, so mtime says when the phone received
  a file rather than when it was recorded — see `MediaDates`.

## Layout
```
backend/        Spring Boot 4.1 / Java 21 / Gradle 9  (REST API, RSS, calendar; bootWar embeds the UI)
frontend/       React 18 + Vite + TypeScript + MUI    (3-pane UI, mini calendar, Markdown editor/viewer)
mobile/         Kotlin + Jetpack Compose (com.knotes) (offline-first Android client, widgets, player)
desktop/        GTK3 / Python                          (note editor + tag bank)
terminal/       Python TUI                             (knotes_tui.py)
audio-player/   Python                                 (desktop player; writes the .ai.txt transcripts)
tools/          gather/load/canonical flashcards, API docs dump
db/             clone-dev-db.sh                        (working-copy setup)
dist/           built artefacts, in a subfolder per type: war/ apk/ zip/ sql/ appimage/
                newest TWO of each kept; the only directory that leaves this machine
DEPLOY.md       fresh-machine deployment guide         (WAR, systemd, nginx, DB + network config)
```

The six components share one version and codename — see the top of `backend/build.gradle`,
`frontend/package.json`, `mobile/app/build.gradle.kts` and the `VERSION` constants in the three
Python apps. `file-watch/`, `scr-annotator/` and `window-tracker/` are separate small tools, each
its own git repo, carrying the same tag in a plain `VERSION` file.
