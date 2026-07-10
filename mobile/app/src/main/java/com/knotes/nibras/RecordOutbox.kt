package com.knotes.nibras

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A phone-authored record waiting to be created on the backend (`mobilePushCreate`).
 * [key] is the local identity — a note's file name — so re-queuing the same note replaces
 * its pending entry instead of duplicating it, and a successful push can be remembered.
 */
data class PendingCreate(
    val key: String,
    /** Module type code (T/J/P/N/G/W/R) — the same vocabulary as note file names. */
    val ecode: String,
    val summary: String,
    val description: String,
    val priority: Int?,
    /** Single-letter Department code, "" for none. */
    val department: String,
)

/**
 * Offline-first outbox for record creation, mirroring [CalendarStore]'s persist-first pattern but
 * process-wide (an object, like PlansStore) so the note list, the drainer, and the Application-level
 * connectivity callback all observe the same state. Also remembers which notes were already pushed
 * (by file name) so the UI can show a "synced" state and avoid accidental duplicates.
 */
object RecordOutbox {

    private val _pending = MutableStateFlow<List<PendingCreate>>(emptyList())
    val pending: StateFlow<List<PendingCreate>> = _pending.asStateFlow()

    private val _pushed = MutableStateFlow<Set<String>>(emptySet())
    val pushed: StateFlow<Set<String>> = _pushed.asStateFlow()

    private var seeded = false

    /** Load persisted state once per process; safe to call from anywhere. */
    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        val prefs = prefs(context)
        _pending.value = runCatching {
            val arr = JSONArray(prefs.getString(K_PENDING, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                PendingCreate(
                    key = o.optString("key"),
                    ecode = o.optString("ecode"),
                    summary = o.optString("summary"),
                    description = o.optString("description"),
                    priority = if (o.has("priority")) o.optInt("priority") else null,
                    department = o.optString("department"),
                ).takeIf { it.key.isNotBlank() && it.ecode.isNotBlank() }
            }
        }.getOrDefault(emptyList())
        _pushed.value = runCatching {
            val arr = JSONArray(prefs.getString(K_PUSHED, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    /** Queue (or re-queue, replacing by [PendingCreate.key]) a record for creation. */
    fun queue(context: Context, item: PendingCreate) {
        seed(context)
        _pending.value = _pending.value.filterNot { it.key == item.key } + item
        // A re-push of an edited note is a deliberate act — forget the old "pushed" mark.
        _pushed.value = _pushed.value - item.key
        save(context)
    }

    fun remove(context: Context, item: PendingCreate) {
        seed(context)
        _pending.value = _pending.value - item
        save(context)
    }

    fun markPushed(context: Context, key: String) {
        seed(context)
        // Cap the remembered set so prefs can't grow without bound.
        _pushed.value = (_pushed.value + key).let { s ->
            if (s.size > MAX_PUSHED) s.drop(s.size - MAX_PUSHED).toSet() else s
        }
        save(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        _pending.value.forEach {
            val o = JSONObject()
                .put("key", it.key)
                .put("ecode", it.ecode)
                .put("summary", it.summary)
                .put("description", it.description)
                .put("department", it.department)
            it.priority?.let { p -> o.put("priority", p) }
            arr.put(o)
        }
        val pushedArr = JSONArray().also { a -> _pushed.value.forEach(a::put) }
        prefs(context).edit()
            .putString(K_PENDING, arr.toString())
            .putString(K_PUSHED, pushedArr.toString())
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("knotes_outbox", Context.MODE_PRIVATE)

    private const val K_PENDING = "pending_creates"
    private const val K_PUSHED = "pushed_keys"
    private const val MAX_PUSHED = 500
}
