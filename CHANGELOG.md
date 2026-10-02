# Changelog

## 2026-10-02 — The tag vocabulary moves into the course records

### Changed — a note's subject resolves through the database, not a file

The mapping from a note's `#A.salat` to course 9208 lived in a markdown file inside the notes
folder. That made the subject view depend on that folder being synced to whichever machine runs the
backend — and it was not: the bank arrived on this machine hours after the notes did, and until it
did the view degraded to bare department letters with no course names.

The courses were already in the database. Their tags belong there too. `course.tags` (V11) holds the
vocabulary in the bank's own format — canonical tag first, then aliases, comma-separated:
`A.salat, salat, صلاة, تعقيبات`. Keeping the file's convention means the file stays a readable
source for the column rather than a competing truth.

Course records now win; the file is read only when no course carries tags, so an installation that
has not loaded the vocabulary yet keeps working instead of silently losing its course names. With
neither, the view still groups by department letter — it degrades, it never breaks.

`dist/sql/course-tags-2026-10-02.sql` loads all 242 courses, generated from section 2 of the bank.
Every statement is an UPDATE keyed on `course.id`, so it is safe to re-run; it never inserts or
deletes a course, and it deliberately does NOT clear tags from courses the bank omits — a course
tagged by hand would otherwise be wiped by the next regeneration. All 242 ids matched real rows in
both the production and the test database before it was written.

The field is editable on the Courses page and searchable, so the vocabulary is now maintained where
the courses are. That also answers the 17 tags the archive uses which the bank's section 2 never
adopted (`F.psychapp`, `I.pkm`, `A.psychrel` and friends): give the course the tag.

### Added — Settings → Local notes

The notes folder is a setting with a UI, defaulting to `$HOME/new/knotes`. Blank CLEARS it back to
the default rather than being ignored — without that there is no way back to the default except
knowing what it was and retyping it. A blank stored value also falls through to the default instead
of resolving to `Paths.get("")`, which is the server's working directory and would have produced a
silent "no notes found" against whatever folder it started in.

Both paths are shown with the evidence that they resolved — the note count and the course count. A
path field that only echoes what you typed is the least useful kind: the mistakes here are a folder
that does not exist and a vocabulary nobody loaded, and both are invisible until something counts
what it found.

9 new tests (246). Two older ones were rewritten after they corrupted real data: they wrote to a
course to prove the new precedence, two non-transactional tests then raced the same detached row,
the optimistic lock fired, and the restore never ran. They assert now, and mutate nothing.

## 2026-09-29b — The notes folder, in the browser

### Added — a local-notes page, and one search over both halves of the archive

The archive has always been two things: records in the database, and a folder of markdown files the
desktop app writes. Only one of them was reachable from a browser. The backend now reads the folder
off its own disk — it sits on the same machine — so the notes are listed, searched, read and written
over HTTP without the desktop app being up.

**Two axes, because the folder genuinely has two.** By DAY is how it was written; by SUBJECT is how
it is looked for. Both come down in one response, so the halves cannot disagree after a write.

The subject axis is the part that needs machinery. A note carries only a slug — `#A.salat` — and it
takes the tag bank to know that means course 9208 inside department A. The bank is parsed from the
markdown document the user maintains, whose section 2 is a table of `id · course · canonical ·
aliases` under department headings. Only section 2 is read: sections 4 and 6 list tags that are
PROPOSED or RETIRED, and offering those as real vocabulary is exactly the drift the bank exists to
stop. Against the real bank this resolves 242 courses across 13 departments, aliases in both
scripts, and all 242 canonicals distinct — the partition the design depends on.

A tag the bank does not carry still lands under its department letter in a course-less bucket, and a
note with no tags and no letter lands in one too. Hiding either would hide precisely the notes whose
filing is unfinished.

**Nothing is indexed.** The folder is a few hundred files and a full scan costs milliseconds; an
index would buy speed in exchange for the one failure this cannot afford — a listing that disagrees
with the folder after the desktop app, the phone or Syncthing has written to it. A fingerprint of
the directory is cached instead, so repeated reads touch no files while any change forces a rescan.

### Added — writing notes over HTTP

An add form, and editing in place. The department letter is deliberately NOT a field: the archive's
invariant is that the letter in the file name equals the department of the note's FIRST tag, so the
form derives it from the text and says what it derived. Letting the form win would let the name and
the tags contradict each other, and the name is the half that would be wrong. A note written with no
tag gets no letter — which honestly reads as "not filed" instead of parking it in `X` on nobody's
judgement. Two notes written in the same second both survive, via the desktop app's `-N` suffix.

File names arriving from a browser are checked against the note grammar and resolved inside the
folder, so `../../etc/passwd` dies at the service rather than at the filesystem.

### Added — consolidated search

`/api/search/all` answers one query with both: records from the index, note files from the folder.
Two lists rather than one merged list, deliberately — a record has an id, a module and a status, a
file has a name and a folder, and flattening them would mean inventing an id for a file and a score
for a substring match. A `~` duplicate query stays records-only, since a note file is not something
a record can be merged into. An unreadable folder returns the record half plus a reason, not a 500.

46 new tests (237). The one that matters most is `writingANoteFilesItFromItsFirstTag` — checked by
breaking the rule and watching it fail.

### Fixed — two things only a rendered page showed

Found by running the build against a real folder in a browser, not by reading the code.

**The department heading said "A · A الدين".** A department's name in this database already begins
with its own letter, because `Department.getName()` returns the `summary` column and that column is
written `A الدين`. The letter is now prefixed only when the name does not already carry it, so the
heading is right whichever convention a database uses.

**The no-department bucket was headed `?`.** That is the service's sentinel for a note with no
department at all — the state a capture starts in — and it now reads "Not filed".

Also corrected the TypeScript for the new endpoints: the API runs
`default-property-inclusion: non_null`, so an absent field is OMITTED rather than sent as null. The
types said `| null`, which would have made `=== null` look safe when it never matches.

## 2026-09-29 — Contacts is a module you can switch off

### Added — the Contacts toggle in Settings → Modules

Contacts now behaves like every other module when it comes to being turned off, which it did not
before. Two halves were missing, and only one of them was where you would look.

The visible half comes free with migration V10: Settings → Modules lists whatever rows are in
`app_module`, so registering Contacts there gives it a switch alongside Tasks and Journal, and the
left rail hides it the moment the switch is off. Nothing in the frontend needed a Contacts case —
that list has always been data-driven. V10 ships in this WAR; a database that has not run it yet has
no Contacts row, and so no toggle, which is exactly what was being seen.

The half that did need code: contact tagging lives on OTHER modules' pages. The "People" picker on a
task's form and the quick-tag row on its details pane are contacts UI mounted on tasks, so switching
Contacts off would have left the feature half-on — a page gone from the rail, its tagging still on
every task. Both now check the module first. A module the backend does not list at all counts as
off, so this stays hidden on older databases instead of appearing half-wired.

Hidden fields are still WRITTEN BACK unchanged, deliberately: the form seeds from the whole record
and the payload is built from every field, not the visible ones. Turning Contacts off therefore hides
the tagging without silently stripping the tags already on a task — turn it back on and the people
are still there.

Not covered: the phone's rail entry is hardcoded, as all of its pages are, so the toggle is a
web-side switch only.

## 2026-09-27b — A notes feed, a rail refresh, and contacts in batch edit

### Added — a read-only feed of recent notes (mobile widget)

"Recent notes": the last few days of writing as one column you scroll down, the way a channel is
read. Oldest first so the newest is at the BOTTOM — and the widget opens scrolled there, since that
is where reading resumes. Minimal metadata in small type, one scrollbar, no filters and no buttons.

Deliberately not another list widget. The others are working lists that filter, count and act; this
one is for reading what you have written, so everything that would invite interaction was left out.
The single tap a row accepts unfolds a long note in place and tapping again folds it back — that is
the only interaction, which is why the rows carry no other controls.

Notes longer than a configurable word count (default 40, over a configurable 3 days) are folded
behind "Read more". Folded by WORDS, not by lines: a line depends on the widget's width and the
phone's font scale, so a line-based cap would put "Read more" on different notes for the same feed
at two sizes. The unfolded set lives in prefs rather than memory, because the rows are built by a
RemoteViewsFactory in a service the system is free to tear down between taps.

5 new unit tests (277), including the off-by-one at the cap — a "Read more" that reveals nothing is
the worst kind, since tapping it appears to do nothing at all.

### Fixed — "Unknown module: contacts" when batch editing

`BatchEditService` works off a hardcoded map of seven modules, so registering a CRUD module does not
enrol it in bulk edits. That is defensible on its own, but it made `*-` over a dozen selected people
fail for a reason nothing on screen could explain. Contacts added.

Which tokens apply is still decided per entity — `CommandParser.parse` gets the class and only emits
assignments for properties it has, so `*-` and `p3` work on a contact while `?status`, a column
contacts do not carry, is rejected rather than failing at the write.

### Added — the Contacts page works offline

The same shape as the flash cards and highlights: the saved copy is shown before the network is
tried, and the header says how old it is. Worth more than it looks — the page went blank with
"Failed to connect" simply because it was opened while the backend was restarting for a deploy, and
"what do I owe this person" does not stop being a useful question mid-deploy.

### Added — one refresh for the server pages, in the rail

The record browsers have always had their own refresh; flash cards, highlights and contacts had
none, so the only way to pull new rows was to open a records page, refresh THAT, and come back. The
server band now ends with a refresh button above Settings, and all three pages watch one signal.

A counter rather than a flag, so two refreshes in a row still re-run and no screen has to reset it
on behalf of the others.

### Changed — the rail's two bands are told apart by weight, not only hue

Tertiary and primary at the same faint alpha read as one shade in most themes, which is the opposite
of the point. The local band stays light; the server band is noticeably deeper.

## 2026-09-27 — Contacts, and typed text that cannot be lost

### Added — a Contacts page: bookmarked people and the work open with them

Tasks can now be tagged by CONTACT as well as by tag, and the phone has a page that reads it back:
one heading per bookmarked person, their live tasks underneath.

Nothing was migrated for this — the legacy schema had it all along. `task_contact` was already
there, and note its shape: it carries TWO possible owning columns, `task_people_id` and
`task_contacts_id`, because the old Grails app had two associations sharing one table. Only the
latter is populated, so that is the one mapped; joining on the other reads an empty relation and
looks exactly like "no task has a contact".

