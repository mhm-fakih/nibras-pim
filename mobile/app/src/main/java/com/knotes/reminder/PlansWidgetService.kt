package com.knotes.reminder

import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Serves the rows for [PlansWidget]'s two independent lists: "Upcoming plans" and "Top priority"
 * (priority-4 notes). The two lists are told apart by the section segment of the service intent's
 * `data` URI ({@code widget://plans/<section>/<widgetId>}, set in [PlansWidget.sectionIntent]).
 */
class PlansWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val section = intent.data?.pathSegments?.firstOrNull()
        return PlansFactory(applicationContext, priorityOnly = section == "priority")
    }
}

private data class ItemRow(val lead: CharSequence, val text: CharSequence)

private class PlansFactory(
    private val context: Context,
    private val priorityOnly: Boolean,
) : RemoteViewsService.RemoteViewsFactory {

    private val recentMs = 2 * 60 * 60 * 1000L
    private var rows: List<ItemRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = if (priorityOnly) {
            val topNotes = runCatching {
                NoteRepository(SettingsStore(context)).listNotes()
                    .filter { it.priority == SettingsStore(context).value.topPriority }
            }.getOrDefault(emptyList())
            if (topNotes.isEmpty()) listOf(ItemRow("", "No priority-4 notes"))
            else topNotes.map { n -> ItemRow(n.type ?: "·", preview20(n.content)) }
        } else {
            val now = System.currentTimeMillis()
            val plans = PlansStore.read(context)
                .filter { it.endMillis > now - recentMs }
                .sortedBy { it.startMillis }
            if (plans.isEmpty()) listOf(ItemRow("", "Nothing upcoming"))
            else plans.map { p ->
                val expired = p.endMillis < now
                ItemRow(
                    maybeStrike(lead(p.startMillis), expired),
                    maybeStrike(p.summary.ifBlank { "(no summary)" }, expired),
                )
            }
        }
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_row_compact).apply {
            setTextViewText(R.id.row_lead, rows[position].lead)
            setTextViewText(R.id.row_text, rows[position].text)
            setOnClickFillInIntent(R.id.row_root, Intent())
        }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false

    private fun preview20(content: String): String {
        val s = content.replace("\n", " ").trim()
        return if (s.length > 20) s.take(20) + "…" else s
    }

    private fun maybeStrike(text: String, strike: Boolean): CharSequence =
        if (!strike) text
        else SpannableString(text).apply {
            setSpan(StrikethroughSpan(), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun lead(startMillis: Long): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        cal.timeInMillis = startMillis
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))
        return when (day - today) {
            0 -> time
            1 -> "Tom $time"
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(startMillis)) + " " + time
        }
    }
}
