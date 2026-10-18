package com.knotes.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

/**
 * Arms the **next** prayer event; [PrayerReceiver] fires it, acts on it, and calls [sync] again to
 * arm the one after. One pending alarm at a time rather than a day's worth: the set changes
 * whenever a setting moves, and re-deriving "what is next" from scratch is both cheaper and
 * impossible to leave stale — there is nothing to cancel that we did not just replace. The chain is
 * restarted by [sync] on boot, on app start, and on every settings change, so a missed link (device
 * off, alarm dropped) heals rather than stopping the schedule for good.
 *
 * <h4>Two chains, because an athan must not wait</h4>
 * Everything the user hears or relies on — reminder, athan, silence, sunrise beeps — is armed with
 * [AlarmManager.setAlarmClock]. An ordinary exact alarm, even "allow while idle", is still rationed
 * in Doze and held back by vendor app-sleeping: the athan sounded minutes late, or only when the
 * screen was next turned on. An alarm clock is exempt from both, at the price of the status-bar
 * alarm icon. The widget's countdown wake-ups, which only redraw it, ride a second, ordinary chain:
 * spending alarm-clock wake-ups on them would be wasteful, and as links in the same chain a
 * deferred redraw would hold back the athan armed after it.
 */
object PrayerScheduler {

    const val ACTION_FIRE = "com.knotes.prayer.FIRE"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_AT = "at"

    private const val CODE = 90_310
    private const val CODE_COUNTDOWN = 90_311

    /** The kinds on the alarm-clock chain: everything but the widget's redraws. */
    private val ALARM_KINDS = PrayerEventKind.entries.toSet() - PrayerEventKind.COUNTDOWN

    /**
     * How far back an unhandled event is still worth firing. Covers the alarm-clock event that is
     * due this very moment but not delivered yet — re-arming past it would lose it — and a phone
     * switched on a minute after the athan.
     */
    private const val GRACE_MS = 90_000L

    /**
     * Re-arm both chains, or cancel everything when nothing is scheduled.
     *
     * The alarm chain looks forward from the last instant [PrayerReceiver] handled rather than from
     * "now": an alarm delivered a few milliseconds early would otherwise still be in the future and
     * be armed a second time — calling the same athan twice — while one a few milliseconds late, or
     * still pending when the countdown chain re-syncs, would be stepped over and never sound.
     */
    fun sync(context: Context) {
        // Switched off in Settings means OFF, not merely hidden: an athan calling from an app whose
        // prayer page you have turned off is the worst possible version of this feature. Checked
        // here, at the one place an alarm is ever armed, so no caller can arm one around it.
        if (!com.knotes.SettingsStore(context).value.prayerEnabled) {
            cancel(context)
            return
        }
        val settings = PrayerSettingsStore(context).value
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        // An athan killed mid-call leaves the alarm volume where it set it; this is the next
        // chance to put it back (app start, boot, any settings change).
        runCatching { PrayerReceiver.restorePendingAlarmLevel(context) }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        val from = maxOf(lastHandled(context), now - GRACE_MS)
        val next = PrayerEvents.next(settings, zone, from, kinds = ALARM_KINDS)
        if (next == null) {
            am.cancel(bareIntent(context, CODE))
        } else {
            val pi = pendingIntent(context, CODE, next)
            if (exact) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(next.atMillis, openPrayerPage(context)), pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, next.atMillis, pi)
            }
        }

        // Only wake for the widget's countdown if there IS a widget. These alarms do nothing else.
        val hasWidget = runCatching {
            android.appwidget.AppWidgetManager.getInstance(context)
                .getAppWidgetIds(
                    android.content.ComponentName(context, com.knotes.reminder.PrayerWidget::class.java),
                ).isNotEmpty()
        }.getOrDefault(false)
        val countdown = if (!hasWidget) null else PrayerEvents.next(
            settings, zone, now, countdownWakeups = true, kinds = setOf(PrayerEventKind.COUNTDOWN),
        )
        if (countdown == null) {
            am.cancel(bareIntent(context, CODE_COUNTDOWN))
        } else {
            val pi = pendingIntent(context, CODE_COUNTDOWN, countdown)
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, countdown.atMillis, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, countdown.atMillis, pi)
            }
        }
    }

    /** Record that every alarm-chain event at [atMillis] has been acted on. Called before [sync]. */
    fun markHandled(context: Context, atMillis: Long) {
        prefs(context).edit().putLong(K_HANDLED, maxOf(lastHandled(context), atMillis)).commit()
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(bareIntent(context, CODE))
        am.cancel(bareIntent(context, CODE_COUNTDOWN))
    }

    private fun lastHandled(context: Context): Long = prefs(context).getLong(K_HANDLED, 0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("prayer_scheduler", Context.MODE_PRIVATE)

    private const val K_HANDLED = "handled_at"

    private fun pendingIntent(context: Context, code: Int, event: PrayerEvent): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, PrayerReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_KIND, event.kind.name)
                .putExtra(EXTRA_PRAYER, event.prayer.name)
                .putExtra(EXTRA_AT, event.atMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Matches an armed alarm for cancellation — extras play no part in that comparison. */
    private fun bareIntent(context: Context, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, PrayerReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What tapping the alarm icon's "next alarm" opens: the prayer page. */
    fun openPrayerPage(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 3,
            Intent(context, com.knotes.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(com.knotes.MainActivity.EXTRA_DEST, com.knotes.ui.PRAYER_DEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
