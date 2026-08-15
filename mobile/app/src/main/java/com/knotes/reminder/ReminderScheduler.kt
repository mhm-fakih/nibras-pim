package com.knotes.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules one exact alarm per (event, reminder-offset) pair via [AlarmManager]. Alarms fire even
 * with the app closed (a manifest-registered [ReminderReceiver] handles them); they're re-scheduled
 * on every refresh and after a reboot. The set of live request codes is tracked so stale alarms can
 * be cancelled before re-scheduling.
 */
object ReminderScheduler {

    const val ACTION_FIRE = "com.knotes.reminder.FIRE"
    const val EXTRA_KEY = "key"
    const val EXTRA_SUMMARY = "summary"
    const val EXTRA_START = "start"
    const val EXTRA_OFFSET = "offset"
    const val EXTRA_REQUIRE_DISMISS = "require_dismiss"
    const val EXTRA_SOUND = "sound"
    const val EXTRA_REPEAT_COUNT = "repeat_count"

    private const val PREFS = "knotes_reminder_codes"
    private const val K_CODES = "codes"

    fun scheduleAll(
        context: Context,
        items: List<PlanItem>,
        offsetsMin: List<Int>,
        soundUri: String,
        requireDismiss: Boolean,
        repeatCount: Int = 1,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Cancel everything we scheduled last time.
        prefs.getStringSet(K_CODES, emptySet()).orEmpty().forEach { code ->
            code.toIntOrNull()?.let { am.cancel(bareIntent(context, it)) }
        }

        val now = System.currentTimeMillis()
        SilencedReminders.prune(context, now)
        val live = mutableSetOf<String>()
        items.forEach { item ->
            // "Stop reminders" has to be honoured HERE, not just by cancelling the pending alarms:
            // this method re-arms everything from scratch on every sync, so a cancelled alarm would
            // be back within minutes.
            if (SilencedReminders.isSilenced(context, item.key, item.startMillis)) return@forEach
            offsetsMin.filter { it >= 0 }.forEach { off ->
                val triggerAt = item.startMillis - off * 60_000L
                if (triggerAt <= now) return@forEach
                val code = (item.key + "@" + off).hashCode()
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_FIRE
                    putExtra(EXTRA_KEY, item.key)
                    putExtra(EXTRA_SUMMARY, item.summary)
                    putExtra(EXTRA_START, item.startMillis)
                    putExtra(EXTRA_OFFSET, off)
                    putExtra(EXTRA_REQUIRE_DISMISS, requireDismiss)
                    putExtra(EXTRA_SOUND, soundUri)
                    putExtra(EXTRA_REPEAT_COUNT, repeatCount)
                }
                val pi = PendingIntent.getBroadcast(context, code, intent, flags(mutable = false))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                live.add(code.toString())
            }
        }
        prefs.edit().putStringSet(K_CODES, live).apply()
    }

    /**
     * Cancel every still-pending alarm for one event, across all reminder offsets.
     *
     * Used the moment "Stop reminders" is tapped, so the next reminder doesn't fire in the minutes
     * before the next sync gets round to re-scheduling. The durable half of the job is
     * [SilencedReminders]; this is just the immediate part.
     */
    fun cancelFor(context: Context, key: String, offsetsMin: List<Int>) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        offsetsMin.forEach { off ->
            am.cancel(bareIntent(context, (key + "@" + off).hashCode()))
        }
    }

    private fun bareIntent(context: Context, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
            flags(mutable = false),
        )

    private fun flags(mutable: Boolean): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        f = f or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return f
    }
}