**Active** means not done, cancelled, deferred or frozen — and a task with NO status is active,
since unset is not the same as finished (most tasks here have never been given one). Matched on the
status CODE, not its display name, because the names are editable in Parameters and renaming
"Cancelled" would otherwise quietly change what the endpoint returns.

A contact with nothing open still gets a heading. "Is there anything outstanding with them?" is a
question the page should answer, and dropping the empty ones makes it silently incomplete.

The write side matters as much as the read side here: the join held **two rows in total**, both on
people who are not bookmarked, so without a way to tag tasks the page would stay empty for ever.
`TaskDto` now takes `contactIds` and returns `contacts`, mirroring `tagIds`/`tags` exactly.

### Added — Contacts as a full module, and a "People" picker on tasks

The page above selects on `bookmarked`, and nothing in any client could set that flag: the sixteen
bookmarked people were bookmarked in the legacy system years ago and the other 171 were unreachable.
So contacts are now a module like any other — `ContactService`/`ContactController` over the shared
CRUD base, giving list, detail, create, edit, delete, search and audit rather than a bespoke screen.

Three things were each individually enough to leave it invisible, so all three are done: the
`entities.ts` entry, `Groups` in the nav's icon map (absent, it falls back to a generic flag), and
**`V10__contacts_module.sql`**, which adds the `app_module` row — that table drives the rail, so a
module declared only in the frontend never appears. The migration adds a row and nothing else; the
legacy `contact` table is untouched. Idempotent, and verified as such on the test clone.

Tasks gain a **People** field: a new `contacts` field type mirroring `tags` at each of the three
points that type touches — the multi-select renderer, the seed from `record.contactIds`, and the
`contactIds` payload. Its options come from the task module's own `lookups()`, the same path tags
use, so there is no extra request and no second source of truth about who exists. Bookmarked people
sort first: 187 contacts, a handful live at a time, and the ones being worked with should be
reachable without typing.

No status, type, department or course on a contact — the legacy table has no such columns, and
offering empty dropdowns is worse than offering none. `summary` is NOT NULL there, so a contact
created with only a name has it filled from the name rather than failing at the insert.

8 new backend tests.

### Fixed — typed text in the widget capture bars could be lost

The bar floats over a home screen, so a thumb that misses the one-line field lands on wallpaper —
and that discarded whatever had been written. Two defences, because the stray tap is only one of the
ways out:

1. A tap on the scrim now closes the bar only while it is EMPTY. With something typed it is refused
   and says so. Refused rather than "dismiss but keep the draft": the text is what you are looking
   at, and having it vanish mid-sentence is alarming even when it is recoverable.
2. Every keystroke is written to `QuickDraft` and read back when the bar reopens, covering the exits
   that cannot be intercepted — Back, Home, and the system finishing an activity declared
   `noHistory`. Only a note reaching disk clears it.

The restored draft puts the caret at its END; a cursor left at position 0 means the next thing typed
lands in front of what was written, which reads as the app having mangled it.

### Changed — prayer strip wording, and where prayer options live

The countdown said "بعد" (in) even after a time had passed, because a Chronometer in countdown mode
does not stop at zero — it counts into negatives, so the strip read "شروق بعد -0:12:34". It now says
"منذ" (since) once the time has gone and counts up, and the widget is woken AT each shown time so
the flip actually happens. Shurooq needed that particularly: it has no athan to redraw the strip.
The countdown is a step larger at 16sp.

The prayer feature switch moved from the global Settings screen onto the prayer page, so everything
about prayer times is configured in one place. It is a one-way door from there — the page you are
standing on is the page that disappears — so Settings keeps a single "hidden · show" row while it is
off. Without that the switch could not be undone.

## 2026-09-13 — Subtitle-labelled bookmarks, and a tighter player

### Added — a bookmark takes the spoken sentence as its label

With an `.srt` beside the file, an unlabelled bookmark is now named with the line being spoken. A
bare timestamp says nothing when you come back to a list of them; the sentence is the only thing
that records why you marked it, and the subtitle file has already done the typing. A note you typed
still wins — that is a deliberate act, and the case where the sentence is not what you meant to keep.

The cue is read at the BOOKMARKED position, not at the playhead. The mark is placed a few seconds
back precisely because that is the line you just heard; labelling it with what is being said now
would name a different sentence from the one it points at.

On mobile this turned out to need two places, not one. The player screen's button was the obvious
one; the other is `PlaybackService`, which bookmarks automatically on pause and wrote the literal
word "auto" — and that is where it helps most, since nothing was typed and a list of them reads
"auto, auto, auto". The label logic lives in `Subtitles` so both paths share it. **Desktop gets the
same**, in `subtitles.label_at`.

### Fixed — the bookmark button kept bumping priority instead of adding a mark

A quick second tap is meant to raise the previous bookmark's priority (p2, p3, …). The only
condition was "was the tap quick?", so two taps a few seconds apart at genuinely different points in
the audio were both quick — and the second silently re-labelled the first instead of marking the new
spot. The bookmark you wanted was never recorded.

The playhead must now still be AT the previous mark as well. The tolerance is that same window
measured on the audio rather than on the clock, scaled by playback speed, since a second of
listening at 1.25x advances 1.25s — without the scaling, double-tapping would have quietly stopped
working for anyone who speeds playback up.

### Changed — two rows of the desktop player given back to the waveform

Subtitles and transcripts now share ONE row: they are the same kind of thing, the sidecar text
belonging to this file, and on separate rows they cost two lines of height for a handful of chips on
every file that had both. They keep independent visibility, so the shared row is shown when either
half has content — an empty parent box draws as a blank line, which is the height this was meant to
recover.

The file-name label under the title entry is gone and the trash button moved to the entry's right.
For these recordings the name and the title tag are the same string, so the label was repeating what
the field above it already showed. The name is still reachable as the entry's tooltip, for the files
where the two genuinely differ.

8 new mobile unit tests (272).

## 2026-09-06 — One card per note, and galleries that work offline

### Changed — the flash-card bank is one canonical card per note

22,385 cards came from **1,576 notes** — an average of 14 each, and 257 for the worst of them. (Not
13,000 notes: that is the whole notes module. Only 1,618 notes have a 200-word description, which is
what the extract required, and 1,576 of those produced passages.) Reviewing fourteen slices of the
same note is not a task anyone finishes.

The bank is now **1,576 cards, one per note** — the passage that best carries the note's point.
20,809 were retired.

The pick is scored on four signals, in the order they matter: summing-up phrases (`بمعنى`, `الخلاصة`,
`in short`, `the point is` — the strongest evidence a sentence is the conclusion and not a step),
then topical weight on the SAME keyword lists the bank was favoured by, then fullness (a passage near
the 100-word cap is a complete thought; a 35-word stub is what was left at the end of a note), then
position as a tie-break only. `tools/gather_flashcards.py` was given a `__main__` guard so its
scoring is imported rather than copied — two copies of a tuned heuristic is two things to retune.

**Nothing is hard-deleted**, and **decided cards are never touched**. Retired cards get `deleted_on`,
which every query already filters on, so the whole reduction is one UPDATE away from being undone —
the SQL says how in its own header.

Two files in `dist/`, for two different situations, and they are not interchangeable:

- **`flashcards-canonical.sql`** — reduces a database that ALREADY has the full bank. It asks that
  database which notes you have decided about and keeps those cards, so it is correct on the
  production box without the generating script ever seeing it. Portable because `card_key` is the
  passage's content hash: the same passage has the same key everywhere.
- **`nibras-flashcards-canonical-1576.sql.gz`** — the reduced bank as a plain load, for a database
  with no bank yet. `INSERT IGNORE` on the unique `card_key`, so re-running adds nothing and cannot
  reset a reviewed card. Verified by loading into a scratch database: 1,576 rows, 1,576 notes, every
  passage byte-identical to the source, and a re-run after marking a card KEPT left it KEPT.

### Added — both galleries work offline

They fetched on open with nothing to fall back on, so no server meant an error and an empty page.
Both now read a **cache first, before the network is tried at all**, and treat the fetch as a refresh.

`FlashCache` stores the server's **raw response verbatim** rather than a re-serialisation, so the
offline copy is parsed by exactly the code that parses a live one — a cache with its own format has
a second parser to keep in step, and the day they disagree is the day nobody notices.

Flash-card decisions work offline too. `FlashOutbox` holds them, keyed by card id so re-deciding
replaces rather than stacks, and `PushCenter` drains it alongside the record and calendar outboxes —
so a decision goes out when connectivity returns, not only if the page happens to be reopened. The
card is written to disk before the network is touched: a decision is the reader's, and the only open
question is when it arrives. The old "that decision did not reach the server — it has been put back"
is gone; the header says `saved copy, 2h ago · 3 decisions to sync` instead.

The one case that still shows an error is a cold start with no cache and no server, which genuinely
has nothing to show.

### Changed — the mobile rail is two bands, one open at a time

The rail had become one undifferentiated column, and the difference that actually matters when you
open it is whether the page will work right now. It is now two faintly tinted bands under icon
headings: **on this phone** (prayer times, notes, image gallery, media player, file collector) and
**from the server** (calendar, the record letters, flash cards, highlights, server search).

The headings are icons, not words: the rail is 56dp wide and any legible label would wrap or be cut.
The words are there for a screen reader as the icon's `contentDescription` — which is also what makes
the bands testable from the accessibility tree rather than only by eye. The tints are at 7% alpha, so
they group without competing with the selected cell's own fill.

Every destination but Settings lives in one of the two bands, and **one band is open at a time**:
opening either closes the other. The rail carries eighteen destinations and cannot show them all on a
phone screen, so whichever half you are working in is fully visible without scrolling and the other
is one tap away. It replaces the old "more" group, which folded four pages behind a neutral icon for
the same reason and could only ever hide four.

The band holding the current page opens itself — a collapsed band whose child is the page you are on
would hide where you already are. Settings sits outside both and is never collapsed: a rail whose
configuration page can be shut out of reach is a trap.

- **Order**, local band: prayer times, notes, image gallery, media, file collector — the collector
  last, being a job you run rather than a place you read. The file collector moved here from the
  bottom of the rail: gathering files into the notes folder happens entirely on the phone.
- **Server band**: search server moved to the END. It searches everything on the server, where every
  cell above it browses what sync already pulled down, so closing the section with the
  widest-reaching thing reads as "and if it is not up there, look here".
