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
import com.knotes.MainActivity
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore
import com.knotes.startOfToday
import com.knotes.ui.QuickTaskActivity

/**
 * Home-screen widget: today's journal entries, with a bar for writing another.
 *
 * The sibling of [TodayTasksWidget] and deliberately built the same way, with one difference that
 * matters: **every priority is listed, not just the top one.** A task list is a do-this-first
 * ranking, so that widget filters; a day's journal is whatever you wrote today, and filtering it
 * would hide entries for a reason nobody applies to a diary.
 *
 * Like its sibling it empties itself overnight — `J`, today only — so what is on it is this day's
 * writing rather than a growing archive.
 *
 * The add bar is a TextView made to look like a text field, and has to be: `RemoteViews` supports
 * no editable view on any Android version, so a widget cannot contain a real one. Tapping it opens
 * [QuickTaskActivity] as a one-line bar on the keyboard, which writes the note and refreshes this
 * widget — so the entry lands here as soon as it is typed.
 */
class TodayJournalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.tj_list)
        }
    }

    companion object {
        /** The type code this widget is about — the `J` of the default type list. */
        const val JOURNAL_TYPE = "J"

        /** The section segment [PlansWidgetService] switches on for these rows. */
        const val SECTION = "today_journal"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TodayJournalWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, build(context, id))
                // The rows come from a RemoteViewsFactory, which updateAppWidget does not re-run;
                // without this the widget redraws around a stale list and an entry just written
                // would not appear until something else happened to invalidate it.
                manager.notifyAppWidgetViewDataChanged(id, R.id.tj_list)
            }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_journal)
            val settings = SettingsStore(context)

            // Counted from the file names alone — this runs on every redraw, including the
            // system's own onUpdate on the main thread. Null priority: every entry counts.
            val count = runCatching {
                NoteRepository(settings).countTasksSince(JOURNAL_TYPE, null, startOfToday())
            }.getOrDefault(0)

            views.setTextViewText(R.id.tj_title, context.getString(R.string.widgets_today_journal_title))
            // Stated, not assessed: a day with little written down is not a worse day.
            WidgetCounts.apply(views, WidgetCounts.plain(count), R.id.tj_badge)
            views.setTextViewText(R.id.tj_empty, context.getString(R.string.widgets_today_journal_empty))
            views.setRemoteAdapter(R.id.tj_list, sectionIntent(context, widgetId))
            views.setEmptyView(R.id.tj_list, R.id.tj_empty)

            // The add bar: straight into the input, with the keyboard already up.
            views.setOnClickPendingIntent(
                R.id.tj_add,
                PendingIntent.getActivity(
                    context, "today_journal_add".hashCode(),
                    // NEW_TASK only. CLEAR_TASK would tear down a still-open input and take the
                    // half-typed entry with it if the bar were tapped twice; singleTop reuses it
                    // instead, text intact.
                    Intent(context, QuickTaskActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(QuickTaskActivity.EXTRA_TYPE, JOURNAL_TYPE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // Everything shown is read from this phone's own notes folder, so there is nothing to
            // pull from the server first.
            views.setOnClickPendingIntent(
                R.id.tj_title,
                WidgetTapReceiver.pendingIntent(context, "today_journal_refresh".hashCode(), sync = false),
            )

            // Tapping a row opens the app on Journals. Mutable, because the rows supply the rest of
            // the intent through setOnClickFillInIntent.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.tj_list,
                PendingIntent.getActivity(
                    context, SECTION.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_DEST, JOURNAL_TYPE),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutable,
                ),
            )
            return views
        }

        /** A distinct `data` URI per widget instance, so each ListView binds its own adapter. */
        private fun sectionIntent(context: Context, widgetId: Int): Intent =
            Intent(context, PlansWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("widget://plans/$SECTION/$widgetId")
            }
    }
}
