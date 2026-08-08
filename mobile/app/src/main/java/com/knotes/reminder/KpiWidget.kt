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
import com.knotes.nibras.SyncNowReceiver
import com.knotes.status.NoteCount

/**
 * Small at-a-glance KPI widget. Tapping the title runs a sync now; tapping elsewhere opens the app.
 *
 * Two columns, two lines each, with the label abbreviated into the value so it fits a 3x1 cell:
 *
 * ```
 * 5 nt. td. / 1204      862 f.
 * 7.7 gb f.             12 f. td.
 * ```
 *
 * * `nt. td. / <n>` — notes written today / notes in total
 * * `gb f.` — gigabytes free on the main storage
 * * `f.` — files in shared-storage `/knotes` (recursive, hidden ignored)
 * * `f. td.` — how many of those were written today
 *
 * The `/new` inbox count that used to sit in a third column is gone; the second column now tracks
 * the knotes folder's own growth instead.
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

            // File counts (recursive, hidden ignored) in shared-storage /knotes: the whole folder,
            // and how much of it arrived today.
            val shared = Environment.getExternalStorageDirectory()
            val knotesDir = java.io.File(shared, "knotes")
            val knotesCount = countFiles(knotesDir)
            val knotesToday = countFiles(knotesDir, since = startOfToday())

            val views = RemoteViews(context.packageName, R.layout.widget_kpi)
            views.setTextViewText(R.id.kpi_notes_value, "${notes.today} nt. td. / ${notes.total}")
            views.setTextViewText(R.id.kpi_free_value, "%.1f gb f.".format(freeGb))
            views.setTextViewText(R.id.kpi_folders_value, "$knotesCount f.")
            views.setTextViewText(R.id.kpi_folders_today_value, "$knotesToday f. td.")

            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, "T")
            val open = android.app.PendingIntent.getActivity(
                context, "kpi".hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.kpi_title, SyncNowReceiver.pendingIntent(context, "kpi_sync".hashCode()))
            return views
        }

        /** Midnight this morning, local time — the cut-off for the "today" file count. */
        private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        /**
         * Recursive file count, skipping hidden files/folders (dot-prefixed). 0 for a missing dir.
         * With [since], counts only files modified at or after that instant — folders are still
         * descended into regardless, since an old folder can hold a new file.
         */
        private fun countFiles(dir: java.io.File, since: Long = 0L): Int = runCatching {
            if (!dir.isDirectory || dir.name.startsWith(".")) return@runCatching 0
            var n = 0
            dir.listFiles()?.forEach { f ->
                if (f.name.startsWith(".")) return@forEach
                n += if (f.isDirectory) countFiles(f, since) else if (f.lastModified() >= since) 1 else 0
            }
            n
        }.getOrDefault(0)
    }
}
