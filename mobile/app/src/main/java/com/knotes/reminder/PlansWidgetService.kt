package com.knotes.reminder

import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StrikethroughSpan
import android.util.TypedValue
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

/** [key] names the underlying record where a row's tap needs to act on it — the feed's notes. */
private data class ItemRow(
    val lead: CharSequence,
    val text: CharSequence,
    val key: String = "",
)

private class PlansFactory(
    private val context: Context,
    private val section: String,
) : RemoteViewsService.RemoteViewsFactory {

    private val recentMs = 2 * 60 * 60 * 1000L
    private var rows: List<ItemRow> = emptyList()
    /** Read with the rows, so a size change lands on the same refresh as the content. */
    private var feedTextSp = 15

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = when (section) {
            "priority" -> priorityRows()
            TodayTasksWidget.SECTION -> todayTaskRows()
            TodayJournalWidget.SECTION -> todayJournalRows()
            RecentNotesWidget.SECTION -> feedRows()
            else -> planRows()
        }
    }

    /** Every note at the top priority, whatever its type. */
    private fun priorityRows(): List<ItemRow> {
        val priority = SettingsStore(context).value.topPriority
        val topNotes = runCatching {
            NoteRepository(SettingsStore(context)).listNotes().filter { it.priority == priority }
        }.getOrDefault(emptyList())
        return if (topNotes.isEmpty()) listOf(ItemRow("", context.getString(R.string.widgets_no_priority_notes, priority)))
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

    /**
     * The recent-notes feed: oldest first, so the newest sits at the bottom like the last message
     * in a channel.
     *
     * A note longer than the configured word count is folded and gets a "Read more"; the reader
     * unfolds one by tapping it. Folding by WORDS rather than by lines because a line depends on
     * the widget's width and the phone's font scale, and "Read more" should appear on the same
     * notes whatever those happen to be.
     */
    private fun feedRows(): List<ItemRow> {
        val settings = SettingsStore(context)
        val days = settings.value.feedDays.coerceIn(1, 60)
        val cap = settings.value.feedWords.coerceIn(10, 400)
        feedTextSp = settings.value.feedTextSp.coerceIn(10, 28)
        val since = RecentNotesWidget.sinceMillis(days)

        val notes = runCatching {
            NoteRepository(settings).listNotes()
                .filter { it.createdMillis >= since }
                .sortedBy { it.createdMillis }
        }.getOrDefault(emptyList())

        val stamp = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
        return notes.map { n ->
            val expanded = FeedExpanded.has(context, n.file.name)
            val meta = listOfNotNull(
                stamp.format(Date(n.createdMillis)),
                n.type?.takeIf { it.isNotBlank() },
                n.priority?.let { "p$it" },
            ).joinToString(" · ")
            ItemRow(meta, foldedBody(n.content, cap, expanded), n.file.name)
        }
    }

    /** The note, or its first [cap] words with a "Read more" after them. */
    private fun foldedBody(content: String, cap: Int, expanded: Boolean): String {
        val text = content.trim()
        if (expanded) return text
        val words = text.split(Regex("\\s+"))
        if (words.size <= cap) return text
        return words.take(cap).joinToString(" ") + "…  " + context.getString(R.string.widgets_read_more)
    }

    private fun planRows(): List<ItemRow> {
        val now = System.currentTimeMillis()
        val plans = PlansStore.read(context)
            .filter { it.endMillis > now - recentMs }
            .sortedBy { it.startMillis }
        return if (plans.isEmpty()) listOf(ItemRow("", context.getString(R.string.widgets_nothing_upcoming)))
        else plans.map { p ->
            val expired = p.endMillis < now
            ItemRow(
                maybeStrike(lead(p.startMillis), expired),
                maybeStrike(p.summary.ifBlank { context.getString(R.string.widgets_no_summary) }, expired),
            )
        }
    }

    override fun onDestroy() { rows = emptyList() }
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        // The launcher can ask for a position from the count before the last refresh shrank the
        // list. Throwing here kills the whole process — once, at the Fajr unlock, the athan with it.
        val row = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_row_compact)
        // The feed reads down and wraps; every other section is a one-line working list.
        if (section == RecentNotesWidget.SECTION) {
            return RemoteViews(context.packageName, R.layout.widget_feed_row).apply {
                setTextViewText(R.id.feed_meta, row.lead)
                setTextViewText(R.id.feed_body, row.text)
                setTextViewTextSize(R.id.feed_body, TypedValue.COMPLEX_UNIT_SP, feedTextSp.toFloat())
                setTextViewTextSize(R.id.feed_meta, TypedValue.COMPLEX_UNIT_SP, (feedTextSp - 2).toFloat())
                // Names the note, so the tap can unfold THIS one. Without the extra, every row
                // would toggle whatever the template happened to carry.
                setOnClickFillInIntent(
                    R.id.feed_row_root,
                    Intent().putExtra(RecentNotesWidget.EXTRA_NOTE, row.key),
                )
            }
        }
        return RemoteViews(context.packageName, R.layout.widget_row_compact).apply {
            setTextViewText(R.id.row_lead, row.lead)
            setTextViewText(R.id.row_text, row.text)
            setOnClickFillInIntent(R.id.row_root, Intent())
        }
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
            1 -> context.getString(R.string.widgets_tomorrow_time, time)
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(startMillis)) + " " + time
        }
    }
}
