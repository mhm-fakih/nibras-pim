package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The astronomical core, checked against published sunrise/sunset and against invariants that hold
 * everywhere.
 *
 * Prayer times are the sort of thing that looks plausible while being half an hour out, so the
 * absolute checks below are pinned to figures anyone can look up (London and New York at the
 * solstice) rather than to this code's own output.
 */
class PrayerTimesTest {

    private fun hhmm(hours: Double): String {
        if (hours.isNaN()) return "—"
        val total = Math.round(hours * 60).toInt()
        return "%02d:%02d".format((total / 60) % 24, total % 60)
    }

    /** Assert a computed hour is within [toleranceMin] of an expected "HH:MM". */
    private fun assertNear(expected: String, actual: Double, toleranceMin: Int, what: String) {
        val (h, m) = expected.split(":").map { it.toInt() }
        val diff = abs(actual - (h + m / 60.0)) * 60.0
        assertTrue(
            "$what: expected ~$expected, got ${hhmm(actual)} (${"%.1f".format(diff)} min out)",
            diff <= toleranceMin,
        )
    }

    private fun london(month: Int, day: Int, tz: Double) = PrayerTimes.compute(
        year = 2026, month = month, day = day,
        latitude = 51.5074, longitude = -0.1278, timeZoneOffsetHours = tz,
        method = CalcMethod.MWL, asr = AsrSchool.STANDARD,
    )

    @Test
    fun sunriseAndSunsetMatchPublishedLondonMidsummer() {
        // London, 21 June 2026, BST (+1): sunrise 04:43, sunset 21:21.
        val t = london(6, 21, 1.0)
        assertNear("04:43", t.getValue(Prayer.SUNRISE), 2, "London midsummer sunrise")
        assertNear("21:21", t.getValue(Prayer.MAGHRIB), 2, "London midsummer sunset")
    }

    @Test
    fun sunriseAndSunsetMatchPublishedNewYorkMidsummer() {
        // New York, 21 June 2026, EDT (−4): sunrise 05:25, sunset 20:31.
        val t = PrayerTimes.compute(
            year = 2026, month = 6, day = 21,
            latitude = 40.7128, longitude = -74.0060, timeZoneOffsetHours = -4.0,
            method = CalcMethod.MWL, asr = AsrSchool.STANDARD,
        )
        assertNear("05:25", t.getValue(Prayer.SUNRISE), 2, "New York midsummer sunrise")
        assertNear("20:31", t.getValue(Prayer.MAGHRIB), 2, "New York midsummer sunset")
    }

    @Test
    fun onTheEquatorAtEquinoxTheDayIsTwelveHoursLongAboutSolarNoon() {
        // Longitude 0 with a zero offset makes solar time the wall clock, so the geometry is exact
        // and any slip in the longitude/timezone shift shows up here.
        //
        // Note the day is NOT centred on 12:00: the equation of time runs about −7 minutes in late
        // March, putting solar noon at 12:07. Expecting 06:00/18:00 here is the classic mistake —
        // it is what the sky would do if the earth's orbit were circular and its axis upright.
        val t = PrayerTimes.compute(
            year = 2026, month = 3, day = 20,
            latitude = 0.0, longitude = 0.0, timeZoneOffsetHours = 0.0,
            method = CalcMethod.MWL, asr = AsrSchool.STANDARD,
        )
        val sunrise = t.getValue(Prayer.SUNRISE)
        val sunset = t.getValue(Prayer.MAGHRIB)
        assertNear("12:07", t.getValue(Prayer.DHUHR), 2, "equinox solar noon")
        // Twelve hours geometrically, plus ~3 minutes either side because the standard 0.833°
        // depression counts the sun risen while it is still below the true horizon.
        assertEquals("day length", 12.1, sunset - sunrise, 0.03)
        assertEquals(
            "sunrise and sunset are symmetric about noon",
            t.getValue(Prayer.DHUHR) - sunrise, sunset - t.getValue(Prayer.DHUHR), 1.0 / 60.0,
        )
    }