- The home icon is a **note**, not a house. Inside a band already labelled "on this phone", a house
  said nothing about what the page holds.
- Both heading glyphs are one size in both states, with more padding below than above — the gap
  under the heading is what separates it from the first destination, and evenly padded the icons
  read as another cell in the list rather than the label over it.

**Prayer times and file collection each have a switch in Settings**, and they govern the feature
rather than the icon: switching one off removes its page AND cancels what it had scheduled. Checked
inside `PrayerScheduler.sync` and `CollectScheduler.sync`, at the single point either arms an alarm,
so no caller can arm one around the switch. An athan calling from a prayer page you have turned off
is the worst possible version of that feature.

Two wiring gaps closed with it: the prayer effect in `MainActivity` was keyed on `Unit`, so it ran
once per composition and would have left a standing alarm armed until the next app restart; the
collect effect keyed only on the automatic-run settings. File collection now has two switches and
the feature one wins — `collectEnabled` says whether the collector is in the app at all,
`collectAutoEnabled` only whether it runs by itself.

### Fixed — "play next: none" auto-played the next file

`openPaused()` prepared the next item but never cleared `playWhenReady`. A `MediaController` carries
that flag from whatever it was last told, so after a file played to its end it was still true and
preparing the next item started it immediately — precisely what the setting exists to prevent. The
restore-on-launch path used the same function and looked fine, because there the flag happens to be
false already.

**And "none" now keeps the same file open**, rewound to its start and paused, instead of cueing the
next sibling. None should mean the player is where you left it, not one file further on.

### Added — deleting transcripts, and deleting a recording with them

An × on **every** language chip in the transcript panel, deleting that `.ai.txt` sidecar outright.
On every chip and not only the selected one: the common case is an English recording carrying a
machine-made Arabic transcript, and being rid of it should not require displaying it first. There is
no confirmation, by request — these are regenerable machine transcripts. The protection is spatial:
the label is padded well wider than a bare "ar" so the language tap target and the × are not two
millimetres apart, which is how the wrong file gets deleted. `Transcripts.delete` is guarded to the
`.ai.txt` suffix so it can never become a general file delete — the recording sits in the same folder
under a name one character different.

The player's own delete **moved out of the top bar to under the file's name**, beside the thing it
deletes, and now removes the `.ai.txt` sidecars along with the recording. One confirmation names them
all: a left-behind transcript is not just clutter, since the next file to take that name inherits it
and shows another recording's words.

3 new mobile unit tests (249).

### Added — selectable text and a copy button in both galleries (mobile)

A card's text is now selectable, and each card carries a copy button in its header — the passage on
a flash card, the bullets as a `- ` list on a highlights card, so what lands in a note is a list
again rather than a paragraph of run-together lines. The copy takes the text alone, without the
record reference or the date: what is wanted elsewhere is the words, and a header line would have to
be deleted at the other end every time.

Selection and the pager do compete — both are horizontal drags — so a plain swipe still turns the
page and a long-press hands the gesture to the text. Verified on the phone: long-pressing an Arabic
passage inside the pager selects a word and raises the system's Copy toolbar, and the copy button's
output pastes back correctly.

The Toast is for Android 12 and below only. Android 13 shows its own confirmation for every
clipboard write, and two notices for one action is worse than none.

### Added — thousand separators in the gallery counts

"22,385", not "22385". A five-digit run of bare digits has to be counted before it can be read, and
these numbers exist to be read at a glance. Pinned to `en-US` on both clients rather than the
device's locale, so the same bank does not group its counts one way on the phone and another in the
browser.

4 new mobile unit tests (246). They cover the failure most likely to bite the cache: the serialiser
writing `date`/`text` where the parser reads `writtenOn`/`passage`. Nothing throws in that case — the
card comes back with an empty passage, and only when offline.

## 2026-09-05i — 1.2.36 "Gratitude"

Version set to **1.2.36** across all six components (backend, frontend, mobile, desktop, terminal,
audio-player). The Android `versionCode` moves to 5 with it, per the convention in
`mobile/app/build.gradle.kts` — a released `versionName` that leaves the code behind cannot be
installed over by the build after it.

### Fixed — the highlights gallery was empty for everyone

"No resource has its highlights filled in yet", on a database with 41 resources whose highlights
were filled in. Two mistakes stacked, either of which alone would have emptied the page.

**It queried one module.** `mainHighlights` is mapped on Task, Planner, Journal, Goal, IndexCard and
Writing, and the frontend offers a Highlights box on all six. The controller read `BookRepository`
and nothing else, so every note and task with key points in it was invisible.

**And it asked resources for the wrong field.** Resources keep theirs in `highlights`, not
`mainHighlights`. The `book` table has both columns; only `highlights` is ever written — it is what
the resource form offers, and what the phone's own `mobilePushHighlight` appends to. So the single
module being queried was asked for the one field of its two that is always empty there.

Now every module is read, each under the name it actually uses, filtered in SQL and sorted newest
first across all seven. `?module=notes` narrows it to one. A record whose field holds only
punctuation splits to no bullets and stays out, because an empty card is worse than no card.

### Added — a record's name on the card, linking to the record

The highlights and flash-card galleries both name the record they came from; on the frontend that
name is now a link to `/read/<module>/<id>`, opened in a new tab so following it does not lose your
place in the deck.

The href is built from the servlet context (`<base href>`), not as a root-absolute `/read/…`: the
same WAR is deployed under several contexts, and a root-absolute link lands on the wrong app under
every one of them but `/`.

### Fixed — highlights rendered their Markdown source

A resource's highlights field is Markdown, and unrendered its bold lines read as
`**Governing principle:**` — on the largest module in the gallery. The frontend renders each bullet
through the existing `MarkdownView`; the phone styles them with the same `Markdown.spans` a note is
read through, markers faded in place rather than stripped.

### Added — hear the athan and the beeps without waiting for them

Preview controls in the Athan panel: **Hear it** beside the file chooser, a per-prayer **Hear:** row
under the volume sliders, and **Hear N** in the beeps section.

They call the same `soundAthan` / `soundBeeps` the alarms call, one level below the alarm itself —
no notification, no silencing, no scheduling. A ringtone picker's own preview plays one file once at
media volume; the real thing plays the picked sound N times on the alarm stream at the volume set
for that prayer, and it is exactly those parts that surprise you at 4am. A percentage cannot be
judged by eye, which is why the per-prayer row exists.

### Fixed — Stop did not stop the built-in beeps

Stop tore down the `MediaPlayer` and nothing else, so the generated tone — the default, and the case
where someone is most likely to reach for Stop — kept beeping. The tone generator is now tracked and
cancelled by run counter; releasing the generator alone would not have interrupted the thread's
sleep between tones.

### Changed — the prayer widget is Arabic, right-to-left, and readable across a room

The strip now reads as one: **فجر 04:59 · شروق 06:14 · ظهر 12:37 · مغرب 19:17**, right to left, with
**23 ربيع الأول** and the countdown on the line beneath.

- `layoutDirection` is pinned to `rtl` in the layout rather than left to the device locale — the
  text is Arabic whatever language the phone is set to, and the columns are added in order by the
  code, so rtl is what puts the first prayer of the day at the right where the eye starts.
- Names are the short Arabic forms (`Prayer.arabicShort` — فجر, not الفجر). Six of them share the
  width of a four-cell widget; the article is two characters that column cannot spare. Anywhere
  there is room for a sentence, `arabic` is still the right form.
- **Digits stay Western** (123, not ١٢٣). The clock formatter is pinned to `Locale.US`, since an
  Arabic-locale phone would otherwise render 19:17 as ١٩:١٧. The countdown's elapsed time is fenced
  in LRM marks: a time like 9:41:31 dropped bare into Arabic text is neutral colons between digits,
  which bidi is free to reorder against the surrounding script.
- **Name and time sit side by side, not stacked.** Stacked, each column was two lines tall and only
  as wide as the longer of the two, leaving a broad channel of dead space between every pair while
  the type stayed small to fit the height. Inline spends that width instead, and the height it
  returns is what pays for the larger type — label 11→15sp, time 14→20sp.
- Sizes are set in code against the column count (`sizeText`), because the layout cannot know it and
  one size cannot serve both four columns and six.
- The Hijri **year is dropped**. It is the part of the date never in doubt at a glance, and its
  width is what lets the countdown share that line.
- The countdown moved off the header, where it and the date were ellipsizing each other as the
  prayer name or the month grew, to the line below the times — which is what it refers to.
- Times are bottom-aligned across the strip: columns are top-aligned by default, so a prayer with no
  settings icons rode higher than its neighbours and the row came out ragged.

Still one cell tall. A second cell would have bought the countdown its own row and was briefly taken;
it is not worth a whole row of home screen.

3 new backend tests (185).

## 2026-09-05h — The Shurooq beeps can be any sound

The beeps before sunrise were the device's own generated tone and nothing else. They can now be a
**ringtone** or **any audio file on the phone**, picked in the Athan panel.

Both pickers, not one. The athan gets a file picker alone, because an athan recording is a specific
thing nobody's ringtone list contains — but a countdown mark is exactly the sort of short sound the
system already ships, so the ringtone list is genuinely useful here and worth the second button.

**The generated tone stays, as the default and as a choice.** It needs no file, cannot be broken by
deleting one, and is the right shape for what this is: a mark, not a piece of music. "Built-in tone"
puts it back.

A chosen sound plays once per beep, so the count now decides how long the countdown runs — said
plainly under the control, because picking a two-minute file and asking for ten of them is a
reasonable thing to do by accident.

The generated tone still runs inside `goAsync` on its own thread, since it is spaced by sleeping
between tones. A chosen sound does not: it repeats off the completion callback, the way the plan
reminders already do, because holding a broadcast open for the length of an audio file is how a
receiver gets killed.

3 new unit tests (242 mobile).

## 2026-09-05g — API documentation in ./dist

Three files, for writing a script that adds records:

* `dist/nibras-api-openapi.json` — the spec. Point a client generator at this.
* `dist/nibras-api-openapi.yaml` — the same, for reading.
* `dist/nibras-api-adding-records.md` — the short version: authenticate, create, and a table of
  every settable field on each of the 19 collections.

All three are **generated from the running backend**, by `tools/dump_api_docs.sh`, rather than
written by hand — so they cannot drift from the code. A new module, a renamed field or a dropped
endpoint appears the next time it is run.

