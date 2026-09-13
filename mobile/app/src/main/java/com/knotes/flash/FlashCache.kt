package com.knotes.flash

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-disk copy of what the two galleries last showed, so both work with no server.
 *
 * Same idea as [com.knotes.nibras.NibrasCache], and the same reason: these pages are for reading,
 * and reading happens on a bus. A gallery that answers "the server is offline" is useless exactly
 * when it is most wanted, and neither page needs the network to do its job once the cards are here.
 *
 * **The RAW server JSON is what is stored**, not a re-serialisation of the parsed objects. A cache
 * that writes its own format has a second parser to keep in step with the first, and the day they
 * disagree is the day the offline copy differs from the live one in some small way nobody notices.
 * Storing the response verbatim means the cached list is parsed by exactly the code that parses the
 * live one — [Flashcard.fromJson] and [Highlight.fromJson].
 */
class FlashCache(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "flash_cache").apply {
        if (!exists()) mkdirs()
    }

    private fun file(key: String) = File(dir, "$key.json")

    fun saveRaw(key: String, json: String) {
        runCatching { file(key).writeText(json) }
    }

    /** When [key] was last written, or null if it never has been. */
    fun savedAt(key: String): Long? = file(key).takeIf { it.exists() }?.lastModified()

    fun has(key: String): Boolean = file(key).exists()

    private fun raw(key: String): String? =
        file(key).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    /** The cached cards for one state, or null if that state has never been cached. */
    fun cards(state: String): List<Flashcard>? = raw(cardsKey(state))?.let { text ->
        runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { Flashcard.fromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    fun highlights(): List<Highlight>? = raw(HIGHLIGHTS)?.let { text ->
        runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { Highlight.fromJson(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    fun summary(): FlashSummary? = raw(SUMMARY)?.let { text ->
        runCatching {
            val o = JSONObject(text)
            FlashSummary(
                total = o.optInt("total"),
                remaining = o.optInt("remaining"),
                reviewed = o.optInt("reviewed"),
                repeat = o.optInt("repeat"),
                kept = o.optInt("kept"),
            )
        }.getOrNull()
    }

    /**
     * Rewrite the three card lists after a decision made with no server.
     *
     * Without this the decision survives only until the screen is closed: the outbox would still
     * push it eventually, but the card would be back in the queue every time the page reopened
     * offline, which reads as the app having forgotten. Re-serialised here — the one place a
     * Flashcard is written rather than read — because there is no server response to store.
     */
    fun saveCards(state: String, cards: List<Flashcard>) {
        val arr = JSONArray()
        cards.forEach { arr.put(it.toJson()) }
        saveRaw(cardsKey(state), arr.toString())
    }

    companion object {
        const val HIGHLIGHTS = "highlights"
        const val SUMMARY = "summary"
        fun cardsKey(state: String) = "cards_$state"
    }
}
