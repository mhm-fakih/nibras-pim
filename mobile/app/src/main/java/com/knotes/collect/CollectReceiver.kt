package com.knotes.collect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotes.MainActivity
import com.knotes.SettingsStore
import com.knotes.reminder.Widgets

/**
 * Runs the daily automatic collection, then arms tomorrow's.
 *
 * Manifest-registered, so it works with the app closed. The scan and the moves happen on a
 * background thread inside a [goAsync] window — this walks several folders and moves files, which
 * is far too much for the main thread a receiver is called on.
 *
 * It reports what it did. A job that quietly moves files out of Camera and Downloads and says
 * nothing is one the user cannot audit, so a notification names the count and opens the note.
 */
class CollectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CollectScheduler.ACTION_COLLECT) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            runCatching { runCollection(app) }
            // Always re-arm, whatever happened: one failed sweep must not end the daily schedule.
            runCatching { CollectScheduler.sync(app) }
            runCatching { pending.finish() }
        }.start()
    }

    private fun runCollection(context: Context) {
        val settings = SettingsStore(context).value
        if (!settings.collectAutoEnabled) return
        // Without file access there is nothing to scan and every move would fail; saying so beats
        // reporting "collected 0 files" as though the folders were empty.
        if (!hasStorageAccess()) {
            notify(context, "Automatic collection needs file access", "Open knotes to grant it.")
            return
        }

        val found = FileCollector.scan(
            sourceDirs = settings.collectSourceDirs,
            newDir = settings.collectNewDir,
            format = settings.timestampFormat,
        )
        if (found.isEmpty()) {
            // Nothing to say. A daily "collected 0 files" would train the user to ignore the one
            // notification that matters.
            return
        }

        val (folder, results) = FileCollector.collect(
            found = found,
            newDir = settings.collectNewDir,
            format = settings.timestampFormat,
            // The same defaults a quick note gets, so an automatic collection is filed exactly like
            // the one the Collect page makes by hand.
            type = settings.effectiveDefaultType,
            priority = settings.effectiveDefaultPriority,
            category = settings.effectiveDefaultCategory,
        )
        val ok = results.count { it.ok }
        val failed = results.size - ok
        notify(
            context,
            "Collected $ok ${if (ok == 1) "file" else "files"}" + if (failed > 0) " ($failed failed)" else "",
            folder.name,
        )
        runCatching { Widgets.refreshAll(context) }
    }

    private fun hasStorageAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun notify(context: Context, title: String, text: String) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 5,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                // Low importance and silent: this is a receipt for work already done, not a thing
                // that needs answering.
                NotificationChannel(CHANNEL, "Automatic collection", NotificationManager.IMPORTANCE_LOW)
                    .apply { setSound(null, null) },
            )
        }
    }

    private companion object {
        const val CHANNEL = "auto_collect"
        const val NOTIF_ID = 4731
    }
}