The guide states three things a script author would otherwise have to discover the hard way, and
each was checked against the code rather than assumed:

* Login answers `{"token": …, "user": …}`, and every `/api/**` path needs that token.
* A 401 arrives **before routing**, so from outside a missing endpoint is indistinguishable from a
  forbidden one — a 401 does not mean "no such path".
* A `PUT` must carry the `version` it read, or it is refused with **409** and *"This record was
  modified by someone else."* That is the optimistic lock, and a script that ignores it will
  silently lose other people's writes.

Server-managed fields (`id`, `version`, `dateCreated`, `lastUpdated`) are left out of the per-field
tables. Listing them as things to send would be actively misleading.

## 2026-09-05f — Section headings that survive a dense page

The lookup groups on the Parameters page are walls of small coloured chips, and their headings were
`overline` in `text.secondary` — small, grey, and the same visual weight as everything around them.
They disappeared into the chips: there was no telling where RESOURCE TYPE ended and CONTEXT began
without reading the values themselves.

Three things do the work now. The heading is heavier and in primary ink, so it outranks the chips
instead of competing with them. It carries an accent bar and a rule beneath, so the eye finds the
boundary without reading anything. And it **sticks**, so on a long page the group you are looking at
is always named — with an opaque background, because chips scrolling under a transparent header
would read as a smear.

Sticking is optional and off inside a card, where there is nothing to scroll past and a stuck header
would look wrong; the Admin page's one heading uses it that way. Group spacing went up a step too,
since the boundary is now something to see rather than infer.

## 2026-09-05e — Keyboard control, and Reviewed under the thumb

### The galleries take the keyboard

**← →** or **PageUp/PageDown** move between cards, **Home/End** jump to the ends. On the flash
cards, **d** or **Enter** marks reviewed, **r** repeats, **k** keeps, **u** unsets.

This also fixes the drift that prompted it: Left and Right were nudging the card's content sideways
a few pixels, because nothing claimed the arrows and the browser was scrolling the overflow
container with them. Every key the galleries use is now `preventDefault`ed.

**Up and Down are deliberately left alone.** A flash card never scrolls, but a resource's highlights
are a list of unknown length, and taking the vertical arrows would leave no way to read down one.
Sideways moves between items; vertical scrolls within one.

Shortcuts never fire while a field has focus, and never when Ctrl/Cmd/Alt is held, so typing and
browser shortcuts are untouched. The keys are named in the header rather than left to be found.

### Reviewed moved to the right, on both

It is the action most cards get, and a phone held in the right hand has its thumb already at that
edge — so the far side of the row is the wrong place for it, and the rare actions belong there
instead. Order is now Unset · Keep · Repeat · **Reviewed**, with Reviewed last and filled.

The web page uses the same order. Two clients that teach opposite habits for the same three buttons
would be worse than either arrangement.

## 2026-09-05d — The refresh button was hiding when it was most wanted

### Fixed — refresh only appeared on a long, unfiltered list

Refresh sat inside the floating overlay's `rows.size > 8` gate, having been added to the same column
as the jump-to-top and jump-to-bottom buttons. So it vanished exactly when it was most useful: a
filtered view, or a day with nothing written yet, shows few rows or none — and that is precisely
when you want to pull and find out whether anything has arrived.

Scrolling controls belong to a long list. Refreshing does not belong to the list at all — it re-lists
the notes, re-pulls the type and department codes, and forces a full sync. It is now always present;
the jump and collapse-all controls stay behind the length check, which is the one thing that check
was ever right about.

The threshold is a named constant now rather than a bare `8` in a condition, with the reason
refresh is not behind it recorded next to it.

Verified on the phone in the reported state: one filter active, nothing written today, empty list —
refresh showing, jump buttons correctly absent.

## 2026-09-05c — Articles that the page never rendered

### Fixed — a long article imported as a single line

Importing an al-akhbar.com opinion piece produced 51 characters of full text: the headline, and
nothing else. The scraper was not at fault in the ordinary sense — there was no article in the
document to find.

The page is a Next.js App Router page. Its content ships as React Server Component "flight" chunks
inside `<script>self.__next_f.push([1,"…")</script>`, as escaped JSON that the browser assembles
into the DOM. In the HTML as fetched, the `<article>` element holds 119 characters — headline,
byline, and the buttons around them — the whole page has six `<p>` tags, and the density scan that
backs up the hint selectors tops out at 92. So every route into the text came back empty-handed and
the headline was all that was left.

There is now a fallback: when the DOM yields less than 400 characters, the flight payload is
decoded, re-parsed as HTML, and run through the *same* block extraction. On the page that prompted
this it recovers **34,210 characters** in place of 51, entities and all.

Deliberately a fallback, not a first choice. The rendered DOM is what a reader sees and is right
wherever it exists; the flight payload also carries navigation and related-article furniture that
the DOM pass strips by structure and this one cannot. It is better than nothing, which is exactly
the case it is for. And it is not one publisher's quirk — the App Router is widely used, so "renders
nothing into its HTML" is now a common page shape.

**A bug this found on the way in.** The first version matched flight chunks with the obvious regex,
`"(?:\\.|[^"\\])*"`, and died with a `StackOverflowError` on the first real page it met: Java's
engine recurses once per repetition and these chunks run to thousands of characters. A scraper that
crashes on a common page shape is worse than one that returns nothing. The scan is hand-written and
linear now, with a test that feeds it a 20,000-character chunk.

### The fetch-from-URL panel stays put

It closed itself after every successful fetch. Building resources from links is done in runs, so
that meant re-opening the control for each URL. It stays open now and closes when asked — the X, or
Escape.

It also has its own tinted, bordered panel. Unstyled it sat directly above the search field as
another bare row of the same shape, the two told apart only by their placeholder text, which is a
poor way to know which box you are typing into.

### The card bank ships as SQL

`dist/nibras-flashcards-22385.sql.gz` — restore it with `zcat … | mysql -u user1 -p… <db>`, no
Python needed. Still `INSERT IGNORE` on the content-hash key, so re-running adds nothing and resets
no decision. Verified by restoring into the test database and counting.

5 new backend tests (179 backend, 239 mobile, 14 frontend).

## 2026-09-05b — Both galleries, on both clients

Two reading pages, deliberately different from each other.

### Flash cards, on the web too

The same rows the phone reads. A card reviewed in the browser is gone from the phone, because there
is one table behind both. One passage at a time with arrows either side, justified, direction taken
from the passage itself, and the three decisions underneath.

### Highlights — a different thing, and built differently

A resource's key points, one resource at a time. Read-only and **live**: filling in a resource's
highlights puts it in the gallery immediately, with nothing to decide and nothing written back.
That is the whole distinction from the flash cards next door, which are a frozen one-time job.

`main_highlights` has always been in the table and on every other record type. `Book` was the one
entity that never mapped it — which is exactly why a resource's highlights could not be read
anywhere. Mapping it was the fix.

The bullets are split **on the server**, so the phone and the web page cannot disagree about what
counts as a line, and neither carries a copy of the tidying rules. Leading glyphs and numbering
(`-`, `*`, `•`, `1.`, `2)`) are stripped; a line that is only punctuation splits to nothing and the
resource is left out rather than shown as an empty card. Direction comes from the first strong
letter, so one quoted Arabic word in an English list does not flip the card.

### Where they scroll and where they don't

A flash card never scrolls — one passage of at most a hundred words, shrunk to fit, always whole.
The highlights gallery **does** scroll, and that is not an inconsistency: a resource's highlights
are a list of unknown length, and shrinking twenty bullets to fit would end at unreadable type. A
list is the one shape where scrolling is the honest answer.

Each gallery remembers its own text size. A short passage and a long list rarely want the same one,
and sharing a single number would have each page quietly resizing the other.

### The rail stopped fitting

Seventeen destinations does not fit a phone screen, so the bottom of the rail — Settings included —
could only be reached by scrolling a column of icons, which is a poor way to reach anything.

Two changes. The record letters moved to the MIDDLE, so the thing reached most often is not pushed
further down every time a page is added above it. And four of the least-often-opened pages — Search
server, Prayer, Flash cards, Highlights — fold behind one toggle, which brings the rail back to
twelve cells and within a screen. The group opens itself when one of its own pages is showing,
since a collapsed group hiding the current page would be worse than the scrolling.

Its icon is a neutral "more", not a picture of what is inside. It was a book to begin with, meaning
"reading pages" — which stopped being true the moment Search server joined. An icon describing a
group's current members has to be re-justified every time they change.

### Notes

* Verified on the phone that the error path names the failure: `HTTP 404 for
  http://192.168.0.47:8081/api/flashcards?state=NEW`, with the URL and the status. That also settled
  a question curl could not: an unauthenticated probe gets 401 before routing on every `/api` path,
  so it cannot tell a missing endpoint from a present one — an authenticated 404 can, and it
  confirms that server is running an older build.
* 13 new backend tests (174 backend, 239 mobile, 14 frontend).

## 2026-09-05 — Flash cards move to the backend

The passage bank is a table now, not a file bundled in the app. Both clients read the same rows and
write the same decisions, so a card reviewed on the phone is gone from the browser too.

### Why it moved

It shipped as `assets/flashcards.jsonl` inside the APK. That was wrong in three ways, and the third
is the one that mattered: it put 21 MB in the app, it froze the extract at build time, and it kept
every decision on a single device. Reviewing 22,000 passages is not a job anyone finishes on one
phone in one sitting.

### The gathering stays one-time

`flash_card` is a frozen extract: notes written or deleted afterwards do not add or remove cards.
That is deliberate — the job is to get through this material once, not to keep up with it, and a
bank that refilled itself would never be finished.

The passage text is copied rather than referenced, so a card keeps saying what it said when it was
gathered even if the note behind it is later edited. `note_id` is provenance — shown as `N13459` —
and is deliberately not a foreign key.

`card_key` is the passage's content hash, with a unique index. The load is therefore idempotent:
re-running it adds nothing **and does not reset a card that has already been reviewed**. Both halves
were checked against the live table before this was called done.

### The shape of the job

The undecided queue is capped at forty; REPEAT and KEPT are not capped at all. That asymmetry is the
whole design. A bank of twenty-odd thousand served whole is unusable, so only a working set is
offered and the rest stays out of sight — but having said "come back to this" or "I want to edit
that note", the app has no business truncating the result.

