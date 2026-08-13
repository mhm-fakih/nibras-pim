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
private val AUX_DIR_STEMS = setOf("deleted", "merged", "imported", "trash", "trashed")

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

/** A file missing its final newline shows as a spurious change on every future line ('No newline
 * at end of file') in git diff and similar tools — always write one. */
private fun String.withTrailingNewline(): String = if (endsWith("\n")) this else this + "\n"

/** Reads and writes notes as plain-text files in the configured directory. */
class NoteRepository(private val settings: SettingsStore) {

    private fun dir(): File {
        val d = File(settings.value.notesDir)
        if (!d.exists()) d.mkdirs()
        return d
    }

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

    /** Updates an existing note's body; renames the file if type/priority/category changed. */
    fun update(note: Note, text: String, type: String?, priority: Int, category: String?): File {
        val newName = NoteFiles.buildName(note.timestamp, type, priority, category)
        var target = File(note.file.parentFile, newName)
        if (target.absolutePath != note.file.absolutePath) {
            var counter = 2
            while (target.exists()) {
                target = File(note.file.parentFile, NoteFiles.buildName("${note.timestamp}-$counter", type, priority, category))
                counter++
            }
            note.file.renameTo(target)
        }
        target.writeText(text.withTrailingNewline())
        return target
    }

    fun delete(note: Note): Boolean = note.file.delete()
}
