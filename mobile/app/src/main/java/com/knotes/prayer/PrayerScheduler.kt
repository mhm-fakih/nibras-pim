package com.knotes.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

/**
 * Arms the **next** prayer event as a single exact alarm; [PrayerReceiver] fires it, acts on it,
 * and calls [sync] again to arm the one after. One pending alarm at a time rather than a day's
 * worth: the set changes whenever a setting moves, and re-deriving "what is next" from scratch is
 * both cheaper and impossible to leave stale — there is nothing to cancel that we did not just
 * replace. The chain is restarted by [sync] on boot, on app start, and on every settings change,
 * so a missed link (device off, alarm dropped) heals rather than stopping the schedule for good.
 */
object PrayerScheduler {

    const val ACTION_FIRE = "com.knotes.prayer.FIRE"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_AT = "at"

    private const val CODE = 90_310

    /**
     * Arm the next event, or cancel everything when nothing is scheduled.
     *
     * [fromMillis] is the instant to look forward from. The receiver passes the moment just past
     * the event it has finished handling rather than "now", because an alarm delivered a few
     * milliseconds early would otherwise still be in the future and get armed a second time —
     * calling the same athan twice.
     */
    fun sync(context: Context, fromMillis: Long = System.currentTimeMillis()) {
        // Switched off in Settings means OFF, not merely hidden: an athan calling from an app whose
        // prayer page you have turned off is the worst possible version of this feature. Checked
        // here, at the one place an alarm is ever armed, so no caller can arm one around it.
        if (!com.knotes.SettingsStore(context).value.prayerEnabled) {
            cancel(context)
            return
        }
        val settings = PrayerSettingsStore(context).value
        // Only wake for the widget's countdown if there IS a widget. These alarms do nothing else.
        val hasWidget = runCatching {
            android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(
                    android.content.ComponentName(context, com.knotes.reminder.PrayerWidget::class.java),
                ).isNotEmpty()
        }.getOrDefault(false)
        val next = PrayerEvents.next(settings, ZoneId.systemDefault(), fromMillis, hasWidget)
        if (next == null) {
            cancel(context)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, next)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(bareIntent(context))
    }

    private fun pendingIntent(context: Context, event: PrayerEvent): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, PrayerReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_KIND, event.kind.name)
                .putExtra(EXTRA_PRAYER, event.prayer.name)
                .putExtra(EXTRA_AT, event.atMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Matches the armed alarm for cancellation — extras play no part in that comparison. */
    private fun bareIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, PrayerReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
