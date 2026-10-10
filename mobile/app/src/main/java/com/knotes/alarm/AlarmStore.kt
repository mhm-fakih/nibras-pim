package com.knotes.alarm

import android.content.Context
import com.knotes.prayer.Prayer
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The alarms, as JSON in SharedPreferences.
 *
 * Process-wide, like the other stores here, because a widget's RemoteViewsFactory and a broadcast
 * receiver both need to read this without an Activity alive to own it.
 *
 * Every write re-arms the scheduler. That is the invariant worth stating: there is no path that
 * edits an alarm and leaves the armed one stale, because saving IS re-arming.
 */
object AlarmStore {

    private const val PREFS = "knotes_alarms"
    private const val KEY = "alarms"

    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Read from disk into the flow. Safe to call repeatedly; cheap after the first. */
    fun load(context: Context): List<Alarm> {
        if (_alarms.value.isEmpty()) {
            _alarms.value = decode(prefs(context).getString(KEY, null))
        }
        return _alarms.value
    }

    /** Read from disk unconditionally — for a receiver in a process that may hold a stale flow. */
    fun reload(context: Context): List<Alarm> {
        _alarms.value = decode(prefs(context).getString(KEY, null))
        return _alarms.value
    }

    fun save(context: Context, list: List<Alarm>) {
        _alarms.value = list
        prefs(context).edit().putString(KEY, encode(list)).apply()
        AlarmScheduler.rearm(context)
    }

    fun put(context: Context, alarm: Alarm) {
        val list = load(context).toMutableList()
        val i = list.indexOfFirst { it.id == alarm.id }
        if (i >= 0) list[i] = alarm else list.add(alarm)
        save(context, list)
    }

    fun remove(context: Context, id: String) =
        save(context, load(context).filterNot { it.id == id })

    fun newId(): String = UUID.randomUUID().toString()

    // ---- JSON ---------------------------------------------------------------

    private fun encode(list: List<Alarm>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("label", a.label)
                    put("enabled", a.enabled)
                    put("kind", a.kind.name)
                    put("hour", a.hour)
                    put("minute", a.minute)
                    put("days", JSONArray(a.days.map { it.name }))
                    put("durationMin", a.durationMin)
                    a.dueAtMillis?.let { put("dueAtMillis", it) }
                    put("prayer", a.prayer.name)
                    put("offsetMin", a.offsetMin)
                    put("soundUri", a.soundUri)
                    put("repeatCount", a.repeatCount)
                    put("volumePercent", a.volumePercent)
                },
            )
        }
        return arr.toString()
    }

    /**
     * Anything unreadable is skipped rather than throwing. A stored alarm that cannot be parsed —
     * an enum renamed, a field gone — must not take the whole list with it and leave someone with
     * no alarms at all.
     */
    private fun decode(json: String?): List<Alarm> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    val daysArr = o.optJSONArray("days") ?: JSONArray()
                    Alarm(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        label = o.optString("label", ""),
                        enabled = o.optBoolean("enabled", true),
                        kind = runCatching { AlarmKind.valueOf(o.optString("kind")) }
                            .getOrDefault(AlarmKind.CLOCK),
                        hour = o.optInt("hour", 7),
                        minute = o.optInt("minute", 0),
                        days = (0 until daysArr.length()).mapNotNull { j ->
                            runCatching { DayOfWeek.valueOf(daysArr.getString(j)) }.getOrNull()
                        }.toSet(),
                        durationMin = o.optInt("durationMin", 10),
                        dueAtMillis = if (o.has("dueAtMillis")) o.optLong("dueAtMillis") else null,
                        prayer = runCatching { Prayer.valueOf(o.optString("prayer")) }
                            .getOrDefault(Prayer.FAJR),
                        offsetMin = o.optInt("offsetMin", -30),
                        soundUri = o.optString("soundUri", ""),
                        repeatCount = o.optInt("repeatCount", 1).coerceIn(1, 20),
                        volumePercent = o.optInt("volumePercent", 80).coerceIn(0, 100),
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
