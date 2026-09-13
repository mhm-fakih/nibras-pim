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
import com.knotes.startOfToday
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Serves the rows for every list-style widget in the app: "Upcoming plans", "Top priority" (notes
 * at the top priority, any type) and "Today's tasks" (type T at the top priority, written today).
 *
 * The lists are told apart by the section segment of the service intent's `data` URI
 * ({@code widget://plans/<section>/<widgetId>}) — Android matches a RemoteViewsService adapter by
 * [Intent.filterEquals], which compares `data` and ignores extras, so that segment is what keeps
 * three widgets from silently sharing one adapter and showing identical rows.
 */
class PlansWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PlansFactory(applicationContext, intent.data?.pathSegments?.firstOrNull().orEmpty())
}

private data class ItemRow(val lead: CharSequence, val text: CharSequence)

private class PlansFactory(
    private val context: Context,
    private val section: String,
) : RemoteViewsService.RemoteViewsFactory {

    private val recentMs = 2 * 60 * 60 * 1000L
    private var rows: List<ItemRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = when (section) {
            "priority" -> priorityRows()
            TodayTasksWidget.SECTION -> todayTaskRows()
            TodayJournalWidget.SECTION -> todayJournalRows()
            else -> planRows()
        }
    }

    /** Every note at the top priority, whatever its type. */
    private fun priorityRows(): List<ItemRow> {
        val priority = SettingsStore(context).value.topPriority
        val topNotes = runCatching {
            NoteRepository(SettingsStore(context)).listNotes().filter { it.priority == priority }
        }.getOrDefault(emptyList())
        return if (topNotes.isEmpty()) listOf(ItemRow("", "No priority-$priority notes"))
        else topNotes.map { n -> ItemRow(n.type ?: "·", widgetRowText(n.content)) }
    }

    /**
     * Today's tasks, newest first, led by the time each was written.
     *
     * No empty-row placeholder here: this widget sets a real `setEmptyView`, and returning a fake
     * row would keep the adapter non-empty and stop that view from ever showing.
     */
    private fun todayTaskRows(): List<ItemRow> {
        val priority = SettingsStore(context).value.topPriority
        val tasks = runCatching {
            NoteRepository(SettingsStore(context))
                .listTasksSince(TodayTasksWidget.TASK_TYPE, priority, startOfToday())
        }.getOrDefault(emptyList())
        return tasks.map { n ->
            ItemRow(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.createdMillis)),
                widgetRowText(n.content),
            )
        }
    }

    /**
     * Today's journal entries, newest first, led by the time each was written.
     *
     * Null priority, unlike [todayTaskRows]: every entry of the day belongs on this list. Same
     * absence of a placeholder row — the widget sets a real `setEmptyView`, and a fake row would
     * keep the adapter non-empty and stop that view ever showing.
     */
    private fun todayJournalRows(): List<ItemRow> {
        val entries = runCatching {
            NoteRepository(SettingsStore(context))
                .listTasksSince(TodayJournalWidget.JOURNAL_TYPE, null, startOfToday())
        }.getOrDefault(emptyList())
        return entries.map { n ->
            ItemRow(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(n.createdMillis)),
                widgetRowText(n.content),
            )
        }
    }

    private fun planRows(): List<ItemRow> {
        val now = System.currentTimeMillis()
        val plans = PlansStore.read(context)
            .filter { it.endMillis > now - recentMs }
            .sortedBy { it.startMillis }
        return if (plans.isEmpty()) listOf(ItemRow("", "Nothing upcoming"))
        else plans.map { p ->
            val expired = p.endMillis < now
            ItemRow(
                maybeStrike(lead(p.startMillis), expired),
                maybeStrike(p.summary.ifBlank { "(no summary)" }, expired),
            )
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
