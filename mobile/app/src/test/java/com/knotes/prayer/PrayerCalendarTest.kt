package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerCalendarTest {

    private val beirut: ZoneId = ZoneId.of("Asia/Beirut")
    private val settings = PrayerSettings(latitude = 33.8938, longitude = 35.5018)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = beirut): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun clock(millis: Long, zone: ZoneId = beirut): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
            .toLocalTime().toString().take(5)

    @Test
    fun aDayResolvesToSixSlotsOnTheWallClock() {
        val day = PrayerCalendar.dayOf(settings, LocalDate.of(2026, 9, 4), beirut)
        assertEquals(6, day.slots.size)
        assertEquals("12:37", clock(day.timeOf(Prayer.DHUHR)!!))
        assertEquals("19:00", clock(day.timeOf(Prayer.MAGHRIB)!!))
    }

    @Test
    fun anAdjustmentMovesExactlyThatManyMinutes() {
        val date = LocalDate.of(2026, 9, 4)
        val plain = PrayerCalendar.dayOf(settings, date, beirut).timeOf(Prayer.ASR)!!
        val nudged = PrayerCalendar
            .dayOf(settings.copy(adjustments = mapOf(Prayer.ASR to 7)), date, beirut)
            .timeOf(Prayer.ASR)!!
        assertEquals(7 * 60_000L, nudged - plain)

        val back = PrayerCalendar
            .dayOf(settings.copy(adjustments = mapOf(Prayer.ASR to -12)), date, beirut)
            .timeOf(Prayer.ASR)!!
        assertEquals(-12 * 60_000L, back - plain)
        // One prayer's nudge must not disturb its neighbours.
        assertEquals(
            PrayerCalendar.dayOf(settings, date, beirut).timeOf(Prayer.DHUHR),
            PrayerCalendar.dayOf(settings.copy(adjustments = mapOf(Prayer.ASR to 7)), date, beirut)
                .timeOf(Prayer.DHUHR),
        )
    }

    @Test
    fun adjustmentsAreClampedToTheRangeTheUiOffers() {
        val date = LocalDate.of(2026, 9, 4)
        val plain = PrayerCalendar.dayOf(settings, date, beirut).timeOf(Prayer.FAJR)!!
        val absurd = PrayerCalendar
            .dayOf(settings.copy(adjustments = mapOf(Prayer.FAJR to 9999)), date, beirut)
            .timeOf(Prayer.FAJR)!!
        assertEquals(PrayerSettings.MAX_ADJUST * 60_000L, absurd - plain)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() {
        // 23:30 on 4 September: everything today is spent.
        val next = PrayerCalendar.nextPrayer(settings, beirut, at(2026, 9, 4, 23, 30))
        assertNotNull(next)
        assertEquals(Prayer.FAJR, next!!.prayer)
        assertTrue("should be tomorrow's", next.isTomorrow)
        assertEquals(
            PrayerCalendar.dayOf(settings, LocalDate.of(2026, 9, 5), beirut).timeOf(Prayer.FAJR),
            next.atMillis,
        )
    }

    @Test
    fun theNextPrayerIsTheSoonestOneStillAhead() {
        // 13:00 sits between Dhuhr (12:37) and Asr (16:13).
        val next = PrayerCalendar.nextPrayer(settings, beirut, at(2026, 9, 4, 13, 0))
        assertEquals(Prayer.ASR, next!!.prayer)
        assertTrue(!next.isTomorrow)
        // A time exactly now is already gone — "next" means strictly ahead.
        val dhuhr = PrayerCalendar.dayOf(settings, LocalDate.of(2026, 9, 4), beirut)
            .timeOf(Prayer.DHUHR)!!
        assertEquals(Prayer.ASR, PrayerCalendar.nextPrayer(settings, beirut, dhuhr)!!.prayer)
    }

    @Test
    fun hiddenTimesAreSkippedEntirely() {
        // With Shurooq and Asr hidden, mid-morning must jump straight to Dhuhr.
        val hidden = settings.copy(visible = setOf(Prayer.FAJR, Prayer.DHUHR, Prayer.MAGHRIB, Prayer.ISHA))
        assertEquals(
            Prayer.DHUHR,
            PrayerCalendar.nextPrayer(hidden, beirut, at(2026, 9, 4, 5, 30))!!.prayer,
        )
        assertEquals(
            Prayer.MAGHRIB,
            PrayerCalendar.nextPrayer(hidden, beirut, at(2026, 9, 4, 13, 0))!!.prayer,
        )
        assertEquals(6, PrayerCalendar.dayOf(hidden, LocalDate.of(2026, 9, 4), beirut).slots.size)
        assertEquals(
            4,
            PrayerCalendar.dayOf(hidden, LocalDate.of(2026, 9, 4), beirut).visibleSlots(hidden).size,
        )
    }

    @Test
    fun theAthanSetCanBeAskedForIndependently() {
        // Only Dhuhr and Maghrib call the athan by default, so from dawn the next one is Dhuhr even
        // though Shurooq is nearer.
        val next = PrayerCalendar.nextPrayer(
            settings, beirut, at(2026, 9, 4, 5, 0), among = settings.effectiveAthan,
        )
        assertEquals(Prayer.DHUHR, next!!.prayer)
    }

    @Test
    fun shurooqCanNeverCallTheAthan() {
        val s = PrayerSettings(athanPrayers = setOf(Prayer.SUNRISE, Prayer.FAJR))
        assertEquals(setOf(Prayer.FAJR), s.effectiveAthan)
        assertTrue(!Prayer.SUNRISE.isPrayer)
    }

    @Test
    fun theIslamicDateTurnsOverAtMaghribWhenAsked() {
        val date = LocalDate.of(2026, 9, 4)
        val maghrib = PrayerCalendar.dayOf(settings, date, beirut).timeOf(Prayer.MAGHRIB)!!
        val before = PrayerCalendar.hijriNow(settings, beirut, maghrib - 60_000)
        val after = PrayerCalendar.hijriNow(settings, beirut, maghrib + 60_000)
        assertEquals(before.day + 1, after.day)

        // Switched off, the date follows the civil midnight instead and does not move at sunset.
        val civil = settings.copy(hijriDayStartsAtMaghrib = false)
        assertEquals(
            PrayerCalendar.hijriNow(civil, beirut, maghrib - 60_000),
            PrayerCalendar.hijriNow(civil, beirut, maghrib + 60_000),
        )
    }

    @Test
    fun theHijriOffsetAndTheMaghribRuleCompose() {
        val date = LocalDate.of(2026, 9, 4)
        val maghrib = PrayerCalendar.dayOf(settings, date, beirut).timeOf(Prayer.MAGHRIB)!!
        val shifted = settings.copy(hijriOffsetDays = 1)
        // A +1 nudge and "after sunset" are two separate days of movement, and both must apply.
        assertEquals(
            PrayerCalendar.hijriNow(settings, beirut, maghrib + 60_000).day + 1,
            PrayerCalendar.hijriNow(shifted, beirut, maghrib + 60_000).day,
        )
    }

    @Test
    fun undefinedTimesBecomeNullSlotsAndAreNeverScheduled() {
        // London at midsummer with MWL: no geometric Fajr or Isha.
        val london = PrayerSettings(
            latitude = 51.5074, longitude = -0.1278, method = CalcMethod.MWL,
            highLat = HighLatRule.NONE,
        )
        val zone = ZoneId.of("Europe/London")
        val day = PrayerCalendar.dayOf(london, LocalDate.of(2026, 6, 21), zone)
        assertNull(day.timeOf(Prayer.FAJR))
        assertNull(day.timeOf(Prayer.ISHA))
        assertNotNull(day.timeOf(Prayer.DHUHR))

        // "Next" must step over the hole rather than returning a slot with no time.
        val next = PrayerCalendar.nextPrayer(
            london, zone, at(2026, 6, 21, 2, 0, zone), among = setOf(Prayer.FAJR, Prayer.DHUHR),
        )
        assertEquals(Prayer.DHUHR, next!!.prayer)
    }

    @Test
    fun timesFollowTheWallClockAcrossADaylightSavingChange() {
        // Beirut moves to summer time at 00:00 on the last Sunday of March. Dhuhr must stay near
        // midday on the clock either side of it, rather than jumping an hour.
        val zone = beirut
        val before = PrayerCalendar.dayOf(settings, LocalDate.of(2026, 3, 28), zone)
        val after = PrayerCalendar.dayOf(settings, LocalDate.of(2026, 3, 29), zone)
        val h1 = clock(before.timeOf(Prayer.DHUHR)!!).take(2).toInt()
        val h2 = clock(after.timeOf(Prayer.DHUHR)!!).take(2).toInt()
        assertTrue("Dhuhr before the change was ${clock(before.timeOf(Prayer.DHUHR)!!)}", h1 in 11..12)
        assertTrue("Dhuhr after the change was ${clock(after.timeOf(Prayer.DHUHR)!!)}", h2 in 12..13)
    }

    @Test
    fun prayerSetsRoundTripAndAnEmptySetStaysEmpty() {
        val set = setOf(Prayer.FAJR, Prayer.MAGHRIB)
        assertEquals(set, PrayerSettingsStore.decodePrayers(PrayerSettingsStore.encodePrayers(set)))
        // "Show nothing" must survive a save; only a key that was never written may take a default.
        assertEquals(
            emptySet<Prayer>(),
            PrayerSettingsStore.decodePrayers(PrayerSettingsStore.encodePrayers(emptySet())),
        )
        assertNull(PrayerSettingsStore.decodePrayers(null))
        // Encoding is in prayer order, whatever order the set came in.
        assertEquals(
            "FAJR,ISHA",
            PrayerSettingsStore.encodePrayers(setOf(Prayer.ISHA, Prayer.FAJR)),
        )
    }

    @Test
    fun adjustmentsRoundTripAndDropZeroes() {
        val map = mapOf(Prayer.FAJR to -5, Prayer.ASR to 3, Prayer.ISHA to 0)
        val encoded = PrayerSettingsStore.encodeAdjustments(map)
        assertEquals("FAJR:-5,ASR:3", encoded)
        assertEquals(
            mapOf(Prayer.FAJR to -5, Prayer.ASR to 3),
            PrayerSettingsStore.decodeAdjustments(encoded),
        )
        assertEquals(emptyMap<Prayer, Int>(), PrayerSettingsStore.decodeAdjustments(null))
        assertEquals(emptyMap<Prayer, Int>(), PrayerSettingsStore.decodeAdjustments(""))
        // Junk in a hand-edited preference must not take the whole map down with it.
        assertEquals(
            mapOf(Prayer.ASR to 3),
            PrayerSettingsStore.decodeAdjustments("NOPE:2,ASR:3,malformed"),
        )
    }
}

