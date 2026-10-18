# Writings, checkout files & publishing

The web app is good for organising, but for long writing you may prefer your own text editor. Nibras
lets you **check out** a record's text to a file, edit it anywhere, and bring the changes back —
safely, with a word-by-word review. This page also covers the master **table of contents** of your
bookmarked records and the **Static website** generator.

## Writings

**Writings** is the module for longer pieces: essays, articles, chapters. Besides **Summary** and
**Body**, a writing has a **Short description**, **Slug** (a web-friendly name), **Source**,
**Status**, **Type** and **Born on** date. Everything in [Modules & records](records.md) applies.

## Edit full text in your editor

Every record's detail pane has an **Edit full text in your editor** section.

1. Click **Export to file**. The record's long text fields are written to one **checkout file** in
   the record's repository-1 folder, named from the module code and ID (for example `W18.md` for
   writing 18). The app shows the file's location — and, if your administrator enabled it, opens it.
2. Edit the file in any editor and save it.
3. Back in the app, click **Review & sync**. A dialog shows each changed field with additions in
   green and deletions struck through in red.
4. Click **Apply** to write the changes into the record, or **Cancel** to leave it as it is.

Applied changes are saved like any edit, so they appear in the record's **History**.

For resources the checkout includes the **Full text** and **Highlights**; for other modules, the
title or summary, description and notes.

!!! tip "Bookmarking can check out for you"
    In [Settings → On bookmarking](settings.md#on-bookmarking), an administrator can make bookmarking
    a record write its checkout text automatically, and optionally create a link to its folder in a
    **checkout folder**.

## Pending sync

You don't have to open each record to bring edits back. **Import files → Pending sync — edited
record files** lists every record whose checkout file differs from the database, with the
differences shown field by field.

- **Sync** applies one record.
- **Sync all** applies every listed record.
- **Rescan** looks again.

When everything matches you'll see **Nothing to sync — all exported files match the database.**

## The bookmarked TOC

On the same panel, **Bookmarked TOC** checks out **every bookmarked record** — tasks, planners,
goals, journals, notes, writings and resources — and rebuilds a master index file, `TOC.md`, at the
top of repository 1. It is organised department → course → record, ready to open as a project in a
Markdown writing app. Edits you make to the checked-out files come back through **Pending sync**.

!!! note "Nothing is overwritten"
    If a checkout file already exists with *different* content (perhaps unsynced edits), it is never
    replaced: the old copy is kept beside it with a timestamp in its name, and the app lists it as a
    conflict so you can compare and merge by hand.

## Static website

**Static website** (sidebar) publishes the records of a saved search as a self-contained website on
disk, ready to upload to any web host.

1. First create a saved search that selects what to publish — for example a **Search form** or
   **HQL** saved search on Writings that picks records tagged `published` (see
   [Saved searches](search.md#saved-searches)).
2. On the Static website page, set the **Output folder** (a folder on the server; remembered for next
   time).
3. Click **Generate** on the saved search you want to publish.

The site has an index of cards (short description and cover thumbnail) and a full page per record.
Every image — covers, embedded attachments and external pictures — is copied into a `media/` folder,
so the site has no outside dependencies. The result tells you how many pages and media files were
written and where to open the index.
