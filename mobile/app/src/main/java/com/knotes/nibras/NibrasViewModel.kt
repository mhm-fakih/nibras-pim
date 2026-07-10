package com.knotes.nibras

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

enum class ConnState { Unknown, Connecting, Online, Offline }

class NibrasViewModel(app: Application) : AndroidViewModel(app) {

    private val api = NibrasApi()
    val store = NibrasLocalStore(app)
    private val cache = NibrasCache(app)

    /** Type currently shown on the type screen (so background syncs can update it live). */
    private var currentCode: String? = null

    private val _conn = MutableStateFlow(ConnState.Unknown)
    val conn: StateFlow<ConnState> = _conn.asStateFlow()

    private val _counts = MutableStateFlow(NIBRAS_TYPES.associate { it.code to (null as Int?) })
    val counts: StateFlow<Map<String, Int?>> = _counts.asStateFlow()

    private val _records = MutableStateFlow<List<NibrasRecord>>(emptyList())
    val records: StateFlow<List<NibrasRecord>> = _records.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** True while any network sync (fetch or push) is in flight; drives the status bar spinner. */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private var syncOps = 0

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    private fun beginSync() { syncOps++; _syncing.value = true }
    private fun endSync() { syncOps = (syncOps - 1).coerceAtLeast(0); if (syncOps == 0) _syncing.value = false }

    /** Seed counts from cache (works offline), heartbeat, then sync every type when online. */
    fun connect(base: String, username: String) {
        _counts.value = NIBRAS_TYPES.associate { it.code to cache.count(it.code) }
        viewModelScope.launch {
            beginSync()
            try {
                _conn.value = ConnState.Connecting
                val online = api.heartbeat(base)
                _conn.value = if (online) ConnState.Online else ConnState.Offline
                if (!online) return@launch
                flushOutbox(base)
                val jobs = NIBRAS_TYPES.map { type ->
                    launch {
                        val recs = runCatching { api.fetchType(base, type.code, username) }.getOrNull()
                        if (recs != null) {
                            cache.save(type.code, recs)
                            _counts.value = _counts.value.toMutableMap().apply { put(type.code, recs.size) }
                            if (currentCode == type.code) _records.value = recs
                        }
                    }
                }
                jobs.joinAll()
            } finally {
                endSync()
            }
        }
    }

    private suspend fun flushOutbox(base: String) {
        val notes = store.pendingNotes.value
        if (notes.isNotEmpty() && runCatching { api.pushNotes(base, notes) }.getOrDefault(false)) {
            store.removeNotes(notes)
        }
        val highlights = store.pendingHighlights.value
        if (highlights.isNotEmpty() && runCatching { api.pushHighlights(base, highlights) }.getOrDefault(false)) {
            store.removeHighlights(highlights)
        }
    }

    fun openType(base: String, code: String, username: String) {
        currentCode = code
        _records.value = cache.load(code)
        viewModelScope.launch {
            _loading.value = true
            beginSync()
            try {
                // Push any queued notes/highlights as part of every fetch.
                flushOutbox(base)
                val result = runCatching { api.fetchType(base, code, username) }
                result.onSuccess { recs ->
                    _records.value = recs
                    cache.save(code, recs)
                    _counts.value = _counts.value.toMutableMap().apply { put(code, recs.size) }
                    // Refresh the KPI widget when the Tasks cache changes ("new tasks today").
                    if (code == "T") com.knotes.reminder.KpiWidget.refresh(getApplication())
                }
                result.onFailure {
                    if (_records.value.isEmpty()) _message.value = "Offline — no cached records for this type yet"
                }
            } finally {
                _loading.value = false
                endSync()
            }
        }
    }

    fun toggleBookmark(record: NibrasRecord) {
        store.toggleBookmark(record.key)
        com.knotes.reminder.BookmarksWidget.refresh(getApplication())
    }

    fun setRead(record: NibrasRecord, value: Boolean) = store.setRead(record.key, value)

    /** Marks (or unmarks) a record complete: pushes to the server's mobilePushComplete and tracks
     *  it locally so the row shows struck-through. Unchecking clears it locally (and from the
     *  outbox if it hasn't pushed yet); there's no server "un-complete" endpoint. */
    fun toggleComplete(base: String, record: NibrasRecord) {
        val key = record.key
        if (store.isCompleted(key)) {
            store.setCompleted(key, false)
            com.knotes.reminder.KpiWidget.refresh(getApplication())
            val stale = store.pendingCompletes.value.filter { it.id == record.id && it.ecode == record.ecode }
            if (stale.isNotEmpty()) store.removeCompletes(stale)
            return
        }
        store.setCompleted(key, true)
        com.knotes.reminder.KpiWidget.refresh(getApplication())
        store.queueComplete(PendingPush(record.ecode, record.id, ""))
        viewModelScope.launch {
            beginSync()
            try {
                val pending = store.pendingCompletes.value
                val synced = runCatching { api.pushCompletes(base, pending) }.getOrDefault(false)
                if (synced) {
                    store.removeCompletes(pending)
                    _message.value = "Marked complete"
                } else {
                    _message.value = "Saved — will mark complete when online"
                }
            } finally {
                endSync()
            }
        }
    }

    /** Saves a note: shows it on the record right away (kept locally), confirms with a message,
     *  and queues it to sync. The display copy survives even after the outbox is emptied. */
    fun addNote(base: String, record: NibrasRecord, note: String) {
        val text = note.trim()
        if (text.isEmpty()) return
        store.addRecordNote(record.key, text)
        store.queueNote(PendingPush(record.ecode, record.id, text))
        _message.value = "Note saved"
        viewModelScope.launch {
            beginSync()
            try {
                val pending = store.pendingNotes.value
                val synced = runCatching { api.pushNotes(base, pending) }.getOrDefault(false)
                if (synced) store.removeNotes(pending)
            } finally {
                endSync()
            }
        }
    }

    /** Tap a sentence: highlight it if it isn't, or remove the highlight if it already is. */
    fun toggleHighlight(base: String, record: NibrasRecord, sentence: String) {
        val clean = sentence.trim()
        if (clean.isEmpty()) return
        val key = store.highlightKey(record.id, clean)
        if (store.isHighlighted(key)) removeHighlight(record, clean, key) else addHighlight(base, record, clean)
    }

    /** Removes a highlight locally and drops it from the outbox if it hasn't been pushed yet.
     *  (There's no server "un-highlight" endpoint, so an already-synced highlight is cleared
     *  only on this device.) */
    private fun removeHighlight(record: NibrasRecord, clean: String, key: String) {
        store.unmarkHighlighted(key)
        val stale = store.pendingHighlights.value.filter { it.id == record.id && it.text == clean }
        if (stale.isNotEmpty()) store.removeHighlights(stale)
    }

    /** Marks a sentence highlighted (bold) locally, saves it to the outbox, then tries to sync. */
    fun addHighlight(base: String, record: NibrasRecord, sentence: String) {
        val clean = sentence.trim()
        if (clean.isEmpty()) return
        val key = store.highlightKey(record.id, clean)
        if (store.isHighlighted(key)) return
        store.markHighlighted(key)
        store.queueHighlight(PendingPush(record.ecode, record.id, clean))
        viewModelScope.launch {
            beginSync()
            try {
                val pending = store.pendingHighlights.value
                val synced = runCatching { api.pushHighlights(base, pending) }.getOrDefault(false)
                if (synced) store.removeHighlights(pending)
            } finally {
                endSync()
            }
        }
    }
}