`PUT /api/flashcards/{id}/state` with `NEW` puts a card back, which is how a decision is undone. It
clears `decided_on` at the same time: a date that outlived its decision is a lie the next read would
repeat.

An unknown state is a 400, not an empty list. Silently returning nothing for a typo looks exactly
like "you have finished", which is the one wrong answer that reads as success.

### Tools

* `tools/gather_flashcards.py` — notes → passages. The one-time extraction.
* `tools/load_flashcards.py` — passages → SQL on stdout, so the load can be read before it is run
  and piped at whichever database is meant.

22,385 cards loaded, in 1.6 seconds.

### The app got 8 MB smaller

25 MB again, down from 32.9. Decisions are gone from the phone's preferences; what is left there is
genuinely local — the text size and which list was last open.

6 new backend tests (167 backend, 239 mobile).

## 2026-09-04e — Flash cards

A new page: passages lifted out of long notes, shown one at a time to be read and decided on.

### The bank

22,385 passages, extracted from the 1,601 notes whose `description` runs to 200 words or more,
each cut at sentence boundaries to at most 100 words. Bundled with the app, so the page works with
no server and no network. `mobile/tools/build_flashcard_bank.py` is the extractor, kept beside the
asset so a 21 MB blob is reproducible rather than unaccountable.

Religious and psychological passages are **favoured**: they form the front of the bank, recent
first, and everything else follows behind on the same recency rule — imported, as asked, but not
standing between the reader and the material they wanted.

The favouring is graded rather than binary, and that mattered. A single incidental word is not a
topic: a US presidential statement signing off "GOD BLESS AMERICA" scored two and led a bank of
22,000 as its most religious entry. Three or more topical words is the line between a passage that
is *about* this material and one that brushes past it.

Notes of type `aya` are excluded. Worth recording the scale, because the instinct is that this
removes a lot: there are 6,236 of them, but only **17** ever had a 200-word description, so the
length filter had already left the other 6,219 out.

### One at a time

A gallery, not a list. The whole screen carries a single passage, swiped through like pictures on a
wall — reading something closely and scrolling a feed are different acts, and a list quietly invites
the second.

**Nothing scrolls.** A passage that will not fit is shrunk until it does, rather than given a
scrollbar, so every card is whole and there is never anything below the fold to go looking for.
Compose 1.7 has no auto-sizing text, so this measures and steps down: `TextOverflow.Clip` on a
height-bounded text makes `hasVisualOverflow` a reliable "did not fit", and each failed layout
shrinks a little until it settles.

Text is justified, with the direction resolved from the passage's own first strong letter — Arabic
justifies to a clean right margin, English to the left, with nothing to keep in step with the text.
The source record is named above the passage as `N13459`, so a quote can always be traced back to
the note holding it.

Font size is adjustable and remembered, in its own store — nothing to do with the Resources
full-text scale, which is a separate setting for a separate kind of reading.

The three decisions are filled buttons 52dp tall, not flat labels. They are the controls the page
exists for, pressed once per card and often without looking closely; as text they were a small
target on a busy card, missed twice while testing on the device itself. "Reviewed" takes the extra
width, because an even three-way split clipped it to "Reviewe" on a 720px screen.

**Fixed before it shipped:** the action row was drawn underneath the navigation bar. Every other
full-height page in the app either scrolls with bottom padding or sits above the mini player bar,
which carries that inset for it — this page is neither, and needed to ask for it itself.

### Three decisions, and what is capped

**Reviewed** retires a card. **Repeat** keeps it for later. **Keep** holds it as the pile to go and
edit in the note itself. On the decided lists an **Unset** button puts a card back.

The review queue holds forty and hides the rest, and tops itself back up as cards are decided. The
Repeat and Kept lists are **not** capped: having said "come back to this", the app has no business
truncating the result.

Nothing here writes to a note or is pushed to the server. The bank is a fixed extract and a decision
is a private reading position, not a change to the record.

### Notes

* The bank is read as a **stream**, one JSON object per line, and the scan stops as soon as it has a
  full queue and every decided card. Loading all 21 MB to fill a screen of forty took about ten
  seconds on the phone this was built for; it now takes about three. The header line carries the
  card count, which is what makes stopping early possible — otherwise knowing how much is held back
  would mean reading every line every time.
* Lines are classified by a substring, and only the handful actually wanted reach a JSON parser.
* `org.json` ships with Android but is a **throwing stub** in JVM unit tests, so the first run of
  these tests silently returned nothing at all. The real implementation is now a test dependency.
* The extract came out of MySQL as JSON-per-line rather than TSV: descriptions carry bare carriage
  returns, and a TSV read through Python's universal newlines split 400 notes into 2,104 half-rows.
* Markdown footnote marks and bracketed citation numbers are stripped. A card reading "anxiety and
  psychological distress[^291]" looks like a typo when the footnote it points at is nowhere on
  screen and never will be.
* Card ids are content hashes, so a decision survives the bank being rebuilt — **unless that
  passage's own text changed**, in which case its id changes with it and the decision is orphaned.
  Stripping the footnote marks did exactly that to a handful of cards.

13 new unit tests (252 mobile, 161 backend).

## 2026-09-04d — Widget badges, per-prayer setting icons, transcripts in the player

### Counts became badges

Every list widget's count moved out of the title's parentheses and into a right-floated pill.
Inside the title it competed with the name for the same ellipsis and was the first thing lost when
the widget was narrowed — exactly backwards, since the number is the part read at a glance.

**Upcoming plans** now carries one at all, red below seven: a low number is the problem here, not a
high one, so the comparison runs the other way from the rest. The colour rule is the KPI widget's
own, moved into one place both now read from, so the two views of the same numbers on the same home
screen cannot drift apart about which are worrying. `TOO_FEW_PLANS` went from 10 to 7 as asked, and
the KPI widget follows it.

Bookmarks and today's tasks get a neutral badge. How many records you chose to bookmark is not a
number that can be too high or too low, and a day with few tasks written down is not a worse day —
colouring either would be inventing a judgement.

### The prayer strip says what will happen at each time

A speaker where the athan will be called, a struck bell where the ringer will go silent, bars where
the beeps will sound before Shurooq. The strip was otherwise silent about the difference between a
time that will announce itself and one that will pass unnoticed — which is exactly the thing worth
checking at a glance, and otherwise needed the app and a settings panel to find out.

Every icon starts hidden, so a column with none of them collapses its row to nothing and the strip
keeps its height.

### Transcripts in the media player

Sidecar `.ai.txt` transcriptions now show in the player, in the slot a video would occupy — above
the transport, readable while the audio plays. One chip per language found, the text below,
right-to-left when the language tag says so.

The convention is the desktop player's, `<base>.<lang>.ai.txt`, with the language *before* the
suffix. Worth stating because the request had it the other way round: a check of the phone found
1,196 files in the first form and none in the second, and a convention of our own would have found
nothing at all.

The tag decides direction before the text does. Machine transcripts routinely open with a stray
Latin token — a mis-heard name, a bracketed marker — and an Arabic transcript is still Arabic when
its first word came out wrong.

**Read-only, deliberately.** The desktop edits these in place, which is right at a desk with a
keyboard. On a phone the realistic outcome of an editable panel is a stray keystroke silently
rewriting a file that syncs to every machine. The text is selectable, so a passage can still be
copied out.

13 new unit tests (239 mobile, 161 backend).

## 2026-09-04c — Prayer strip, athan files and volumes, a daily collection, calendar entries that stay

### The prayer widget is a strip now

Rebuilt to the shape it is actually read in: the Islamic date and a live countdown across the top,
then one column per shown time with the next one in an amber pill. A glance for what is next and
how long is left, not a list to work down. Six columns divide the width as readily as four, so
hiding Shurooq widens the rest instead of leaving a gap.

The countdown is a `Chronometer` in countdown mode, which ticks on its own between redraws.
`RemoteViews` offers no other way to show a running clock, and waking the app once a second to
redraw a widget would be indefensible. Against the reference widget on the same home screen it
agrees to the second.

The highlight follows whatever the countdown is counting to — **including tomorrow's Fajr** once the
day is spent. It first shipped restricted to today, which left the strip with nothing marked all
evening while the header still named a prayer; that read as a fault rather than as a distinction.

**Fixed — the widget could not be resized.** `minResizeWidth`/`minResizeHeight` were absent, and
Android takes the resize floor from `minWidth`/`minHeight` when they are. A widget declaring a
comfortable minimum therefore became one that could not be made any smaller at all. `dumpsys` read
back `min=(...) minResize=(...)` with both identical; they now differ, and the widget resizes.

### Athan: a file, and a volume for each time

The athan is picked from the phone's **files** now, not from the system ringtone list. That list
holds notification tones and alarm jingles — an athan recording is neither, so the picker was
offering a list that could not contain the answer. A persistable read grant is taken, because the
alarm receiver has to open that URI months later, after reboots, with the app closed.

Each prayer that calls the athan gets **its own volume**. The Fajr athan and the Dhuhr athan want
different volumes from the same phone — one is heard in a silent house at dawn, the other over a
working day — and one setting cannot serve both. Scaled within the alarm stream, so it can quieten
an athan but never make it louder than the device's own alarm volume; 0 mutes that one, and a 0 is
stored rather than dropped, because "call this one silently" is a setting and not the absence of one.

The percentage is squared into the amplitude the player takes. Amplitude is not loudness: a linear
50% is only about 6 dB down and barely sounds quieter, which crams the useful range into the bottom
of the slider. Squaring puts the middle at roughly 12 dB down, which is about what someone means
when they drag a volume control halfway.

### The file collection can run itself

Once a day, at a configurable time (15:00 by default). Same collection the Collect page performs,
filed with the same defaults, into the same notes folder. Off by default and stated plainly in
Settings, because it **moves** files out of Camera and Downloads and that is not a thing to discover
by accident. It reports what it did, and says nothing at all when there was nothing to collect — a
daily "collected 0 files" would train you to ignore the one notification that matters.

### Calendar entries stop disappearing

`exportJournal` served **bookmarked records only**, which had a specific and bad consequence: an
entry made on the phone synced up, came back down on the next pull only if something had bookmarked
it, and otherwise vanished from the calendar it was just entered into. Journal and Planner now
export anything bookmarked **or** starting within the past week / coming fortnight. The bookmark is
kept as an OR rather than replaced, so anything deliberately pinned still comes down however old.