    @Test
    fun dhuhrIsAlwaysTheMidpointOfSunriseAndSunset() {
        // True by construction — solar noon is what the whole sun-position calculation is built
        // around — so it holds at any latitude and date, and catches a broken equation of time.
        listOf(
            Triple(33.8938, 35.5018, 3.0),    // Beirut
            Triple(21.4225, 39.8262, 3.0),    // Makkah
            Triple(-33.8688, 151.2093, 11.0), // Sydney
            Triple(59.9139, 10.7522, 2.0),    // Oslo
        ).forEach { (lat, lon, tz) ->
            listOf(1 to 15, 4 to 15, 7 to 15, 10 to 15).forEach { (month, day) ->
                val t = PrayerTimes.compute(
                    year = 2026, month = month, day = day,
                    latitude = lat, longitude = lon, timeZoneOffsetHours = tz,
                    method = CalcMethod.MWL, asr = AsrSchool.STANDARD,
                )
                val mid = (t.getValue(Prayer.SUNRISE) + t.getValue(Prayer.MAGHRIB)) / 2.0
                assertEquals(
                    "solar noon at $lat/$lon on $month/$day",
                    mid, t.getValue(Prayer.DHUHR), 1.0 / 60.0,
                )
            }
        }
    }

    @Test
    fun theSixTimesComeOutInOrder() {
        listOf(
            Triple(33.8938, 35.5018, 3.0),
            Triple(21.4225, 39.8262, 3.0),
            Triple(-33.8688, 151.2093, 11.0),
        ).forEach { (lat, lon, tz) ->
            (1..12).forEach { month ->
                val t = PrayerTimes.compute(
                    year = 2026, month = month, day = 15,
                    latitude = lat, longitude = lon, timeZoneOffsetHours = tz,
                    method = CalcMethod.MWL, asr = AsrSchool.STANDARD,
                )
                val ordered = Prayer.entries.map { t.getValue(it) }
                assertEquals(
                    "$lat/$lon month $month came out as ${ordered.map(::hhmm)}",
                    ordered.sorted(), ordered,
                )
            }
        }
    }