/** The coordinate display helper — see [formatCoord] for why it exists. */
class CoordFormatTest {

    @Test
    fun coordinatesReadTheWayAPersonWouldWriteThem() {
        assertEquals("33.8938", formatCoord(33.8938))
        assertEquals("-0.1278", formatCoord(-0.1278))
        assertEquals("0", formatCoord(0.0))
        assertEquals("51", formatCoord(51.0))
    }

    @Test
    fun theLongTailOfABinaryFractionIsTrimmed() {
        // 33.89379806518555 is what latitude 33.8938 came back as while the store kept it in a
        // Float. Note what this helper can and cannot do: it trims the tail to something readable,
        // but it cannot recover the 33.8938 that was thrown away — six decimals is not enough, and
        // rounding harder would destroy the precision of coordinates that are genuinely that exact.
        // Keeping the value out of a Float in the first place is the actual fix; this is only the
        // display half of it.
        assertEquals("33.893798", formatCoord(33.89379806518555))
        assertEquals("35.501801", formatCoord(35.50180053710938))
    }

    @Test
    fun aDoubleSurvivesTheStoresEncodingExactly() {
        // The contract PrayerSettingsStore relies on: SharedPreferences has no putDouble, so
        // coordinates go in as the Double's raw bits and must come back bit-identical.
        listOf(33.8938, -0.1278, 35.5018, 69.6492, -180.0, 0.0).forEach {
            assertEquals(it, Double.fromBits(it.toRawBits()), 0.0)
        }
    }

    @Test
    fun sixDecimalsSurviveButNoMore() {
        // ~10 cm of precision is kept; below that it is the binary representation showing through.
        assertEquals("1.234567", formatCoord(1.2345674))
        assertEquals("1.234568", formatCoord(1.2345678))
    }
}
