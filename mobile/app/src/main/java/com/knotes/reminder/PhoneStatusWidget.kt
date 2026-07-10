package com.knotes.reminder

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.knotes.R

/**
 * Home-screen widget showing phone status: free/total space on the main (internal) memory, and the
 * current Ring/Notification/Alarm/Music volume levels as read-only horizontal bars. It refreshes
 * periodically, when tapped, and on volume / ringer-mode changes.
 */
class PhoneStatusWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    /** Refresh on tap and on the volume/ringer-mode broadcasts we subscribe to in the manifest. */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            "android.media.VOLUME_CHANGED_ACTION",
            "android.media.RINGER_MODE_CHANGED" -> refresh(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.knotes.PHONE_STATUS_REFRESH"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PhoneStatusWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_phone_status)

            // Storage on the main (internal) memory.
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            views.setTextViewText(
                R.id.ps_storage,
                "Free ${formatBytes(free)} / ${formatBytes(total)}",
            )
            val usedPct = if (total > 0) (((total - free) * 100) / total).toInt() else 0
            views.setProgressBar(R.id.ps_storage_bar, 100, usedPct, false)

            // Volume levels (read-only): bar fill + numeric current/max for each stream.
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            setVolume(views, am, R.id.ps_bar_ring, R.id.ps_val_ring, AudioManager.STREAM_RING)
            setVolume(views, am, R.id.ps_bar_notif, R.id.ps_val_notif, AudioManager.STREAM_NOTIFICATION)
            setVolume(views, am, R.id.ps_bar_alarm, R.id.ps_val_alarm, AudioManager.STREAM_ALARM)
            setVolume(views, am, R.id.ps_bar_music, R.id.ps_val_music, AudioManager.STREAM_MUSIC)

            // Tap anywhere to refresh.
            val intent = Intent(context, PhoneStatusWidget::class.java).setAction(ACTION_REFRESH)
            val pi = android.app.PendingIntent.getBroadcast(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }

        private fun setVolume(views: RemoteViews, am: AudioManager, barId: Int, valId: Int, stream: Int) {
            val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            val cur = am.getStreamVolume(stream)
            views.setProgressBar(barId, max, cur, false)
            views.setTextViewText(valId, "$cur/$max")
        }

        /** Human-readable size, e.g. "12.3 GB" / "640 MB". */
        private fun formatBytes(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            if (gb >= 1.0) return "%.1f GB".format(gb)
            val mb = bytes / 1_000_000.0
            return "%.0f MB".format(mb)
        }
    }
}
