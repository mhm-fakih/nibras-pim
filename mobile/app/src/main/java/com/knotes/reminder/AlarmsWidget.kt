package com.knotes.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.alarm.AlarmScheduler
import com.knotes.alarm.AlarmStore
import com.knotes.alarm.AlarmTimes
import com.knotes.alarm.minutesLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home-screen widget: how long until the day's remaining alarms.
 *
 * It ticks itself. A widget's `updatePeriodMillis` floor is thirty minutes, and a countdown that
 * refreshes half-hourly is wrong for twenty-nine of them — so while at least one of these is placed
 * the widget schedules its own alarm for the next minute boundary and redraws then. The tick is
 * cancelled when the last instance is removed, which is what [onDisabled] is for: a widget nobody
 * has on a screen must not keep waking the phone.
 */
class AlarmsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
        scheduleTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            refresh(context)
            scheduleTick(context)
        }
    }

    override fun onEnabled(context: Context) = scheduleTick(context)

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(context))
    }

    companion object {
        private const val ACTION_TICK = "com.knotes.ALARMS_WIDGET_TICK"
        private const val REQUEST = 0x4A1B

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AlarmsWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        /** True when at least one instance is on a home screen — the tick's whole justification. */
        private fun placed(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, AlarmsWidget::class.java))
                .isNotEmpty()

        private fun scheduleTick(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            if (!placed(context)) {
                am.cancel(tickIntent(context))
                return
            }
            // The next minute boundary, so the countdown changes when the clock does rather than
            // sixty seconds after whenever the widget happened to be added.
            val now = System.currentTimeMillis()
            val next = now - (now % 60_000L) + 60_000L
            // Inexact on purpose: this only redraws a widget. Spending the app's exact-alarm budget
            // on a cosmetic refresh would be taking it from the alarms that actually have to ring.
            am.set(AlarmManager.RTC, next, tickIntent(context))
        }

        private fun tickIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST,
                Intent(context, AlarmsWidget::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_alarms)
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val due = AlarmTimes.dueToday(
                AlarmStore.reload(context),
                now,
                zone,
                AlarmScheduler.prayerTimes(context),
            )

            views.setTextViewText(
                R.id.aw_title,
                if (due.isEmpty()) "Alarms" else "Alarms · ${due.size} today",
            )
            val rows = listOf(R.id.aw_row1, R.id.aw_row2, R.id.aw_row3)
            rows.forEachIndexed { i, id ->
                val entry = due.getOrNull(i)
                views.setTextViewText(
                    id,
                    when {
                        entry != null -> row(entry.first.label, entry.first, entry.second, now, zone)
                        i == 0 -> "Nothing left today"
                        else -> ""
                    },
                )
            }

            views.setOnClickPendingIntent(
                R.id.aw_root,
                PendingIntent.getActivity(
                    context,
                    REQUEST,
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_DEST, com.knotes.ui.ALARMS_DEST)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        /** "07:00 · in 2h 15m — Wake up". The remaining time first, since that is what is read. */
        private fun row(
            label: String,
            alarm: com.knotes.alarm.Alarm,
            atMillis: Long,
            now: Long,
            zone: ZoneId,
        ): String {
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone)
            val mins = ((atMillis - now) / 60_000L).toInt().coerceAtLeast(0)
            val left = if (mins < 1) "now" else "in ${minutesLabel(mins)}"
            val name = label.ifBlank {
                when (alarm.kind) {
                    com.knotes.alarm.AlarmKind.COUNTDOWN -> minutesLabel(alarm.durationMin)
                    com.knotes.alarm.AlarmKind.PRAYER ->
                        com.knotes.alarm.offsetLabel(alarm.prayer, alarm.offsetMin)
                    else -> ""
                }
            }
            val clock = "%02d:%02d".format(t.hour, t.minute)
            return if (name.isBlank()) "$clock · $left" else "$clock · $left — $name"
        }
    }
}
