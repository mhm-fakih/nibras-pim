package com.knotes.status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.SettingsStore

/**
 * Lightweight foreground service that shows a persistent, silent notification with today's note
 * count. Being a foreground service keeps the process alive so the exact alarms (reminders + the
 * quarter-hour chime) fire reliably. Started/stopped from the app and re-asserted on boot.
 */
class StatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsStore(this)
        if (!settings.value.statusNotificationEnabled) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWith(buildNotification(this, NoteCount.read(settings)))
        return START_STICKY
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        // v2: IMPORTANCE_MIN so the foreground-service notification keeps the process alive for
        // alarms but shows no icon in the status bar (it only appears at the bottom of the shade).
        // A fresh channel id is required because the OS locks a channel's importance after creation.
        private const val CHANNEL = "knotes_status_v2"
        private const val NOTIF_ID = 4720

        /** Start or stop the service to match the current setting. */
        fun sync(context: Context) {
            if (SettingsStore(context).value.statusNotificationEnabled) start(context) else stop(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, StatusService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Re-post with a fresh count (a no-op start that re-runs onStartCommand). */
        fun refresh(context: Context) {
            if (SettingsStore(context).value.statusNotificationEnabled) start(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StatusService::class.java))
        }

        private fun buildNotification(context: Context, counts: Counts): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                // A plain static glyph in the status bar (no number). The counts live in the
                // expanded notification line and in the KPI home-screen widget instead.
                .setSmallIcon(R.drawable.ic_stat_notes)
                .setContentTitle("${counts.total} " + if (counts.total == 1) "note" else "notes")
                .setContentText("Today: ${counts.today}  ·  Total: ${counts.total}")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Status", NotificationManager.IMPORTANCE_MIN)
                        .apply {
                            setShowBadge(false)
                            setSound(null, null)
                        },
                )
            }
        }
    }
}
