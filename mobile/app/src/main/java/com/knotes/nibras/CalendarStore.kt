package com.knotes.nibras

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Locally-added calendar entries not yet confirmed on the server (offline-first outbox + display). */
class CalendarStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("knotes_calendar", Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<List<CalendarEvent>> = _pending.asStateFlow()

    fun add(event: CalendarEvent) {
        _pending.value = _pending.value + event
        save(_pending.value)
    }

    fun remove(event: CalendarEvent) {
        _pending.value = _pending.value - event
        save(_pending.value)
    }

    private fun save(events: List<CalendarEvent>) {
        val arr = JSONArray()
        events.forEach {
            arr.put(
                JSONObject()
                    .put("type", it.type)
                    .put("id", it.id)
                    .put("date", it.date.toString())
                    .put("start", it.start.toString())
                    .put("end", it.end.toString())
                    .put("summary", it.summary),
            )
        }
        prefs.edit().putString(K_PENDING, arr.toString()).apply()
    }

    private fun load(): List<CalendarEvent> {
        val raw = prefs.getString(K_PENDING, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                runCatching {
                    CalendarEvent(
                        type = o.optString("type"),
                        id = o.optString("id"),
                        date = LocalDate.parse(o.getString("date")),
                        start = LocalTime.parse(o.getString("start")),
                        end = LocalTime.parse(o.getString("end")),
                        summary = o.optString("summary"),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val K_PENDING = "pending_calendar"
    }
}
