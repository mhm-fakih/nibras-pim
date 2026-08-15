# Changelog

## 2026-08-15 — 1.3.33 "Gratitude"

Version bumped to **1.3.33** and the release codename changed to **Gratitude**, across all six
components (backend, frontend, mobile, desktop, terminal, audio player).

### Added — a language convention: `'code`

- **A leading apostrophe sets or filters the language**: `'en`, `'ar`, `'pt-br`. One rule in one
  place (`CommandParser`), so it means the same thing in batch edit, batch entry, search and note
  import. In search it filters rather than assigns — `prayer 'ar` finds Arabic records matching
  "prayer" — and on import a tag in the note's first line sets the record's language and is
  stripped from the title, since it is metadata and not part of what you wrote.

  The apostrophe was free: no other token starts with one. The risk with a punctuation prefix is
  not failing to match but matching too much, so it is guarded twice:

  - **It must be its own word** — preceded by a space or starting the text. Without that, anything
    ending in a code matches: `rabi'ar` would be read as Arabic.
  - **The code must be one you configured** (Settings → "Language codes", default
    `ar,en,fr,fa,de`). Checking a list rather than accepting any letters is what stops a quoted
    word being eaten: an unrecognised `'xyz` stays ordinary text instead of vanishing from a title
    or a search. `don't`, `'quoted'` and a bare `'` were never candidates either way.

  Codes are lower-cased, so `'AR` and `'ar` are one language.

### Changed — repositories 2 and 3

- **The repo buttons read `Repo1` / `Repo2` / `Repo3`** instead of `R1`/`R2`/`R3`, which collided
  with the `R` of Resources.
- **Repos 2 and 3 can be hidden** (Settings → Features). Repo 1 stays: it is where checkouts and
  bookmarks live, so every record has one. Hiding a button changes nothing on disk and the
  `f`→`2`/`3` shortcut still works — this is about a toolbar that fits.

### Changed — desktop

- **The side editor pane's starting mode is a setting** (Settings → "Editor pane starts in":
  read or edit). Switching mode in the pane still sticks, as before; this is what a fresh window
  starts in.
- **Sort cards by note length**, longest first — for finding the notes that have quietly grown into
  something that should be split. Measured on the note's text, not the file size, so the metadata
  in the name and any highlight markers don't count towards "how much have I written here".

### Changed — desktop

- **The tag bank is per-machine and no longer tracked** (`.gitignore`). The desktop app writes a
  starter `tag-bank.md` wherever one is missing, holding `tag1`, `tag2`, `tag3` and a comment block
  explaining the format. Placeholders rather than a curated vocabulary on purpose: the bank is the
  user's own writing, and seeding it with someone else's taxonomy means every install begins by
  deleting tags it never asked for. An existing bank — including one deliberately emptied — is
  never touched, and an unwritable location is not worth failing over.

- **Merged the w335 editor-pane / autocomplete update** from the other machine: a reworked editor
  pane (read/edit modes, configurable width, autosave), a tag bank with autocomplete
  (`tagbank.py` + `tag-bank.md`, both new), per-card counts, and a start-on-today filter.
  `knotes.py` grows by 313 lines; build number 62 → 81.

  `config.json` was **not** overwritten — it holds this machine's repository paths and its backend
  password. The seven new settings the incoming code reads (`editor_pane_mode`,
  `editor_pane_width`, `editor_pane_visible`, `editor_autosave_ms`, `highlight_color`,
  `card_counts_min_chars`, `start_filter_today`) were merged in as keys, leaving every existing
  local value alone.

### Changed — audio player

- **Play/pause is Ctrl+Space, not Ctrl+P** — and it now works while a bookmark label is being
  typed, which is what was actually broken. A letter key's keyval is layout-dependent: on an Arabic
  layout the P key does not produce `Gdk.KEY_p`, so the shortcut silently did nothing exactly when
  you were typing an Arabic label and worked fine everywhere else. Space carries the same keyval in
  every layout. Plain Space still types a space while you're in a text field, as it must.

- **Bookmark ticks are painted on the waveform strip, and are actually visible now.** They were
  `Gtk.Scale` marks, which the theme draws as a one-pixel hairline in the trough — lost against a
  44px waveform, which is exactly where they need to be read. They now get the full height of the
  strip, a solid colour, a flag at the top, and a translucent halo so a tick stays legible over a
  loud stretch (tall bars) and a silent one (flat) alike. They also draw when there is no waveform
  at all — still decoding, or over the new size limit — which is when they matter most.

- **The browser / playlist split can be dragged.** The genre-artist-album columns were packed at
  their natural height, so the playlist and player below them could never be given more room, or
  less, whatever the window size. There is a divider between them now, and its position is
  remembered. Dragging it right down is allowed, for when the playlist is what you're working in.

- **Waveforms are skipped above a configurable file size** (Settings → "Waveform limit (MB)",
  default 10, 0 disables the limit). Drawing the envelope decodes the *whole* file, which for a
  multi-hour recording is minutes of CPU and a large peak list — spent on a decorative strip, while
  the seek bar, the bookmark ticks and playback itself never needed it. Over the limit the strip is
  left empty and a one-line note says why.

## 2026-08-14 — 1.2.33

Version bumped to **1.2.33** across all six components (backend, frontend, mobile, desktop,
terminal, audio player).

### Fixed — mobile

- **Today's-notes count was reading the file's modification time, not the note's own date.**
  Syncthing rewrites files it hasn't changed, so yesterday's sync of a few hundred old notes set
  every mtime to "now" and all of them counted as written today. Editing one word, or retagging
  (which renames the file), did the same on a smaller scale.

  A note's date is in its file name, written once and never changed — the only timestamp that
  answers "when was this made". All three counters that got this wrong now go through one shared
  `NoteFiles.createdMillisOf`: the homepage header, the status-bar notification, and the KPI widget.
  Having three copies of the rule is why they could disagree; there is one now. A file whose name
  carries no timestamp (a foreign file dropped in the folder) still falls back to its mtime, since
  that is genuinely all there is.

  The calendar strip and the date filters were already correct — they used the file-name date.

