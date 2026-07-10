package com.knotes.reminder

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.NoteRepository
import com.knotes.R
import com.knotes.SettingsStore
import com.knotes.nibras.NibrasCache
import com.knotes.nibras.NibrasLocalStore
import com.knotes.nibras.parseServerDateTime
import com.knotes.status.NoteCount
import java.time.LocalDate

/**
 * Small at-a-glance KPI widget: today's vs. total notes, and how many tasks were entered today
 * (from the cached Tasks page) vs. completed today (in-app completions). Tapping opens the app.
 */
class KpiWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KpiWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val notes = NoteCount.read(SettingsStore(context))
            val today = LocalDate.now()
            val cache = NibrasCache(context)
            // Tasks (code "T") from the offline cache of the server export.
            val cachedTasks = cache.load("T")
            val tasksNew = cachedTasks.count { r -> parseServerDateTime(r.date)?.toLocalDate() == today }
            // Completed today = from the SERVER (completedOn) across Tasks AND Goals, so server-side
            // completions count too — not just in-app checkbox ticks on this device.
            val tasksDone = (cachedTasks + cache.load("G")).count { r ->
                parseServerDateTime(r.completedOn)?.toLocalDate() == today
            }

            // Top-priority (priority-4) notes — the "Top priority list" count.
            val topPriority = runCatching {
                NoteRepository(SettingsStore(context)).listNotes().count { it.priority == 4 }
            }.getOrDefault(0)

            val views = RemoteViews(context.packageName, R.layout.widget_kpi)
            views.setTextViewText(R.id.kpi_notes_value, "${notes.today} / ${notes.total}")
            views.setTextViewText(R.id.kpi_tasks_new_value, "$tasksNew / $tasksDone")
            views.setTextViewText(R.id.kpi_top_value, "$topPriority")

            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, "T")
            val open = android.app.PendingIntent.getActivity(
                context, "kpi".hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
