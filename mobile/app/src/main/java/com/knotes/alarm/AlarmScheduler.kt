package com.knotes.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.knotes.prayer.PrayerCalendar
import com.knotes.prayer.PrayerSettingsStore
import java.time.LocalDate
import java.time.ZoneId

/**
 * Arms the next alarm with the system.
 *
 * ONE pending intent for the whole list, not one per alarm. The system caps how many exact alarms
 * an app may hold, and more importantly a per-alarm scheme has to cancel precisely the right ones
 * when the list is edited — the bug being that an alarm you deleted still goes off. Arming only the
 * soonest, and re-arming after every fire and every edit, has one state to keep correct.
 */
object AlarmScheduler {

    private const val REQUEST = 0x4A1A

    /** Prayer times for the alarm engine, read from the prayer page's own settings. */
    fun prayerTimes(context: Context): (LocalDate, com.knotes.prayer.Prayer) -> Long? {
        val settings = PrayerSettingsStore(context).value
        val zone = ZoneId.systemDefault()
        return { date, prayer -> PrayerCalendar.dayOf(settings, date, zone).timeOf(prayer) }
    }

    /**
     * Cancel whatever is armed and arm the soonest alarm there is, if any.
     *
     * Also redraws the countdown widget. Re-arming happens on every edit and every firing, which is
     * exactly the set of moments the widget's numbers change — so there is no separate path to
     * forget.
     */
    fun rearm(context: Context) {
        com.knotes.reminder.AlarmsWidget.refresh(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)

        val next = AlarmTimes.upcoming(
            AlarmStore.reload(context),
            System.currentTimeMillis(),
            ZoneId.systemDefault(),
            prayerTimes(context),
        ).firstOrNull() ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.second, pi)
        } else {
            // Without the exact-alarm permission an inexact alarm is still far better than none:
            // it fires late rather than never, and the page says the permission is missing.
            am.set(AlarmManager.RTC_WAKEUP, next.second, pi)
        }
    }

    /** True when the system will honour exact timing — the page warns when it will not. */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
