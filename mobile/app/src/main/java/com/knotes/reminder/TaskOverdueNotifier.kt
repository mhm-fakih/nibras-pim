package com.knotes.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knotes.MainActivity

/**
 * Posts one consolidated "N tasks overdue" notification when [PlansRepository.refresh] finds
 * tasks whose due date has already passed (unlike the "before it's due" alarms in
 * [ReminderScheduler], which only schedule *future* triggers — there's nothing to pre-remind
 * about once the moment has passed). Dedups by task-id set + day so a refresh that finds the same
 * still-overdue tasks doesn't re-notify every time (refresh runs on launch, connectivity change,
 * settings change, and boot).
 */
object TaskOverdueNotifier {

    // Same id as ReminderReceiver's default channel — same purpose (a plan/task reminder),
    // channel creation is idempotent so either side creating it first is fine.
    private const val CHANNEL = "plan_reminders"
    private const val NOTIF_ID = 4712
    private const val PREFS = "knotes_task_reminders"
    private const val K_LAST_KEY = "last_notified_key"

    /** `overdue` = tasks with a due date already in the past and no completedOn. */
    fun notifyIfChanged(context: Context, overdue: List<PlanItem>) {
        if (overdue.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val key = today + "|" + overdue.map { it.id }.sorted().joinToString(",")
        if (prefs.getString(K_LAST_KEY, null) == key) return // same overdue set already notified today
        prefs.edit().putString(K_LAST_KEY, key).apply()

        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (overdue.size == 1) {
            overdue[0].summary.ifBlank { "1 task overdue" }
        } else {
            overdue.take(3).joinToString(", ") { it.summary.ifBlank { "(untitled)" } } +
                if (overdue.size > 3) "…" else ""
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("${overdue.size} task${if (overdue.size == 1) "" else "s"} overdue")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build()) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Plan reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }
}
