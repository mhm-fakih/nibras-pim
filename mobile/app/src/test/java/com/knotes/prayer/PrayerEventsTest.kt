package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerEventsTest {

    private val beirut: ZoneId = ZoneId.of("Asia/Beirut")
    private val date: LocalDate = LocalDate.of(2026, 9, 4)

    private fun base() = PrayerSettings(
        latitude = 33.8938, longitude = 35.5018,
        reminderEnabled = false, silenceEnabled = false, beepsEnabled = false,
        athanPrayers = emptySet(),
    )

    private fun at(h: Int, m: Int): Long =
        LocalDateTime.of(2026, 9, 4, h, m).atZone(beirut).toInstant().toEpochMilli()

    private fun events(s: PrayerSettings) =
        PrayerEvents.forDay(s, PrayerCalendar.dayOf(s, date, beirut))

    @Test
    fun everythingOffMeansNoEvents() {
        assertEquals(emptyList<PrayerEvent>(), events(base()))
    }

    @Test
    fun theMasterSwitchSilencesEverything() {
        val busy = base().copy(
            enabled = false, reminderEnabled = true, beepsEnabled = true,
            athanPrayers = Prayer.entries.toSet(),
        )
        assertEquals(emptyList<PrayerEvent>(), events(busy))
        assertNull(PrayerEvents.next(busy, beirut, at(3, 0)))
    }

    @Test
    fun athanFiresOnlyForTheChosenPrayers() {
        val s = base().copy(athanPrayers = setOf(Prayer.DHUHR, Prayer.MAGHRIB))
        val athans = events(s).filter { it.kind == PrayerEventKind.ATHAN }
        assertEquals(listOf(Prayer.DHUHR, Prayer.MAGHRIB), athans.map { it.prayer })
        // And exactly at the prayer's own time, not a minute either side.
        val day = PrayerCalendar.dayOf(s, date, beirut)
        assertEquals(day.timeOf(Prayer.DHUHR), athans.first().atMillis)
    }

    @Test
    fun shurooqNeverGetsAnAthanEvenIfItIsStored() {
        val s = base().copy(athanPrayers = setOf(Prayer.SUNRISE, Prayer.DHUHR))
        assertEquals(
            listOf(Prayer.DHUHR),
            events(s).filter { it.kind == PrayerEventKind.ATHAN }.map { it.prayer },
        )
    }

    @Test
    fun theReminderLandsExactlyItsLeadBeforeEachVisiblePrayer() {
        val s = base().copy(
            reminderEnabled = true, reminderLeadMin = 10,
            visible = setOf(Prayer.DHUHR, Prayer.ASR),
        )
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val reminders = events(s).filter { it.kind == PrayerEventKind.REMINDER }
        assertEquals(listOf(Prayer.DHUHR, Prayer.ASR), reminders.map { it.prayer })
        assertEquals(day.timeOf(Prayer.DHUHR)!! - 10 * 60_000L, reminders[0].atMillis)
        assertEquals(day.timeOf(Prayer.ASR)!! - 10 * 60_000L, reminders[1].atMillis)
    }

    @Test
    fun hidingARowRemovesItsReminderButNotItsAthan() {
        // The two selections are independent on purpose: hiding Isha from the list should not
        // silently cancel the athan the user explicitly asked for.
        val s = base().copy(
            reminderEnabled = true,
            visible = setOf(Prayer.DHUHR),
            athanPrayers = setOf(Prayer.ISHA),
        )
        val kinds = events(s).groupBy({ it.kind }, { it.prayer })
        assertEquals(listOf(Prayer.DHUHR), kinds[PrayerEventKind.REMINDER])
        assertEquals(listOf(Prayer.ISHA), kinds[PrayerEventKind.ATHAN])
    }

    @Test
    fun silenceComesInPairsAndLastsTheConfiguredSpan() {
        val s = base().copy(
            silenceEnabled = true, silenceMinutes = 20,
            silencePrayers = setOf(Prayer.MAGHRIB),
        )
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val starts = events(s).filter { it.kind == PrayerEventKind.SILENCE_START }
        val ends = events(s).filter { it.kind == PrayerEventKind.SILENCE_END }
        assertEquals(1, starts.size)
        assertEquals(1, ends.size)
        assertEquals(day.timeOf(Prayer.MAGHRIB), starts[0].atMillis)
        assertEquals(20 * 60_000L, ends[0].atMillis - starts[0].atMillis)
    }

    @Test
    fun aSilenceThatRunsPastMidnightIsStillFoundAfterMidnight() {
        // Paris in mid-May: Isha falls at 23:46, so a 30-minute silence ends at 00:16 the next
        // morning — an event that belongs to the previous civil day's list but has not happened
        // yet. Missing it would leave the ringer off all night.
        val zone = ZoneId.of("Europe/Paris")
        val s = PrayerSettings(
            latitude = 48.8566, longitude = 2.3522, method = CalcMethod.MWL,
            reminderEnabled = false, beepsEnabled = false, athanPrayers = emptySet(),
            silenceEnabled = true, silenceMinutes = 30, silencePrayers = setOf(Prayer.ISHA),
        )
        val day = PrayerCalendar.dayOf(s, LocalDate.of(2026, 5, 15), zone)
        val silenceEnd = day.timeOf(Prayer.ISHA)!! + 30 * 60_000L
        val justAfterMidnight = LocalDateTime.of(2026, 5, 16, 0, 1)
            .atZone(zone).toInstant().toEpochMilli()
        assertTrue("expected the silence to end after midnight", silenceEnd > justAfterMidnight)

        val next = PrayerEvents.next(s, zone, justAfterMidnight)
        assertEquals(PrayerEventKind.SILENCE_END, next!!.kind)
        assertEquals(silenceEnd, next.atMillis)
    }

    @Test
    fun anIshaAfterMidnightStaysWithTheDayItBelongsTo() {
        // Paris at midsummer: Isha is at 01:07, i.e. on the following calendar date. It is still
        // the 21st of June's Isha, and the calculation hands it over as "25.1 hours" — so this is
        // also the check that the hour-to-instant conversion rolls the day rather than wrapping.
        val zone = ZoneId.of("Europe/Paris")
        val s = PrayerSettings(
            latitude = 48.8566, longitude = 2.3522, method = CalcMethod.MWL,
            reminderEnabled = false, silenceEnabled = false, beepsEnabled = false,
            athanPrayers = setOf(Prayer.ISHA),
        )
        val day = PrayerCalendar.dayOf(s, LocalDate.of(2026, 6, 21), zone)
        val isha = day.timeOf(Prayer.ISHA)!!
        val midnight = LocalDateTime.of(2026, 6, 22, 0, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue("Isha should land after midnight", isha > midnight)
        // And it must still come out after Maghrib rather than at the wrong end of the day.
        assertTrue(isha > day.timeOf(Prayer.MAGHRIB)!!)
        assertEquals(isha, PrayerEvents.forDay(s, day).single().atMillis)
    }

    @Test
    fun beepsSitTheirOwnLeadBeforeSunriseAndIgnoreVisibility() {
        val s = base().copy(
            beepsEnabled = true, beepsLeadMin = 15, beepCount = 4,
            visible = setOf(Prayer.DHUHR),   // Shurooq hidden from the list
        )
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val beeps = events(s).filter { it.kind == PrayerEventKind.BEEPS }
        assertEquals(1, beeps.size)
        assertEquals(Prayer.SUNRISE, beeps[0].prayer)
        assertEquals(day.timeOf(Prayer.SUNRISE)!! - 15 * 60_000L, beeps[0].atMillis)
    }

    @Test
    fun eventsComeOutInTimeOrder() {
        val s = base().copy(
            reminderEnabled = true, beepsEnabled = true, silenceEnabled = true,
            athanPrayers = Prayer.entries.toSet(),
            silencePrayers = Prayer.entries.toSet(),
        )
        val times = events(s).map { it.atMillis }
        assertEquals(times.sorted(), times)
        assertTrue("expected a full day of events, got ${times.size}", times.size > 15)
    }

    @Test
    fun nextPicksTheSoonestEventAcrossAllKinds() {
        val s = base().copy(
            reminderEnabled = true, reminderLeadMin = 10,
            visible = setOf(Prayer.DHUHR),
            athanPrayers = setOf(Prayer.DHUHR),
        )
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val dhuhr = day.timeOf(Prayer.DHUHR)!!
        // Well before both: the reminder is first.
        val early = PrayerEvents.next(s, beirut, at(8, 0))!!
        assertEquals(PrayerEventKind.REMINDER, early.kind)
        assertEquals(dhuhr - 10 * 60_000L, early.atMillis)
        // Between the two: the athan is next.
        val later = PrayerEvents.next(s, beirut, dhuhr - 5 * 60_000L)!!
        assertEquals(PrayerEventKind.ATHAN, later.kind)
        assertEquals(dhuhr, later.atMillis)
    }

    @Test
    fun afterTheLastEventOfTheDayNextRollsIntoTomorrow() {
        val s = base().copy(athanPrayers = setOf(Prayer.FAJR))
        val next = PrayerEvents.next(s, beirut, at(23, 0))!!
        val tomorrowFajr = PrayerCalendar
            .dayOf(s, LocalDate.of(2026, 9, 5), beirut).timeOf(Prayer.FAJR)
        assertEquals(PrayerEventKind.ATHAN, next.kind)
        assertEquals(tomorrowFajr, next.atMillis)
    }

    @Test
    fun anUndefinedPrayerSchedulesNothingAtAll() {
        // London midsummer: no geometric Isha, so an Isha athan simply has no moment to fire at.
        val s = PrayerSettings(
            latitude = 51.5074, longitude = -0.1278, method = CalcMethod.MWL,
            reminderEnabled = false, silenceEnabled = false, beepsEnabled = false,
            athanPrayers = setOf(Prayer.ISHA),
        )
        val zone = ZoneId.of("Europe/London")
        val day = PrayerCalendar.dayOf(s, LocalDate.of(2026, 6, 21), zone)
        assertEquals(emptyList<PrayerEvent>(), PrayerEvents.forDay(s, day))
    }

    @Test
    fun `the countdown wake-up lands one window before each shown time`() {
        // The widget redraws only when an event fires, so this alarm is what makes the countdown
        // appear at the start of its window rather than whenever the next alarm happened to be.
        val s = base().copy(countdownWithinMin = 60, elapsedForMin = 0)
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val events = PrayerEvents.forDay(s, day, countdownWakeups = true)
            .filter { it.kind == PrayerEventKind.COUNTDOWN }

        // With the elapsed timer off: two per shown time: one when the countdown becomes due, one AT the time so the strip
        // flips from "بعد" to "منذ" instead of counting into negatives.
        assertEquals(s.visible.size * 2, events.size)
        events.groupBy { it.prayer }.forEach { (prayer, list) ->
            val prayerAt = day.timeOf(prayer)!!
            assertEquals(
                listOf(prayerAt - 60 * 60_000L, prayerAt),
                list.map { it.atMillis }.sorted(),
            )
        }
    }

    @Test
    fun `the elapsed timer adds a wake-up when its window closes`() {
        // Without it the strip would keep counting "منذ" past the window, until the next event.
        val s = base().copy(countdownWithinMin = 60, elapsedForMin = 30)
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val events = PrayerEvents.forDay(s, day, countdownWakeups = true)
            .filter { it.kind == PrayerEventKind.COUNTDOWN }
        assertEquals(s.visible.size * 3, events.size)
        events.groupBy { it.prayer }.forEach { (prayer, list) ->
            val prayerAt = day.timeOf(prayer)!!
            assertEquals(
                listOf(prayerAt - 60 * 60_000L, prayerAt, prayerAt + 30 * 60_000L),
                list.map { it.atMillis }.sorted(),
            )
        }
    }

    @Test
    fun `the recent prayer is the one that arrived inside the elapsed window`() {
        val s = base().copy(elapsedForMin = 30)
        val day = PrayerCalendar.dayOf(s, date, beirut)
        val dhuhr = day.timeOf(Prayer.DHUHR)!!
        assertEquals(null, PrayerCalendar.recentPrayer(s, beirut, dhuhr - 1_000L))
        assertEquals(Prayer.DHUHR, PrayerCalendar.recentPrayer(s, beirut, dhuhr)?.prayer)
        assertEquals(Prayer.DHUHR, PrayerCalendar.recentPrayer(s, beirut, dhuhr + 29 * 60_000L)?.prayer)
        assertEquals(null, PrayerCalendar.recentPrayer(s, beirut, dhuhr + 30 * 60_000L))
        // Off means off.
        assertEquals(null, PrayerCalendar.recentPrayer(s.copy(elapsedForMin = 0), beirut, dhuhr + 60_000L))
    }

    @Test
    fun `no countdown wake-ups without a widget to redraw`() {
        // The default. An alarm whose only effect is redrawing a widget nobody has placed is pure
        // cost, so the scheduler asks AppWidgetManager before turning these on.
        val s = base().copy(countdownWithinMin = 60)
        val day = PrayerCalendar.dayOf(s, date, beirut)
        assertTrue(PrayerEvents.forDay(s, day).none { it.kind == PrayerEventKind.COUNTDOWN })
    }

    @Test
    fun `a wider window moves the wake-up earlier, not the prayer`() {
        val day = PrayerCalendar.dayOf(base(), date, beirut)
        fun firstCountdown(minutes: Int): Long =
            PrayerEvents.forDay(base().copy(countdownWithinMin = minutes), day, countdownWakeups = true)
                .filter { it.kind == PrayerEventKind.COUNTDOWN && it.prayer == Prayer.DHUHR }
                .minOf { it.atMillis }

        val dhuhr = day.timeOf(Prayer.DHUHR)!!
        assertEquals(dhuhr - 30 * 60_000L, firstCountdown(30))
        assertEquals(dhuhr - 120 * 60_000L, firstCountdown(120))
    }

    @Test
    fun anAthanAndItsSilenceAreBothDueAtTheSameInstant() {
        // One alarm is armed per instant, so the receiver must find BOTH here — acting on the
        // event the alarm carried alone used to leave the ringer on through the prayer.
        val s = base().copy(
            athanPrayers = setOf(Prayer.MAGHRIB),
            silenceEnabled = true, silencePrayers = setOf(Prayer.MAGHRIB),
        )
        val maghrib = PrayerCalendar.dayOf(s, date, beirut).timeOf(Prayer.MAGHRIB)!!
        val due = PrayerEvents.at(s, beirut, maghrib).map { it.kind }.toSet()
        assertEquals(setOf(PrayerEventKind.ATHAN, PrayerEventKind.SILENCE_START), due)
    }

    @Test
    fun theAlarmChainSkipsTheWidgetsCountdownWakeUps() {
        // The athan rides its own chain: a countdown redraw armed ahead of it must not be a link
        // that can hold it back.
        val s = base().copy(athanPrayers = setOf(Prayer.DHUHR))
        val dhuhr = PrayerCalendar.dayOf(s, date, beirut).timeOf(Prayer.DHUHR)!!
        val kinds = PrayerEventKind.entries.toSet() - PrayerEventKind.COUNTDOWN
        val next = PrayerEvents.next(s, beirut, dhuhr - 2 * 3_600_000L, countdownWakeups = true, kinds = kinds)!!
        assertEquals(PrayerEventKind.ATHAN, next.kind)
        assertEquals(dhuhr, next.atMillis)
    }
}