### Changed — search, on both mobile and desktop

- **Opening search puts the caret in the box.** On mobile the keyboard comes up with it. On the
  desktop it looked wired already — `grab_focus()` on toggle — but a `Gtk.SearchBar` reveals its
  entry through a `GtkRevealer`, so at that instant the widget is not yet mapped and the grab is
  dropped when the animation finishes. It now focuses again once the entry is actually on screen,
  which is why the keyboard shortcut appeared to work while the button did not.
- **Searching clears any active filter first.** A search run inside yesterday's sidebar selection
  gives the worst kind of empty result: the note IS there, the filter hid it, and nothing on screen
  says so. Starting from everything makes "no results" mean it.

### Added — markdown in the mobile note reader

- **`**bold**`, `*italic*` / `_italic_`, `` `code` ``, `~~strike~~` and `#` headings now render.**

  The markers stay in the text and are dimmed rather than stripped, which is what makes this safe
  to add to the reader rather than a rewrite of it: every character keeps its offset, so the clause
  spans, the tap-to-highlight hit-testing and the stored `==…==` highlights all still line up with
  the text they were computed from. Removing a `**` would shift every position after it and quietly
  highlight the wrong words.

  Emphasis inside backticks is left alone — a path with an underscore is not italic — and headings
  size relative to the surrounding text rather than to a fixed point size, so they follow the
  reader's own font size instead of fighting it. Deliberately not a Markdown implementation: no
  links, lists or tables, because a reader that half-parses those is worse than one that shows them
  as written.

### Changed — mobile note cards: read by default, edit full-screen

- **Tapping a card now reads it.** Editing is a labelled **Edit** button on the card. Reading is the
  common act and the one that cannot lose anything, so it is the one the whole card surface does.
- **Editing is full-screen.** Read and Edit are two views of one screen — the same note, the same
  full height, one chip apart, as on the desktop's side pane. A note long enough to be worth
  reading was unworkable in the six-line bar at the bottom of the list.
- **The text saves as you type**: once when you pause, and again when the editor leaves the screen.
  Per-keystroke writes would rewrite the file dozens of times a sentence and Syncthing would
  faithfully propagate every one; waiting only for Back would lose the text if the process were
  killed. Both together cost one write per pause.
- **Type / priority / category are pinned under the text, in both reading and editing.** Changing a
  priority is a one-tap thought you have *while reading* — "this matters more than I thought". If
  the chips scrolled away with the body, acting on that would mean scrolling a long note to its end
  and back, which is enough friction that the priority simply doesn't get changed. Only the content
  above scrolls. The composer keeps its own copy for new notes, and the same chips are used in both
  so a code looks and means the same wherever it is set.

  Changing a tag renames the file (the name encodes them), so the screen adopts the note that comes
  back from the rename — holding the old one would write the body to a path that no longer exists
  and leave two notes where there was one. Whatever is typed but not yet saved rides along with the
  tag change rather than being lost to the rewrite.

  Two further guards, because that rename makes every held reference stale and the resulting failure
  is silent and duplicating rather than loud:

  - The editor's save effects use the **current** commit callback, not the one captured when they
    started. `DisposableEffect` never restarts, so on the way out it would otherwise have fired a
    lambda pointing at the pre-rename path. The debounce had the same trap by a narrower door:
    retagging without typing leaves the text byte-identical, so a `LaunchedEffect` keyed on it
    doesn't restart either.
  - A body-only save **skips when its file is gone** rather than recreating it. That holds whatever
    the UI does, and there is nothing to lose by skipping — the rename already carried the body.

### Fixed — mobile text direction

- **English notes were rendering right-to-left.** Direction was a property of the whole note, set by
  "does this text contain any Arabic character" — so one quoted Arabic word, one Arabic name, one
  `'ar` tag, and the entire English note laid itself out right-to-left with its punctuation thrown
  to the wrong end.

  Direction is now decided **per line, by its first strong letter**, everywhere text is shown: the
  note reader, the note cards, and the Nibras record/full-text views. A note that mixes an English
  heading with an Arabic quotation now gets both halves right.

  - **Digits, punctuation, brackets and emoji are neutral** — a line opening `1.` or `(` is decided
    by the first real letter after it, which is what a reader would say the line is written in.
  - **A line with nothing to judge by inherits the line above**, rather than snapping to a default,
    so a numbered list doesn't alternate sides.
  - **Everything is justified**, in both directions.

  The same fix reaches card titles, calendar entries and the quick-note field, because the
  underlying `NoteFiles.isRtl` — which every one of those calls — was changed at its definition
  rather than at each call site. It now answers with the first strong letter too, so one rule
  governs a whole string (a title, a file name) and its per-line sibling governs a body of text.

### Added — mobile reminders

- **"Stop reminders" beside Dismiss** on a plan reminder. Dismiss clears the one in front of you and
  leaves the event's later reminders alone; Stop reminders switches off everything still to come for
  that event — for when you're already at the venue and the 5-minutes-before nudge is just noise.

  - **It silences one occurrence, not the plan.** The token stored is the event key *plus its start
    time*: a repeating plan has the same key every week, so silencing by key alone would quietly
    kill next week's reminder too.
  - **It has to be recorded, not just cancelled.** Alarms are cancelled and re-armed from scratch on
    every sync, so cancelling the pending ones would see them back within minutes. Both happen: the
    pending alarms go immediately, and the scheduler skips the event from then on.
  - Because a token names one occurrence, it expires — it stops meaning anything once that
    occurrence has started, so the set stays the size of "events silenced today".

### Added — reading and highlighting

