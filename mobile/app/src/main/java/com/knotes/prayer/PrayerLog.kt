package com.knotes.prayer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One athan (or recital) as it actually happened: when it was due, when the alarm really woke the
 * app, and whether a sound started. The difference between the first two is the delay the phone
 * added — Doze, app freezing, a busy CPU — which is otherwise invisible.
 */
data class PrayerFiring(
    val kind: PrayerEventKind,
    val prayer: Prayer,
    /** When it should have fired (the athan's time, or the recital's start). */
    val dueMillis: Long,
    /** When the alarm actually reached the app. */
    val firedMillis: Long,
    /** When the sound started playing, if it did. */
    val soundMillis: Long?,
    /** What was played (a recital's file name), when there is something to say. */
    val note: String? = null,
) {
    val delayMillis: Long get() = firedMillis - dueMillis
}

/** The last [MAX] firings, newest first, kept in the app's preferences. */
object PrayerLog {
    private const val PREFS = "knotes_prayer_log"
    private const val KEY = "firings"
    private const val MAX = 100

    private val _entries = MutableStateFlow<List<PrayerFiring>>(emptyList())
    val entries: StateFlow<List<PrayerFiring>> = _entries.asStateFlow()

    fun load(context: Context): List<PrayerFiring> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        val list = runCatching {
            val a = JSONArray(raw ?: "[]")
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val kind = PrayerEventKind.entries.firstOrNull { it.name == o.optString("k") } ?: return@mapNotNull null
                val prayer = Prayer.of(o.optString("p")) ?: return@mapNotNull null
                PrayerFiring(
                    kind, prayer, o.getLong("due"), o.getLong("fired"),
                    if (o.has("sound")) o.getLong("sound") else null,
                    o.optString("note").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
        _entries.value = list
        return list
    }

    @Synchronized
    fun add(context: Context, firing: PrayerFiring) {
        val list = (listOf(firing) + load(context)).take(MAX)
        save(context, list)
    }

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, list: List<PrayerFiring>) {
        val a = JSONArray()
        list.forEach { f ->
            a.put(JSONObject().apply {
                put("k", f.kind.name)
                put("p", f.prayer.name)
                put("due", f.dueMillis)
                put("fired", f.firedMillis)
                f.soundMillis?.let { put("sound", it) }
                f.note?.let { put("note", it) }
            })
        }
        // commit, not apply: the receiver's process may be frozen the moment it returns.
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).commit()
        _entries.value = list
    }
}
