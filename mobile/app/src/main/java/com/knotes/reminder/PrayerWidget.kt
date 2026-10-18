package com.knotes.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.prayer.Prayer
import com.knotes.prayer.PrayerCalendar
import com.knotes.prayer.PrayerSettings
import com.knotes.prayer.PrayerSettingsStore
import com.knotes.ui.PRAYER_DEST
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget: a wide strip carrying the Islamic date, a live countdown, and one column per
 * shown prayer with the next one highlighted.
 *
 * Horizontal because that is how it is read — a glance for what is next and how long is left, not a
 * list to work down. The countdown is a `Chronometer` running in countdown mode, so it ticks on its
 * own between redraws; `RemoteViews` offers no other way to show a running clock, and waking the app
 * once a second to redraw a widget would be indefensible.
 *
 * Redrawn as each prayer event fires rather than on a timer — see PrayerReceiver — because the only
 * thing that goes stale is which column is highlighted, and that changes exactly when an alarm fires.
 */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context)) }
    }

    companion object {
        /** Drawn on the amber pill, so it has to be dark rather than the row's usual near-white. */
        private const val ON_PILL = 0xFF1C1C1E.toInt()
        private const val PLAIN = 0xFFEEEEEE.toInt()
        private const val PASSED = 0xFF7A7F85.toInt()
        private const val NAME = 0xFF9AA0A6.toInt()

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, PrayerWidget::class.java))
                .forEach { manager.updateAppWidget(it, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_prayer)
            views.removeAllViews(R.id.pw_columns)

            val settings = PrayerSettingsStore(context).value
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
            val day = PrayerCalendar.dayOf(settings, today, zone)
            val upcoming = PrayerCalendar.nextPrayer(settings, zone, now)
            // A time that has just arrived — the elapsed timer counts up from it for a while.
            val recent = PrayerCalendar.recentPrayer(settings, zone, now)

            val hijri = PrayerCalendar.hijriNow(settings, zone, now)
            // "12 ربيع الأول 1448" — the year is back. It was dropped to buy width for the
            // countdown to share this line; at 14sp both fit with room to spare, so there is no
            // longer anything to buy. The numbers are plain Kotlin ints, so they are Western digits
            // whatever the phone's locale; bidi puts them where an Arabic reader expects them.
            views.setTextViewText(
                R.id.pw_hijri, "${hijri.day} ${hijri.monthArabic} ${hijri.year}",
            )
            // Only once the next time is close. Drawn whatever the gap, the countdown spent most of
            // the day reading "فجر بعد 5:31:23" — a number nobody acts on, occupying the one spare
            // line the strip has. Within the window it is the thing being watched.
            val withinMs = settings.countdownWithinMin.coerceIn(
                PrayerSettings.MIN_COUNTDOWN, PrayerSettings.MAX_COUNTDOWN,
            ) * 60_000L
            val due = upcoming?.atMillis?.takeIf { it - now <= withinMs }
            // Just after a time, the line counts UP from it ("ظهر منذ 4:12") instead of falling
            // silent because the next time is hours off. setCountdown already words and runs a
            // past target that way.
            if (recent != null) setCountdown(views, recent.prayer.arabicShort, recent.atMillis, now)
            else setCountdown(views, upcoming?.prayer?.arabicShort, due, now)
            // The highlighted column follows whatever the line is timing.
            val marked = recent?.prayer ?: upcoming?.prayer

            val rows = day.visibleSlots(settings)
            if (rows.isEmpty()) {
                views.setViewVisibility(R.id.pw_empty, View.VISIBLE)
                views.setTextViewText(R.id.pw_empty, "لا أوقات معروضة — اخترها من صفحة الصلاة.")
            } else {
                views.setViewVisibility(R.id.pw_empty, View.GONE)
                rows.forEach { slot ->
                    // The highlight follows whatever the countdown is counting to — including
                    // tomorrow's Fajr once the day is spent. Restricting it to today left the strip
                    // with nothing marked all evening while the header still named a prayer, which
                    // read as a fault rather than as a distinction.
                    val isNext = marked == slot.prayer
                    // Dimmed only when it has gone AND is not what we are now counting towards.
                    val passed = !isNext && slot.atMillis != null && slot.atMillis < now
                    val col = RemoteViews(context.packageName, R.layout.widget_prayer_col)
                    sizeText(col, rows.size)
                    col.setTextViewText(R.id.pw_col_name, slot.prayer.arabicShort)
                    col.setTextViewText(R.id.pw_col_time, slot.atMillis?.let(::clock) ?: "—")
                    col.setTextColor(R.id.pw_col_name, if (isNext) PLAIN else NAME)
                    markSettings(col, settings, slot.prayer)
                    if (isNext) {
                        col.setInt(R.id.pw_col_time, "setBackgroundResource", R.drawable.widget_pill)
                        col.setTextColor(R.id.pw_col_time, ON_PILL)
                    } else {
                        col.setTextColor(R.id.pw_col_time, if (passed) PASSED else PLAIN)
                    }
                    views.addView(R.id.pw_columns, col)
                }
            }

            val open = PendingIntent.getActivity(
                context, "prayer".hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_DEST, PRAYER_DEST),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            // The date doubles as the refresh control — this strip has no title row to carry it.
            // Everything shown is computed on this phone, so there is nothing to pull first.
            views.setOnClickPendingIntent(
                R.id.pw_hijri,
                WidgetTapReceiver.pendingIntent(context, "prayer_refresh".hashCode(), sync = false),
            )
            return views
        }

        /**
         * Set the name and time sizes from how many columns share the strip.
         *
         * The name and time sit side by side, so a column's content is as wide as both of them
         * together — and the width available is the strip's divided by [columns]. Six chosen times
         * get barely half what four do. Sized here rather than in the layout because the layout
         * cannot know the count, and a single size that fits six would leave four looking timid at
         * exactly the moment there is room to be read across a room.
         */
        private fun sizeText(col: RemoteViews, columns: Int) {
            val time: Float
            val name: Float
            when {
                // These now decide whether the row FITS, not just how it looks: the columns size to
                // their content instead of dividing the width, so nothing stops them running past
                // the edge. The budget, measured on a 720px screen: ~323dp of usable strip after
                // the root padding, and a name+time block at 18/16sp measures ~74dp.
                //
                // The Arabic name gains what the clock gives up — 4 columns come to ~313dp with the
                // 6dp gaps, inside the budget with a little to spare.
                columns <= 4 -> { name = 18f; time = 16f }
                columns == 5 -> { name = 14f; time = 13f }
                // Six on a four-cell widget was always going to be small; this is what fits.
                else -> { name = 11f; time = 10f }
            }
            col.setTextViewTextSize(R.id.pw_col_time, TypedValue.COMPLEX_UNIT_SP, time)
            col.setTextViewTextSize(R.id.pw_col_name, TypedValue.COMPLEX_UNIT_SP, name)
        }

        /**
         * Show, above each column, which of the per-prayer settings apply to it.
         *
         * The strip is otherwise silent about the difference between a time that will call the
         * athan and one that will pass unannounced — which is exactly the thing you want to check
         * at a glance, and otherwise have to open the app and a settings panel to find out.
         *
         * Beeps belong to Shurooq alone: they mark the closing of the window in which Fajr can
         * still be prayed, so they are a property of sunrise rather than a per-prayer choice.
         */
        private fun markSettings(col: RemoteViews, settings: PrayerSettings, prayer: Prayer) {
            fun show(id: Int, on: Boolean) {
                col.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
            }
            show(R.id.pw_col_athan, prayer in settings.effectiveAthan)
            show(R.id.pw_col_silence, settings.silenceEnabled && prayer in settings.effectiveSilence)
            show(R.id.pw_col_beeps, settings.beepsEnabled && prayer == Prayer.SUNRISE)
        }

        /**
         * Point the Chronometer at [targetMillis] and let it count itself down.
         *
         * Countdown mode is API 24+. Below that a started Chronometer would count *up* from the
         * base and show a climbing number where a shrinking one belongs, so it is left stopped and
         * given plain text instead — wrong by a few minutes as the hour wears on, rather than wrong
         * in the direction it moves.
         */
        private fun setCountdown(views: RemoteViews, label: String?, targetMillis: Long?, now: Long) {
            if (label == null || targetMillis == null) {
                views.setChronometer(R.id.pw_countdown, 0L, null, false)
                views.setTextViewText(R.id.pw_countdown, "")
                return
            }
            // Past or future decides both the wording and which way the clock runs.
            val delta = targetMillis - now
            val passed = delta < 0
            val magnitude = kotlin.math.abs(delta)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                views.setChronometerCountDown(R.id.pw_countdown, !passed)
                views.setChronometer(
                    R.id.pw_countdown,
                    if (passed) SystemClock.elapsedRealtime() - magnitude
                    else SystemClock.elapsedRealtime() + magnitude,
                    counting(label, passed),
                    true,
                )
            } else {
                views.setChronometer(R.id.pw_countdown, 0L, null, false)
                views.setTextViewText(
                    R.id.pw_countdown, counting(label, passed).format(coarse(magnitude)),
                )
            }
        }

        /**
         * "فجر بعد %s" while the time is still ahead, "شروق منذ %s" once it has gone.
         *
         * A Chronometer in countdown mode does not stop at zero — it carries on into negatives —
         * so a strip that was not redrawn at the moment a time arrived read "شروق بعد -0:12:34":
         * "Shurooq in minus twelve minutes". The wording has to follow the sign, and the widget is
         * woken at each shown time (see PrayerEventKind.COUNTDOWN) so the flip actually happens.
         *
         * The clock is held left-to-right regardless.
         *
         * The LRM marks around the placeholder are not decoration. A time like 1:23:45 dropped bare
         * into an Arabic run is a sequence of neutral colons between digits, and bidi is free to
         * reorder it against the surrounding right-to-left text; the marks fence it into its own
         * left-to-right run so it reads in the order it counts.
         */
        private fun counting(label: String, passed: Boolean): String =
            if (passed) "$label منذ \u200E%s\u200E" else "$label بعد \u200E%s\u200E"

        /** "7h 23m" / "43m" — only used on API 23, where the clock cannot tick. */
        private fun coarse(millis: Long): String {
            val minutes = millis / 60_000
            return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        }

        /**
         * Locale.US, not the device's: the times are to be Western digits (123) whatever language
         * the phone is set to, and an Arabic locale would otherwise render them ١٢:٣٤. The pattern
         * is explicit, so nothing else about the locale can move.
         */
        private fun clock(millis: Long): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
    }
}
