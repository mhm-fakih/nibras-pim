package com.knotes

import com.knotes.alarm.Alarm
import com.knotes.alarm.AlarmKind
import com.knotes.alarm.AlarmTimes
import com.knotes.alarm.minutesLabel
import com.knotes.alarm.offsetLabel
import com.knotes.prayer.Prayer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an alarm fires next.
 *
 * The cases here are the ones that go wrong silently — an alarm that simply does not sound, which
 * nobody reports as a bug because they assume they set it wrong. A day already past this week, a
 * prayer time that has gone by, a countdown that elapsed while the phone was off, and the clock
 * change that makes "add 24 hours" the wrong arithmetic.
 */
class AlarmTimesTest {

    private val zone = ZoneId.of("Asia/Beirut")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun clock(hour: Int, minute: Int = 0, days: Set<DayOfWeek> = emptySet()) =
        Alarm(id = "a", kind = AlarmKind.CLOCK, hour = hour, minute = minute, days = days)

    // ---- clock alarms --------------------------------------------------------

    @Test
    fun aOneOffClockAlarmTakesTheNextOccurrence() {
        // Thursday 10:00. 07:00 has gone, so tomorrow.
        val now = at(2026, 10, 8, 10, 0)
        assertEquals(at(2026, 10, 9, 7, 0), AlarmTimes.nextFire(clock(7), now, zone))
        // ...and one later today is today.
        assertEquals(at(2026, 10, 8, 22, 30), AlarmTimes.nextFire(clock(22, 30), now, zone))
    }

    @Test
    fun aRepeatingAlarmSkipsDaysItIsNotSetFor() {
        // Thursday 10:00; set for Mondays only -> the coming Monday.
        val now = at(2026, 10, 8, 10, 0)
        val monday = clock(7, days = setOf(DayOfWeek.MONDAY))
        assertEquals(at(2026, 10, 12, 7, 0), AlarmTimes.nextFire(monday, now, zone))
    }

    @Test
    fun todayCountsOnlyIfTheTimeHasNotPassed() {
        val thursday = clock(7, days = setOf(DayOfWeek.THURSDAY))
        // 06:00 on Thursday -> today.
        assertEquals(at(2026, 10, 8, 7, 0), AlarmTimes.nextFire(thursday, at(2026, 10, 8, 6, 0), zone))
        // 08:00 on Thursday -> next Thursday, NOT today. The off-by-one that fires an alarm in the
        // past, or never.
        assertEquals(at(2026, 10, 15, 7, 0), AlarmTimes.nextFire(thursday, at(2026, 10, 8, 8, 0), zone))
    }

    @Test
    fun exactlyNowDoesNotCount_strictlyLater() {
        val now = at(2026, 10, 8, 7, 0)
        // Firing "now" would mean re-arming into the moment that just fired, and looping.
        assertEquals(at(2026, 10, 9, 7, 0), AlarmTimes.nextFire(clock(7), now, zone))
    }

    @Test
    fun aDisabledAlarmHasNoNextTime() {
        assertNull(AlarmTimes.nextFire(clock(7).copy(enabled = false), at(2026, 10, 8, 6, 0), zone))
    }

    // ---- countdowns ----------------------------------------------------------

    @Test
    fun aCountdownFiresOnlyWhileItIsRunning() {
        val now = at(2026, 10, 8, 10, 0)
        val idle = Alarm(id = "c", kind = AlarmKind.COUNTDOWN, durationMin = 30)
        assertNull("a countdown that was never started has no time", AlarmTimes.nextFire(idle, now, zone))

        val running = idle.copy(dueAtMillis = now + 30 * 60_000L)
        assertEquals(now + 30 * 60_000L, AlarmTimes.nextFire(running, now, zone))
    }

    @Test
    fun acountdownThatElapsedWhileThePhoneWasOffDoesNotFireLate() {
        // Re-arming it on boot would be an alarm going off hours after its moment, with no warning.
        val now = at(2026, 10, 8, 10, 0)
        val stale = Alarm(id = "c", kind = AlarmKind.COUNTDOWN, dueAtMillis = now - 60_000L)
        assertNull(AlarmTimes.nextFire(stale, now, zone))
    }

    // ---- prayer-relative -----------------------------------------------------

    /** Fajr 05:00, Maghrib 18:00 every day — enough to pin the arithmetic. */
    private val times: (LocalDate, Prayer) -> Long? = { d, p ->
        when (p) {
            Prayer.FAJR -> at(d.year, d.monthValue, d.dayOfMonth, 5, 0)
            Prayer.MAGHRIB -> at(d.year, d.monthValue, d.dayOfMonth, 18, 0)
            else -> null
        }
    }

