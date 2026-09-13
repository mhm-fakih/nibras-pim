package com.knotes.flash

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Flash-card decisions taken with no server, waiting to be sent.
 *
 * The same offline-first shape as [com.knotes.nibras.RecordOutbox], and an object rather than a
 * class for the same reason: the screen that queues them and the drainer that empties them
 * ([com.knotes.nibras.PushCenter], run again whenever connectivity returns) must be looking at one
 * list, not two copies.
 *
 * **Keyed by card id, so a decision replaces the previous one for that card** rather than stacking.
 * Deciding "repeat" and then "reviewed" on the same card offline should send one state, not two in
 * an order that depends on how the list happened to be iterated — and putting a card back with NEW
 * is a decision like any other, not a cancellation of what is queued.
 */
object FlashOutbox {

    /** A card id and the state it is waiting to be set to. */
    data class Pending(val id: Long, val state: String)

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    val pending: StateFlow<List<Pending>> = _pending.asStateFlow()

    private var seeded = false

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        _pending.value = runCatching {
            val arr = JSONArray(prefs(context).getString(K_PENDING, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optLong("id")
                val state = o.optString("state")
                if (id > 0 && CardState.of(state) != null) Pending(id, state) else null
            }
        }.getOrDefault(emptyList())
    }

    fun queue(context: Context, id: Long, state: String) {
        seed(context)
        _pending.value = _pending.value.filterNot { it.id == id } + Pending(id, state)
        save(context)
    }

    fun remove(context: Context, item: Pending) {
        seed(context)
        _pending.value = _pending.value - item
        save(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        _pending.value.forEach { arr.put(JSONObject().put("id", it.id).put("state", it.state)) }
        prefs(context).edit().putString(K_PENDING, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("knotes_flash_outbox", Context.MODE_PRIVATE)

    private const val K_PENDING = "pending_states"
}