    @Test
    fun hanafiAsrIsLaterThanStandardAsr() {
        val std = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.MWL, AsrSchool.STANDARD,
        ).getValue(Prayer.ASR)
        val hanafi = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.MWL, AsrSchool.HANAFI,
        ).getValue(Prayer.ASR)
        assertTrue("Hanafi ${hhmm(hanafi)} should follow standard ${hhmm(std)}", hanafi > std)
        // A second shadow-length is worth roughly an hour in midsummer, not a couple of minutes.
        assertTrue("gap was only ${"%.0f".format((hanafi - std) * 60)} min", hanafi - std > 0.5)
    }

    @Test
    fun onlyTheShiaAndTehranMethodsMoveMaghribOffSunset() {
        val sunset = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.MWL, AsrSchool.STANDARD,
        ).getValue(Prayer.MAGHRIB)
        val jafari = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.JAFARI, AsrSchool.STANDARD,
        ).getValue(Prayer.MAGHRIB)
        // 4° below the horizon is on the order of twenty minutes past sunset at this latitude.
        assertTrue("Jaʿfari maghrib ${hhmm(jafari)} vs sunset ${hhmm(sunset)}", jafari > sunset)
        assertTrue((jafari - sunset) * 60 in 10.0..40.0)
    }

    @Test
    fun ummAlQuraPutsIshaNinetyMinutesAfterMaghrib() {
        val t = PrayerTimes.compute(
            2026, 6, 21, 21.4225, 39.8262, 3.0, CalcMethod.MAKKAH, AsrSchool.STANDARD,
        )
        assertEquals(90.0, (t.getValue(Prayer.ISHA) - t.getValue(Prayer.MAGHRIB)) * 60, 0.5)
    }

    @Test
    fun ummAlQuraStretchesToTwoHoursInRamadan() {
        val t = PrayerTimes.compute(
            2026, 3, 1, 21.4225, 39.8262, 3.0, CalcMethod.MAKKAH, AsrSchool.STANDARD,
            ramadan = true,
        )
        assertEquals(120.0, (t.getValue(Prayer.ISHA) - t.getValue(Prayer.MAGHRIB)) * 60, 0.5)
    }

    @Test
    fun aBritishMidsummerLeavesFajrUndefinedUntilAHighLatitudeRuleIsChosen() {
        // London on 21 June is the case the rules exist for. The sun sets and rises normally, but
        // at solar midnight it is only about 15° below the horizon — so it never reaches the 18°
        // that MWL calls Fajr, and the arc-cosine has no answer.
        fun at(rule: HighLatRule) = PrayerTimes.compute(
            2026, 6, 21, 51.5074, -0.1278, 1.0, CalcMethod.MWL, AsrSchool.STANDARD, highLat = rule,
        )
        assertTrue("expected no geometric Fajr", at(HighLatRule.NONE).getValue(Prayer.FAJR).isNaN())
        assertTrue("expected no geometric Isha", at(HighLatRule.NONE).getValue(Prayer.ISHA).isNaN())

        val sunrise = at(HighLatRule.NONE).getValue(Prayer.SUNRISE)
        val sunset = at(HighLatRule.NONE).getValue(Prayer.MAGHRIB)
        listOf(HighLatRule.MIDDLE_OF_NIGHT, HighLatRule.ONE_SEVENTH, HighLatRule.ANGLE_BASED)
            .forEach { rule ->
                val fajr = at(rule).getValue(Prayer.FAJR)
                val isha = at(rule).getValue(Prayer.ISHA)
                assertTrue("$rule still left Fajr undefined", !fajr.isNaN())
                assertTrue("$rule still left Isha undefined", !isha.isNaN())
                // Whatever the rule, both must land inside the night it is dividing up.
                assertTrue("$rule put Fajr at ${hhmm(fajr)}, outside the night", fajr in sunset - 24..sunrise)
                assertTrue("$rule put Isha at ${hhmm(isha)}, outside the night", isha > sunset)
            }
    }

    @Test
    fun underTheMidnightSunEveryTimeStaysUndefined() {
        // Tromsø on 21 June: the sun does not set at all, so there is no night to take a portion
        // of and no high-latitude rule can rescue anything. Blank rows are the honest answer here,
        // and this pins that rather than leaving it to chance.
        val t = PrayerTimes.compute(
            2026, 6, 21, 69.6492, 18.9553, 2.0, CalcMethod.MWL, AsrSchool.STANDARD,
            highLat = HighLatRule.MIDDLE_OF_NIGHT,
        )
        listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.MAGHRIB, Prayer.ISHA).forEach {
            assertTrue("$it should be undefined under the midnight sun", t.getValue(it).isNaN())
        }
        // Noon and Asr still exist — the sun is up, it just never goes down.
        assertTrue(!t.getValue(Prayer.DHUHR).isNaN())
        assertTrue(!t.getValue(Prayer.ASR).isNaN())
    }

    @Test
    fun aMidLatitudeDayIsUntouchedByAHighLatitudeRule() {
        // The rules must only intervene where the geometry fails; applying one in Beirut should
        // change nothing at all.
        val plain = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.MWL, AsrSchool.STANDARD,
        )
        val ruled = PrayerTimes.compute(
            2026, 6, 21, 33.8938, 35.5018, 3.0, CalcMethod.MWL, AsrSchool.STANDARD,
            highLat = HighLatRule.MIDDLE_OF_NIGHT,
        )
        Prayer.entries.forEach {
            assertEquals(it.name, plain.getValue(it), ruled.getValue(it), 1e-9)
        }
    }

    @Test
    fun earlierFajrAnglesGiveEarlierFajr() {
        // ISNA's 15° is the shallowest of the angle methods and Egypt's 19.5° the deepest, so this
        // ordering is a direct check that the angle reaches the calculation at all.
        fun fajr(m: CalcMethod) = PrayerTimes.compute(
            2026, 9, 4, 33.8938, 35.5018, 3.0, m, AsrSchool.STANDARD,
        ).getValue(Prayer.FAJR)
        assertTrue(fajr(CalcMethod.EGYPT) < fajr(CalcMethod.MWL))
        assertTrue(fajr(CalcMethod.MWL) < fajr(CalcMethod.ISNA))
    }
}
