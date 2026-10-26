package com.knotes

import java.io.File

/** True for note files: current `.md` and legacy `.txt`. */
fun isNoteFile(name: String): Boolean =
    name.endsWith(".md", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true)

/**
 * The canonical note-file name: `<timestamp>~<type>~p<priority>~<subject>.md` (type/subject may be
 * empty; an optional `-N` de-dup suffix is allowed on the timestamp). This is the single rule used to
 * *count* notes, shared with the desktop/backend/terminal — only `.md`, only matching names, never
 * recursive. Foreign `.md` files and legacy `.txt` are not counted.
 */
private val NOTE_NAME = Regex("""^\d{4}_\d{2}_\d{2}-\d{6}(-\d+)?~[^~]*~[pP]\d+~[^~]*\.md$""")

fun isFormattedNote(name: String): Boolean = NOTE_NAME.matches(name)

/** A note's attachments folder, named after the note's stem. Same convention as desktop/backend. */
const val COMPANION_SUFFIX = ".d"

/**
 * Housekeeping folders that are never "needs metadata" placeholders. Matched on the bare stem with
 * leading dots stripped and case folded, so `Deleted`, `.deleted` and `TRASH` are all caught —
 * these arrive from several machines through Syncthing and their spelling isn't consistent.
 * Mirrors desktop's `NoteRepository.AUX_DIR_STEMS`.
 */
private val AUX_DIR_STEMS = setOf("deleted", "merged", "imported", "trash", "trashed", "checklists")

/**
 * Extensions whose contents are shown as a note's text. Deliberately just these two: the note
 * format is `.md`, and `.txt` is what the older captures used. Anything else in the folder is a
 * file that happens to live there, not a note.
 */
private val PLAIN_TEXT_EXT = setOf("md", "txt")

/** Whether a file's contents should be read and previewed as note text. */
fun isPlainTextNote(name: String): Boolean = com.knotes.util.FileNames.ext(name) in PLAIN_TEXT_EXT

/**
 * Sidecar files that live in the notes folder but are not notes and never will be — the app's own
 * SQLite database and its write-ahead/shared-memory/journal companions.
 *
 * Matched on the PREFIX, case-insensitively, so `notes.sqlite`, `notes.sqlite-wal`,
 * `notes.sqlite-shm`, `notes.sqlite-journal` and `Notes.SQLite` are all covered by one rule — and
 * so are any future companions SQLite invents, which it does. Treated exactly like a dotfile:
 * invisible to the board, uncounted, never offered as a "needs metadata" item.
 *
 * A real note can never collide with this: notes are `<timestamp>~…~p<n>~….md`, which cannot begin
 * with a letter.
 */
private const val SQLITE_PREFIX = "notes.sqlite"

/** True for files the notes folder holds but which are not, and never will be, notes. */
fun isIgnoredSidecar(name: String): Boolean =
    name.lowercase(java.util.Locale.ROOT).startsWith(SQLITE_PREFIX)

fun isAuxDir(name: String): Boolean = name.trimStart('.').lowercase() in AUX_DIR_STEMS

/**
 * Whether a note FILE NAME is a [type] note at [priority] written on or after [sinceMillis].
 *
 * Deliberately answered from the name alone, with no file read. The name already carries the type,
 * the priority *and* the time it was written, so a widget can find today's tasks with one directory
 * listing — where the obvious `listNotes().filter { … }` would read the full text of every note in
 * the folder to decide it wanted three of them.
 *
 * Written-at comes from the name rather than the mtime for the usual reason: Syncthing rewrites
 * files it has not changed, and retagging renames them, either of which would report a year-old
 * task as today's.
 */
/**
 * Whether a note file name says "this type, written since then" — and, when [priority] is given,
 * "at this priority".
 *
 * **[priority] is nullable, meaning ANY priority.** Today's tasks are the ones at the top priority,
 * because the widget is a do-this-first list; today's journal is every entry, because a day's
 * writing is not ranked. One predicate with an optional filter rather than two that would have to
 * be kept in step about what counts as today.
 */
