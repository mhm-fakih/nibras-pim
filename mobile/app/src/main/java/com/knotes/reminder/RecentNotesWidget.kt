package com.knotes.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore

/**
 * A read-only feed of the notes written recently — the last few days as one column you scroll down.
 *
 * Deliberately not another list widget. The others are working lists: they filter, they count, they
 * take actions. This one is for READING what you have been writing, the way a channel is read —
 * oldest first so the newest is at the bottom where you left off, minimal metadata in small type,
 * one scrollbar, no filters and no buttons.
 *
 * Long notes are folded after [SettingsStore] `feedWords` words with a "Read more"; tapping a note
 * unfolds it in place and tapping again folds it back. That single tap is the only interaction the
 * widget offers, and it is why the rows carry no other controls.
 */
class RecentNotesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.feed_list)
        }
    }

    companion object {
        /** The section segment [PlansWidgetService] switches on for these rows. */
        const val SECTION = "recent_notes"

        /** Broadcast action for unfolding/folding one note. */
        const val ACTION_TOGGLE = "com.knotes.feed.TOGGLE"
        const val EXTRA_NOTE = "note"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, RecentNotesWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, build(context, id))
                // The rows come from a RemoteViewsFactory, which updateAppWidget does not re-run.
                manager.notifyAppWidgetViewDataChanged(id, R.id.feed_list)
            }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_recent_notes)
            val settings = SettingsStore(context)
            val days = settings.value.feedDays.coerceIn(1, 60)

            val count = runCatching {
                NoteRepository(settings).listNotes().count { it.createdMillis >= sinceMillis(days) }
            }.getOrDefault(0)

            views.setTextViewText(R.id.feed_title, if (days == 1) "Today's notes" else "Last $days days")
            WidgetCounts.apply(views, WidgetCounts.plain(count), R.id.feed_badge)
            views.setTextViewText(R.id.feed_empty, "Nothing written in the last $days days.")
            views.setRemoteAdapter(R.id.feed_list, sectionIntent(context, widgetId))
            views.setEmptyView(R.id.feed_list, R.id.feed_empty)

            // Newest is at the BOTTOM, so open the feed there — that is where reading resumes.
            // Without this a feed of forty notes opens on the oldest one, which is backwards.
            if (count > 0) views.setScrollPosition(R.id.feed_list, count - 1)

            // Tapping a row toggles its fold. A broadcast, not an activity: the widget should
            // unfold in place rather than throwing you into the app, which is the whole point of a
            // feed you can read from the home screen.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.feed_list,
                PendingIntent.getBroadcast(
                    context, SECTION.hashCode(),
                    Intent(context, WidgetTapReceiver::class.java).setAction(ACTION_TOGGLE),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.feed_title,
                WidgetTapReceiver.pendingIntent(context, "feed_refresh".hashCode(), sync = false),
            )
            return views
        }

        /** Midnight, [days] days ago — whole days, so "3 days" is three calendar days and not 72h. */
        fun sinceMillis(days: Int): Long = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis

        private fun sectionIntent(context: Context, widgetId: Int): Intent =
            Intent(context, PlansWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("widget://plans/$SECTION/$widgetId")
            }
    }
}

/**
 * Which notes the reader has unfolded.
 *
 * In prefs rather than in memory because the rows are rebuilt by a RemoteViewsFactory in a service
 * that the system is free to tear down between taps — an in-memory set would forget what was open
 * the moment the widget was scrolled away from.
 */
object FeedExpanded {

    fun toggle(context: Context, note: String) {
        val open = all(context).toMutableSet()
        if (!open.remove(note)) open.add(note)
        prefs(context).edit().putStringSet(KEY, open).apply()
    }

    fun has(context: Context, note: String): Boolean = all(context).contains(note)

    private fun all(context: Context): Set<String> =
        prefs(context).getStringSet(KEY, emptySet()) ?: emptySet()

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("knotes_feed", Context.MODE_PRIVATE)

    private const val KEY = "expanded"
}
