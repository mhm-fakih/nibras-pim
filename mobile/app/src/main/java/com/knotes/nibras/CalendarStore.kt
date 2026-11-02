package com.knotes.nibras

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Locally-added calendar entries not yet confirmed on the server (offline-first outbox + display).
 *
 * ONE queue per process, however many `CalendarStore(context)` objects exist: the calendar page,
 * the background sync (PushCenter) and the reminders each make one, and each used to hold its own
 * copy in memory. One copy removing an entry it had sent was then undone by another copy's next
 * save, which wrote its stale list back — and the entry was sent again, which is how a timetable
 * entered once became two or three copies of every class on the server. Sending goes through
 * [flush] only, under one lock, for the same reason.
 */
class CalendarStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("knotes_calendar", Context.MODE_PRIVATE)

    private val _pending: MutableStateFlow<List<CalendarEvent>> = synchronized(CalendarStore::class.java) {
        shared ?: MutableStateFlow(load()).also { shared = it }
    }
    val pending: StateFlow<List<CalendarEvent>> = _pending.asStateFlow()

    /**
     * Sends every queued entry, one at a time, and drops each one the server takes. The only place
     * calendar entries are sent from; the lock keeps two callers (the page and the background sync)
     * from sending the same entry at once. Returns how many were sent.
     */
    suspend fun flush(api: NibrasApi, base: String, username: String): Int = flushLock.withLock {
        if (base.isBlank()) return@withLock 0
        var sent = 0
        _pending.value.forEach { event ->
            val ok = runCatching { api.pushCalendar(base, username, listOf(event.toEntry())) }.getOrDefault(false)
            if (ok) {
                remove(event)
                sent++
            }
        }
        sent
    }

    /** Still waiting to be sent (the same entry, by id or local key)? */
    fun isPending(event: CalendarEvent): Boolean = _pending.value.any { same(it, event) }

    fun add(event: CalendarEvent) = addAll(listOf(event))

    fun addAll(events: List<CalendarEvent>) {
        _pending.update { it + events }
        save(_pending.value)
    }

    /**
     * Queue an edit, replacing any pending entry for the SAME entry rather than appending a copy,
     * so repeated edits before the next sync collapse into just the latest one.
     *
     * Identity is the server id when there is one, else the client-generated [CalendarEvent.localKey].
     * The localKey case matters: an entry created offline has a blank id, and matching on id alone
     * treated every edit of it as a brand-new entry — the outbox grew a copy per edit and each copy
     * was pushed as its own create, which is how one edited entry became several server records.
     */
    fun upsert(event: CalendarEvent) {
        _pending.update { list -> list.filterNot { same(it, event) } + event }
        save(_pending.value)
    }

    fun remove(event: CalendarEvent) {
        // By identity, not value equality: the copy handed back after a push may differ from the
        // stored one (an edit landed in between), and a value-equality removal would silently miss
        // it and leave the entry to be pushed again on the next flush.
        _pending.update { list -> list.filterNot { same(it, event) } }
        save(_pending.value)
    }

    /** Same logical entry: server id when both have one, otherwise the stable local key. */
    private fun same(a: CalendarEvent, b: CalendarEvent): Boolean =
        if (a.id.isNotBlank() && b.id.isNotBlank()) a.type == b.type && a.id == b.id
        else a.localKey == b.localKey

    /** The entry type ("J"/"P") last used in the "New entry" dialog — persists across app restarts. */
    var lastType: String
        get() = prefs.getString(K_LAST_TYPE, "J") ?: "J"
        set(value) = prefs.edit().putString(K_LAST_TYPE, value).apply()

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
                    .put("summary", it.summary)
                    .put("description", it.description)
                    .put("localKey", it.localKey)
                    .put("repeatWeeklyUntil", it.repeatWeeklyUntil?.toString() ?: ""),
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
                        description = o.optString("description"),
                        // Entries persisted before localKey existed get a fresh one; they're
                        // identified by server id anyway once synced.
                        localKey = o.optString("localKey").ifBlank { java.util.UUID.randomUUID().toString() },
                        repeatWeeklyUntil = o.optString("repeatWeeklyUntil").takeIf { it.isNotBlank() }
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        @Volatile var shared: MutableStateFlow<List<CalendarEvent>>? = null
        val flushLock = Mutex()
        const val K_PENDING = "pending_calendar"
        const val K_LAST_TYPE = "last_entry_type"
    }
}
