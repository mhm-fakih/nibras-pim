package com.knotes.reminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cached Tasks that have a due date (endDate) and aren't completed yet — used only to re-arm the
 * "remind before it's due" alarms (see [PlansRepository.reschedule]). Deliberately separate from
 * [PlansStore]: that one drives the Upcoming-plans widget/panel (calendar events), and mixing
 * Tasks into it would change what that widget means. This store has no UI of its own.
 */
object TasksReminderStore {

    private const val PREFS = "knotes_task_reminders"
    private const val K_ITEMS = "items"

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): List<PlanItem> = decode(prefs(context).getString(K_ITEMS, null))

    fun save(context: Context, list: List<PlanItem>) {
        prefs(context).edit().putString(K_ITEMS, encode(list)).apply()
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