    @Test
    fun thirtyMinutesBeforeFajr() {
        val alarm = Alarm(id = "p", kind = AlarmKind.PRAYER, prayer = Prayer.FAJR, offsetMin = -30)
        // 02:00 -> 04:30 today.
        assertEquals(at(2026, 10, 8, 4, 30), AlarmTimes.nextFire(alarm, at(2026, 10, 8, 2, 0), zone, times))
        // 06:00 -> 04:30 TOMORROW, not today's, which has gone.
        assertEquals(at(2026, 10, 9, 4, 30), AlarmTimes.nextFire(alarm, at(2026, 10, 8, 6, 0), zone, times))
    }

    @Test
    fun anOffsetThatCrossesMidnightIsNotSkipped() {
        // 90 minutes before Fajr 05:00 is 03:30 the SAME morning; but 7 hours before is 22:00 the
        // night before. Searching from today only would miss tonight's.
        val alarm = Alarm(id = "p", kind = AlarmKind.PRAYER, prayer = Prayer.FAJR, offsetMin = -7 * 60)
        val now = at(2026, 10, 8, 21, 0)
        // Tomorrow's Fajr 05:00 minus 7h = 22:00 TONIGHT.
        assertEquals(at(2026, 10, 8, 22, 0), AlarmTimes.nextFire(alarm, now, zone, times))
    }

    @Test
    fun afterAPrayerWorksToo() {
        val alarm = Alarm(id = "p", kind = AlarmKind.PRAYER, prayer = Prayer.MAGHRIB, offsetMin = 15)
        assertEquals(at(2026, 10, 8, 18, 15), AlarmTimes.nextFire(alarm, at(2026, 10, 8, 12, 0), zone, times))
    }

    @Test
    fun aPrayerWithNoTimeThatDayIsSkipped_notCrashed() {
        // Isha has no time in this fixture — the high-latitude case the prayer engine models.
        val alarm = Alarm(id = "p", kind = AlarmKind.PRAYER, prayer = Prayer.ISHA, offsetMin = -10)
        assertNull(AlarmTimes.nextFire(alarm, at(2026, 10, 8, 12, 0), zone, times))
    }

    @Test
    fun aPrayerAlarmCanBeLimitedToCertainDays() {
        val alarm = Alarm(
            id = "p", kind = AlarmKind.PRAYER, prayer = Prayer.FAJR, offsetMin = -30,
            days = setOf(DayOfWeek.FRIDAY),
        )
        // Thursday noon -> Friday 04:30.
        assertEquals(at(2026, 10, 9, 4, 30), AlarmTimes.nextFire(alarm, at(2026, 10, 8, 12, 0), zone, times))
    }

    // ---- ordering ------------------------------------------------------------

    @Test
    fun upcomingIsSoonestFirstAndDropsAlarmsWithNoTime() {
        val now = at(2026, 10, 8, 10, 0)
        val later = clock(22).copy(id = "later")
        val sooner = clock(12).copy(id = "sooner")
        val off = clock(11).copy(id = "off", enabled = false)
        val list = AlarmTimes.upcoming(listOf(later, sooner, off), now, zone)
        assertEquals(listOf("sooner", "later"), list.map { it.first.id })
    }

    @Test
    fun dueTodayStopsAtMidnight() {
        val now = at(2026, 10, 8, 23, 0)
        val tonight = clock(23, 30).copy(id = "tonight")
        val tomorrow = clock(7).copy(id = "tomorrow")      // 07:00 has passed -> tomorrow
        val today = AlarmTimes.dueToday(listOf(tonight, tomorrow), now, zone)
        assertEquals(listOf("tonight"), today.map { it.first.id })
        assertEquals(2, AlarmTimes.upcoming(listOf(tonight, tomorrow), now, zone).size)
    }

    // ---- labels --------------------------------------------------------------

    @Test
    fun durationsReadAsPeopleSayThem() {
        assertEquals("5m", minutesLabel(5))
        assertEquals("1h", minutesLabel(60))
        assertEquals("1h 30m", minutesLabel(90))
        assertEquals("2h", minutesLabel(120))
    }

    @Test
    fun prayerOffsetsReadAsPeopleSayThem() {
        assertEquals("30m before Fajr", offsetLabel(Prayer.FAJR, -30))
        assertEquals("at Maghrib", offsetLabel(Prayer.MAGHRIB, 0))
        assertEquals("15m after Isha", offsetLabel(Prayer.ISHA, 15))
        assertEquals("1h 30m before Fajr", offsetLabel(Prayer.FAJR, -90))
    }
}
