package com.knotes.reminder

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A single upcoming event (Planner "P" or Journal "J") kept for reminders, the widget and panel. */
data class PlanItem(
    val type: String,
    val id: String,
    val summary: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    /** Stable key for an alarm request code, independent of the server id being present.
     *  Namespaced by `type` — Tasks and Plans/Journal entries are separate DB tables, so their
     *  server ids can collide numerically; without the prefix that would silently drop one
     *  side's reminder when both lists are merged for scheduling (see PlansRepository.reschedule). */
    val key: String get() = "$type:" + id.ifBlank { "$startMillis-$summary" }
}

/**
 * Process-wide cache of upcoming events, persisted in SharedPreferences. The homepage panel
 * observes [items]; the widget and reminder receivers read straight from prefs via [read].
 */
object PlansStore {

    private const val PREFS = "knotes_plans"
    private const val K_ITEMS = "items"

    private val _items = MutableStateFlow<List<PlanItem>>(emptyList())
    val items: StateFlow<List<PlanItem>> = _items

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load persisted items into the observable flow (call once at startup / panel mount). */
    fun seed(context: Context) {
        _items.value = read(context)
    }

    fun read(context: Context): List<PlanItem> = decode(prefs(context).getString(K_ITEMS, null))

    fun save(context: Context, list: List<PlanItem>) {
        val sorted = list.sortedBy { it.startMillis }
        _items.value = sorted
        prefs(context).edit().putString(K_ITEMS, encode(sorted)).apply()
    }

    private fun encode(list: List<PlanItem>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("type", it.type)
                    .put("id", it.id)
                    .put("summary", it.summary)
                    .put("start", it.startMillis)
                    .put("end", it.endMillis),
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String?): List<PlanItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlanItem(
                    type = o.optString("type"),
                    id = o.optString("id"),
                    summary = o.optString("summary"),
                    startMillis = o.optLong("start"),
                    endMillis = o.optLong("end"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
