package com.knotes.checklist

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.SettingsStore
import com.knotes.ui.CHECKLISTS_DEST
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily nudge to fill in a checklist group, at the time written before its title (see
 * [ChecklistTimes]). One alarm at a time: each firing posts the groups due then and arms the next,
 * the prayer alarms' pattern. The week file is read afresh every time, so a time changed on the
 * desktop and synced over is picked up by the next arming — app start, a checklist edit, or the
 * previous reminder.
 *
 * Silent on purpose — a notification with the group's name and nothing else, no sound or buzz —
 * and skipped when the group is already filled in for the day.
 */
object ChecklistReminders {

    private const val ACTION_FIRE = "com.knotes.checklist.REMIND"
    private const val EXTRA_AT = "at"
    private const val CODE = 90_420
    private const val CHANNEL = "checklist_reminders"
    private const val NOTIF_BASE = 4800

    fun sync(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val settings = SettingsStore(app).value
        val next = if (settings.checklistsEnabled) runCatching { nextDue(app, settings.checklistsFolder()) }.getOrNull() else null
        if (next == null) {
            am.cancel(pendingIntent(app, 0L))
            return
        }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(app, at)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** The first reminder time after now and after the last one handled, today or in the next week. */
    private fun nextDue(context: Context, folder: String): LocalDateTime? {
        val store = ChecklistStore(File(folder))
        val now = LocalDateTime.now()
        val handled = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastHandled(context)), ZoneId.systemDefault())
        val today = LocalDate.now()
        for (d in 0L..7L) {
            val date = today.plusDays(d)
            val week = weekFor(store, date) ?: continue
            ChecklistTimes.dueOn(week, date).firstOrNull { it.at.isAfter(now) && it.at.isAfter(handled) }
                ?.let { return it.at }
        }
        return null
    }

    /** The week holding [date]: its file, else the latest earlier week's groups — as the page opens it. */
    private fun weekFor(store: ChecklistStore, date: LocalDate): ChecklistWeek? {
        val id = WeekId.of(date)
        return store.load(id) ?: store.weeks().lastOrNull { it < id }?.let(store::load)?.carriedTo(id)
    }

    internal fun fire(context: Context, atMillis: Long) {
        val app = context.applicationContext
        val settings = SettingsStore(app).value
        if (settings.checklistsEnabled && atMillis > lastHandled(app)) {
            val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
            val store = ChecklistStore(File(settings.checklistsFolder()))
            weekFor(store, at.toLocalDate())?.let { week ->
                val day = at.dayOfWeek.value - 1
                ChecklistTimes.dueOn(week, at.toLocalDate())
                    .filter { it.at == at && !ChecklistTimes.filledIn(week, it.sectionCode, day) }
                    .forEach { notify(app, it) }
            }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(K_HANDLED, atMillis).apply()
        }
        sync(app)
    }

    private fun notify(context: Context, due: ChecklistTimes.Due) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.checklist_reminders_channel), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { setSound(null, null); enableVibration(false) },
            )
        }
        val open = PendingIntent.getActivity(
            context, CHECKLISTS_DEST.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, CHECKLISTS_DEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setContentTitle(due.title)
            .setContentText(context.getString(R.string.checklist_reminder_text))
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_BASE + due.sectionCode, n) }
    }

    private fun lastHandled(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_HANDLED, 0L)

    private const val PREFS = "checklist_reminders"
    private const val K_HANDLED = "handled_at"

    private fun pendingIntent(context: Context, at: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE,
            Intent(context, ChecklistReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    class ChecklistReminderReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val at = intent.getLongExtra(EXTRA_AT, 0L)
            val result = goAsync()
            Thread {
                runCatching { fire(context, at) }
                result.finish()
            }.start()
        }
    }
}