- **Note cards on the mobile homepage now have a Read action** (the 📖 button beside ⋮, and a Read
  item in the menu). Tapping the card still opens it for editing, as before. Read opens the note
  full-screen with comfortable line spacing and **tap-a-clause-to-highlight**, the local-file
  counterpart of the Resources full-text reader. The reader has an Edit button, so read → spot
  something → edit is one tap rather than a trip back to the list.

  **Highlights are written into the note file itself** as `==…==`, not kept in a phone-local set.
  That is the one real difference from the Nibras resource reader, and it follows from where the
  thing being read lives: a resource is a server record the phone mirrors, so its highlight must be
  queued and pushed. A note *is* a file in a synced folder — writing the marker into the file means
  the highlight reaches the desktop and the backend by the same route the note does, with nothing
  to sync, queue or reconcile. It is also the same markdown the web client writes, so a note
  highlighted on the phone reads correctly in the browser.

  Saving keeps the note's tags, so the filename doesn't change and a highlight can't drag the note
  to a new position in the list.

- **Highlighting is now clause-level, not sentence-level** — in the web reader (Resources full
  text and every other record's reading page), in the mobile Resources reader, and in the new note
  reader. A sentence in this material regularly runs a whole paragraph, so sentence mode highlighted
  a block at a time: correct, and useless for picking out the line that mattered.

  The web reader's Sentence/Clause toggle is unchanged and per-record choices already saved still
  win — only the default moved.

  Two details:

  - **Clause boundaries are identical on every client.** The Kotlin splitter uses the same
    terminator sets as the web's `HighlightableText.tsx` (`. ! ? ؟ ؛` and newlines for sentences,
    then `، ؛ , ; : — –` within them). A highlight is stored *as text*, so a disagreement about
    where a clause starts would mean the same tap saving a different string on each client.
  - **Highlights made before this change still show.** They are keyed by the whole sentence they
    were made on, which no clause key can ever match, so a record already worked through would have
    come back looking untouched. A clause inside a highlighted sentence still renders highlighted,
    and tapping it removes the old sentence highlight rather than layering a clause on top of one
    the second tap couldn't undo.

### Added — mobile player

- **"Play next" now has three modes**, in the player's settings panel:

  - **Off** — what the player has always done, unchanged: when a file ends the next one in the
    folder is *cued paused*, so one tap continues, but nothing starts by itself.
  - **Same folder** — the next file in the folder plays automatically.
  - **Random old** — a file from anywhere in your configured library folders (all of them) that you
    have never bookmarked and haven't touched in 90 days. For working through what was downloaded
    and forgotten.

  The mode also governs the ⏭ button, so "next" means one thing everywhere — except under **Off**,
  where pressing ⏭ still gives you the next file in the folder. Off is about what happens *by
  itself*, not about refusing to do what you asked.

  Details that decide whether "random old" is usable rather than merely implemented:

  - **A library filled last month has nothing 90 days old.** Rather than silently doing nothing —
    which reads as broken — the pick then comes from the 25 oldest unbookmarked files: the same
    "old end of the library", measured relatively instead of absolutely.
  - **The current file and recent plays are excluded.** With a small library, uniform random hands
    back what you just heard often enough to feel like a bug.
  - **Bookmarked files are excluded outright**, not merely deprioritised — a bookmark marks
    something already being worked through, and this mode is for what isn't.
  - **An emptied sidecar counts as unbookmarked.** Deleting every bookmark on a file should put it
    back in the pool.
  - **The library walk shares the Media page's own folder pruning**, so it skips `.stversions` —
    Syncthing's archive of every previous version of every file — rather than offering you a
    three-year-old copy of something you already deleted.
  - The walk runs off the main thread (the chips show "finding one…" while it does).

### Fixed — audio player

- **Pressing Enter after typing a bookmark label crashed the player.** Not a Python exception — a
  segfault, preceded by `gtk_container_get_focus_child: assertion 'GTK_IS_CONTAINER' failed`.

  Committing the label unparented the inline entry to put the static label back. Unparenting a
  widget that *has focus* makes GTK emit `focus-out-event` synchronously, **during**
  `holder.remove(entry)` — and the focus-out handler was the same restore function, which then
  added the label to a container in the middle of having its child removed. The outer removal then
  completed and orphaned the label it had just been handed.

  The swap now happens on an idle callback, so the entry is never unparented while a signal is
  being dispatched on it, and the entry's handlers are disconnected before it. A latch makes the
  edit end exactly once (Enter is normally followed by a focus-out, which would otherwise commit
  and swap a second time).

- **A label typed and then clicked away from was thrown away. It autosaves now.** Focus-out used to
  cancel the edit; it commits. Escape is the way to abandon an edit, which is what Escape is for.

  The save also uses the file that was open when the edit *started*, not whichever file is open when
  it ends — opening another file mid-edit would otherwise have written this file's bookmarks into
  the new file's `.tmk` sidecar.

- **Clicking a different file left the highlight on the previous one.** Two causes, both fixed:
  playing rebuilds the playlist store, and the click handler did that *before* the tree view's own
  handler had run, so the view was left selecting a row index against a model that had been
  replaced underneath it. Playing is now deferred to an idle so the click finishes against the model
  it was aimed at, and after any rebuild the now-playing row is selected explicitly rather than
  left to whatever index the view happens to restore.

### Added

