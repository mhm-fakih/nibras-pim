package com.knotes.status

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.knotes.SettingsStore
import java.util.Calendar

/**
 * Schedules the next quarter-hour chime (:00/:15/:30/:45) as an exact alarm. [ChimeReceiver] fires
 * it, plays the sound when inside the configured window, and re-arms the following one — so a single
 * call keeps the chimes going. Disabling the chime cancels the pending alarm.
 */
object ChimeScheduler {

    const val ACTION_CHIME = "com.knotes.status.CHIME"
    private const val CODE = 90_125

    /** Arm (or, when the chime is off, cancel) the next quarter-hour alarm per current settings. */
    fun sync(context: Context) {
        val enabled = SettingsStore(context).value.chimeEnabled
        if (enabled) scheduleNext(context) else cancel(context)
    }

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextQuarterMillis()
        val pi = pendingIntent(context)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
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

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, ChimeReceiver::class.java).setAction(ACTION_CHIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
