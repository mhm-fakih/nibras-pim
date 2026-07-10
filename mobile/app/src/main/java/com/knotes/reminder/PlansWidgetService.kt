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

/** Serves the scrollable rows for [PlansWidget]: upcoming plans, then a "Top priority list" header
 *  and the priority-4 notes — all in one ListView so the whole thing scrolls. */
class PlansWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = PlansFactory(applicationContext)
}

private sealed interface PlanRow
private data class HeaderRow(val text: String) : PlanRow
private data class ItemRow(val lead: CharSequence, val text: CharSequence) : PlanRow

private class PlansFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val recentMs = 2 * 60 * 60 * 1000L
    private var rows: List<PlanRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val now = System.currentTimeMillis()
        val list = mutableListOf<PlanRow>()

        val plans = PlansStore.read(context)
            .filter { it.endMillis > now - recentMs }
            .sortedBy { it.startMillis }
        if (plans.isEmpty()) {
            list.add(ItemRow("", "Nothing upcoming"))
        } else {
            plans.forEach { p ->
                val expired = p.endMillis < now
                list.add(ItemRow(
                    maybeStrike(lead(p.startMillis), expired),
                    maybeStrike(p.summary.ifBlank { "(no summary)" }, expired),
                ))
            }
        }

        list.add(HeaderRow("Top priority list"))
        val topNotes = runCatching {
            NoteRepository(SettingsStore(context)).listNotes().filter { it.priority == 4 }
        }.getOrDefault(emptyList())
        if (topNotes.isEmpty()) {
            list.add(ItemRow("", "No priority-4 notes"))
        } else {
            topNotes.forEach { n -> list.add(ItemRow(n.type ?: "·", preview20(n.content))) }
        }

        rows = list
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews = when (val row = rows[position]) {
        is HeaderRow -> RemoteViews(context.packageName, R.layout.widget_row_header).apply {
            setTextViewText(R.id.row_header, row.text)
        }
        is ItemRow -> RemoteViews(context.packageName, R.layout.widget_row_compact).apply {
            setTextViewText(R.id.row_lead, row.lead)
            setTextViewText(R.id.row_text, row.text)
            setOnClickFillInIntent(R.id.row_root, Intent())
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 2   // header + item
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
