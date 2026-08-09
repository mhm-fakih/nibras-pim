# Changelog

## 2026-08-09

### Added

- **Build a resource from a URL.** A URL field beside the New button on the resources list: paste a
  link, and it opens the ordinary New-resource form prefilled with the page's title, author,
  publisher, description, year and its article text as Markdown. A banner names anything that
  couldn't be extracted ("No article text found — the page may be JavaScript-rendered or paywalled").

  It prefills a form rather than saving a record, because extraction from someone else's markup is
  never reliable enough to write straight to the database. Metadata comes from Open Graph, then
  schema.org JSON-LD, then `<title>`/`<h1>`; the body prefers `<article>`/`<main>`, and on a page
  with no semantic markup it scores each block by **text length minus link text** — a nav column is
  almost all link text and scores near zero however long it is. Nav, header, footer, asides,
  comments and share widgets are stripped either way.

  **This endpoint makes the server fetch a URL you choose, which is an SSRF vector by
  construction** — the browser can't reach the machine's loopback interface, its LAN neighbours or a
  cloud metadata endpoint, but this process can. It resolves the host and refuses loopback, private,
  link-local, unique-local and multicast addresses, rejects any scheme but http(s) (`file:` would
  read the server's own disk), and **re-checks after every redirect**, since a public host that 302s
  to `169.254.169.254` would walk straight past a check done only on the original URL. It is a POST
  so `SecurityConfig` gates it on `content.write`; as a GET, any authenticated user including
  READ_ONLY could have driven it. One residual limit: DNS could change between the check and the
  fetch, which needs a custom resolver or an egress proxy to close.

- **Mobile: search the server.** A new *Search server* page — every other screen shows only what sync
  already pulled down, i.e. only bookmarked records, so anything not yet flagged is invisible on the
  phone however you look for it. This queries the backend directly, filtered by type (the enabled
  modules, from the server), and gives each result a bookmark button.

  **Flagging is the sync mechanism, not a separate feature**: the exports carry bookmarked records
  exclusively, so the button is literally "send this to my phone" and it arrives on the next pull.
  Results come back in the export's own wire shape, so they parse into the record model the browser
  screen already uses. `mobilePushBookmark` is a dedicated endpoint rather than a generic PUT —
  `apply()` writes every DTO field, so a phone PUTting back a record it had only partly parsed would
  blank whatever it left out.

- **Mobile: annotate photos in the gallery** — the desktop `scr-annotator`'s pen, highlighter, crop
  and rename, with **no edit button and no save button**. The tools sit on the photo; picking one
  starts drawing, and the work is saved on leaving the image (page change, switching back to View,
  or leaving the gallery).

  Ported semantics that are wrong if reversed: **crop is applied last** whatever order you drew in,
  because every other mark is positioned in the uncropped image's coordinates; the **highlighter is
  composited on its own layer**, so it blends with the photo rather than with earlier marks, and its
  drag sets only the horizontal extent while the band thickness stays fixed like a real nib; the
  **original is backed up once** to a `.scr-backup` sibling folder before the first burn. As on the
  desktop, **a failed save does not advance** — the marks stay in hand and say so.

  Two things specific to a phone: marks are stored in **image pixels**, converted as they are drawn,
  so a stroke made on a 1080-wide screen lands correctly on a 4000-wide photo; and **EXIF rotation is
  applied when decoding**, since the viewer shows photos upright and burning onto an unrotated bitmap
  would put every mark rotated off its target.

  Arming a tool is one tap and can't be removed: a horizontal drag can only mean one thing, and on a
  phone that gesture is already the page turn. While a tool is armed, the always-visible Previous/Next
  buttons page instead — they are also what triggers the autosave, which is why they are never hidden.

### Fixed

- **The clients were told a different vocabulary from the one Admin claimed.** The phone fetched
  `/api/departments?bookmarked=true` — not priority >= 2 at all — and applied its own enabled-filter
  and sort, so its department list had no necessary relationship to the Admin page's "synced to
  clients" list, the very list that exists to make such drift visible. Both now come from one call to
  `/sync/clientTaxonomy`, served by the same service behind that page, with a test asserting the two
  routes return identical JSON. Ordering follows **`sortOrder`** (the Settings page's "Order"
  column), so there is one order maintained in one place. *`syncOrder` now has no consumer — its
  column, DTO field and Settings input are still there and can be removed.*

- **The bookmarked-records widget used only half its height.** Left over from splitting the plans
  widget: it drew into `widget_collection.xml`, the two-list layout, but only ever filled the first
  list, while the second `ListView` still claimed `layout_weight="1"`. The two-list layout is now
  deleted outright, since nothing referenced it. **Remove and re-add the widget** — a placed instance
  keeps the layout it was created with.

- **The volume widget's title now refreshes in place.** Tapping already refreshed, but the same tap
  also launched Sound settings, so there was no way to just re-read the levels. Title and body are
  now separate targets (title refreshes; bars refresh and open the mixer). They carry **distinct
  request codes** — Android treats PendingIntents differing only by `action` as equal, so a shared
  code would have silently given both targets the same one. This matters because
  `VOLUME_CHANGED_ACTION` isn't reliably delivered to background receivers, which is why the levels
  went stale in the first place.

- **The KPI and volume widgets refused to shrink below three columns**, and the plans widget below
  three rows. `minWidth`/`minHeight` are the floors the launcher enforces (cells are `70·n − 30` dp),
  and they were left at 180dp when `targetCellWidth`/`Height` — only a placement hint — were changed.
  All five widgets now sit at a 110dp (2-cell) floor. **Remove and re-add** to pick it up.

- **Mobile filters show codes, not full names**, with counts parenthesised; the tag cloud selects
  **one tag at a time**, matching the desktop.

- **The desktop tag cloud never appeared.** Its FlowBox carries `no_show_all` so the section's
  collapsed state survives the window's `show_all()` — which also means `show_all()` never reaches
  *into* it, so the chips were created and never shown. The facet and tri-state sections already show
  their rows one by one for exactly this reason; the cloud now does too.

## 2026-08-08

### Added — recurring planner events

- **Planner entries can now repeat, with a start AND end time** — habits at a fixed time of day, not
  just to-dos due on a date. Set it on any planner with the same "Repeats" picker tasks use.

  **A series is a rule, not a chain.** Task recurrence materialises one occurrence at a time and
  clears the source's flag, so exactly one future instance ever exists; that suits a to-do and fails
  a 07:00 habit, where you want to see next Tuesday and to know what you missed. A recurring planner
  instead keeps the rule on one row — the *master*, whose own start/end are the first occurrence —
  and the calendar computes the rest inside whatever window it is drawing. **A year of a daily habit
  is 365 events and zero extra rows** (there's a test asserting exactly that). A real record appears
  only when an occurrence is *touched*: opened, moved, or annotated.

  Same model as iCalendar's `RRULE` + `RECURRENCE-ID` + `EXDATE`, and the reason `recurrence_id` is
  stored separately from `start_date`: drag one occurrence to 09:00 and the row moves, but it still
  stands for the 07:00 slot, so expansion suppresses that slot instead of drawing a ghost at 07:00
  and a duplicate at 09:00.

  Details worth knowing:
  - **Ends: never / on a date / after N occurrences.** Open-ended is the default and is only
    tractable because occurrences are virtual.
  - **× on an occurrence removes just that one.** Deleting the *first* one moves the rule's anchor
    forward rather than destroying the series.
  - **DST is handled by construction.** Expanding a rule gives 07:00 wall-clock every day;
    pre-computed absolute instants would drift by an hour twice a year.
  - **Monthly from the 31st** clamps to Feb 28 for that month and returns to the 31st afterwards,
    because each step is taken from the anchor, not from the previous clamped result.
  - The one recurrence step function (`RecurrenceSteps`) now serves both tasks and planners, so the
    two can't drift apart on what "monthly" means.
  - **On the phone too** (see below). Desktop and terminal still read plain rows via
    `/sync/exportJson*` and so show a recurring planner as its master row only.

  Schema: `V4__planner_recurrence.sql`. Applies on first boot — back up before deploying.

- **Recurring plans appear on the phone's calendar.** `/sync/exportJsonP` expands each series inside
  the ±14-day window it already covers, so a 07:00 habit shows on every day it happens instead of
  only on the master row's own day. Marked **⟳** and drawn paler than a stored entry, because the
  difference is real: tapping or dragging one *creates* the record first (the same idempotent
  endpoint the web uses), and offline it can't be edited at all — you get *"Can't edit a repeat while
  offline"* rather than a silently dropped edit.

  Computed occurrences carry a composite id, `<seriesId>@<recurrenceId>`, plus `seriesId` and
  `recurrenceId` as separate fields. Deliberately not a bare number: a client that mistook it for a
  record id and pushed an edit would rewrite the master and so change *every* occurrence. The
  composite is inert — nothing accepts it — which turns that silent corruption into an obvious
  rejection.

### Added — filters

- **The desktop sidebar's filters are now on the phone**, in a collapsible panel above the note list:
  priority, type and category facets, the non-standard and bad-taxonomy tri-states, the tag cloud,
  and a date strip — with the same faceted counting, so an unpicked option still shows the count it
  *would* match rather than reading 0. Collapsed by default; the header always shows how many filters
  are on and what they narrowed the list to, so a short list is never mistaken for missing notes.

  The date strip is **two weeks** (last week + this, Monday-first) rather than desktop's full month:
  a 6x7 grid can't shrink to fit beside a phone list and still be tappable. It shades by note count
  and filters on tap, and Created/Updated switches which date it reads — "created" comes from the
  file *name's* timestamp, never the mtime, since retagging renames the file and would otherwise drag
  every edited note onto today.

- **Foreign files in the notes folder are visible on the phone.** The folder is a Syncthing share, so
  anything another machine drops in lands there, correctly-named or not; those now appear as "needs
  metadata" placeholders exactly as they do on desktop, which is what gives the non-standard filter
  something to act on. They are kept *out* of the counted set — the header's "N notes", the status
  notification and the KPI widget all read that, and folding foreign files in would inflate all three.

- **A tag cloud, on desktop and mobile.** Free-form `#hashtags` written in a note's **body**, sized by
  how often each is used; clicking one narrows to the notes carrying it, and picking several means
  "carries all of them" (a union could only ever grow the result, which is useless for narrowing).
  This is a different thing from type/category: those are single codes in the file name drawn from a
  configured list, while a tag needs no declaring and a note can hold any number — which is exactly
  what a cloud is for and a fixed row list is not. `## Markdown headings` and `http://…#fragments`
  are not tags.

### Fixed

- **Arabic hashtags were dropped on the phone but kept on the laptop.** Caught by a test before it
  shipped, and worth recording because the two files looked identical: Python 3's `\w` is
  Unicode-aware, Java's is ASCII-only, so the same regex matched `#كتابة` on desktop and not on
  mobile — the two surfaces would have disagreed about the same folder. The Kotlin side now spells
  the class out as `[\p{L}\p{N}_]` rather than relying on `\w` meaning the same thing in both
  languages. (Java's `UNICODE_CHARACTER_CLASS` flag would also fix it, but it needs API 24 and
  minSdk here is 23.)

- **Mobile: a file with an Arabic name wouldn't open.** Three things go wrong on non-ASCII names and
  nothing else, which is why only Arabic files were affected. Copying Arabic text carries invisible
  bidi marks (U+200E/200F and friends) along with it, and they land *after* the extension as often
  as inside the name — so `محاضرة.mp3‏` has extension `"mp3‏"`, matches no MIME type, fails
  `isMediaFile`, skips the in-app player, and ends up in an `ACTION_VIEW` that nothing can resolve.
  The same text can also be spelled in the Unicode presentation block (the lam-alef ligature U+FEFB
  where the plain letters would do), so the name in the server's `files_list` column and the name
  Syncthing wrote to disk can differ byte-wise while reading identically. And `lowercase()` on an
  extension used the device locale. New `FileNames` handles all three: extensions are bidi-stripped
  and folded with `Locale.ROOT`, and a file whose exact name isn't on disk is resolved by an
  NFKC-normalised directory scan before giving up. A genuine miss now names the folder's actual
  contents in the toast instead of only the path it looked for. Covered by unit tests
  (`:app:testDebugUnitTest`, new to this module).

### Added

- **Desktop: a "Bad taxonomy" filter** in the sidebar, alongside "Non-standard". Deliberately a
  separate tri-state: non-standard is about the file *name's shape*, this is about its *values* —
  `2026_08_08-101500~Y~p3~A.md` is perfectly well-formed but invisible to every type filter when no
  type `Y` exists. The two queues want different fixes (retag vs. rename), so a file is never
  counted in both: non-standard names are excluded here, and a *blank* tag is not a bad tag.
- **The same filter on the mobile home screen**, in a collapsible panel above the list. It hides
  itself when nothing is wrong — a permanent "0 bad" row is furniture on a phone — but the header
  stays visible while the filter is on, so a narrowed board can't be mistaken for data loss.
- **Mobile: "Upcoming plans" and "Top priority" are now two widgets** instead of two lists stacked in
  one, so each can be sized and placed on its own. The lists were already independent adapters; this
  is a packaging change. Both are served by the shared `SectionListWidget`.
- **Mobile: the KPI widget is two columns with abridged labels** — `5 nt. td. / 1204` over
  `7.7 gb f.`, and `862 f.` over `12 f. td.`. Three columns of value-plus-spelled-out-caption didn't
  survive being shrunk to a 3x1 cell. The second column now tracks the knotes folder's own growth
  (total files, and how many arrived today) in place of the `/new` inbox count.
- **Mobile: an unsaved note draft survives closing the app.** `rememberSaveable` covers rotation and
  a system-initiated kill but not swiping the app away, which is exactly when a half-typed note was
  lost. Drafts are written to disk as you type; only saving or clearing removes one. New notes only —
  restoring a copy of an existing note's text later would resurrect stale content.
- **Audiobooks: a "Bookmarked" tab** listing every file that has bookmarks, most recently bookmarked
  first, with how far in the furthest mark sits. The folder tree pins the same list for the current
  folder above its alphabetical contents — a folder of 40 numbered chapters sorts the one you were on
  into the middle. Tapping resumes from the player's saved position.
- **Settings: a "Features" section** for parts of the UI that aren't whole pages — currently the RSS
  feed link and the standard per-module reports. Same default-on rule as the page toggles, separate
  `feature.*` key namespace. Your own saved searches are unaffected by the reports toggle: nobody
  would look in Settings for why those vanished. Switching the reports off also skips their
  count queries (one request per report).
- **Saved-search results are full record cards** in the middle pane, not a minimal table, and clicking
  one opens the full record in the detail pane. The search projection only yields module/id/title, so
  each card's body is fetched per record — capped at 40 with a "Show more" button, and showing the
  known title over a skeleton until its fetch lands.

## 2026-08-07

### Fixed

- **WAR silently 404'd on Tomcat** (see `war-fix.md`). `providedRuntime
  'spring-boot-starter-tomcat'` pulls the whole framework transitively on Boot 4, and the war plugin
  subtracts everything reachable from `providedRuntime` out of `WEB-INF/lib` — so the deployed WAR
  had no `spring-web`, Tomcat found no `ServletContainerInitializer`, Spring never booted, and every
  URL returned 404 with a clean log. (`java -jar` kept working, which is what made it easy to miss.)
  Replaced with the four jars the container actually supplies. `bootWar` now fails the build if
  `spring-web` isn't in `WEB-INF/lib`, so this can't regress silently — the guard was verified by
  reintroducing the bug.
- **A task set to repeat monthly repeated every day.** The nightly fixed-schedule sweep spawns any
  recurring task whose due date has passed, but advanced the new occurrence only one step from the
  old anchor — so a task overdue by six months landed five months in the past, was immediately due
  again, and spawned another copy the next night. One task per day until the chain crawled back to
  the present, whatever interval you configured. Fixed-schedule recurrence now advances to the first
  occurrence in the future; completion-triggered recurrence deliberately keeps the single step (a
  weekly task finished late should read as overdue, not silently reschedule itself). A rule that
  can't advance at all — the legacy `recurring` varchar holds nulls and stray values — now spawns
  nothing and logs, instead of duplicating the same task nightly forever.
- **Resource covers (and repo downloads, and the image renamer) were broken off the root context.**
  `fetchBlob` stripped a hardcoded `/api`, but the URLs are built as `${BASE}api/...`; under a Tomcat
  context path that strip silently no-ops and axios prepends its base again, producing
  `/nibras/api/nibras/api/...`. Only covers were noticed because the repo-1 file list uses a relative
  path.
- **Mobile: editing an entry created offline produced several server records.** The outbox
  identified entries only by server id, which is blank until the first successful push, so an edit
  appended a second copy rather than replacing it — and every copy was pushed as its own create.
  Entries now carry a client-generated key assigned at creation; removal matches on identity too, so
  an entry edited mid-push is no longer re-pushed.
- **Mobile reported "Synced" when the password was wrong.** `/page/heartbeatJson` is unauthenticated,
  so the heartbeat passed regardless; every `fetchType` then 401'd into `.getOrNull()` and the
  enclosing `runCatching` still succeeded. Nothing was cached and success was recorded. It now counts
  successful fetches and says *"Sign-in failed — check the username and password in Settings"*.
- **Bluetooth/headset pause didn't rewind.** The on-screen bookmark button marks
  `bookmarkRewind` seconds behind the playhead and seeks there; auto-bookmark-on-pause did neither —
  it stored the raw cut-off point and left the playhead alone. Both paths now behave identically.
- **Global search found nothing on a new machine.** Not a bug: `search_doc` fills incrementally on
  write plus a bulk rebuild, so a fresh install over an existing database has an empty index while
  each module's own search still works. A zero-result search now detects the empty index and points
  at **Rebuild search index** instead of saying "no matches" (`GET /api/search/index-status`).
- **Checking out a goal wrote a blank `title` field.** Goal keeps a vestigial `title` column from the
  legacy schema — populated on 9 of 1300+ rows — while the app edits `summary`. Now exports
  `summary`. Resources still use `title` (Book has no summary).

### Added

- **Automatic schema migrations (Flyway).** `db/extra-schema.sql` no longer needs running by hand on
  a new machine: migrations live in `backend/src/main/resources/db/migration` and apply at startup,
  baselined so the legacy tables are stamped as version 0 and left alone. `ddl-auto` stays `none`.
  Note `spring-boot-flyway` is required alongside `flyway-core` — Boot 4 moved auto-configuration
  into per-integration modules, so without it the app starts cleanly and skips every migration in
  silence. **Any WAR from here on migrates whatever database it's pointed at, on first boot — back
  up before deploying.**
- **Click-to-highlight on the reading page**, mirroring the mobile resource card: clicking wraps the
  text in `==…==` in the body field and appends it to the record's highlights field; clicking again
  removes both. Two granularities, remembered per record — **sentence** (the same split the phone
  uses) and **clause** (also breaks at `، ؛ , ; : —`, for articles whose sentences run a whole
  paragraph). Both are device-independent, unlike a visual-line split, so a highlight stores the same
  text whatever the screen. Now available on every module with a description: `mainHighlights` was
  added to Tasks, Journal, Planner, Goals and Writings; resources keep `fullText` → `highlights`,
  which is the field the phone's own highlight push writes to.
- **The reading page is a real destination**: Edit and repo 1/2/3 buttons, and an expand icon in the
  detail pane that opens it. Edit hands the record back to the master-detail shell via
  `?select=<id>&edit=1` rather than duplicating the form.
- **Admin → Synced to clients**: the parameter vocabulary mobile/terminal/desktop should be showing,
  filtered exactly as they filter it (departments priority ≥ 2 by order number; types of enabled
  modules, in each module's sync order) so a drifted device is visible by comparison. Adds a
  `syncOrder` per module, seeded T G P J N W R I and editable in Settings.
- **Parameters moved to their own page** (`/parameters`, gated by `params.write`) out of Settings
  (`tools.settings`), so the existing PARAMETERS_DATA_ENTRY role can actually maintain the vocabulary
  without being handed the admin surface.
- **Journal nudge** (`journal-nudge.sh` + a `systemd --user` timer): a true OS-level `notify-send`
  reminder, independent of any browser tab. During configurable work hours/days it nudges unless
  **both** a journal entry overlaps the last hour and a task timer is running. Persistent
  notification, time in the title. Install steps are in the README.
- **Dashboards toggle** in Settings → Pages, and toggles for the new Parameters/Insights pages.

### Changed

- **Calendar events are coloured by their own type**, using the chip colour configured on the
  Parameters page. Journal and planner types are separate lookups that can share a colour, so module
  identity moved off the colour channel: planner events get a dashed border, journals solid.
- **The rail's mini calendar shows two weeks by default** (previous + current), expandable to the
  full month — enough to reach "yesterday" or "last Tuesday" while leaving the reports and
  saved-searches list room.
- **Goal habit tracking**: the log entry takes a date (defaulting to today) so a missed day can be
  backfilled, and the "add another indicator" button is gone — a goal tracks one habit, and extra
  indicators only fragmented its history.
- **Calendar "New entry" pre-selects the configured default type** instead of showing "None" for a
  value that wouldn't have stayed blank once saved.
- **The browser tab title follows the app name** from Settings.
- **History is always expanded** in the detail pane; **quick-add note** now confirms with a toast.
- **Mobile**: the add form on the home screen collapses.

## 2026-08-03

- **Insights page** (`/insights`): a new dashboard for the 7 core record types (Tasks, Journal,
  Planner, Resources, Notes, Writings, Goals) — a pie chart of where records live, a "needs review
  first" ranked list (share updated recently, ascending), and a per-module card with a
  created-vs-updated weekly bar chart plus a stale-records mini-list linking into the record.
  Covers a **configurable past period** (default last 3 months) with a separate **configurable
  review window** (default last week), both as dropdowns remembered per-browser — deliberately
  scoped to recent activity rather than a module's entire history, so the page motivates reviewing
  recent work instead of years of backlog. Backend: `InsightsService`/`InsightsController`
  (`GET /api/insights/summary?from=&to=&reviewDays=`), scoping `total` to records **created** in
  the period (not `countAll()`) so the denominator matches what's on screen.
- **Append to description**: every record's detail pane now has an always-visible, autofocused
  "Append to `<field>`…" field under its description field (Enter to add) — no need to open Edit
  for a quick addendum. The prefix/suffix wrapped around the appended text is configurable in
  Settings → "Append to description" (default: a blank-line separator).
- **History section is always expanded** in the record detail pane (previously collapsed by
  default, one click to open).
- **Quick-add note now shows an unmissable confirmation** — a bottom-center Snackbar toast (the
  same one `QuickEditDialog` uses), not just a small flash in the collapsed bar's own header.
- **Calendar "New calendar entry" dialog pre-selects the admin-configured default type** for
  Journal/Planner instead of showing "None" for a value that silently wouldn't stay blank once
  saved (the backend's `LookupDefaultsService` already fills it in on save either way).
- **Browser tab title follows the "app name" setting** (Settings → Application) instead of the
  static `Nibras PIM` baked into `index.html`.
- **1st-pane calendar → one-line date field + icon.** The full month-grid calendar in the sidebar
  used to always take a fixed chunk of vertical space, squeezing out the reports/saved-searches
  list below it. It's now a compact one-line date field with a calendar icon that opens the full
  grid in a popover; picking a date either way runs the same handler and closes the popover.
- **Datetime pickers now support typing digits directly**, everywhere. The remaining native
  `<input type="date">`/`type="datetime-local">` fields (new calendar entry's Start/End, the audit
  log's From/To, the new sidebar date field) are now MUI X `DatePicker`/`DateTimePicker` — a
  JS-driven field whose keyboard section-editing (click a segment, type digits, auto-advance) is
  consistent across every browser/OS, unlike native date-widget typing support, which can vary by
  platform.
- **Journal nudge**: a new local script + `systemd --user` timer (`journal-nudge.sh`,
  `systemd/journal-nudge.{timer,service}`, `systemd/install-journal-nudge.sh`) — a true OS-level
  `notify-send` desktop notification, independent of any browser tab, separate from the web app's
  own once-a-day in-tab reminder. During configurable work hours/days (default 8am–8pm Mon–Fri),
  checked every 15 minutes: unless **both** a journal entry overlaps the last hour (via
  `/api/calendar/events`, a real start/end overlap check, not just "started recently") **and** a
  task timer is currently running (`/api/timer`), it nudges — either condition alone isn't enough,
  and the notification names which one is missing. The notification's title carries the time it
  fired and is **persistent** (`-u critical -t 0`) — stays on screen until dismissed rather than
  auto-hiding after a few seconds. Same config/token-cache convention as `timer-status.sh`
  (`~/.config/nibras/journal-nudge.conf`). New README section covers install on a new machine.

## 2026-08-01

- **Version 1.2.4 "Dastgheib".** All six components (backend, frontend, desktop, terminal, audio
  player, mobile) bumped to 1.2.4 and given this release's codename. Version strings now carry the
  build number as a `.b<N>` suffix where the app has one (e.g. mobile's `1.2.4.b68`, frontend's
  `1.2.4.b78`) — each app's build counter is independent (auto-incremented per build), so the
  suffix is per-app, not a shared number.
- **Calendar: drag to resize/move events, click to edit.** Web (`/calendar`) and mobile now both
  support dragging an event's edge to resize it or its body to move it (mobile: within the same
  day — cross-day drag isn't implemented yet), and clicking/tapping an event opens it for editing
  instead of just navigating away. Backend fix: `mobilePushCalendar` only supported update-by-id
  for Planner entries — Journal entries were always duplicated instead of updated, silently
  breaking edit/resize/move for Journal-type events from the phone.
- **Calendar events bookmarked by default.** The web "New calendar entry" dialog now sends
  `bookmarked: true` on create (mobile's `mobilePushCalendar` already forced this server-side), so
  journal/planner entries created from either calendar round-trip back down to the phone on its
  next sync (mobile only pulls bookmarked records).
- **Record reading page** (`/read/:entity/:id`): clicking a calendar event now opens it on a new
  tab, narrow and chrome-free — the record's content without the full 3-pane master-detail shell.
- **Search by numeric id.** A purely-numeric query now resolves to "the record whose id is this"
  (checked across every module, since ids aren't unique per module) instead of a full-text scan —
  both the global search and each module's own list search. Falls back to normal text search if no
  record anywhere has that id.
- **Goal detail page: inline "add a task" field** — always visible, no button needed to reveal it;
  Enter creates the task linked to that goal.
- **Mobile reminders**: new "Repeat sound (times)" setting (default 4) — plays the reminder sound
  that many times instead of just once, a middle ground between "once" and "loop until dismissed."
  Every reminder notification is now persistent (can't be swiped away) regardless of playback mode
  — previously only the loop-until-dismissed mode was.
- **Mobile: connectivity-triggered sync**, plus tap-to-sync on the KPI and Upcoming-plans widgets'
  titles — both run the same background sync as the periodic timer and the in-app refresh button,
  always ending in a toast with the outcome.
- **Desktop kanban**: empty type-subgroups (a category with zero notes of some type) are hidden
  instead of always showing an empty header. Toolbar split into two rows — title bar (app name ·
  build · note count) and a dedicated toolbar row below it — plus a new refresh button (Ctrl+R),
  and the active note folder is watched on disk (`Gio.FileMonitor`) so a note synced in from
  another machine (e.g. via Syncthing) appears automatically, debounced.
- **Frontend quick-add note field auto-detects RTL** (`dir="auto"`) like the rest of the app's text
  inputs — previously stayed left-to-right even for Arabic content.

## 2026-07-23

- **Version 1.2.0 "Maitham".** Backend, frontend, and the file-watch/scr-annotator/window-tracker
  sibling tools all bumped and tagged with this release's codename.
- **Dashboard builder** (`/dashboards` · `GET/POST/PUT/DELETE /api/custom-dashboards`): named,
  grid-arranged dashboards built from typed regions — quote-of-the-day (last bookmarked note of a
  chosen type), a filtered week calendar (journal/planner entries by type, real hour-grid not a
  list), the goals habit heatmap, the kanban board, or a file's plain-text content. Each region can
  span 1+ of the dashboard's grid columns (`colSpan`, e.g. a calendar spanning 3/4 columns next to a
  1-column heatmap).
- **`/dashboards/{id}/view` — chrome-free, unauthenticated TV/kiosk view.** No SideNav, no login at
  all: backed by one new aggregate, read-only snapshot endpoint (`GET
  /api/public/custom-dashboards/{id}`, explicitly `permitAll` in `SecurityConfig`) that resolves
  only the exact data that one dashboard's regions need, server-side — deliberately narrower than
  opening the generic notes/goals/calendar/kanban read endpoints to anonymous callers. Auto-refreshes
  the data on an interval and reloads the whole page periodically (configurable `refreshMinutes`) so
  a screen left running for days never accumulates stale state.
- **Goals gain a `dashboard` flag** ("Show habit heatmap on dashboards") — marks a goal's linked
  habit indicators for the dashboard builder's heatmap component. Habit heatmap redesigned: 40 past
  days (10/row), a fixed red/orange/light-green/dark-green intensity legend independent of the
  indicator's own colour, future days greyed out, and a count badge when a day has more than one
  logged entry.
- **Deep-link / SPA-fallback fix.** A direct navigation or refresh on a multi-segment route (e.g.
  `/dashboards/2/view`) used to hit Spring's raw 404 Whitelabel page — the WAR's `SpaController`
  pattern only ever matched a single path segment. It now also implements `ErrorController`: any GET
  that falls through to a real 404 and isn't clearly a backend path (`/api`, `/feeds`, `/sync`,
  `/page`, springdoc) gets the SPA shell instead; backend/API 404s still get byte-for-byte the same
  JSON error body Spring Boot produced before (verified — the ~130 `ResponseStatusException` call
  sites across the backend that the frontend reads error messages from are unaffected).
- **Links open in a new tab.** Resources' `url` field is now a clickable link (new `"url"` field
  type) instead of plain text; every link inside markdown/rich-text content anywhere in the detail
  pane (explicit `[text](url)` or an auto-linkified bare URL) now also forces `target="_blank"
  rel="noopener noreferrer"`.
- **HTTPS via Caddy, for desktop notifications.** Browsers silently refuse the Notifications
  permission over plain HTTP on a LAN IP (only `localhost`/`127.0.0.1` count as a secure context).
  Three setup scripts at the repo root (`setup-caddy-prod.sh`, `setup-caddy-client-trust.sh`,
  `setup-caddy-client-local.sh`) cover HTTPS on the server itself vs. a client-side fallback proxy —
  see [DEPLOY.md](DEPLOY.md#https-via-caddy-needed-for-desktop-notifications).
- **Search fixes**: an exact-title-match boost so a record whose title literally contains the full
  query ranks first regardless of MySQL's natural-language-mode word-frequency scoring; and the
  bookmarked/typed token grammar (`* #quote`) now does a real per-module filtered query instead of
  only scanning the ~100 most-recently-updated records across all modules.
- **File upload collisions get a datetime-stamped suffix** instead of silently overwriting an
  existing file of the same name in a repo.
- **Settings page**: lookup/parameter values (resource type, status, context, …) can now be
  created, renamed, and deleted in-app, not just recoloured/defaulted; the "Parameter colours"
  heading is legible again (was near-invisible small grey text).
- **Quick note bar**: an always-present, collapsible entry form at the bottom of the 3rd pane — the
  first line (if under 120 chars after trimming) becomes the summary, the rest the description;
  otherwise the whole thing is the description.

## 2026-07-21

- **Version 1.1.1 "Ammar".** All components (backend, frontend, desktop, terminal, audio player,
  mobile) bumped to 1.1.1 and given this release's codename — shown next to the version number in
  each app's title bar / About section (web: Help page "About" + status bar; desktop apps: window
  title bar; mobile: the version line under the app name).

## 2026-07-17

- **Import notes: resource type chosen up front for 'R' notes.** The type places the record's
  repo folder (`R/<type>/…`), so the page now has a session-wide "Default resource type" select
  at the top plus per-note override chips on each R note card (default highlighted; clicking the
  override again reverts to the default). Import is blocked until an R note has an effective
  type; `POST /api/import/notes` accepts the new optional `resourceTypeId`.
- **Task-timer probe for the status bar.** The web tracker now reports start/stop to the server
  (`/api/timer`: GET status, POST start/stop — state in the settings store, one active timer),
  and a new root-level `timer-status.sh` prints `🟢 <task, 20 chars>` while tracking, `🔴` when
  idle, `🔴 offline` when unreachable — creds via `~/.config/nibras/timer-probe.conf`, JWT cached.
  Character-safe truncation (Arabic titles don't get mangled).
- **Mobile: attachment opening fixed.** Files now always open via the app chooser — knotes is
  itself a registered audio handler, so a plain VIEW on an mp3 silently resolved back into
  knotes; unmapped text extensions (.md, …) get text/plain instead of unresolvable `*/*`.
  "Open files folder" works: the DocumentsUI intent no longer carries an illegal grant flag on a
  foreign provider (it threw SecurityException and fell through), and paths are canonicalised.
- **Terminal: block cursor actually visible** — explicit fg/bg colours; `text-style: reverse`
  swapped in the field's transparent background, rendering the cursor invisible.
- **Command grammar: date tokens.** `(DATE` sets startDate, `)DATE` sets endDate (batch entry +
  batch edit). DATE forms: `2026-07-17`, `2026-07-17_1600`, ISO week+day `295` (wk 29, day 5 =
  Fri; 1=Mon…7=Sun, current week-based year), `295_25` (year 2025), `295_1600`, `295_25_1600`,
  and day offsets `-1` / `+3` / `0` with optional `_HHMM` (`-1_1600` = yesterday 16:00). Time
  defaults to 00:00. In batch entry a malformed date falls back to a title word; batch edit
  rejects it. Example: `j p2 (2026-07-17 quarterly review`. The Batch-entry page lists the
  syntax under the token chips.
- **In-app Help: "Feature guides" section** (kanban goals, time tracking + probe, import
  resource types, checkout files / pending sync / bookmarked TOC); README's features list
  updated to match (checkout naming, TOC, timer probe, R-note types).

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
  accessibility deliberately skipped) in a balanced 2+2+2 icon-and-bar grid (no numeric levels),
  coloured green / orange >60% / red >80% (API 31+). Tapping the widget refreshes the bars AND
  opens the Volume slider page directly (One UI's SecVolumeSettingsActivity; generic sound
  settings as fallback). All widgets share the bookmarks widget's typography (12dp frame,
  14sp bold title, 13sp rows).
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
