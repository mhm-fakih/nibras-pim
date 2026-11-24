package com.knotes.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.RemoteViews
import com.knotes.MainActivity
import com.knotes.R
import com.knotes.calendar.IranCalendar
import com.knotes.calendar.OfficialDay
import com.knotes.calendar.Shamsi
import com.knotes.prayer.Hijri
import com.knotes.prayer.PrayerSettingsStore
import com.knotes.ui.CALENDAR_DEST
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget: today in the Shamsi, Hijri and Gregorian calendars, and the occasions of
 * Iran's official calendar for yesterday, today and tomorrow — each in Persian with its Arabic.
 * The Hijri date is the prayer page's (with its day adjustment), so the two never disagree.
 */
class DatesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, build(context, scale(manager, it))) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        manager.updateAppWidget(id, build(context, scale(manager, id)))
    }

    companion object {
        private const val HOLIDAY = 0xFFFF8A80.toInt()
        private const val LABEL = 0xFF80CBC4.toInt()

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, DatesWidget::class.java))
                .forEach { manager.updateAppWidget(it, build(context, scale(manager, it))) }
        }

        /** Text grows with the widget's height: 1× at two rows (~130 dp), up to 1.5× when taller. */
        private fun scale(manager: AppWidgetManager, id: Int): Float {
            val h = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return if (h <= 0) 1f else (h / 130f).coerceIn(0.85f, 1.5f)
        }

        private fun build(context: Context, scale: Float = 1f): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dates)
            listOf(R.id.dw_shamsi to 22f, R.id.dw_others to 15f, R.id.dw_yesterday to 15f,
                R.id.dw_today to 16f, R.id.dw_tomorrow to 15f).forEach { (v, sp) ->
                views.setTextViewTextSize(v, android.util.TypedValue.COMPLEX_UNIT_SP, sp * scale)
            }
            val today = LocalDate.now()
            val prayer = PrayerSettingsStore(context).value
            val s = Shamsi.of(today)
            val h = Hijri.fromGregorian(today.year, today.monthValue, today.dayOfMonth, prayer.hijriOffsetDays)
            val official = IranCalendar.day(context, today)
            val holiday = official?.holiday == true || today.dayOfWeek == java.time.DayOfWeek.FRIDAY

            val head = "${Shamsi.WEEKDAYS_FA[Shamsi.weekdayIndex(today)]} ${Shamsi.faDigits(s.day)} ${s.monthFa} ${Shamsi.faDigits(s.year)}"
            views.setTextViewText(R.id.dw_shamsi, head)
            views.setTextColor(R.id.dw_shamsi, if (holiday) HOLIDAY else 0xFFEEEEEE.toInt())
            val greg = today.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH))
            views.setTextViewText(R.id.dw_others,
                "${Shamsi.arDigits(h.day)} ${h.monthArabic} ${Shamsi.arDigits(h.year)}  ·  ⁦$greg⁩")

            views.setTextViewText(R.id.dw_yesterday, dayLine("دیروز", IranCalendar.day(context, today.minusDays(1))))
            views.setTextViewText(R.id.dw_today, dayLine("امروز", official))
            views.setTextViewText(R.id.dw_tomorrow, dayLine("فردا", IranCalendar.day(context, today.plusDays(1))))

            val open = PendingIntent.getActivity(
                context, "dates".hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_DEST, CALENDAR_DEST),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }

        /** "امروز: occasion — its Arabic · next…", holidays in red; "—" for a day with none. */
        private fun dayLine(label: String, day: OfficialDay?): CharSequence {
            val b = SpannableStringBuilder()
            b.append(label, ForegroundColorSpan(LABEL), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.BOLD), 0, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.append(": ")
            val occ = day?.occasions.orEmpty()
            if (day == null) {
                b.append("—")
                return b
            }
            if (occ.isEmpty()) {
                b.append("—")
                return b
            }
            occ.forEachIndexed { i, o ->
                if (i > 0) b.append("\n")
                val start = b.length
                b.append(o.fa)
                if (o.holiday) b.setSpan(ForegroundColorSpan(HOLIDAY), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (o.ar.isNotBlank()) {
                    b.append("\n")
                    val a = b.length
                    b.append(o.ar)
                    b.setSpan(RelativeSizeSpan(0.88f), a, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            return b
        }
    }
}