Worth recording precisely: `exportPlans` never filtered on the bookmark at all — it was already
date-windowed, at ±14 days, now −7/+14 as asked. Journal was the one doing it.

### Fixed — deep links opened a page of JSON

Opening a record in a new tab returned `{"detail":"No static resource read/planners/1231."}` instead
of the record. `SpaController` already serves the React shell for unmatched paths, but only for 404s
that reach the container's `/error` dispatch — and since `spring.mvc.problemdetails.enabled` was
turned on, Spring resolves `NoResourceFoundException` into an RFC 7807 body and returns it directly,
so it never got that far. Single-segment routes kept working throughout, because `SpaController`'s
own mapping catches those before any resource lookup, which is why this only surfaced on a
two-segment route opened directly.

The new advice has to outrank Boot's own `ProblemDetailsExceptionHandler`, which sits at order 0 and
already claims that exception — an unordered advice sorts last and never sees it. Backend paths keep
their machine-readable 404, and so do genuinely missing assets: answering `/assets/index-abc.js`
with HTML would turn every broken script tag into a page parsed as JavaScript.

### Fixed — the published APK could be named for the wrong build

`version.properties` advances on every Gradle configuration, so reading it *after* a build — running
the tests in between is enough — names the file for a build that is not the one in it. A b314 APK
was published as b315 this way. Gradle now owns the whole step: `./gradlew :app:publishDebugApk`
builds and copies in one invocation, so the number compiled in and the number in the name come from
the same resolution and cannot drift. The APK also carries `assets/build.txt`, so the claim is
checkable from the artefact with nothing but a zip reader — `verifyPublishedApk` does exactly that
and fails if they disagree.

12 new unit tests (226 mobile, 161 backend).

## 2026-09-04b — Today's tasks widget, with an add bar

A home-screen widget listing the tasks written **today** at the top priority — type `T`, priority
`p4` — with a bar at the top for adding another. Distinct from the existing "Top priority" widget,
which lists every top-priority note ever written whatever its type: this one is a day's work list,
so it empties itself overnight and what is on it is what is still outstanding now.

### The add bar is not a text field, and cannot be

`RemoteViews` supports no editable view on any Android version — there is no `EditText` in a
widget, and no API that adds one. So the bar is a `TextView` drawn to look like an input, and
tapping it opens `QuickTaskActivity`: a translucent, keyboard-first window over the home screen with
the real field in it. It opens already focused, one line, with **Done** on the key. Enter writes the
note, refreshes the widget and closes, so the task is on the list as the input disappears.

This is the same arrangement Keep and Todoist use for the same reason. It costs one window
transition; there is no version of it that types directly into the widget.

### Notes

* Tasks are found from **file names alone** — `<timestamp>~T~p4~<category>.md` already carries the
  type, the priority *and* the time the note was written, so the widget needs one directory listing
  and no file reads. The obvious `listNotes().filter { … }` would have read the full text of every
  note in the folder to decide it wanted three of them, on the main thread, on every redraw.
* "Today" comes from the name's timestamp, never the mtime. Syncthing rewrites files it has not
  changed and retagging renames them, either of which would report a year-old task as today's.
* The name-only filter falls back to the default timestamp format when the configured one does not
  fit. The two can genuinely disagree — a note name must match `yyyy_MM_dd-HHmmss` whatever the
  setting says — and without the fallback the widget would have quietly reported that nothing was
  ever written today.
* Priority follows the configurable `topPriority` rather than a hardcoded 4, and the title says
  which it is: "Today's p4 tasks (3)".
* A task added **inside the app** now refreshes this widget too, alongside the KPI widget that
  `MainViewModel.add` already refreshed. Typing a task in the app and not seeing it on the widget
  would have been the same bug from the other end.
* Fixed in passing: the Top priority list's empty state said "No priority-4 notes" while the filter
  used the configurable priority. It now names the priority actually in force.
* `PlansWidgetService` now switches on a section string rather than a boolean, since it serves
  three lists instead of two.

9 new unit tests (214 in total).

## 2026-09-04 — Prayer times

A new page on the rail, a home-screen widget, and the alarms that go with them. Everything is
computed on the phone: no network call, no lookup service, and the coordinates never leave the
device.

### The calculation

`PrayerTimes` is the standard sun-position algorithm — declination and equation of time for the
moment in question, then solving for the hour angle at each prayer's defining altitude, iterated to
a fixed point because each time's own hour is an input to the sun's position at that hour. It is
pure Kotlin with no Android in it, so the part most likely to be quietly wrong is the part under
test: London and New York sunrise/sunset at the solstice are pinned to published figures, and Beirut
comes out matching local tables to the minute.

Two settings decide the answer, and they are separate because they answer different questions:

* **School** — whether Asr begins when a shadow equals an object's length (Standard: Shafiʿi, Maliki,
  Hanbali, and the Jaʿfari reckoning) or twice it (**Hanafi**). About an hour apart in summer, and
  the setting most likely to make the app disagree with the mosque down the road.
* **Method** — how far below the horizon the sun must be for Fajr and Isha. Seven are offered,
  including **Shia Ithna-ʿAshari (Leva Institute, Qum)** and **Tehran**, the two that hold Maghrib
  back until the sun is 4° down rather than treating sunset as the moment. Umm al-Qura's Isha is an
  interval rather than an angle, and stretches to 120 minutes in Ramadan — which the app knows
  because it has the Hijri date already.

Times are rounded to the whole minute on purpose, so the row you read *is* the instant the alarm
fires. Above about 48° there are summer nights when Fajr and Isha have no geometric answer at all;
a blank row is the honest default, and three high-latitude rules are offered for anyone who needs a
time named anyway. Under the midnight sun nothing can be rescued and every row stays blank.

### What you can set, on the page itself

The settings live with the times rather than in the app's Settings screen, for the reason the
player's do: nudging Asr by three minutes is a decision you make while watching what Asr says.
Everything writes through immediately and re-arms the alarm chain, so the screen and the schedule
never drift apart.

* **Which times to show** — any of Fajr, Shurooq, Dhuhr, Asr, Maghrib, Isha.
* **Per-time adjustment** — −/+ minutes against the calculated time, shown beside the time it
  produced. This is how you match the mosque you actually pray at; no method agrees with every one.
* **Athan** — which prayers call it and what it plays. Shurooq is never on the list: it is sunrise,
  not a prayer. Played on the alarm stream, so it is still heard through the silence below.
* **Reminder** — a configurable number of minutes before each shown time.
* **Silence** — the ringer drops to silent from the time until *n* minutes after, then goes back
  exactly as it was. Chosen separately from the athan, because wanting the phone quiet while you
  pray and wanting to hear the call are different decisions. Needs Do Not Disturb access, and the
  panel says so and offers the button.
* **Beeps before Shurooq** — yes/no, how many, and how long before. Sunrise closes the window in
  which Fajr can still be prayed, so it earns a signal of its own; the beeps sound on the alarm
  stream, to be heard by someone asleep.
* **Islamic date** — a ±3-day nudge, and whether the day turns over at Maghrib (it does, by default,
  because it does).

### The Hijri date

Arithmetical, from the tabular calendar: reproducible offline, on any date, with no network and no
lunar-visibility model. What it is not is authoritative — the real month begins on a sighting — and
the day nudge exists precisely so the user can read the announcement and correct it, rather than the
app pretending to know. Checked against the epoch (1 Muharram 1 AH = 19 July 622) and against the
starts of Ramadan 1446 and 1447, both of which it lands on exactly.

### The widget

Today's times, the Islamic date and what is next. The next prayer is drawn in the accent and the
ones already passed recede. Redrawn as each event fires rather than on a timer, because the only
thing that goes stale is which row is highlighted, and that changes exactly when an alarm goes off.

### Notes

* One exact alarm is pending at a time; each firing arms the next. A missed link heals on the next
  app start or reboot rather than stopping the schedule for good, and the receiver re-arms in a
  `finally` — a thrown exception costs one athan, not every one after it.
* A silence interrupted by a reboot is undone at boot. The alarm that would have ended it did not
  survive the restart either, and a phone left silent indefinitely is much the worse failure.
* Coordinates are stored as a Double's raw bits. `putFloat` is not merely imprecise here, it is
  visibly wrong: 33.8938 comes back as 33.89379806518555, which is what the text field would then
  show. The 0.2 m of positional error would never have mattered; the fifteen spurious digits would.
* Location is coarse, optional, and read only when the button is pressed — the last known position,
  not a fresh fix, since a prayer time moves by seconds across a whole town.

50 new unit tests (205 in total).

## 2026-09-03b — The Volumes widget shows only what you can set

### Removed — the in-call volume bar

The bar that read almost full was **in-call volume** (`STREAM_VOICE_CALL`), not ring. Ring was at
5/15 = 33% and drawing correctly; the two were confused because `ic_ring_volume` and `ic_call` are
both telephone handsets at 18dp, and this widget has no labels.

Rather than making the icons more distinguishable, the bar is gone. Verified on the device:
**Sounds and vibration → Volume** offers Ringtone, Media, Notifications, System, AI assistant,
Accessibility and Alarm — and *no in-call slider*. Android exposes that stream only through the
volume keys during an active call. So the bar reported a level that could not be checked against
anything or changed from anywhere, and sat near its default maximum while every other bar sat at a
third. A widget that reports a number the user can neither verify nor act on is worse than one
that leaves it out.

Five bars remain — Ring, Notif, Alarm, Music, System — which is exactly the set on that Settings
screen, and every one of them now has its own silhouette. `ic_vol_call` is deleted.

## 2026-09-03 — Player options that survive a restart; "next" answers back

### Fixed — the player's sliders are settings again

Skip seconds reverted to 10 on every launch. So did speed (to 1×) and volume gain (to 0 dB): all
three were plain in-memory values while every knob around them — bookmark rewind, double-tap
window, auto-bookmark, play-next mode, audio-only — was stored. The comment above them even claimed
they were durable.

They now persist, and the restored values reach the playback service through the existing
`syncFromController`, which already re-asserts them when the MediaController connects.

**Verified on a device across a real force-stop**, not just in the slider: Skip 15s came back with
the transport buttons reading **+15 / −15**, and Speed 1.15×.