- **A report builder** (Tools → Reports): pick a record type, tick the columns you want, tick the
  filters that decide which records appear, choose the order, save it. It is a saved search you
  build by pointing at things rather than by writing a query, and the editor shows a live preview of
  the first five matching rows as you build it — including changes you haven't saved yet.

  - **`cover` is offered as a column** and renders the record's image inline, not a filename.
  - **Values show their full name** — a resource's type reads "Article", never `art`. This needed no
    resolution step: running a report hands its stored filters to *the module's own list service*,
    so the rows come back as the module's ordinary records, and those already carry every lookup as
    `{id, name, code}`. Projecting the columns on the server instead would have meant
    re-implementing that resolution, and getting it subtly different.
  - **Generic from the start.** Resources were the ask, but a report stores which module it is
    over, so the same builder already works for tasks, journals, notes and the rest — the field list
    comes from the same definitions the forms are built from, and every module's list endpoint
    already accepts the same filters. Restricting it to resources would have been the *larger*
    change, not the smaller one.

  Two smaller decisions worth knowing:

  - **Only real database columns are offered as sort keys.** An image isn't one, and neither is a
    tag list — "sorted by tags" has no single meaning when a record has three. Offering them and
    letting the query fail would be a worse answer than not offering them.
  - **A report can be written as a query instead of ticked.** `from Book where type.code = 'art'
    order by id desc`, in the same HQL a saved search takes. The query's own `order by` *is* the
    sort — the stored sort field is ignored while a query is set, rather than being applied on top
    of it, because two answers to "in what order" can only disagree. The sort controls say so
    instead of sitting there looking editable.

    Either way a report is a query, never a table: the filters become a `Specification` plus a
    `Pageable`, so the WHERE, the ORDER BY and the LIMIT all reach the database and nothing is
    fetched to be narrowed afterwards.

    What the query path refuses, and why:

    - **Anything that writes.** Checked on the text, because a read-only transaction does *not*
      stop HQL bulk DML — Hibernate will happily run `delete from Book` inside one. Opening a
      report must never be a way to change data.
    - **Projections** (`select title, author from Book`). Rows have to be whole records or there is
      nothing behind them to draw a cover or a status chip from. Columns are chosen with the column
      picker, not in the select clause.
    - **Another module's records**, which would render as a table of blanks. The error names the
      type the query actually returns.

    Rows still come back as the module's own DTOs — mapped inside the query's own transaction,
    since that transaction closes on the way out and a lazily-loaded `tags` collection read
    afterwards would fail on every record that has a tag.
  - **A report opens even when it has gone stale.** A column whose field was since renamed is
    dropped from the table rather than drawn as a blank column with a raw property name in its
    header, and a sort on a field that no longer exists falls back to newest-first instead of
    refusing to run.

## 2026-08-13

### Added

- **The task timer moved into a shared store, so only one can run.** It persisted under
  `nibras.timer.task.<id>` — one slot per task — so nothing stopped three running at once, and two
  trackers for the *same* task (its record page and its sprint row) couldn't see each other. A guard
  inside the component couldn't have fixed it: the task being stopped is usually not the one whose
  component is mounted. One key now holds the running session, starting one **stops and saves** the
  previous, and a `storage` listener extends that across tabs.

- **The timer appears under each task on the sprint board**, so the list you work from is the list
  you start the clock from. Hidden for completed tasks and closed sprints.

- **The auto-stop is configurable and now actually fires.** Settings → Tasks → "Auto-stop a timer
  after (minutes)", default 60. It had always been 60, but in a per-browser preference with no UI at
  all. More importantly it lived in the tracker component, so a timer started on a sprint row and
  navigated away from had nothing left to stop it; it is armed in the store now, survives navigation,
  and is re-armed from the persisted start time on load. A deadline already passed (tab closed
  overnight) saves the session ending **at the cut-off, not when the browser noticed** — a maximum
  that can be exceeded by forgetting isn't one.

- **Two timer notifications**: one when the auto-stop fires, and a 15-minute nudge while nothing is
  being tracked. The nudge's interval is re-armed from the store rather than running always and
  checking — an interval ticking through a tracked session would fire the instant you stopped, which
  reads as nagging. It only runs while a tab is open.

- **Ticking a task's completion checkbox now sets its status**, to whatever you choose in
  **Settings → Tasks → "Status on completion"** — a menu of the task form's own statuses, so the
  choice can only ever be one that exists.

  This started as a magic string. `SyncService` already set the status on completion via a hardcoded
  `findFirstByCode("done")`, so a task completed *from the phone* changed status while the same task
  completed on its record page or in a sprint review did not — it got a completion date and kept
  whatever status it had. The same task read as finished on one screen and open on another depending
  on where you ticked it. One resolver now serves all three paths, and the code is a setting rather
  than a literal. The old `"done"` code remains the fallback, so an instance that never opens that
  setting behaves exactly as it did.

  Three behaviours worth knowing, each with a test:

  - **An explicit status in the same save wins.** Choosing a status and completing in one action is
    a decision, and the automatic one must not overrule it.
  - **Only on the transition.** Editing an already-complete task doesn't keep resetting its status.
  - **Un-completing leaves the status alone.** What it was beforehand isn't recorded anywhere, so
    restoring it would be a guess.

  "Leave unchanged" is a real option, and a setting pointing at a since-deleted status falls back
  rather than failing the save — completing a task must not break over a parameter someone tidied up.

### Fixed

- **Optimistic locking actually locks now.** Every entity has carried an `@Version` column since the
  legacy schema and every DTO shipped the value to the client — but nothing carried it back in.
  `update()` loaded the row and applied the DTO over it, so Hibernate compared the row against
  itself and no conflict was possible. Two tabs editing one record, or two controls on one screen
  writing before the first refetch landed, silently overwrote each other.

  The version the client read is now compared against the row's current one, and a mismatch is a
  **409** telling you to reload. Worth being precise about what this adds: Hibernate's own check
  covers the window *inside* the transaction (`update … where id = ? and version = ?`), so it
  catches a concurrent commit while the server works. It cannot see a **client** whose copy is
  stale — the row and the server's copy agree perfectly; the browser is what's behind. That is the
  case that was losing data.

  `AbstractCrudService` is *bounded* on a `Versioned` interface rather than reading `version()`
  reflectively, so a twelfth DTO cannot quietly skip the check — it won't compile. A body with no
  version is still accepted: that is a caller with no baseline, not a caller dodging the check.

  Six tests, and the decisive one is **"does the first writer's change survive"** rather than "does
  a stale version 409" — a guard that returned 409 and still wrote would pass the weaker test.