fun isTaskNameSince(
    name: String,
    type: String,
    priority: Int?,
    timestampFormat: String,
    sinceMillis: Long,
): Boolean {
    if (!isFormattedNote(name)) return false
    val parsed = NoteFiles.parse(name)
    if (priority != null && parsed.priority != priority) return false
    if (!type.equals(parsed.type.orEmpty(), ignoreCase = true)) return false
    return createdFromName(parsed.timestamp, timestampFormat) >= sinceMillis
}

/**
 * When a canonical note name says it was written, or [Long.MIN_VALUE] if it cannot be read.
 *
 * Falls back to the default timestamp format when the configured one does not fit. The two can
 * genuinely disagree: [isFormattedNote] insists on `yyyy_MM_dd-HHmmss` whatever the setting says,
 * so pointing the format at something else would otherwise make every existing note unplaceable in
 * time — and this predicate would quietly report that nothing was written today, ever.
 */
private fun createdFromName(timestamp: String, timestampFormat: String): Long {
    val configured = NoteFiles.parseCreated(timestamp, timestampFormat, Long.MIN_VALUE)
    if (configured != Long.MIN_VALUE) return configured
    return NoteFiles.parseCreated(timestamp, SettingsStore.DEFAULT_FORMAT, Long.MIN_VALUE)
}

/** Midnight this morning, local time — the cut-off for anything counted as "today's". */
fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 0)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/** A file missing its final newline shows as a spurious change on every future line ('No newline
 * at end of file') in git diff and similar tools — always write one. */
private fun String.withTrailingNewline(): String = if (endsWith("\n")) this else this + "\n"

/** Reads and writes notes as plain-text files in the configured directory. */
/**
 * The notes in a folder.
 *
 * [dirOverride] points the repository at a folder other than the configured notes folder — the
 * AI-answers page uses it. When it is set the folder is NOT created if missing: that folder is
 * filled by Syncthing, and conjuring an empty one here would both hide the real problem (it has not
 * synced yet) and hand Syncthing a directory it did not make.
 */
