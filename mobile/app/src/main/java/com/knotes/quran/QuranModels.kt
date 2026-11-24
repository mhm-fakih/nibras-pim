package com.knotes.quran

import org.json.JSONArray
import org.json.JSONObject

/** One surah in the list: its number, name, size, and what is marked or noted in it. */
data class QuranSurahRow(
    val number: Int,
    val name: String,
    val revelationOrder: Int?,
    val ayaCount: Int,
    val withNotes: Int,
    val linked: Int,
    val bookmarkedAyat: Int,
    val bookmarked: Boolean,
)

data class QuranOverview(val enabled: Boolean, val surahs: List<QuranSurahRow>)

/** A note linked to an aya the old way (entity code N + record id), with those linked to it. */
data class QuranLinkedNote(
    val id: Long,
    val title: String,
    val text: String,
    val notes: String,
    val type: String,
    val children: List<QuranLinkedNote>,
)

data class QuranAya(
    val number: Int,
    val text: String,
    /** The aya fully vocalized (the source record's short description). */
    val vocalized: String,
    val exegesis: String,
    val notes: String,
    val bookmarked: Boolean,
    val linked: List<QuranLinkedNote>,
    /** Notes written on this phone and not yet on the server, shown after [notes]. */
    val pendingNotes: List<String> = emptyList(),
)

data class QuranSurah(
    val number: Int,
    val name: String,
    val revelationOrder: Int?,
    val ayaCount: Int,
    val notes: String,
    val bookmarked: Boolean,
    val ayat: List<QuranAya>,
)

/**
 * A change made on the phone, waiting for the server: a surah or aya bookmark set or cleared, or a
 * note to append to an aya's notes. [ref] is unique per change; the server applies a note with a
 * ref it has seen only once, so a resend after a lost reply does not append it twice.
 */
data class QuranOp(
    val ref: String,
    val kind: String,
    val surah: Int,
    val aya: Int = 0,
    val on: Boolean = false,
    val text: String = "",
    /** When it was made, ISO local date-time — the date line the server writes above the note. */
    val at: String = "",
    /** Why the server refused it (no such aya any more), else null. */
    val error: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("ref", ref).put("kind", kind).put("surah", surah)
        .put("aya", aya).put("on", on).put("text", text).put("at", at).put("error", error ?: JSONObject.NULL)

    companion object {
        const val SURAH_BOOKMARK = "surah_bookmark"
        const val AYA_BOOKMARK = "aya_bookmark"
        const val NOTE = "note"

        fun fromJson(o: JSONObject) = QuranOp(
            ref = o.optString("ref"),
            kind = o.optString("kind"),
            surah = o.optInt("surah"),
            aya = o.optInt("aya"),
            on = o.optBoolean("on"),
            text = o.optString("text"),
            at = o.optString("at"),
            error = o.optString("error").takeIf { !o.isNull("error") && it.isNotBlank() },
        )

        fun listFromJson(raw: String?): List<QuranOp> = runCatching {
            val a = JSONArray(raw ?: "[]")
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<QuranOp>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

internal fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

object QuranJson {

    fun overview(raw: String): QuranOverview {
        val o = JSONObject(raw)
        val list = o.optJSONArray("list") ?: JSONArray()
        return QuranOverview(
            enabled = o.optBoolean("enabled"),
            surahs = (0 until list.length()).map { i ->
                val s = list.getJSONObject(i)
                QuranSurahRow(
                    number = s.optInt("number"),
                    name = s.str("name"),
                    revelationOrder = if (s.isNull("revelationOrder")) null else s.optInt("revelationOrder"),
                    ayaCount = s.optInt("ayaCount"),
                    withNotes = s.optInt("withNotes"),
                    linked = s.optInt("linked"),
                    bookmarkedAyat = s.optInt("bookmarked"),
                    bookmarked = s.optBoolean("surahBookmarked"),
                )
            },
        )
    }

    fun surah(raw: String): QuranSurah {
        val o = JSONObject(raw)
        val ayat = o.optJSONArray("ayat") ?: JSONArray()
        return QuranSurah(
            number = o.optInt("number"),
            name = o.str("name"),
            revelationOrder = if (o.isNull("revelationOrder")) null else o.optInt("revelationOrder"),
            ayaCount = o.optInt("ayaCount"),
            notes = o.str("notes"),
            bookmarked = o.optBoolean("bookmarked"),
            ayat = (0 until ayat.length()).map { i ->
                val a = ayat.getJSONObject(i)
                QuranAya(
                    number = a.optInt("number"),
                    text = a.str("text"),
                    vocalized = a.str("vocalized"),
                    exegesis = a.str("exegesis"),
                    notes = a.str("notes"),
                    bookmarked = a.optBoolean("bookmarked"),
                    linked = linked(a.optJSONArray("linked")),
                )
            },
        )
    }

    private fun linked(a: JSONArray?): List<QuranLinkedNote> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { i ->
            val n = a.getJSONObject(i)
            QuranLinkedNote(
                id = n.optLong("id"),
                title = n.str("title"),
                text = n.str("text"),
                notes = n.str("notes"),
                type = n.str("type"),
                children = linked(n.optJSONArray("children")),
            )
        }
    }
}
