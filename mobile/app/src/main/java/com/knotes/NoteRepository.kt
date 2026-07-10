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
            isRtl = NoteFiles.isRtl(content),
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
        file.writeText(text)
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
        target.writeText(text)
        return target
    }

    fun delete(note: Note): Boolean = note.file.delete()
}
