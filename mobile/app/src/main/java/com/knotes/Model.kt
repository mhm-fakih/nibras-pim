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
    /**
     * When the note was WRITTEN, from its file-name timestamp — not the file's mtime. Retagging a
     * note rewrites and renames the file, which would otherwise drag it to "today" and wreck the
     * calendar strip. Falls back to [lastModified] when the name doesn't parse (a foreign file, or
     * a timestamp in a format the settings no longer use). Same rule as desktop's
     * `Taxonomy.created_on`.
     */
    val createdMillis: Long,
    /** True when the body contains Arabic (or other RTL) text. */
    val isRtl: Boolean,
    /** Names of the files in the note's companion folder (attachments), sorted. */
    val fileNames: List<String> = emptyList(),
) {
    /**
     * The card's body text. Falls back to the FILE NAME when there is nothing to show — a folder,
     * or a file whose contents aren't read because it isn't a `.md`/`.txt` note (see
     * NoteRepository.toPlaceholder). A card with nothing on it tells you a file is there but not
     * which one, which is worse than useless when the whole point of listing it is to go and deal
     * with it.
     */
    fun preview(maxLines: Int): String {
        // Highlight markers stripped, as everywhere a note is SHOWN rather than edited (the desktop
        // does the same) — a card is a plain label with no markup, so leaving the "==" in would put
        // punctuation on screen that means nothing to the reader.
        val shown = com.knotes.util.Highlights.strip(content)
        val text = shown.lineSequence().take(maxLines.coerceAtLeast(1)).joinToString("\n").trimEnd()
        return text.ifBlank { shown.take(120).ifBlank { file.name } }
    }

    /** The body without highlight markers — what searching should look at, so a query can match
     *  across a highlighted clause instead of tripping over its "==". */
    fun searchText(): String = com.knotes.util.Highlights.strip(content)

    fun timeText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastModified))

    /**
     * When the note was WRITTEN, from its file name — not when the file was last touched.
     *
     * For a folder that arrives over Syncthing the two are different things: Syncthing rewrites
     * mtimes on transfer, so [timeText] on a synced note reads as the moment it reached this
     * phone. [createdMillis] already falls back to the file's own time when the name can't be
     * parsed, so this is never worse than [timeText], only sometimes righter.
     */
    fun createdText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(createdMillis))

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
 * Whether anything at all sits in the note's companion folder — the condition that blocks a delete.
 *
 * Deliberately stricter than [companionFileNames]: it walks nested folders too, because the point
 * is not "does the card show attachments" but "would deleting this note strand a file". A folder
 * that exists but is empty doesn't strand anything, so it isn't a blocker (and the delete removes
 * it on the way out).
 */
fun companionHasContent(noteFile: File): Boolean {
    val dir = companionDirOf(noteFile)
    if (!dir.isDirectory) return false
    return dir.walkTopDown().any { it.isFile }
}

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

    private val PRIORITY = Regex("^p(\\d+)$")

    /**
     * Whether [text] reads right-to-left, from its FIRST strong letter.
     *
     * It used to be "contains an Arabic character anywhere", which meant one quoted Arabic word,
     * one Arabic name, or an `'ar` tag laid an entire English title out right-to-left with its
     * punctuation thrown to the wrong end. The first strong letter is what a reader would answer
     * if asked what language a line is written in.
     *
     * Delegates to [com.knotes.util.Bidi] so there is one rule: this answers for a whole string
     * (a title, a file name, a card heading), and `Bidi.lines` answers per line for a body of text
     * that mixes the two. Text with no strong letter at all — digits, punctuation — is left-to-right,
     * which is only ever a tie-break.
     */
    fun isRtl(text: String): Boolean = com.knotes.util.Bidi.isRtlLine(text) ?: false

    fun nowToken(format: String): String =
        runCatching { SimpleDateFormat(format, Locale.US).format(Date()) }
            .getOrElse { SimpleDateFormat("yyyy_MM_dd-HHmmss", Locale.US).format(Date()) }

    /**
     * The instant encoded in a note's file-name [timestamp], or [fallbackMillis] when it can't be
     * read — a foreign file, or a token written in a format the settings no longer use. Mirrors
     * desktop's `notes.parse_created`.
     *
     * The de-dup suffix (`…-2`, added when two notes land in the same second) is stripped first:
     * it is part of the file name, not of the time, and `SimpleDateFormat` would reject it.
     */
    /**
     * When a note file was WRITTEN, from its name — falling back to the file's mtime only when the
     * name carries no usable timestamp.
     *
     * <h4>Why the name, and not the mtime</h4>
     * The mtime says when the bytes last changed, which is a different question and frequently the
     * wrong one. Retagging rewrites and renames the file. Syncthing rewrites it on every device it
     * reaches. Editing one word rewrites it. Any of those would move the note to "today" — and a
     * sync of a few hundred old notes makes every one of them today's, which is exactly what the
     * counters used to report. The name is written once and never changes, so it is the only
     * timestamp that answers "when was this note made".
     *
     * This is the single definition all the counters and the calendar strip go through, so they
     * cannot drift apart again.
     */
    fun createdMillisOf(file: java.io.File, format: String): Long =
        parseCreated(parse(file.name).timestamp, format, file.lastModified())

    fun parseCreated(timestamp: String, format: String, fallbackMillis: Long): Long {
        val token = timestamp.substringBeforeLast('-', timestamp).takeIf {
            // Only treat a trailing "-N" as the de-dup suffix; the format itself contains dashes.
            timestamp.substringAfterLast('-', "").toIntOrNull() != null
        } ?: timestamp
        for (candidate in listOf(token, timestamp)) {
            runCatching {
                return SimpleDateFormat(format, Locale.US).parse(candidate)?.time ?: fallbackMillis
            }
        }
        return fallbackMillis
    }

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