class NoteRepository(
    private val settings: SettingsStore,
    private val dirOverride: (() -> String)? = null,
) {

    private fun dir(): File {
        val override = dirOverride?.invoke()
        if (override != null) return File(override)
        val d = File(settings.value.notesDir)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** True when the folder is actually there — the read-only pages say so instead of showing 0. */
    fun dirExists(): Boolean = dir().isDirectory

    /** The folder this repository reads, for the UI to name. */
    fun dirPath(): String = dir().absolutePath

    /** Note files directly in the dir (non-recursive), matching the canonical `.md` name, oldest first. */
    fun listNotes(): List<Note> {
        val files = (dir().listFiles() ?: emptyArray())
            .filter { it.isFile && isFormattedNote(it.name) }
        return files.map(::toNote).sortedBy { it.lastModified }
    }

    /**
     * How many notes carry [priority], from the FILE NAMES alone — one directory listing and a
     * regex per entry.
     *
     * Deliberately not `listNotes().count { … }`, which is the same answer at a far higher price:
     * that reads every note's full text and lists every note's companion folder, and this runs on
     * the widget-redraw path (see TopPriorityWidget's title count), including the system's own
     * `onUpdate` on the main thread. The priority is encoded in the name, so none of that reading
     * is needed to count.
     */
    fun countByPriority(priority: Int): Int =
        (dir().listFiles() ?: emptyArray()).count {
            it.isFile && isFormattedNote(it.name) && NoteFiles.parse(it.name).priority == priority
        }

    /**
     * Files and folders sitting in the notes directory that aren't canonical notes — surfaced as
     * "needs metadata" placeholders (type/priority/category all null) rather than silently ignored,
     * matching desktop's `NoteRepository.list_unrecognized`.
     *
     * This is not a theoretical case on a phone: the folder is a Syncthing share, so anything
     * another machine drops in lands here, correctly-named or not. Without this the non-standard
     * filter would have nothing to act on and those files would be invisible on the phone.
     *
     * Deliberately kept OUT of [listNotes]: that is the counted set — the header's "N notes", the
     * status notification and the KPI widget all derive from it, and folding foreign files in would
     * silently inflate every one of those numbers.
     *
     * Skipped: dotfiles (Syncthing's own `.stfolder`/`.stversions`, `.knotes.json`), companion
     * `.d/` folders, and the app's housekeeping folders.
     */
    fun listUnrecognized(): List<Note> {
        val d = dir()
        return (d.listFiles() ?: emptyArray())
            .filter { f ->
                !f.name.startsWith(".") &&
                    !isAuxDir(f.name) &&
                    !isIgnoredSidecar(f.name) &&
                    if (f.isDirectory) !f.name.endsWith(COMPANION_SUFFIX) else !isFormattedNote(f.name)
            }
            .map(::toPlaceholder)
            .sortedBy { it.lastModified }
    }

    /**
     * A non-conforming file/folder wrapped as a [Note] with blank tags, so it slots into the
     * existing card rendering and grouping with no changes there.
     *
     * Only a `.md` or `.txt` file has its CONTENT read. Everything else — a directory, an `.xml`,
     * a `.json`, a `.csv` — is listed but previewed blank. It used to read whatever it was handed,
     * which meant a config file Syncthing dropped into the folder appeared as a note with a screen
     * of raw markup in it. "Is this text?" is not the question; a file being decodable as text
     * doesn't make it something you wrote.
     */
    private fun toPlaceholder(file: File): Note {
        val content = if (file.isDirectory || !isPlainTextNote(file.name)) "" else {
            runCatching { file.readText() }.getOrDefault("")
        }
        return Note(
            file = file,
            timestamp = "",
            type = null,
            priority = null,
            category = null,
            content = content,
            lastModified = file.lastModified(),
            createdMillis = file.lastModified(),   // no parseable name: the file's own time is all there is
            isRtl = NoteFiles.isRtl(content),
            fileNames = if (file.isDirectory) {
                (file.listFiles() ?: emptyArray()).filter { it.isFile }.map { it.name }.sorted()
            } else {
                emptyList()
            },
        )
    }

    private fun toNote(file: File): Note {
        val parsed = NoteFiles.parse(file.name)
        val content = runCatching { file.readText() }.getOrDefault("")
        return Note(
            file = file,
            timestamp = parsed.timestamp,
            type = parsed.type,
            priority = parsed.priority,
            category = parsed.category,
            content = content,
            lastModified = file.lastModified(),
            createdMillis = NoteFiles.parseCreated(
                parsed.timestamp, settings.value.timestampFormat, file.lastModified()),
            isRtl = NoteFiles.isRtl(content),
            fileNames = companionFileNames(file),
        )
    }

    /**
     * Notes of [type] at [priority] written on or after [sinceMillis], newest first.
     *
     * Names are filtered before any file is opened (see [isTaskNameSince]), so only the handful
     * that survive are read. Newest first because a task added a moment ago should appear at the
     * top of the widget that added it.
     */
    /** Notes of [type] written since [sinceMillis], newest first. Null [priority] means any. */
    fun listTasksSince(type: String, priority: Int?, sinceMillis: Long): List<Note> =
        (dir().listFiles() ?: emptyArray())
            .filter {
                it.isFile &&
                    isTaskNameSince(it.name, type, priority, settings.value.timestampFormat, sinceMillis)
            }
            .map(::toNote)
            .sortedByDescending { it.createdMillis }

    /** Notes of [type] at any of [priorities], whenever written. Only the matching files are read. */
    fun listByTypeAndPriorities(type: String, priorities: Set<Int>): List<Note> =
        (dir().listFiles() ?: emptyArray())
            .filter {
                it.isFile && isFormattedNote(it.name) &&
                    NoteFiles.parse(it.name).let { p ->
                        p.priority in priorities && type.equals(p.type.orEmpty(), ignoreCase = true)
                    }
            }
            .map(::toNote)

    /** How many notes of [type] sit at [priority], from the file names alone. */
    fun countByTypeAndPriority(type: String, priority: Int): Int =
        (dir().listFiles() ?: emptyArray()).count {
            it.isFile && isFormattedNote(it.name) &&
                NoteFiles.parse(it.name).let { p -> p.priority == priority && type.equals(p.type.orEmpty(), ignoreCase = true) }
        }

    /** How many there are, without reading a single one of them. */
    fun countTasksSince(type: String, priority: Int?, sinceMillis: Long): Int =
        (dir().listFiles() ?: emptyArray())
            .count {
                it.isFile &&
                    isTaskNameSince(it.name, type, priority, settings.value.timestampFormat, sinceMillis)
            }

    fun saveNew(text: String, type: String?, priority: Int, category: String?): File {
        val token = NoteFiles.nowToken(settings.value.timestampFormat)
        var file = File(dir(), NoteFiles.buildName(token, type, priority, category))
        var counter = 2
        while (file.exists()) {
            file = File(dir(), NoteFiles.buildName("$token-$counter", type, priority, category))
            counter++
        }
        file.writeText(text.withTrailingNewline())
        return file
    }

    /**
     * Updates an existing note's body; renames the file if type/priority/category changed, and
     * moves its `<stem>.d` attachments folder along with it (as the desktop does).
     *
     * Returns null, writing nothing, when [note]'s file is gone or the rename fails. Writing anyway
     * is how one note became several: a screen still holding the pre-rename [Note] retagged it again,
     * the rename of the missing file failed silently, and the body went into a brand-new file.
     */
    fun update(note: Note, text: String, type: String?, priority: Int, category: String?): File? =
        updateNoteFile(note, text, type, priority, category)

    /** One note, read from [file] — without listing the whole folder. Null if it isn't a note. */
    fun noteAt(file: File): Note? =
        file.takeIf { it.isFile && isFormattedNote(it.name) }?.let(::toNote)

    /**
     * Delete a note — but only when its companion folder holds nothing.
     *
     * A note's attachments live beside it in `<stem>.d`, and nothing else in the app knows how to
     * find them once the `.md` that names them is gone. So a note with files is not deletable from
     * here at all: empty its folder first, deliberately, where you can see what you're throwing
     * away. An empty (or missing) companion folder strands nothing, and is removed with the note.
     *
     * Returns false when the note was refused or the delete failed, so the caller can say why
     * rather than silently appearing to work.
     */
    fun delete(note: Note): Boolean = deleteNoteFile(note.file)
}

/** [NoteRepository.update] as a function of the note alone, testable like [deleteNoteFile]. */
fun updateNoteFile(note: Note, text: String, type: String?, priority: Int, category: String?): File? {
    if (!note.file.isFile) return null
    val newName = NoteFiles.buildName(note.timestamp, type, priority, category)
    var target = File(note.file.parentFile, newName)
    if (target.absolutePath != note.file.absolutePath) {
        var counter = 2
        while (target.exists()) {
            target = File(note.file.parentFile, NoteFiles.buildName("${note.timestamp}-$counter", type, priority, category))
            counter++
        }
        if (!note.file.renameTo(target)) return null
        moveCompanion(companionDirOf(note.file), companionDirOf(target))
    }
    target.writeText(text.withTrailingNewline())
    return target
}

/**
 * Move a note's attachments folder to its new name. A folder already at the new name — even an
 * empty one, which is how two notes lost their files — gets the files merged in, with a `-N`
 * suffix on a clashing name, as the desktop's _relocate_companion does.
 */
private fun moveCompanion(oldDir: File, newDir: File) {
    if (!oldDir.isDirectory) return
    if (!newDir.exists() && oldDir.renameTo(newDir)) return
    newDir.mkdirs()
    oldDir.listFiles()?.forEach { f ->
        var dest = File(newDir, f.name)
        var n = 2
        while (dest.exists()) {
            dest = File(newDir, f.nameWithoutExtension + "-$n" + (if (f.extension.isEmpty()) "" else "." + f.extension))
            n++
        }
        f.renameTo(dest)
    }
    if (oldDir.listFiles().isNullOrEmpty()) oldDir.delete()
}

/**
 * The delete itself, as a function of the file alone — the repository holds no state it needs, and
 * this way the rule can be tested without an Android [SettingsStore] behind it.
 */
fun deleteNoteFile(noteFile: File): Boolean {
    if (companionHasContent(noteFile)) return false
    if (!noteFile.delete()) return false
    companionDirOf(noteFile).takeIf { it.isDirectory }?.deleteRecursively()
    return true
}
