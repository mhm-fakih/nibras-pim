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
 * Home-screen widget: the tasks written today at the top priority, with a bar for adding another.
 *
 * Distinct from [TopPriorityWidget], which lists every top-priority note ever written whatever its
 * type. This one is a day's work list — type `T`, today only — so it empties itself overnight and
 * what is on it is what is still outstanding *now*.
 *
 * The add bar is a TextView made to look like a text field, and has to be: `RemoteViews` supports
 * no editable view on any Android version, so a widget cannot contain a real one. Tapping it opens
 * [QuickTaskActivity], a small floating input over the home screen, which writes the note and
 * refreshes this widget — so the task lands here as soon as it is entered.
 */
class TodayTasksWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.tt_list)
        }
    }

    companion object {
        /**
         * The type code this widget is about. Hardcoded because it is the widget's whole subject —
         * "today's tasks" is not a view that would still mean something pointed at Ideas — and it
         * matches the `T` in the default type list.
         */
        const val TASK_TYPE = "T"

        /** The section segment [PlansWidgetService] switches on for these rows. */
        const val SECTION = "today_tasks"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TodayTasksWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, build(context, id))
                // The rows come from a RemoteViewsFactory, which updateAppWidget does not re-run;
                // without this the widget redraws around a stale list and a task just added would
                // not appear until something else happened to invalidate it.
                manager.notifyAppWidgetViewDataChanged(id, R.id.tt_list)
            }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tasks)
            val settings = SettingsStore(context)
            val priority = settings.value.topPriority

            // Counted from the file names alone — this runs on every redraw, including the
            // system's own onUpdate on the main thread.
            val count = runCatching {
                NoteRepository(settings).countTasksSince(TASK_TYPE, priority, startOfToday())
            }.getOrDefault(0)

            views.setTextViewText(R.id.tt_title, context.getString(R.string.widgets_today_tasks_title, priority))
            // Stated, not assessed: a day with few tasks written down is not a worse day.
            WidgetCounts.apply(views, WidgetCounts.plain(count), R.id.tt_badge)
            views.setTextViewText(R.id.tt_empty, context.getString(R.string.widgets_today_tasks_empty))
            views.setRemoteAdapter(R.id.tt_list, sectionIntent(context, widgetId))
            views.setEmptyView(R.id.tt_list, R.id.tt_empty)

            // The add bar: straight into the input, with the keyboard already up.
            views.setOnClickPendingIntent(
                R.id.tt_add,
                PendingIntent.getActivity(
                    context, "today_tasks_add".hashCode(),
                    // NEW_TASK only. CLEAR_TASK would tear down a still-open input and take the
                    // half-typed task with it if the bar were tapped twice; singleTop reuses it
                    // instead, text intact.
                    Intent(context, QuickTaskActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            // Everything shown is read from this phone's own notes folder, so there is nothing to
            // pull from the server first.
            views.setOnClickPendingIntent(
                R.id.tt_title,
                WidgetTapReceiver.pendingIntent(context, "today_tasks_refresh".hashCode(), sync = false),
            )

            // Tapping a row opens the app on Tasks. Mutable, because the rows supply the rest of
            // the intent through setOnClickFillInIntent.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.tt_list,
                PendingIntent.getActivity(
                    context, SECTION.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_DEST, TASK_TYPE),
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
