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
import com.knotes.AppSettings
import com.knotes.SettingsStore
import com.knotes.nibras.SyncCenter
import com.knotes.nibras.SyncStatusStore
import com.knotes.util.FriendlyTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight foreground service that shows a persistent, silent notification with today's note
 * count and/or the background-sync status. Being a foreground service keeps the process alive so
 * the exact alarms (reminders + the quarter-hour chime) fire reliably, and so the background sync
 * loop below keeps ticking with the screen off. Started/stopped from the app and re-asserted on
 * boot.
 */
class StatusService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SyncStatusStore.seed(this)
        ensureLoopStarted()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = SettingsStore(this).value
        if (!settings.statusNotificationEnabled && !settings.syncEnabled) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureLoopStarted()
        startForegroundWith(buildNotification(this, settings))
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        super.onDestroy()
    }

    /** Runs for the lifetime of the service: alternates a cheap online check with a fuller sync
     *  once [AppSettings.syncIntervalSec] has elapsed, then re-posts the notification either way.
     *  Settings are re-read every iteration, so edits from Settings take effect on the next tick
     *  without needing to restart the service. */
    private fun ensureLoopStarted() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            var nextSyncAtMillis = 0L // 0 forces a sync on the very first eligible tick
            while (isActive) {
                val settings = SettingsStore(this@StatusService).value
                if (settings.syncEnabled) {
                    val now = System.currentTimeMillis()
                    if (now >= nextSyncAtMillis) {
                        SyncCenter.syncNow(this@StatusService)
                        nextSyncAtMillis = now + settings.syncIntervalSec.coerceAtLeast(10) * 1000L
                    } else {
                        SyncCenter.checkOnline(this@StatusService)
                    }
                    // SyncCenter already reposts the notification with the fresh state.
                }
                delay(settings.syncCheckIntervalSec.coerceAtLeast(10) * 1000L)
            }
        }
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

        /** Start or stop the service to match the current settings. */
        fun sync(context: Context) {
            val settings = SettingsStore(context).value
            if (settings.statusNotificationEnabled || settings.syncEnabled) start(context) else stop(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, StatusService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Re-post with fresh counts/sync status (a no-op start that re-runs onStartCommand). */
        fun refresh(context: Context) {
            val settings = SettingsStore(context).value
            if (settings.statusNotificationEnabled || settings.syncEnabled) start(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StatusService::class.java))
        }

        private fun buildNotification(context: Context, settings: AppSettings): Notification {
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val title: String
            val lines = mutableListOf<String>()
            if (settings.statusNotificationEnabled) {
                val counts = NoteCount.read(SettingsStore(context))
                title = "${counts.total} " + if (counts.total == 1) "note" else "notes"
                lines += "Today: ${counts.today}  ·  Total: ${counts.total}"
            } else {
                title = settings.appName
            }
            if (settings.syncEnabled) {
                val online = SyncStatusStore.online.value
                val status = when (online) {
                    true -> "Online"
                    false -> "Offline"
                    null -> "—"
                }
                lines += "$status · Synced ${FriendlyTime.ago(SyncStatusStore.lastSyncMillis.value)}"
            }
            return NotificationCompat.Builder(context, CHANNEL)
                // A plain static glyph in the status bar (no number). The counts/sync status live
                // in the expanded notification lines and in the KPI home-screen widget instead.
                .setSmallIcon(R.drawable.ic_stat_notes)
                .setContentTitle(title)
                .setContentText(lines.joinToString("  ·  "))
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
