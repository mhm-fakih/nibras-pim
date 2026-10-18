package com.knotes.prayer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/** One prayer on one day, resolved to an instant and carrying the nudge that was applied. */
data class PrayerSlot(
    val prayer: Prayer,
    /**
     * Epoch millis of the time as the user will see it — the computed instant plus their manual
     * adjustment. Null when the sun never reaches the angle (see [HighLatRule]).
     */
    val atMillis: Long?,
    /** Minutes of manual adjustment folded into [atMillis], for the page to display. */
    val adjustmentMin: Int,
)

/** The six times for one civil day, plus the Islamic date that day falls on. */
data class PrayerDay(
    val date: LocalDate,
    val hijri: HijriDate,
    val slots: List<PrayerSlot>,
) {
    fun slot(prayer: Prayer): PrayerSlot? = slots.firstOrNull { it.prayer == prayer }

    fun timeOf(prayer: Prayer): Long? = slot(prayer)?.atMillis

    /** The rows the user asked to see, in the order they occur. */
    fun visibleSlots(settings: PrayerSettings): List<PrayerSlot> =
        slots.filter { it.prayer in settings.visible }
}

/** A prayer still to come, and which day it belongs to. */
data class UpcomingPrayer(
    val prayer: Prayer,
    val atMillis: Long,
    val isTomorrow: Boolean,
)

/**
 * Turns [PrayerSettings] plus a date into concrete instants.
 *
 * This is the layer that knows about time zones, manual adjustments and which rows the user cares
 * about; [PrayerTimes] below it knows only astronomy, and the alarm scheduler above it knows only
 * instants. Everything here is deterministic given a [ZoneId], so it can be tested without a device.
 */
object PrayerCalendar {

    /** The full day: all six times, adjusted, plus the Hijri date for that civil day. */
    fun dayOf(settings: PrayerSettings, date: LocalDate, zone: ZoneId): PrayerDay {
        val hijri = Hijri.fromGregorian(
            date.year, date.monthValue, date.dayOfMonth, settings.hijriOffsetDays,
        )
        val offsetHours =
            zone.rules.getOffset(date.atTime(12, 0).atZone(zone).toInstant()).totalSeconds / 3600.0

        val hours = PrayerTimes.compute(
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            latitude = settings.latitude,
            longitude = settings.longitude,
            timeZoneOffsetHours = offsetHours,
            method = settings.method,
            asr = settings.asr,
            highLat = settings.highLat,
            elevationMeters = settings.elevationMeters,
            ramadan = hijri.isRamadan,
        )

        val slots = Prayer.entries.map { prayer ->
            val adjust = settings.adjustmentOf(prayer)
            val base = instantOf(date, hours.getValue(prayer), zone)
            PrayerSlot(prayer, base?.plus(adjust * 60_000L), adjust)
        }
        return PrayerDay(date, hijri, slots)
    }

    /**
     * The next visible time strictly after [nowMillis], looking into tomorrow when today is spent.
     *
     * Tomorrow matters more than it looks: after Isha every one of today's times is in the past, and
     * the answer the page needs is tomorrow's Fajr — not "nothing".
     */
    fun nextPrayer(
        settings: PrayerSettings,
        zone: ZoneId,
        nowMillis: Long,
        /** Restrict to a subset, e.g. only the prayers that call an athan. Null = the visible set. */
        among: Set<Prayer>? = null,
    ): UpcomingPrayer? {
        val wanted = among ?: settings.visible
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        listOf(today to false, today.plusDays(1) to true).forEach { (date, tomorrow) ->
            dayOf(settings, date, zone).slots
                .filter { it.prayer in wanted }
                .mapNotNull { s -> s.atMillis?.let { s.prayer to it } }
                .filter { it.second > nowMillis }
                .minByOrNull { it.second }
                ?.let { return UpcomingPrayer(it.first, it.second, tomorrow) }
        }
        return null
    }

    /**
     * The visible time that arrived within the last [PrayerSettings.elapsedForMin] minutes, or null —
     * what the elapsed timer counts up from. The latest one wins when two fall in the window.
     *
     * Yesterday is searched too: an Isha at 23:50 is still "منذ 15 دقيقة" at five past midnight.
     */
    fun recentPrayer(settings: PrayerSettings, zone: ZoneId, nowMillis: Long): UpcomingPrayer? {
        val windowMs = settings.elapsedForMin
            .coerceIn(PrayerSettings.MIN_ELAPSED, PrayerSettings.MAX_ELAPSED) * 60_000L
        if (windowMs == 0L) return null
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        return listOf(today.minusDays(1), today)
            .flatMap { dayOf(settings, it, zone).slots }
            .filter { it.prayer in settings.visible }
            .mapNotNull { s -> s.atMillis?.let { s.prayer to it } }
            .filter { nowMillis >= it.second && nowMillis - it.second < windowMs }
            .maxByOrNull { it.second }
            ?.let { UpcomingPrayer(it.first, it.second, isTomorrow = false) }
    }

    /**
     * The Islamic date to show right now.
     *
     * The Islamic day turns over at sunset, not at midnight — so on a Tuesday evening after Maghrib
     * the correct answer is already Wednesday's. Optional, because a phone that disagrees with the
     * wall calendar for five hours every evening is confusing if you did not ask for it.
     */
    fun hijriNow(settings: PrayerSettings, zone: ZoneId, nowMillis: Long): HijriDate {
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val today = dayOf(settings, date, zone)
        if (!settings.hijriDayStartsAtMaghrib) return today.hijri
        val maghrib = today.timeOf(Prayer.MAGHRIB) ?: return today.hijri
        return if (nowMillis >= maghrib) {
            Hijri.fromGregorian(
                date.year, date.monthValue, date.dayOfMonth, settings.hijriOffsetDays + 1,
            )
        } else {
            today.hijri
        }
    }

    /**
     * Local wall-clock hours → epoch millis on [date], rolling the day when the value falls outside
     * 0..24 (an Isha past midnight arrives here as 24.6).
     *
     * Rounded to the whole **minute**, deliberately. Prayer times are read, printed and spoken to
     * the minute, so keeping the seconds would mean an alarm firing at 18:59:47 under a row that
     * says 19:00 — the page, the widget and the athan would each be telling a slightly different
     * story. Rounding here makes the displayed time *be* the scheduled instant.
     *
     * Built through [LocalDateTime] rather than by adding seconds to the start of the day, so a
     * clock change lands the prayer at the right wall-clock time rather than the right number of
     * elapsed seconds after midnight.
     */
    private fun instantOf(date: LocalDate, hours: Double, zone: ZoneId): Long? {
        if (hours.isNaN() || abs(hours) > 48) return null
        var minutes = Math.round(hours * 60.0)
        var d = date
        while (minutes >= MINUTES_PER_DAY) {
            minutes -= MINUTES_PER_DAY
            d = d.plusDays(1)
        }
        while (minutes < 0) {
            minutes += MINUTES_PER_DAY
            d = d.minusDays(1)
        }
        return LocalDateTime.of(d, LocalTime.ofSecondOfDay(minutes * 60))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private const val MINUTES_PER_DAY = 24L * 60L
}
