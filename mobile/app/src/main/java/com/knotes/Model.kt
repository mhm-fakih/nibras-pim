package com.knotes

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A configurable tag used for both types and categories, e.g. code "W" -> name "Work". */
data class Tag(
    val code: String,
    val name: String,
)

/** A note backed by a plain-text file on disk. */
data class Note(
    val file: File,
    /** Raw timestamp token taken from the file name (kept stable across edits). */
    val timestamp: String,
    /** Single type code parsed from the file name, or null. */
    val type: String?,
    /** Priority parsed from the file name, or null for foreign/unmanaged files. */
    val priority: Int?,
    /** Single category code parsed from the file name, or null. */
    val category: String?,
    /** Full file contents. */
    val content: String,
    val lastModified: Long,
    /** True when the body contains Arabic (or other RTL) text. */
    val isRtl: Boolean,
    /** Names of the files in the note's companion folder (attachments), sorted. */
    val fileNames: List<String> = emptyList(),
) {
    fun preview(maxLines: Int): String {
        val text = content.lineSequence().take(maxLines.coerceAtLeast(1)).joinToString("\n").trimEnd()
        return text.ifBlank { content.take(120) }
    }

    fun timeText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))

    /** The `<stem>.d` folder holding the note's attached files, next to the .md (may not exist). */
    fun companionDir(): File = companionDirOf(file)
}

/** The `<stem>.d` companion folder of a note file — same convention as the desktop/backend. */
fun companionDirOf(noteFile: File): File =
    File(noteFile.parentFile, noteFile.name.substringBeforeLast('.') + ".d")

/** Sorted names of the files inside a note's companion folder (empty when there is none). */
fun companionFileNames(noteFile: File): List<String> =
    companionDirOf(noteFile).listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

/**
 * File-name encoding for notes.
 *
 * Layout: `<timestamp>~<type>~p<priority>~<category>.md`  (type / priority / category order)
 * e.g. `2026_06_10-143025~W~p3~A.md`
 *
 * The priority token always carries a `p` prefix, so it can be located unambiguously
 * regardless of the timestamp format; the token before it is the type, the token after
 * it is the category. Empty type/category are allowed (`...~~p3~.md`). Legacy `.txt` notes
 * are still read (and migrate to `.md` when edited).
 */
object NoteFiles {

    // Arabic, Arabic Supplement, Arabic Extended-A, and the presentation forms.
    private val RTL = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val PRIORITY = Regex("^p(\\d+)$")

    fun isRtl(text: String): Boolean = RTL.containsMatchIn(text)

    fun nowToken(format: String): String =
        runCatching { SimpleDateFormat(format, Locale.US).format(Date()) }
            .getOrElse { SimpleDateFormat("yyyy_MM_dd-HHmmss", Locale.US).format(Date()) }

    // Characters illegal in a file name on Android's storage (plus our '~' field separator).
    // Stripping them keeps a tag like "*" from producing an unwritable name (which would crash).
    private val UNSAFE_CODE = Regex("[~/\\\\:*?\"<>|\\x00-\\x1F]")

    fun sanitizeCode(code: String?): String = code?.trim()?.replace(UNSAFE_CODE, "").orEmpty()

    fun buildName(timestamp: String, type: String?, priority: Int, category: String?): String {
        val t = sanitizeCode(type)
        val c = sanitizeCode(category)
        return "$timestamp~$t~p$priority~$c.md"
    }

    data class Parsed(
        val timestamp: String,
        val type: String?,
        val priority: Int?,
        val category: String?,
    )

    fun parse(fileName: String): Parsed {
        // Accept both the current `.md` and legacy `.txt` notes.
        val base = if (fileName.endsWith(".md", ignoreCase = true) ||
            fileName.endsWith(".txt", ignoreCase = true)
        ) fileName.substringBeforeLast('.') else fileName
        val parts = base.split("~")
        val timestamp = parts.firstOrNull() ?: base

        // Locate the priority token (p<number>) among everything after the timestamp.
        var prioIdx = -1
        var priority: Int? = null
        for (i in 1 until parts.size) {
            val m = PRIORITY.matchEntire(parts[i])
            if (m != null) {
                prioIdx = i
                priority = m.groupValues[1].toIntOrNull()
                break
            }
        }

        if (prioIdx < 0) {
            // No recognizable priority (e.g. a foreign .txt file dropped in the directory).
            return Parsed(
                timestamp = timestamp,
                type = parts.getOrNull(1)?.ifBlank { null },
                priority = null,
                category = parts.getOrNull(2)?.ifBlank { null },
            )
        }

        val type = if (prioIdx > 1) parts[1].ifBlank { null } else null
        val category = parts.drop(prioIdx + 1).firstOrNull { it.isNotBlank() }
        return Parsed(timestamp, type, priority, category)
    }
}
