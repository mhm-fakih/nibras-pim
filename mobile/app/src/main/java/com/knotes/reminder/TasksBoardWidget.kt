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

/**
 * Home-screen widget, meant to fill a page: every task at the top two priorities (p4 and p3 with
 * the default settings), whenever written, grouped by priority and then by department, laid out
 * in [SettingsStore] `boardColumns` columns at `boardTextSp`.
 *
 * Read-only, unlike [TodayTasksWidget]: no add bar. It is the standing picture of what matters,
 * not today's work list, so it neither empties overnight nor stops at the top priority.
 *
 * The columns are drawn by the rows themselves — each list row carries up to [MAX_COLUMNS] cells
 * and shows as many as are configured — because a GridView cannot put a heading across its full
 * width, and the headings are the point of the grouping.
 */
class TasksBoardWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, build(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.tb_list)
        }
    }

    companion object {
        /** The section segment [PlansWidgetService] switches on for these rows. */
        const val SECTION = "tasks_board"

        /** Cells in widget_board_row; the columns setting cannot go past it. */
        const val MAX_COLUMNS = 4

        /** The top priority and the one under it — p4 and p3 unless the settings move the top. */
        fun priorities(topPriority: Int): Set<Int> = setOf(topPriority, topPriority - 1)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, TasksBoardWidget::class.java)).forEach { id ->
                manager.updateAppWidget(id, build(context, id))
                // updateAppWidget does not re-run the RemoteViewsFactory; this does.
                manager.notifyAppWidgetViewDataChanged(id, R.id.tb_list)
            }
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks_board)
            val settings = SettingsStore(context)
            val top = settings.value.topPriority
            val wanted = priorities(top)

            // Counted from the file names alone — this runs on every redraw, on the main thread too.
            val count = runCatching {
                wanted.sumOf { NoteRepository(settings).countByTypeAndPriority(TodayTasksWidget.TASK_TYPE, it) }
            }.getOrDefault(0)

            views.setTextViewText(R.id.tb_title, context.getString(R.string.widgets_tasks_board_title, top - 1, top))
            WidgetCounts.apply(views, WidgetCounts.plain(count), R.id.tb_badge)
            views.setTextViewText(R.id.tb_empty, context.getString(R.string.widgets_tasks_board_empty))
            views.setRemoteAdapter(R.id.tb_list, sectionIntent(context, widgetId))
            views.setEmptyView(R.id.tb_list, R.id.tb_empty)

            views.setOnClickPendingIntent(
                R.id.tb_title,
                WidgetTapReceiver.pendingIntent(context, "tasks_board_refresh".hashCode(), sync = false),
            )
            // A task opens the app on Tasks. Mutable: the cells fill in the rest of the intent.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            views.setPendingIntentTemplate(
                R.id.tb_list,
                PendingIntent.getActivity(
                    context, SECTION.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_DEST, TodayTasksWidget.TASK_TYPE),
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
