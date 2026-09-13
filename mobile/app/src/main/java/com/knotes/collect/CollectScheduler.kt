package com.knotes.collect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.knotes.SettingsStore
import java.util.Calendar

/**
 * Arms the daily automatic collection as one exact alarm; [CollectReceiver] runs it and re-arms
 * tomorrow's. Same one-pending-alarm-at-a-time shape as the prayer schedule and the chime: the
 * time is a setting, so re-deriving "when next" from scratch on every change is both cheaper than
 * tracking alarms and impossible to leave stale.
 */
object CollectScheduler {

    const val ACTION_COLLECT = "com.knotes.collect.RUN"
    private const val CODE = 90_412

    /** Arm the next run, or cancel when the automatic collection is switched off. */
    fun sync(context: Context) {
        val settings = SettingsStore(context).value
        // Two switches, and the feature one wins: `collectEnabled` says whether the collector is
        // in the app at all, `collectAutoEnabled` only whether it runs by itself.
        if (!settings.collectEnabled || !settings.collectAutoEnabled) {
            cancel(context)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = nextRunMillis(settings.collectAutoHour, settings.collectAutoMinute)
        val pi = pendingIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /**
     * The next occurrence of [hour]:[minute], tomorrow if today's has passed.
     *
     * Strictly in the future: an alarm armed for a moment that has already gone would fire the
     * instant it was set, so opening Settings at 15:01 would run a collection on the spot.
     */
    internal fun nextRunMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, CollectReceiver::class.java).setAction(ACTION_COLLECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