- **Server error messages reach the screen.** Found while testing the above: the 409 body came back
  empty. Boot returns `{timestamp,status,error,path}` by default, but the frontend reads
  **`data.detail`** in five places — the RFC 7807 shape, which was never switched on. So every
  carefully-worded server message ("Refusing to fetch … it resolves to a private address", "The
  page returned HTTP 404", the new conflict message) has been arriving empty and falling back to a
  generic string. `spring.mvc.problemdetails.enabled: true` makes the frontend's long-standing
  expectation true.

- **The reading page's highlight mode ran every record together as one line.** The sentence splitter
  breaks on `\n` and then trims each chunk — right for matching, since a highlight has to resolve
  to the same string on any client — but the newlines were gone by render time and the chunks were
  joined with a literal space. `white-space: pre-wrap` was set the whole time on a container with no
  newlines left to honour. The gaps are now recovered from the original text by position, so
  whatever sat between two chunks (space, newline, blank line) is emitted verbatim while the chunks
  stay trimmed.

- **Appending to Description and then Notes could undo the first append.** Both bars read the same
  cached record; appending to one writes, invalidates and refetches, and appending to the other
  before that refetch landed sent the stale copy back — whole record and all, because the generic
  PUT applies every DTO field. The append now re-reads the record at submit time, narrowing the
  window from "until the refetch lands" to the milliseconds between that GET and its PUT. The
  locking above catches what remains.

- **Mobile: the app died on every note save.** Saving calls `StatusService.refresh()`, which still
  started the service when *sync* was enabled — a leftover from before sync moved to WorkManager.
  `startForegroundService()` is a contract: call `startForeground()` within ~5 seconds or Android
  kills the process. The new `onStartCommand` saw the notification setting was off and returned
  early through `stopSelf()`, never posting the notification. The note was written first, which is
  why it survived every crash.

  Two fixes, since either alone leaves the trap armed: `refresh()` now starts the service only when
  the notification is on, and `onStartCommand` calls `startForeground()` **first, unconditionally**,
  before deciding whether to stop. Any caller that used `startForegroundService()` entered that
  contract on our behalf, whatever we then decide.

### Changed

- **The "Append to…" bar takes multiple lines and grows to seven**, instead of a single-line slot
  you had to type a paragraph through blind. That moves submitting to **Ctrl/Cmd+Enter**, since
  Enter now inserts a newline — binding Enter to submit would make a second line impossible to type.

- **Notes gets its own append bar**, alongside Description. It was hardcoded to `record.description`
  and now takes the field as a prop, so the two keep independent text.

- The append bar shows the **server's own message** on failure rather than "Could not append." — a
  409 says the record changed and to reload, which is the one thing worth knowing.

### Mobile

- **Media page** (was Audiobooks), with a filter that searches the whole library regardless of where
  you are in it. Three folders deep in an album, typing a name finds it anywhere — the filter
  overrides the tab and shows a flat list, and clearing it returns you exactly where you were.
  Matches file names plus the cached artist/album/genre. Embedded TITLE tags are **not** indexed:
  the tag cache holds genre/artist/album, and adding titles would mean re-reading every file, which
  is the cost that cache exists to avoid.

- **A file collection is now a note with attachments.** It writes
  `<timestamp>~<type>~p<priority>~<category>.d` plus a matching `.md` beside it, which is the layout
  the desktop, the backend importer and this app already agree on — so a collection stops being a
  loose folder nothing recognises. The folder name is *derived* from the note name, so the two can't
  drift apart; nothing would catch it at runtime if they did.

- **Notes and collected files default to the same folder, `/Notes`.** They used to default apart
  (`/knotes` and `/New`), which quietly broke what collections are for: the desktop only reads a
  note plus its `.d` if they sit in the notes folder. Only fresh installs are affected. This also
  exposed a hardcoded `"knotes"` path in the KPI widget's file counts — already wrong for anyone who
  had moved their notes, and about to count a folder that doesn't exist.

- **A note's media attachment opens in the app's own player.** Tapping one called the external
  intent chooser for every file type; there was no in-app path from a note at all, which is why
  audio would open in some other app but never this one. Media now plays in-app, everything else
  still goes out to whatever handles it.

- **`notes.sqlite` and its companions are ignored** like dotfiles — prefix-matched
  case-insensitively, so `-wal`, `-shm`, `-journal`, `Notes.SQLite` and any future companion are one
  rule rather than a list to maintain.

- **KPI widget: a second row, and colour as the signal.** Top-priority notes and upcoming plans,
  green when the number is where it should be and red when it isn't — over 20 for today's notes and
  top-priority, and **under** 10 for upcoming plans, where a low number is the problem. Colour
  rather than styling because the widget is read at arm's length. The priority counted is now a
  setting, and all three consumers follow it — the KPI count, the Top priority widget's title, and
  the rows beneath that title, which came from two different places.

- **The Upcoming plans panel starts collapsed and remembers how you left it.** `rememberSaveable`
  survives a rotation but dies with the process, so it reopened itself on every cold start.

## 2026-08-12

### Added

- **Sprints.** A dated block of committed work — usually a single day — built from your bookmarked
  tasks, reviewed by ticking off what got done, and drawn on the calendar alongside journals and
  planners. New page at `/sprints` (toggleable like any other tool page), new `sprint` and
  `sprint_task` tables, service, controller and API client.

  **Membership is a row, not a column on the task**, and that is the design decision everything
  else rests on. The requirement — *how many times was this task committed to a sprint and not
  done* — is a question about history. A `sprint_id` on `task` can only remember the most recent
  commitment; a task carried from Monday's sprint into Tuesday's into Thursday's has three
  memberships and two misses, and only rows can say that.

  **A miss only counts once the sprint has been reviewed.** An unfinished task in a sprint that is
  still running is not a broken promise, so `sprint.closed_on` exists purely to draw that line —
  without it every task would accumulate misses simply by being in today's sprint. A task's slip
  count shows on its row and in the pick list, worst offenders first, so the decision to commit a
  repeat straggler is made deliberately rather than by scrolling past it.

  **The expected duration comes from `task.planned_duration`** — a legacy column that has been on
  the table since before this app and was referenced by nothing: no entity, no DTO, no form. Rather
  than add a second column meaning the same thing, it is now mapped and exposed on the task form,
  narrowed from `float(9,3)` to `INT`, and read as **minutes**. The unit is the point: a sprint is
  built from tasks small enough to estimate at 10, 20, 30, 45, 60 minutes, and hours push you toward
  "about half a day" — the granularity a sprint exists to break down. An integer says the same thing
  in the type.

  Its 15 legacy values were **cleared**, not converted. They predate anything reading the column and
  are not in one unit — some were entered as minutes, some as hours, and nothing on the row says
  which — so any single multiplier would be wrong for part of the set. A wrong estimate is worse
  than an absent one: absent is visible (the task is counted at the default and named in the
  warning) while a wrong number is silently believed. Ten of the fifteen were on completed tasks.

  A task without an estimate is counted at **30 minutes** (configurable via
  `sprint.default-task-minutes`) and **named** in the sprint's warning — not merely counted, so the
  warning can say which tasks to go and estimate. The sprint also reports its window, so a plan
  that doesn't fit the time booked for it says so.

  Estimates are **snapshotted at commit time**: re-estimating a task later cannot rewrite what a
  past sprint was planned to be.

  Ticking a task in the review also completes the task itself and clears its bookmark, so a
  finished task doesn't offer itself for the next sprint. Un-ticking undoes both — a review
  submitted too early should be correctable, which is also why closing can be re-opened.

  On the **calendar** a sprint is a block from start to end, titled with its done/total count, in
  its own colour with a doubled border (planners are dashed, journals solid). Dragging its edges
  moves the window like any other event; clicking it opens the Sprints page rather than a `/read`
  view, because a sprint is a board you work rather than a record you read.

### Fixed

- **Imported notes arrive typed.** A knote's filename type code picks the module (`J` → journals),
  and a journal has always taken its **start date from the filename timestamp** rather than the
  moment of import — that part was already right, and there are now tests holding it there. What was
  missing is the type: records created through a form go via `AbstractCrudService.create()`, which
  fills blank parameters from the admin-configured defaults, but the importer persists straight
  through the `EntityManager` and skipped that entirely. A `J` knote became a Journal with **no type
  at all**, invisible to every type filter. 1,444 of the 3,217 journals in the database show the
  damage.

  One call fixes it for every module at once — planner type, work status and the rest now follow the
  same defaults a form-created record does. Six tests; the decisive one configures the default
  itself rather than reading whatever the database happens to have, because an earlier version
  *skipped* when none was set and a skipped test proves nothing.

  Existing untyped records are left alone: back-filling 1,444 rows is a bulk edit to real data, not
  a migration.

### Changed

- **Bookmarking no longer touches the filesystem unless you ask it to.** Flagging a record created a
  symlink in the checkout folder and wrote-and-**opened** its checkout text — the opening because
  the export inherited `export.open`, a setting that defaults to on and exists for the manual
  "Export to file" button. So clicking a star could pop an editor window.

  Both are now switches under **Settings → On bookmarking**, and **both are off by default**.
  Bookmarking is a note to self; on its own it has no business writing to disk or launching a
  desktop app. The export is passed an explicit open/don't rather than `null`, so it can no longer
  inherit a setting meant for something else.

  **Removal stays ungated on purpose**: un-bookmarking always removes the symlink, whatever the
  switch says. Otherwise turning the feature off would strand every link already on disk.

  Two of the five new tests fail against the old unconditional behaviour — the two that assert the
  defaults, which is the part that matters.

### Mobile

- **The Audiobooks page no longer rebuilds itself on every visit.** Three separate costs were being
  paid each time the page opened: the scan walked hidden folders, read tags from every file, and
  then threw all of it away when the composition ended. On this phone the hidden half is not a
  rounding error — **1,195 visible files against 780 hidden**, mostly Syncthing's `.stversions`
  archive of previous versions of everything.

  The scan now lives in a process-level `MediaLibrary` instead of a `produceState` inside the
  composable, and hidden directories are pruned at the *descent* rather than filtered afterwards —
  a filter runs after the walker has already gone in, which is precisely the cost being avoided.
  Tags are cached to disk against each file's path, size and mtime, so a refresh re-reads only files
  that actually changed and an unchanged library costs one directory walk and no file reads at all.
  Since it no longer rescans by itself, there is a refresh button in the top bar.

- **Folder view: dead ends are hidden, and the first few tracks play from the list.** A folder with
  no media anywhere beneath it is no longer shown — it was a tap in and a tap back to discover
  nothing. Up to five of a folder's own tracks are listed under it and start playing when tapped,
  with "…and N more" opening the folder. The emptiness check short-circuits at the first media file
  found rather than counting them.

- **Only `.md` and `.txt` are read as note text.** Any other file dropped in the notes folder had
  its contents read and shown as a note body, so a stray `.xml` appeared as a note full of raw
  markup. Other files are still listed as "needs metadata" items — they just aren't previewed. A
  file being decodable as text doesn't make it something you wrote.

- **A file with no preview shows its name** rather than an empty card. Done in `preview()` rather
  than by faking the note's content, so `content` still honestly means "what we read", search and
  hashtag parsing aren't polluted with filenames, and folders get the same treatment.

- **Background sync moved from a foreground service to WorkManager**, so it no longer requires a
  permanent status-bar notification. Android does not allow a foreground service's notification to
  be hidden, and the service existed only to hold a polling loop — reminders and chimes are
  AlarmManager alarms with manifest-registered receivers and never depended on it. The service now
  runs *only* when the status notification is switched on, and the two settings are finally
  independent.

  **The trade:** WorkManager's minimum period is 15 minutes and it is a floor rather than a
  schedule — the OS batches work and may run it later. A shorter configured interval still applies
  while the app is open. Sync failures return `retry()` rather than `success()` so an unreachable
  server is retried on backoff instead of dropped until the next period.

- **Arabic file and folder names right-align.** Track names, folder names, breadcrumbs, library
  group names and bookmark rows now use `TextDirection.Content` with `TextAlign.Start`, which
  resolves direction from the text's first strong character. Preferred to the `if (isRtl)
  TextAlign.Right` test used elsewhere because the alignment cannot disagree with the direction the
  text is actually laid out in.

- **Widget titles refresh, Top priority carries a count, and the volume bars colour by headroom** —
  see the 2026-08-11 entries; shipped in the same build.

## 2026-08-11

### Added

- **Resources search now looks in Notes as well as Title, Author and Description.** On a resource,
  Notes is where your own words live — the description is usually the publisher's blurb, scraped or
  pasted — so searching without it finds books by how they were advertised rather than by what you
  said about them. Author was already searched and still is; there are now tests pinning each
  searched field individually, each with its term in exactly one field so a hit proves that field is
  read.

- **A "Read on" date on resources.** The `read_on` column has been on the table all along and was
  simply never exposed — not in the DTO, so not in the API, so not on the form. It round-trips now.

- **The sidebar's tool buttons show their names when the rail has room for them.** Calendar,
  Kanban, Dashboards and the rest sat under the enabled modules as a wrapped strip of bare icons
  whatever the window size, so a tall screen with few saved searches left a band of empty rail above
  a row of unlabelled glyphs. They now draw as labelled rows whenever the space is there, and fall
  back to icons when it isn't.

  "Has room" is measured, not guessed, and the measurement has one trap in it: ask "does the reports
  region have slack for the extra height?" and the answer changes the moment you act on it — labels
  appear, the flex region shrinks by exactly that much, the slack is gone, the labels come off,
  forever. So what is measured is the space the reports region and the tool block occupy
  **together**, which is the same either way. A ResizeObserver re-decides on a window resize, a rail
  drag, the calendar opening to a full month, or a saved search being added — so widening anything
  brings the names back.

  The Users and Roles buttons, previously hand-written after the loop, are now ordinary entries in
  the same list, so a tool can no longer appear in one mode and not the other.

- **Settings → Features → "Release codename"** replaces the "Version in the status bar" toggle
  added earlier today. The version number is always shown now — it is what you quote when something
  is wrong — and the switch governs only the nickname beside it, in **both** places it appears: the
  sidebar header and the status bar. (The stored key changed from `feature.statusVersion.enabled`
  to `feature.codename.enabled`; anyone who had switched the old one off gets the default, on.)

- **Settings → Features → "Resource detail fields"**, covering Full text, Highlights, Comments, URL
  and Read-on. Between them they make the resource form several screens long for anyone who mostly
  fills in title and author; one switch collapses that.

  **Hiding a field never empties it.** The form is still seeded from the whole record and the save
  payload is still built from the *full* field list, so a hidden field is written back exactly as it
  was loaded. Building the payload from the visible fields instead would omit those keys, and the
  server's `apply()` writes every DTO field — a missing key arrives as null, so switching off "Full
  text" would have blanked it on the next save of every resource that had one. The form, the detail
  pane and the standalone reading page all honour the same switch, so a hidden field doesn't
  reappear on the way out.

- **Mobile: every widget title is now a "bring this up to date" button, and Top priority counts.**
  A home-screen widget is only ever as fresh as the last thing that redrew it, and nothing redraws
  it on its own. Plans and KPIs already ran a sync on a title tap — but nothing refreshed the
  widgets afterwards, so the numbers on screen stayed stale even when the sync worked. Top priority
  and Bookmarked records had no title tap at all.

  All four titles now redraw, and they redraw **before** consulting the network as well as after: a
  tap that goes quiet for a network round-trip reads as a dead control, and the local half of what
  these widgets show is already current at the moment of the tap. A failed sync therefore costs the
  server half, not the whole gesture. Top priority skips the network entirely — those notes are
  files on the phone, so the server cannot have a different answer.

  "Top priority" now carries its count, like "Bookmarked records" does. It is the true total, so a
  widget too short to show every note still says how many there are, and it is counted from the
  file names rather than by loading notes — the same redraw runs on the system's own update path,
  on the main thread, and loading notes reads every file's contents.

- **Mobile: the home list opens on today.** The phone is where notes get captured rather than
  browsed, so the useful default is "what I've put down today" rather than the whole folder. It is
  an ordinary day selection — today is highlighted on the two-week strip, the filter badge counts
  it, and Clear drops it like any other filter. Applied once at startup, so a rotation or a return
  from the background restores whatever you last chose instead of re-imposing it.

- **A resource's cover is imported with it.** Building a resource from a URL now also takes the
  page's main image and saves it as the record's cover (`cover.<ext>` in repository 1), so a
  scraped article arrives looking like the ones entered by hand.

  The picture is chosen from the social-preview metadata first — `og:image` is the publisher's own
  answer to "which image represents this page", which is exactly what a cover asks. Only when none
  of that is present is the article body scanned, and that scan steps over the sprites, avatars,
  logos and tracking pixels that outnumber real illustrations in most markup, reads `data-src` and
  `srcset` as well as `src` (a src-less `<img>` is normal on any site with a lazy loader), and
  skips anything declaring itself under 200px. "No cover image found on the page" joins the other
  warnings when it comes up empty.

  **The download happens after the record is saved, not as part of saving it.** There is nowhere to
  put a file until the record has an id, and a picture that won't download is no reason to lose a
  resource that saved perfectly well — so it reports rather than fails. It only ever fills an empty
  cover slot; replacing an existing one stays a manual upload, since this picture is a guess made
  from someone else's markup, not a considered choice.

  The fetch carries the same SSRF guard and the same hop-by-hop redirect re-validation as the page
  scrape, because it is the same exposure with a picture at the end of it. **SVG is refused** even
  though browsers call it an image: covers are served back inline from the app's own origin and an
  SVG can carry script, which would make an imported cover an XSS vector. Rasters are capped at
  8 MB, and a file landing exactly on the cap is treated as truncated rather than written half-
  decoded — jsoup cuts the body off at the limit instead of failing.

### Fixed

- **Opening a record no longer swallows every keyboard shortcut.** The "Append to Description" bar
  is drawn inline on every record you open, and it carried `autoFocus` — so selecting a record put
  the caret in that field immediately. The shell's key handler stands down whenever an input has
  focus, by design, which meant `j`/`k` to move through the list, `e` to edit, `o` to open, `p`/`#`
  to quick-edit and `f` then `1`/`2`/`3` for the repository folders were all dead from the moment a
  record appeared. Only Escape did anything, because it blurs.

  The `autoFocus` is gone. A field you have to click into costs one click; a field that takes focus
  by itself costs every shortcut on the page. The subtask dialog keeps its own autofocus — a dialog
  you opened on purpose should be ready to type into, and it captures the keyboard only while open.

- **Mobile: the ringtone bar sat orange at a level nobody would call high.** The Volumes widget
  coloured each bar by its percentage of that stream's maximum — but Android does not give every
  stream the same number of steps. A phone typically gives the ring stream 7 and music 15, so
  identical positions were being judged on different yardsticks: one notch above the middle is
  5/7 = 71% on the ring, past the old "orange above 60%" line, while the equivalent 9/15 = 60% on
  music stayed green. A 7-step scale has no step between 57% and 71%, so it skipped the green
  band's top half entirely and the ring bar was orange almost all the time.

  Colour now goes by **headroom left** — green normally, orange within a fifth of the top, red at
  maximum — which is scale-independent: "one notch below maximum" is near the top whether the scale
  has 7 steps or 15. Tests pin the cross-stream equivalence directly, and 4 of the 8 fail against
  the old rule.

- **Roles & permissions: a checkbox that appeared to refuse the click.** Granting Data entry the
  audit capability — or any second capability to any role — silently failed. The save 500'd, the
  matrix came back unchanged, and since each box is drawn from the server's matrix the tick simply
  never appeared.

  Writing a role's capabilities cleared its rows and re-inserted the whole set. **Hibernate orders
  INSERTs before DELETEs within a flush**, so re-inserting a key the role was keeping hit
  `uk_role_permission` before the delete that was meant to clear the way had run —
  `Duplicate entry 'DATA_ENTRY-content.write'`. Granting the *first* capability to a role with none
  worked, because with nothing kept there was nothing to collide with, which is what made the
  failure look arbitrary.

  Now applied as a delta: rows the role keeps are left untouched, and only genuine additions and
  removals are written. Seven tests cover it, and the decisive ones are the cases that *keep*
  something — the old "grant the first capability" case passed throughout and would not have
  caught this.

  The frontend had no error handler on that mutation at all, so the failure had no way to reach
  the screen. It now shows what went wrong instead of leaving a box that looks stuck.

- **A record's History panel is hidden from roles that may not read it**, rather than shown empty.
  The panel reads `/api/audit/{entity}/{id}`, which is gated on `tools.audit`, and a 403 was
  indistinguishable from an empty result — so a role without the capability was told the record had
  no recorded changes. Telling someone "no changes" when they simply aren't permitted to know is
  worse than telling them nothing. Gated inside the component rather than at its call site, so the
  request is never made either: an ordinary record view was firing a 403 on every open for most of
  the roles in the matrix.

- **A disabled Journals module no longer gets written to by the mini calendar.** Clicking a day in
  the rail calendar called *get-or-create the day's journal* unconditionally, so a module that had
  been switched off in Settings still grew a record every time a date was picked — the one record
  in the app created by navigating rather than by asking, arriving through a side door in a module
  the owner had turned off.

  With journals enabled nothing changes: a day **is** a journal entry, and clicking one opens it,
  creating it first if needed. With journals disabled the click now **creates nothing** and instead
  shows what the enabled modules already hold for that date, as record cards in the middle pane.
  Each module answers for its own idea of "that day" — journals and planners by their own date
  field, everything else by when the record was created — which is what lets one date mean
  something across modules that don't share a calendar. A day with nothing on it says so rather
  than opening an empty pane.

  The enabled-module list is read through `fetchQuery`, not the `useModules` hook: that hook
  reports "everything enabled" while its request is in flight, and this decision leads to a write.
  A wrong default for a few hundred milliseconds is exactly how the original bug would come back.

  The mini calendar's bold "this day has a journal entry" marker goes with it — with journals off
  it is a promise the click no longer keeps — and it stops querying the journals module at all.

- **Desktop: the tag cloud was unfindable.** It was rendering correctly all along — six chips,
  laid out, mapped, clickable — at y=964 inside a 614px-tall viewport. A full screen-height of
  scrolling below Calendar (329px on its own), Priority, Type, Category and Non-standard, with
  nothing above the fold to suggest it was there.

  Seven expanded sections come to roughly twice a laptop sidebar's height, so no ordering of them
  all fits; something has to be fifth. **Tags now leads the sidebar, above the calendar** — it is
  the one dimension you go *looking* for rather than pick from a list already on screen, and the
  most compact of them (a wrapped row of chips against the calendar's month grid). The two
  clean-up queues, Non-standard and Bad taxonomy, moved to the bottom where they belong: they
  answer "what needs fixing", not "what am I looking for".

  It costs nothing when unused — a folder whose notes carry no hashtags hides the section
  outright, header and all, rather than parking an empty box in the best slot.

- **Desktop: a collapsed Tags section reopened itself.** `_sync_tag_cloud` set the cloud's
  visibility from the tag count alone, ignoring whether the user had collapsed the section — and
  it runs on every refresh, which includes every keystroke in the search box. Collapsing Tags and
  then typing anything popped it straight back open.

### Changed

- **"New resource from a URL" is now a link button beside New**, not a URL box permanently above
  the search field, where it read as though it were part of the search. Clicking it opens the box;
  Escape or the × closes it.

- **Two more Settings → Features toggles.** *Version in the status bar* hides the
  `Version: v1.2.32 "Dastgheib"` cell (the update check beside it stays, so turning it off doesn't
  cost you the "a newer release exists" warning), and *New resource from a URL* hides the link
  button above. Both default to on, like every other feature toggle.

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
