package com.knotes.nibras

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phone-local Nibras state: which records are read/bookmarked, which resource sentences are
 * highlighted (shown bold), and an offline-first **outbox** of notes/highlights waiting to be
 * synced to the server. Read/bookmark state is never sent anywhere; notes and highlights are
 * stored here first and pushed when the server is reachable.
 */
class NibrasLocalStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("knotes_nibras", Context.MODE_PRIVATE)

    private val _read = MutableStateFlow(loadSet(K_READ))
    val read: StateFlow<Set<String>> = _read.asStateFlow()

    private val _bookmarked = MutableStateFlow(loadSet(K_BOOKMARKED))
    val bookmarked: StateFlow<Set<String>> = _bookmarked.asStateFlow()

    private val _highlighted = MutableStateFlow(loadSet(K_HIGHLIGHTED))
    val highlighted: StateFlow<Set<String>> = _highlighted.asStateFlow()

    private val _completed = MutableStateFlow(loadSet(K_COMPLETED))
    val completed: StateFlow<Set<String>> = _completed.asStateFlow()

    // When each record was marked complete (key -> epoch millis), so a KPI widget can count
    // "completed today". Only in-app completions are timestamped.
    private val _completedAt = MutableStateFlow(loadLongMap(K_COMPLETED_AT))

    // Notes the user added to records, kept for display even after they've synced (the outbox
    // below is separate and is emptied once a note reaches the server). Keyed by record key.
    private val _recordNotes = MutableStateFlow(loadNotesMap())
    val recordNotes: StateFlow<Map<String, List<String>>> = _recordNotes.asStateFlow()

    private val _pendingNotes = MutableStateFlow(loadQueue(K_PENDING_NOTES))
    val pendingNotes: StateFlow<List<PendingPush>> = _pendingNotes.asStateFlow()

    private val _pendingHighlights = MutableStateFlow(loadQueue(K_PENDING_HL))
    val pendingHighlights: StateFlow<List<PendingPush>> = _pendingHighlights.asStateFlow()

    private val _pendingCompletes = MutableStateFlow(loadQueue(K_PENDING_COMPLETE))
    val pendingCompletes: StateFlow<List<PendingPush>> = _pendingCompletes.asStateFlow()

    // ---- read / bookmark ----

    fun isRead(key: String) = key in _read.value
    fun setRead(key: String, value: Boolean) = mutateSet(K_READ, _read, key, value)
    fun toggleBookmark(key: String) = mutateSet(K_BOOKMARKED, _bookmarked, key, key !in _bookmarked.value)

    // ---- highlight display (bold) ----

    /** Stable key for one highlighted sentence: "R:<id>:<sentenceHash>". */
    fun highlightKey(id: String, sentence: String): String =
        "$RESOURCES_CODE:$id:${sentence.trim().hashCode()}"

    fun isHighlighted(key: String) = key in _highlighted.value
    fun markHighlighted(key: String) = mutateSet(K_HIGHLIGHTED, _highlighted, key, true)
    fun unmarkHighlighted(key: String) = mutateSet(K_HIGHLIGHTED, _highlighted, key, false)

    // ---- complete (record-level) ----

    fun isCompleted(key: String) = key in _completed.value
    fun setCompleted(key: String, value: Boolean) {
        mutateSet(K_COMPLETED, _completed, key, value)
        // Track (or clear) the completion time so "completed today" can be counted.
        val next = _completedAt.value.toMutableMap()
        if (value) next[key] = System.currentTimeMillis() else next.remove(key)
        _completedAt.value = next
        saveLongMap(K_COMPLETED_AT, next)
    }

    /** Number of records marked complete since local midnight (in-app completions only). */
    fun completedTodayCount(): Int {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return _completedAt.value.values.count { it >= startOfDay }
    }

    // ---- record notes (kept for display) ----

    fun notesFor(key: String): List<String> = _recordNotes.value[key].orEmpty()

    fun addRecordNote(key: String, text: String) {
        val next = _recordNotes.value.toMutableMap()
        next[key] = next[key].orEmpty() + text
        _recordNotes.value = next
        saveNotesMap(next)
    }

    // ---- outbox (offline-first) ----

    fun queueNote(item: PendingPush) = addToQueue(K_PENDING_NOTES, _pendingNotes, item)
    fun queueHighlight(item: PendingPush) = addToQueue(K_PENDING_HL, _pendingHighlights, item)
    fun queueComplete(item: PendingPush) = addToQueue(K_PENDING_COMPLETE, _pendingCompletes, item)

    fun removeNotes(items: List<PendingPush>) = removeFromQueue(K_PENDING_NOTES, _pendingNotes, items)
    fun removeHighlights(items: List<PendingPush>) = removeFromQueue(K_PENDING_HL, _pendingHighlights, items)
    fun removeCompletes(items: List<PendingPush>) = removeFromQueue(K_PENDING_COMPLETE, _pendingCompletes, items)

    // ---- internals ----

    private fun mutateSet(prefKey: String, flow: MutableStateFlow<Set<String>>, key: String, present: Boolean) {
        val next = flow.value.toMutableSet().apply { if (present) add(key) else remove(key) }
        flow.value = next
        prefs.edit().putStringSet(prefKey, next).apply()
    }

    private fun loadSet(prefKey: String): Set<String> =
        prefs.getStringSet(prefKey, emptySet())?.toSet() ?: emptySet()

    private fun addToQueue(prefKey: String, flow: MutableStateFlow<List<PendingPush>>, item: PendingPush) {
        val next = flow.value + item
        flow.value = next
        saveQueue(prefKey, next)
    }

    private fun removeFromQueue(prefKey: String, flow: MutableStateFlow<List<PendingPush>>, items: List<PendingPush>) {
        if (items.isEmpty()) return
        val next = flow.value.toMutableList().apply { removeAll(items.toSet()) }
        flow.value = next
        saveQueue(prefKey, next)
    }

    private fun saveQueue(prefKey: String, items: List<PendingPush>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("ecode", it.ecode).put("id", it.id).put("text", it.text)) }
        prefs.edit().putString(prefKey, arr.toString()).apply()
    }

    private fun saveNotesMap(map: Map<String, List<String>>) {
        val obj = JSONObject()
        map.forEach { (key, notes) -> obj.put(key, JSONArray(notes)) }
        prefs.edit().putString(K_RECORD_NOTES, obj.toString()).apply()
    }

    private fun loadNotesMap(): Map<String, List<String>> {
        val raw = prefs.getString(K_RECORD_NOTES, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    val arr = obj.getJSONArray(key)
                    put(key, (0 until arr.length()).map { arr.getString(it) })
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveLongMap(prefKey: String, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(prefKey, obj.toString()).apply()
    }

    private fun loadLongMap(prefKey: String): Map<String, Long> {
        val raw = prefs.getString(prefKey, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { put(it, obj.getLong(it)) } }
        }.getOrDefault(emptyMap())
    }

    private fun loadQueue(prefKey: String): List<PendingPush> {
        val raw = prefs.getString(prefKey, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PendingPush(o.optString("ecode"), o.optString("id"), o.optString("text"))
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val K_READ = "read"
        private const val K_BOOKMARKED = "bookmarked"
        private const val K_HIGHLIGHTED = "highlighted"
        private const val K_COMPLETED = "completed"
        private const val K_COMPLETED_AT = "completed_at"
        private const val K_RECORD_NOTES = "record_notes"
        private const val K_PENDING_NOTES = "pending_notes"
        private const val K_PENDING_HL = "pending_highlights"
        private const val K_PENDING_COMPLETE = "pending_completes"
    }
}