The open panel under the transport (bookmarks / folder / recents / options) persists too, like the
home screen's composer and filter panels. "Never chosen" and "deliberately closed" are stored as
different answers, so closing the panels stays closed instead of springing back to Bookmarks.

The sleep timer stays deliberately transient — it is a one-shot countdown, not a preference.

### Fixed — ⏭ no longer does nothing in silence

"Next" can legitimately find nothing, and it used to say so by not moving, which is
indistinguishable from a tap that missed the button. It now flashes a message on the same centre
overlay a bookmark uses — and distinguishes the cases, because they call for different responses:

- **no media folder set** → points at Settings;
- **nothing playing** in a folder-scoped mode → there is no "here" to pick from;
- **folder exhausted** vs. **library exhausted** → different scopes, different facts;
- **last file in the folder** — the ordered modes ran out just as silently and were never reported.

### Fixed — volume bars measured on each stream's own range

Android streams do not all start at zero. On a Galaxy A07 the **call and alarm** streams report
`Min: 1` — Android will not let either be silenced from its slider, so index 1 *is* the bottom of
that slider. The widget drew every bar from 0, putting those two above where the system's own
slider sits (call 11 of 1..15 is 10/14 = 71%, not 11/15 = 73%). Bars and their colours are now
measured on `min..max`.

Streams with `Min: 0` — ring, music, notification, system — are unaffected; the ring bar was
already agreeing with the system, measured at 5/15 on the device.

Mobile suite: 155 tests.

## 2026-09-03 — Top priority widget rows use the width they have

### Fixed — widget rows no longer cut at a fixed 20 characters

The **Top priority** widget stopped every row after twenty characters however wide the widget was,
while **Bookmarked records** filled whatever width it was given. Both draw the same row layout, and
that layout was never the problem: `widget_row_compact` already puts `ellipsize="end"` on a
weighted `TextView`, which measures itself against the real width.

The Top priority factory cut the text in Kotlin first:

```kotlin
if (s.length > 20) s.take(20) + "…" else s
```

That runs long before anything knows how wide the widget is, so the ellipsis was always in the same
place. Bookmarked records passes its text through whole and lets the view decide — which is why the
two behaved differently from the same layout.

The cut now belongs to the view. `widgetRowText` produces one clean line and nothing more:
flattened, whitespace runs collapsed (a flattened indented note otherwise spends the row's width on
gaps), and highlight `==markers==` stripped — a widget row is a label, like a note card, and that
punctuation means nothing on a home screen.

**The 200-character bound that remains is a transport guard, not a display limit.** A note body is
unbounded and every row crosses to the home screen through a binder transaction with about a
megabyte shared between all of them; shipping whole notes for forty rows is how a widget dies with
`TransactionTooLargeException` instead of drawing. It sits far above what the widest home screen
can show, so it never decides where text ends — the ellipsis a reader sees is always the view's.
The tests assert both halves of that: bounded, and not bounded so tightly it becomes a width again.

Mobile suite: 148 tests.

## 2026-08-29 — Honest failure messages, a first-run tour

### Fixed — a request that never reached the server no longer blames the operation

Importing a resource by URL reported **"Could not fetch that page."** when the real cause was that
the backend was down. The message was not merely unhelpful, it pointed the wrong way: it sent
someone to check a URL that was fine.

