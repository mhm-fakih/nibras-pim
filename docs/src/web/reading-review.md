# Bookmarks, highlights & flash cards

Three pages help you *re-read* what you have collected rather than add to it.

## All bookmarks

**All bookmarks** (sidebar, or **g b**) gathers every bookmarked record from every module into one
list, grouped by module: departments, courses, planner, journal, goals, tasks, resources, notes and
writings. Click one to read it in the right-hand pane; **Edit** takes you to the record in its own
module.

Bookmarks matter beyond this page: they decide what appears on the
[Kanban](tasks-kanban-sprints.md#kanban), what can join a [sprint](tasks-kanban-sprints.md#sprints),
what goes into the [bookmarked TOC](writings-fulltext.md#the-bookmarked-toc) and
[RSS feeds](rss.md), and what your phone receives — see [Sync](../concepts/sync.md).

## Highlights

**Highlights** shows the key points of your records, one record at a time. Every module with a
highlights field feeds it — notes, tasks, journals, planners, goals, writings and resources. Each
card names the record it came from; click the name to open the record.

The page is live: fill in a record's highlights today and it is here today. It is read-only — to
change a highlight, edit the record, or use **Highlight** on its [reading page](records.md#the-reading-page).

- **←** / **→** (or **Page Up** / **Page Down**) move between cards; **Home** and **End** jump to the
  first and last. **↑** / **↓** scroll within a long card.
- The **−** and **+** buttons (**Smaller text** / **Larger text**) change the reading size; your
  choice is remembered.

## Flash cards

**Flash cards** presents passages taken from your longer notes, one at a time, for review. The deck
is shared with the phone app, so a card dealt with in one place is gone in the other.

Three tabs sort the deck: **Review** (cards not yet decided), **Repeat** and **Kept**. For each card,
choose:

| Button | Key | Meaning |
|---|---|---|
| **Reviewed** | **d** | Done with it — it leaves the deck. |
| **Repeat** | **r** | Show it again later (moves to **Repeat**). |
| **Keep** | **k** | Worth keeping (moves to **Kept**). |
| **Unset** | **u** | Put a repeat/kept card back into **Review**. |

Move between cards with **←** / **→**. The note a passage came from is linked above the passage.
Text size is adjustable as on the Highlights page.

!!! note
    The **Review** tab shows a limited number of undecided cards at a time; more appear as you work
    through them. **Repeat** and **Kept** always show everything.

## Videos

**Videos** (in the sidebar's **Library** group) is the phone's Videos page on the web, laid out like
YouTube: every video in repository 1 — the folder Syncthing carries to the phone, so the same list —
newest first, as cards with a frame from the video, its length, and a red bar showing how far you
watched it in this browser. Under each card: the name, its folder, when it was added, its size, and
how many bookmarks it has. Chips above filter to one folder; the search box filters by name.

Click a card (Ctrl-click for a new tab) to watch it: the player, its name and details, **Download**,
and its **Bookmarks** — the same `.tmk` marks the phone player uses, so marks made on either side
show on both. Click a mark to jump there; type what is there (optional) and click **Bookmark here**
(or press Enter) to add one at the current time. **Up next** at the side lists the rest of the same
folder, then the newest elsewhere. A video resumes where you left it. Frames and lengths are made
on the server the first time the list is opened; refresh the page to see the ones that were missing.

## Quran

**Quran** (code `A`, first among the modules) is the Quran exegesis and notes module. It is off
until switched on in [Settings → Modules](settings.md#modules); while it is on, the surah records
(Writings of type *سورة*) and aya records (Notes of type *آية*) it is made from are left out of the
Writings and Notes lists — choosing that type in the list's filter still shows them.

The first time, **Copy them into the Quran module** copies the 114 surahs and their ayat. **⋮ → Copy
again from the records** refreshes the text and exegesis later; notes written on this page are kept.

- On the left, the surahs in **Mushaf** or **Revelation** order, with their number of ayat (a dot:
  some have notes or linked notes). Type a name or number to find one.
- Each aya shows its **vocalized text** (the record's short description), its **exegesis**
  (*التفسير*, the record's description), its **notes** (*ملاحظات*; a long one starts folded) and
  its **linked notes**: notes linked to that aya record the old way (entity code `N` + record id),
  with the notes linked to them in turn, nested. A linked note's title opens it in Notes.
- The pencil writes or edits an aya's notes (Ctrl+Enter saves); the bookmark marks it. The bookmark
  beside the surah's name marks the surah (shown in the list). A surah can have notes of its own,
  above its first aya. Notes added on the phone are appended after a dated line.
- **Search** looks through the ayat (diacritics ignored), their exegesis and notes; a result opens
  the surah at that aya.
- **Export** saves the surah on screen as Markdown, HTML or EPUB, with its exegesis, notes and
  linked notes.

## Excerpt library

**Excerpt library** (sidebar → **Library**) holds samples of your ebooks to read and summarize: 2–3
pages taken at random from the body of an **ebk** resource's book file — past the opening pages,
before the notes and indexes (between about 12% and 85% of the book).

- **Make more** draws that many books not sampled yet, at random, and runs in the background. A
  PDF or DjVu gives the pages themselves (**Pages**) and their text when the file has a real text
  layer (**Text**); an EPUB gives about 2–3 pages' worth of text; MOBI, AZW3 and CHM are converted
  first. A scanned book gives pages only. A book with no ebook file in its folders is noted and not
  tried again.
- Pick a sample, read it, and write a **one-line summary** (Enter saves) and any **details**
  (Ctrl+Enter saves). **Save & next** moves on to the next one.
- **Promote to an Excerpt** makes it a real Excerpt of its book: the summary as its title, the
  details as its description, the text as its notes, the pages ("pp. 120–122") as its chapters,
  and the sample PDF in its folder. Every sample PDF is kept in repository 1 under
  `E/ebk/<book id>/` (named after its pages, e.g. `pp. 120-122.pdf`). **Set aside** hides one; **Another place** takes 2–3 pages from
  elsewhere in the same book; **Open the book** opens the whole file (a PDF at the sample's page).
- The tabs show what is **To summarize**, **Summarized**, **Promoted** and **Set aside**.
