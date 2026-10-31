package com.knotes.status

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.knotes.SettingsStore
import java.util.Calendar

/**
 * Schedules the next quarter-hour tick (:00/:15/:30/:45) as an exact alarm. [ChimeReceiver] fires
 * it, plays the sound when the tick falls on the chosen interval inside the configured window, and
 * re-arms the following one — so a single call keeps the chimes going. The alarm stays quarter-hourly
 * whatever the interval, since each tick also refreshes the status notification. Disabling the chime
 * cancels the pending alarm.
 */
object ChimeScheduler {

    const val ACTION_CHIME = "com.knotes.status.CHIME"
    /** The tick's scheduled time (epoch millis) — a late-delivered alarm still counts as its own slot. */
    const val EXTRA_AT = "at"
    private const val CODE = 90_125

    /** Arm (or, when the chime is off, cancel) the next quarter-hour alarm per current settings. */
    fun sync(context: Context) {
        val enabled = SettingsStore(context).value.chimeEnabled
        if (enabled) scheduleNext(context) else cancel(context)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextQuarterMillis()
        val pi = pendingIntent(context, triggerAt)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, 0L))
    }

    /** The next :00/:15/:30/:45 boundary strictly after now. */
    private fun nextQuarterMillis(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val minute = c.get(Calendar.MINUTE)
        c.add(Calendar.MINUTE, 15 - (minute % 15))
        return c.timeInMillis
    }

    private fun pendingIntent(context: Context, triggerAt: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, ChimeReceiver::class.java).setAction(ACTION_CHIME).putExtra(EXTRA_AT, triggerAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