The backend was never at fault. Its rejections are written for a person ("Refusing to fetch x — it
resolves to a private address", "The page returned HTTP 403") and `spring.mvc.problemdetails` has
been carrying them to the client all along. The frontend threw them away in the case where there
were none to carry: **fourteen** call sites each ended in

```ts
(e as {...})?.response?.data?.detail ?? 'Could not do the thing.'
```

and when there is no response at all — Tomcat restarting, the laptop asleep, Caddy up with nothing
behind it — `response` is undefined and every one of them fell through to a sentence blaming the
operation.

All fourteen now go through `errorDetail`, which distinguishes what actually happened:

- the server's own words, when it sent any;
- a **502/503/504** named as what it is — "Nibras is not answering — the backend is down,
  restarting, or still starting up" — rather than as a failure of whatever was being attempted;
- 401/403/404/413 explained in terms of the status, not a bare number;
- **no response at all** reported as unreachable, naming the API address, and explicitly *not*
  appended to the caller's message, which would produce the self-contradicting "Could not fetch
  that page. The server did not answer.";
- offline, timed out, and cancelled told apart, because they have different fixes.

An HTML error body (a proxy's 502 page, Tomcat's whitelabel) is now recognised as a string rather
than being read through as an object, so markup can never reach the screen.

`errorDetail` moved to `src/util/errors.ts` — `api/client.ts` reads `document.baseURI` at import
time, and the one function that has to be right when nothing else works should be testable outside
a browser. `api/client` re-exports it, so call sites are unchanged.

### Added — `npm test` in the frontend

There was no way to run a test here at all. There is now a ~30-line runner: each `test/*.test.mjs`
imports the real TypeScript source, esbuild bundles it, node runs it. No framework — everything
worth testing in this app is either a pure function in `src/util` or needs a real browser, and
vitest + jsdom is a large dependency for the first group. 14 checks on `errorDetail`.

### Added — a first-run setup tour (mobile)

knotes is a front end for folders on the device, and almost every screen it has is dark until the
folder behind it points somewhere real. Nothing says so: an unconfigured Media page is empty, and
empty is exactly what a correctly-configured library with no files looks like. A new install
therefore looks like a working app that happens to have nothing in it.

Eight steps, one folder each, each stated together with what it turns on: notes, collected files,
media, pictures, the Nibras connection, then reminders and the chime. Under every path is what is
actually there right now — *doesn't exist yet*, *empty*, *3 items*, *that's a file* — because the
commonest way to mis-set this app is a path that reads correctly and isn't there, and the symptom
is silence.

Details that matter more than the screens:

- **It never appears for an existing install.** Its own "done" flag cannot tell a fresh install
  from an upgrade that predates the flag — both look unwritten. The test is instead whether
  settings have ever been saved (`SettingsStore.isConfigured`), so an upgrade is marked done
  without ever being shown a screen.
- **It never creates a folder on its own.** Every folder here is where the user's own files live;
  guessing wrong scatters empty directories through their storage, and on a device whose card
  isn't mounted yet it would create the "missing" folder in the wrong place and then sync into it.
  Creation is one clearly-labelled tap.
- **Moving the notes folder moves the collection destination with it** while the two match. They
  default to the same folder for a reason: a collection writes a note *and* its companion folder,
  and the desktop only reads that as a note if it sits among the notes.
- **Answers are saved on every step**, so an interrupted setup keeps what was already answered.
- **Skip is on every screen**, not just the first.

Reopenable from the bottom of Settings — the tour is also the only place that says what each folder
is for.

Mobile suite: 141 tests. Frontend: 14 checks.

## 2026-08-27 — Legible attachments, a "newest here" play-next mode

### Changed — attached files are readable on a note card (mobile)

An attached file's name used to be `labelSmall` grey text behind a paperclip — the same weight as
the type/priority badges under it, so a card with attachments read as one grey block and the file
names, which are the whole reason to tap, were the least visible thing on it.

Each file now gets its own row: body-sized text on a `surfaceVariant` strip, with an icon chosen
from its extension (audio, video, image, PDF, document, spreadsheet, slides, text, archive, code).
The strip separates the files from the note's prose above and its badges below without needing a
divider.

Two details that are not decoration:

- **A playable file's icon wears the accent colour.** Tapping a media file opens the in-app player;
  tapping anything else throws it out to whatever app claims it. Those are quite different things
  to have happen, and the tint is the only warning of which one you are about to get.
- **The name is laid out directionally.** An Arabic file name in a left-to-right card otherwise
  renders backwards, and now that these names are long enough to be truncated, the ellipsis would
  land at the wrong end too.

Extension matching goes through `FileNames.ext`, so a name carrying a trailing invisible bidi mark —
`تسجيل.mp3‏`, which is how such names actually arrive from the server and from Syncthing — still
gets its own icon instead of falling back to a blank page.

### Added — play-next mode "Newest here" (mobile)

The same folder scope as **Random here** (the folder around what's playing; from inside a note's
`.d` companion folder, the folder that *holds* them), picked from the other end of the clock: a
recent unbookmarked arrival rather than a forgotten old one. For hearing what has just come in.

The two ends are deliberately not symmetric. "Old" means *forgotten* — downloaded, never heard,
gone stale — so it keeps its absolute 90-day threshold. "New" is inherently relative: a folder whose
newest file is a year old still has a newest file, and that is the one you meant, so there is no
threshold at all, only a ranking.

**The pool is a proportion, not a fixed size.** The first implementation drew from the 25 newest,
which passes every small-folder test and is wrong at exactly the size this mode is for: scoped to
one folder of note attachments, "the newest 25" is every file there is, and the mode silently
degrades into plain shuffle. It is now the newer *half*, capped at 25 — a real recency bias at any
scope size. It stays a pool rather than "the newest file" because Syncthing copies a folder in one
pass and every file lands on the same second; a strict maximum would hand back one arbitrary file
forever.

Mobile suite: 133 tests.

## 2026-08-18 — Tickable checklists, sample routines, delete from the player

### Added — `- [ ]` items are tickable (frontend)

Option A of `dist/habits-proposal.md`: a routine is a recurring Planner whose `description` holds a
markdown checklist. This is the half that needed building — the recurrence already existed.

Checklist items now render as real checkboxes wherever a markdown field is shown (record detail and
the read page) and ticking one writes straight back to the record.

Two properties the implementation is built around:

- **Only the character between the brackets changes.** Not the line, not the list — indentation,
  bullet character and spacing survive a tick untouched. The same text is edited by hand in the
  checkout `.md` and the desktop editor, and a renderer that quietly reformatted on every click
  would fight whoever wrote it.
- **Fenced code blocks don't count.** A `- [ ]` inside a fence is documentation of the syntax — the
  Markdown cheat-sheet page renders exactly that — so counting it would shift every index after it
  and tick the wrong line.

The DOM checkbox is never the source of truth: the click is prevented, the record is rewritten from
its *current* stored value, and the re-render decides what is ticked. So a failed save can't leave a
ticked box sitting over untouched text, and two quick ticks can't race.

### Added — sample routines

Three recurring Planners seeded into `db_nibras2_uat` for trying this out: **Routine — Morning**
(07:00), **Routine — Evening** (19:00) and **Routine — Night** (22:30), each `CUSTOM` cron, each
with a four-or-five item checklist. The script is `dist/seed-sample-routines.sql` — idempotent, and
it names its own undo.

### Added — delete the playing file, from the player (mobile)

A delete action in the player's top bar, behind a confirmation that names the file and says how many
bookmarks go with it.

Playback is stopped first: the media session holds the file open, and deleting underneath a running
player leaves it either erroring or playing something that no longer exists. The bookmark sidecar,
the saved position and the recents entry go too — leaving those behind means the next file to take
that exact path silently inherits another file's bookmarks. Playback advances to the next sibling so
working through a folder doesn't stop dead on every delete. A delete that fails changes nothing at
all, and says so.

## 2026-08-17 — 1.2.34 "Gratitude" (b262)

### Fixed — the home list still landed at the bottom

The previous attempt guarded the auto-scroll effect, which was necessary but not the cause. The
cause was one line in `AppRoot`: opening the player `return`ed before `AppShell` instead of drawing
over it.

That reads the same on screen and is not. Returning removes the whole shell from the composition,
and a composable that leaves the composition takes its remembered state with it — including the
`SaveableStateHolder` that exists precisely to keep each page's scroll position while it is
off-screen. So opening a file from a note rebuilt the home screen from nothing on the way back, and
landed at the bottom with the note being read scrolled away.

The player is now an overlay: the shell stays composed and is covered by an opaque, click-absorbing
surface. Its own `BackHandler` is registered after the shell's, so it is still the one that fires.

### Fixed — the fourth play-next chip was a sliver

Four chips no longer fit on one line of a phone, and a `Row` squeezed them all — a tall thin box
with one clipped letter in it. They wrap now (`FlowRow`), each at its natural width.

### Changed — every options slider has − and + buttons

A bare slider is a poor control for a *value*: on a phone track, one step of speed or one decibel is
a few pixels, and a tap lands where your finger is rather than where you meant. All six option
sliders (skip, speed, bookmark rewind, 2-tap priority, volume, resources font size) now have
buttons either side. The slider stays for coarse moves, which is what it is good at.

Every value — the slider's own included — is snapped to the step grid, measured from the range's
minimum rather than from zero, so a drag can't land between steps and leave the buttons nudging
from a fraction the label never showed.

## 2026-08-17 — 1.2.34 "Gratitude"

Version set to **1.2.34** across all six components. The Android `versionCode` is unchanged, so the
new build still installs over the previous one.

### Added — a Today home page (frontend)

`/` was a redirect to whichever module sorted first, which told you nothing you didn't know. It is
now a page: per enabled module, the **last N created** and the **last N modified**, N configurable
in Settings (default 5, capped at 50).

The two columns answer different questions — "what did I start?" and "what am I still working on?"
— so a record appears under Modified only if it has actually been touched since it was written.
Otherwise every new record would appear twice and the second column would just be a copy of the
first, which is what makes a page like this ignorable. Both orderings come from the server
(`dateCreated,desc` / `lastUpdated,desc`), not a re-sort of one page.

### Added — search says *where* it matched (frontend + backend)

A search result used to be a title, which for a resource matched deep inside a scanned page looked
like an unexplained hit. Each row now carries the excerpt(s) where the term was found, labelled by
field — Description, Full text, Notes, Highlights, Short description — with the term emboldened.

- It looks for the query **as typed**, because that is what listed the record: the filter is a
  single `LIKE %q%`, not a per-word match, so pointing at where one common word of a phrase
  happened to land would explain a match that never happened that way.
- It is a separate request (`GET /api/<module>/match-context`) for the ids on screen, not extra
  fields on every module's DTO: the context is wanted only while searching and is far larger than
  the row that shows it. The list renders at once and the context fills in underneath.
- **Resources are now searched by `fullText` and `highlights` too.** A resource is very often
  findable only by a phrase inside its scanned text, and a search that silently skipped the biggest
  field on the record was the wrong kind of fast. It costs about a second on 25k books.

### Added — mobile player: "Random here"

A fourth play-next mode: the same never-bookmarked, oldest-first random pick as "Random old", but
confined to the folder around what is playing, searched recursively.

From **inside a note's `.d` companion folder the search starts one level up**, so it sweeps the
sibling notes' attachments rather than replaying the two files belonging to the note already
playing — which is the point of the mode: working through the whole pipeline.

Every mode now has an icon (⃠ / playlist / shuffle / stacked folders), shown on its chip **and** as a
badge on the ⏭ button — the same way the bookmark rides on pause. What "next" will do is a setting
two panels away; this is where you find out before pressing it.

### Fixed — mobile

- **The "New note" form stayed collapsed only until the app restarted.** Its state lived in the
  saved-instance bundle, which a cold start throws away, so the form reopened every morning however
  often it was closed. It is a standing preference and now lives on disk.
- **Returning to the home list dumped you at the bottom.** Opening a note's file in the player and
  coming back lost the note you were reading and its neighbours. The list's scroll position always
  survived the trip — the auto-scroll-to-newest effect was overwriting it on every composition. It
  now moves only when the list has actually grown (a note was added) or the ordering changed.

## 2026-08-16 — A star that un-bookmarks, list filters, and a rebuild that survives a bad record

### Added — the mobile star can un-bookmark on the server

Settings → "Star button" now chooses what the star beside a Nibras record means.

- **Bookmark on this device** (unchanged, still the default) — a private shortlist that is never
  sent anywhere.
- **Un-bookmark in Nibras** — starts from the fact that every record on the phone is *already*
  bookmarked on the server, which is why it synced down at all. So the star is drawn **filled**: it
  states a fact rather than offering a toggle. Tapping it takes the record off the server's list —
  immediately when online, on the next sync when not.

  The row deliberately **stays on screen**, tinted and labelled, until a sync actually stops
  returning it. That window is the undo: tap the star again and the record is restored. The flag is
  written as a *desired state* rather than an action, and a queued flag is replaced rather than
  appended, so a tap and its undo cancel instead of racing each other onto the wire — the outcome is
  the same whether or not the first tap got through.

  In this mode the local "★ Bookmarked" grouping is dropped: the star no longer writes that set, and
  a section the user can't change any more would just be frozen.

### Added — two filters in the list pane

- **Records per page** (10 / 25 / 50 / 100 / 200), remembered across sessions. It sits with the
  filters rather than beside the pager: how much you want to see at once is part of how you're
  looking.
- **Priority**, right after Type. An *exact* priority, which the module didn't have — the existing
  "high priority" report preset means "3 or higher", and asking for the p3s is a different question.

### Fixed — "Rebuild search index" reported only "Reindex failed"

The rebuild reads ~50,000 records and wrote them all in **one transaction**, so any single record
the database refused rolled back the entire thing — and the Admin page turned every possible cause
(a rejected row, a missing table, a permission, a dropped connection) into the same four words.

- The work is now **committed in chunks**; a failing chunk is retried row by row to find the record
  actually at fault, and the rest of the index is still built. The result carries what was skipped
  and why, so "51,430 indexed, 3 skipped" reads as a success with a footnote rather than a failure.
- A **missing `search_doc` table** — what a database that predates the migrations actually produces —
  is now reported in plain words up front, naming Flyway, instead of surfacing as a raw SQL error on
  the first of 51,433 inserts. (Two databases on this machine, `db_nibras` and `db_nibras_470s`, have
  neither the table nor a Flyway history; an instance pointed at either fails exactly this way.)
- The Admin page shows **the server's own message** instead of a fixed string, and colours the result
  by what happened rather than always green.

### Fixed — mobile

- **The filter calendar's per-day counts** were on a second line inside each cell and fell outside it
  on a tablet, so they simply weren't there to read. Date and count now share one line — `14 (3)`.

## 2026-08-16 — Maintenance page, and a delete that can't strand files

### Added — a Maintenance page

A new tool page (`/maintenance`, toggleable in Settings, gated on `content.write`) for the two
stores that grow without anyone deciding they should.

- **Heaviest repository-1 folders.** Every record's repo-1 folder is measured and the top 30 are
  ranked by size, with the total, the record count scanned, and the path. Tick rows (or "select
  all") and move their files to the same record's folder in repository 2 — repo 1 is the small
  portable one, repo 2 is the archive.

  The scan is driven from the database rather than by walking the tree, so a folder is attributed
  to a record exactly the way the rest of the app resolves it (module code, type bucket, `id/100`
  bucket) and an orphaned folder from a deleted record can't masquerade as a record.

  Two things deliberately stay behind: the **cover image** and the **checkout `.md`**. Both are
  looked up in repo 1 by path (the thumbnail, and the pending-sync scan), both are tiny, and moving
  them would break a visible feature to reclaim a few KB. So the page offers a *movable* size,
  a little under the folder size, and that is what the button promises to reclaim.

  Nothing in repository 2 is ever overwritten: a name already taken there gets a `-1` suffix,
  because the destination may hold an older copy of the same document and losing it silently is the
  one outcome a space-saving tool must never produce. The move falls back to copy-then-delete when
  the two repos are on different mounts, and the delete only runs after the copy succeeded.

- **Longest resource full text.** Resources ranked by the size of their `fullText` column, with the
  same select/act shape. Offloading writes the text to `R-<id>-fulltext.md` in the resource's
  repo-1 folder and clears the column — the file is written and verified *before* the column is
  cleared, and an existing file of that name is parked with a timestamp rather than replaced.

  Stated on the page, because it is a real trade: an emptied field is out of record search. The
  text is still on disk, greppable and openable, but the app won't match on it.

### Changed — deleting a note (desktop + mobile)

- **Delete is now a visible action on the card**, not only a keyboard shortcut (desktop) or a menu
  item (mobile). On mobile it opens a dialog that either confirms or explains; on desktop it joins
  the card's action row beside "open folder" / "open note".

- **A note with attached files can no longer be deleted.** Its `<stem>.d` companion folder is only
  reachable *through* the note — nothing else on disk names those files — so deleting the `.md`
  turns them into an orphan folder named after a timestamp, which is how a photo goes missing for a
  year. Empty the folder first, deliberately, where you can see what you are throwing away.

  An empty or missing companion folder strands nothing, so it isn't a blocker: mobile removes the
  empty folder with the note, desktop moves it to `.deleted` alongside as before. The check walks
  nested folders, so it is stricter than the card's file list (which shows only the top level) —
  the question is "would this strand a file", not "does the card show one".

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
