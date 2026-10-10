package com.knotes.alarm

import com.knotes.prayer.Prayer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When each alarm fires next.
 *
 * Pure, and deliberately so: this is the part that is easy to get wrong in ways nobody notices
 * until an alarm does not go off — the day that has already passed this week, the prayer time that
 * has gone by, the countdown that was started and then edited. Keeping it free of AlarmManager and
 * of the clock means every one of those cases can be stated as a test.
 *
 * [prayerTimeAt] is supplied by the caller rather than computed here so this file does not depend
 * on the prayer engine: give it a function from (date, prayer) to epoch millis, or null when that
 * prayer has no time that day (the high-latitude case the prayer code already handles).
 */
object AlarmTimes {

    /** How far ahead a repeating alarm is searched. A week covers every weekday pattern. */
    private const val SEARCH_DAYS = 8

    /**
     * The next moment [alarm] should fire after [nowMillis], or null if it never will.
     *
     * Null is a real answer, not a failure: a disabled alarm, a countdown that has not been
     * started, and a prayer alarm for a prayer that does not occur all legitimately have no next
     * time, and the scheduler simply does not arm them.
     */
    fun nextFire(
        alarm: Alarm,
        nowMillis: Long,
        zone: ZoneId,
        prayerTimeAt: (LocalDate, Prayer) -> Long? = { _, _ -> null },
    ): Long? {
        if (!alarm.armable()) return null
        return when (alarm.kind) {
            AlarmKind.COUNTDOWN ->
                // Already due is not "fire now": a countdown that elapsed while the phone was off
                // has had its moment, and re-firing it on boot would be a surprise alarm.
                alarm.dueAtMillis?.takeIf { it > nowMillis }

            AlarmKind.CLOCK -> nextClock(alarm, nowMillis, zone)

            AlarmKind.PRAYER -> nextPrayer(alarm, nowMillis, zone, prayerTimeAt)
        }
    }

    /**
     * A wall-clock alarm.
     *
     * Searched day by day from today rather than computed, because the arithmetic that looks
     * obvious ("add a day until the weekday matches") gets daylight saving wrong: 07:00 is 07:00 on
     * both sides of a clock change, and only building the local date-time each day gives that.
     */
    private fun nextClock(alarm: Alarm, nowMillis: Long, zone: ZoneId): Long? {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        for (i in 0 until SEARCH_DAYS) {
            val date = today.plusDays(i.toLong())
            if (alarm.days.isNotEmpty() && date.dayOfWeek !in alarm.days) continue
            val at = LocalDateTime.of(date, java.time.LocalTime.of(alarm.hour, alarm.minute))
                .atZone(zone).toInstant().toEpochMilli()
            if (at > nowMillis) return at
        }
        return null
    }

    /**
     * An alarm pinned to a prayer.
     *
     * Yesterday is searched too. A negative offset can pull a time back across midnight — "90
     * minutes before Fajr" on the 5th is late on the 4th — and starting at today would skip it.
     */
    private fun nextPrayer(
        alarm: Alarm,
        nowMillis: Long,
        zone: ZoneId,
        prayerTimeAt: (LocalDate, Prayer) -> Long?,
    ): Long? {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var best: Long? = null
        for (i in -1 until SEARCH_DAYS) {
            val date = today.plusDays(i.toLong())
            if (alarm.days.isNotEmpty() && date.dayOfWeek !in alarm.days) continue
            val prayerAt = prayerTimeAt(date, alarm.prayer) ?: continue
            val at = prayerAt + alarm.offsetMin * 60_000L
            if (at > nowMillis && (best == null || at < best!!)) best = at
        }
        return best
    }

    /**
     * Every alarm's next fire time, soonest first, dropping those that have none.
     *
     * What both the list page and the widget read: one ordering, computed once, so the page and the
     * home screen cannot disagree about what is coming next.
     */
    fun upcoming(
        alarms: List<Alarm>,
        nowMillis: Long,
        zone: ZoneId,
        prayerTimeAt: (LocalDate, Prayer) -> Long? = { _, _ -> null },
    ): List<Pair<Alarm, Long>> =
        alarms.mapNotNull { a -> nextFire(a, nowMillis, zone, prayerTimeAt)?.let { a to it } }
            .sortedBy { it.second }

    /** Those firing before the end of today, for the widget's "what is left today". */
    fun dueToday(
        alarms: List<Alarm>,
        nowMillis: Long,
        zone: ZoneId,
        prayerTimeAt: (LocalDate, Prayer) -> Long? = { _, _ -> null },
    ): List<Pair<Alarm, Long>> {
        val endOfDay = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return upcoming(alarms, nowMillis, zone, prayerTimeAt).filter { it.second < endOfDay }
    }
}
