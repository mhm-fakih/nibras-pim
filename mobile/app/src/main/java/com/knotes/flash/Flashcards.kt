package com.knotes.flash

import org.json.JSONObject

/** What has been decided about a card. Mirrors the server's `FlashCardState`. */
enum class CardState {
    /** Not looked at yet. Only these are queued, and only a few at a time. */
    NEW,

    /** Done with — retired from the job. */
    REVIEWED,

    /** Come back to it. Listed in full, never capped. */
    REPEAT,

    /** Held onto: the pile to go and edit in the note itself. Listed in full. */
    KEPT,
    ;

    companion object {
        fun of(name: String?): CardState? = entries.firstOrNull { it.name.equals(name, true) }
    }
}

/**
 * One passage, as the server hands it over.
 *
 * The bank lives in the backend's `flash_card` table, NOT in this app. It was bundled as an asset
 * to begin with, which put 21 MB inside the APK, froze the extract at build time, and — worst —
 * kept every decision on one device. A card reviewed on the phone is now gone from the web page
 * too, because both are reading the same rows.
 *
 * [recordRef] is built by the server so the phone and the browser cannot disagree about how a
 * source note is named.
 */
data class Flashcard(
    val id: Long,
    val noteId: Long,
    val recordRef: String,
    val date: String,
    val text: String,
    val rtl: Boolean,
    val topic: String,
) {
    /**
     * The inverse of [fromJson], for the offline cache's decision path.
     *
     * The cache normally stores the server's response verbatim, so this is needed only where there
     * is no response to store: a card moved between lists while offline. **The field names must
     * match the server's `FlashCardDto` exactly** — `writtenOn`, not `date`; `passage`, not `text` —
     * because what this writes is read back by [fromJson], which is the same code that reads a live
     * response. A name that drifts here does not fail loudly; it produces a card that quietly loses
     * its text or its date the next time the app is opened offline.
     */
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("recordRef", recordRef)
        .put("writtenOn", date)
        .put("passage", text)
        .put("rtl", rtl)
        .put("topic", topic)

    companion object {
        fun fromJson(o: JSONObject): Flashcard? = runCatching {
            Flashcard(
                id = o.getLong("id"),
                noteId = o.optLong("noteId"),
                recordRef = o.optString("recordRef").ifBlank { "N" + o.optLong("noteId") },
                date = if (o.isNull("writtenOn")) "" else o.optString("writtenOn"),
                text = o.getString("passage"),
                rtl = o.optBoolean("rtl"),
                topic = o.optString("topic", "o"),
            )
        }.getOrNull()
    }
}

/** How much of the job is left, and how much has been set aside. */
data class FlashSummary(
    val total: Int = 0,
    val remaining: Int = 0,
    val reviewed: Int = 0,
    val repeat: Int = 0,
    val kept: Int = 0,
)

/**
 * One resource's key points, as the server hands them over.
 *
 * Live, not a frozen extract — unlike [Flashcard]. A resource whose highlights are filled in today
 * is in the gallery today, and there is nothing here to decide. The bullets are split server-side.
 */
data class Highlight(
    val id: Long,
    /** The record's module ("notes", "resources", …) — shown so a card says where it came from. */
    val module: String,
    val recordRef: String,
    val title: String,
    val subtitle: String,
    val date: String,
    val bullets: List<String>,
    val rtl: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject): Highlight? = runCatching {
            val arr = o.optJSONArray("bullets")
            val bullets = if (arr == null) emptyList() else
                (0 until arr.length()).map { arr.getString(it) }
            if (bullets.isEmpty()) return null
            Highlight(
                id = o.getLong("id"),
                module = str(o, "module"),
                // No fallback letter: the gallery draws from seven modules, so guessing one here
                // would put the wrong name on the card rather than none.
                recordRef = str(o, "recordRef"),
                title = str(o, "title"),
                subtitle = str(o, "subtitle"),
                date = str(o, "date"),
                bullets = bullets,
                rtl = o.optBoolean("rtl"),
            )
        }.getOrNull()

        /** `optString` returns the literal "null" for a JSON null; this does not. */
        private fun str(o: JSONObject, key: String): String =
            if (o.isNull(key)) "" else o.optString(key)
    }
}
