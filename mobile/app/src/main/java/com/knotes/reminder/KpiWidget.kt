package com.knotes.reminder

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.SettingsStore
import com.knotes.status.NoteCount

/**
 * Small at-a-glance KPI widget: today's vs. total notes, free space on the main storage, and the
 * /knotes vs. /new file counts (recursive, hidden ignored). (The top-priority-notes count lives
 * on the Upcoming-plans widget's second section.) Tapping opens the app.
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

            // Free space on the main (internal) storage, in GB.
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val freeGb = stat.availableBlocksLong * stat.blockSizeLong / 1_000_000_000.0

            // Capture pipeline: file counts (recursive, hidden ignored) in shared-storage
            // /knotes vs. the /new inbox, shown as a ratio.
            val shared = Environment.getExternalStorageDirectory()
            val knotesCount = countFiles(java.io.File(shared, "knotes"))
            val newCount = countFiles(java.io.File(shared, "new"))

            val views = RemoteViews(context.packageName, R.layout.widget_kpi)
            views.setTextViewText(R.id.kpi_notes_value, "${notes.today} / ${notes.total}")
            views.setTextViewText(R.id.kpi_free_value, "%.1f GB".format(freeGb))
            views.setTextViewText(R.id.kpi_folders_value, "$knotesCount / $newCount")

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

        /** Recursive file count, skipping hidden files/folders (dot-prefixed). 0 for a missing dir. */
        private fun countFiles(dir: java.io.File): Int = runCatching {
            if (!dir.isDirectory || dir.name.startsWith(".")) return@runCatching 0
            var n = 0
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                n += if (f.isDirectory) countFiles(f) else 1
            }
            n
        }.getOrDefault(0)
    }
}
