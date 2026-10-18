package com.knotes.nibras

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import com.knotes.R
import com.knotes.NoteFiles

/**
 * The server delivers `description`/`fullText` as HTML (Grails `encodeAsHTML()` with newlines turned
 * into `<br/>`). Render it back to plain text — decode entities (`&amp;`, `&#39;`, …) and turn
 * `<br/>` into real line breaks — so the markup isn't shown verbatim. Done at display time (not at
 * parse) so it also fixes already-cached records without needing a fresh fetch from the server.
 */
fun htmlToPlain(raw: String): String {
    if (raw.isBlank()) return raw
    val decoded = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    // Some records are double-encoded (e.g. "&lt;BR&gt;"), so the first decode yields a *literal*
    // "<BR>" instead of a line break. Turn any such leftover <br> variants into real newlines.
    return decoded.replace(BR_LITERAL, "\n").trim()
}

private val BR_LITERAL = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)

/**
 * A Nibras data type, e.g. code "T" -> label "Tasks". [label] is the English name (kept for any
 * caller that compares or stores it); show [label] (context) / [labelRes] to the user instead.
 */
data class NibrasType(val code: String, val label: String, @StringRes val labelRes: Int = 0) {
    /** The type name in the app's language. */
    fun label(context: Context): String = if (labelRes != 0) context.getString(labelRes) else label
}

/** A note/highlight queued locally, to be appended to a record's field when online. */
data class PendingPush(val ecode: String, val id: String, val text: String)

/**
 * Parses a server `filesList` string into file names. The field is newline-separated
 * "name|size" entries (sometimes "None" or empty). Only split on newlines — a filename may
 * itself contain commas, so splitting on ',' would wrongly break one file into several.
 */
fun parseFilesList(raw: String): List<String> =
    raw.split('\n')
        .map { it.substringBefore('|').trim() }
        .filter { it.isNotBlank() && !it.equals("None", ignoreCase = true) }

/** The data types browsable in the app (from the Ionic app's appTypes list). */
val NIBRAS_TYPES: List<NibrasType> = listOf(
    NibrasType("T", "Tasks", R.string.server_type_tasks),
    NibrasType("P", "Plans", R.string.server_type_plans),
    NibrasType("J", "Journal", R.string.server_type_journal),
    NibrasType("G", "Goals", R.string.server_type_goals),
    NibrasType("N", "Notes", R.string.server_type_notes),
    NibrasType("W", "Writings", R.string.server_type_writings),
    NibrasType("R", "Resources", R.string.server_type_resources),
)

/** The one type that supports full-text sentence highlighting. */
const val RESOURCES_CODE = "R"

/** A single record fetched from the Nibras server (plain DB data; files ignored). */
data class NibrasRecord(
    val type: String,
    /** Entity code used by the server to locate the record (usually equals [type]). */
    val ecode: String,
    val id: String,
    val summary: String,
    val description: String,
    val meta: String,
    val context: String,
    val date: String,
    val textDate: String,
    val color: String,
    val language: String,
    val resourceType: String,
    /** Server-provided sub-type (Plans/Journal grouping), e.g. "act", "smm"; blank when none. */
    val subtype: String,
    /** Start/end datetimes (Journal/Plans calendar entries); blank for other types. */
    val startDate: String,
    val endDate: String,
    /** When the task was completed on the server (Tasks only); blank if still open. */
    val completedOn: String = "",
    /** Full text, present only for Resources records (when the server provides it). */
    val fulltext: String,
    /** File names attached to the record (synced to the phone by Syncthing). */
    val files: List<String>,
    /**
     * Whether the record is flagged on the SERVER. Only meaningful on backend-search results — the
     * exports carry bookmarked records exclusively, so on a synced record it is always true.
     */
    val bookmarked: Boolean = false,
    /** Set on a recurring planner: the series master's id. Blank when the record isn't part of one. */
    val seriesId: String = "",
    /** The slot this occurrence stands for — its original start. Blank when not part of a series. */
    val recurrenceId: String = "",
    /**
     * True for a computed occurrence of a recurring planner that has no record of its own yet. Its
     * [id] is the composite `<seriesId>@<recurrenceId>`, NOT a record id — pushing an edit against
     * it would rewrite the master and so change every occurrence. Materialise it first.
     */
    val virtual: Boolean = false,
) {
    /** Stable key for local read/bookmark tracking. */
    val key: String get() = "$type:$id"

    val isRtl: Boolean get() = NoteFiles.isRtl(summary.ifBlank { description })

    /** A short single-line label for the dense table row. */
    fun line(): String {
        // Summaries are plain text; the description fallback is server HTML, so decode that case.
        val text = if (summary.isNotBlank()) summary else htmlToPlain(description)
        return text.replace("\n", " ").trim()
    }
}
