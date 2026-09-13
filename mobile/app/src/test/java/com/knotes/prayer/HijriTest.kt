package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HijriTest {

    @Test
    fun knownDatesConvert() {
        // The epoch itself: 1 Muharram 1 AH is 16 July 622 CE in the Julian calendar, which is
        // 19 July 622 proleptic Gregorian.
        assertEquals(HijriDate(1, 1, 1), Hijri.fromGregorian(622, 7, 19))
        // Two starts of Ramadan that the tabular calendar happens to land on exactly: 1 Ramadan
        // 1446 was 1 March 2025, and 1 Ramadan 1447 is 18 February 2026.
        assertEquals(HijriDate(1446, 9, 1), Hijri.fromGregorian(2025, 3, 1))
        assertEquals(HijriDate(1447, 9, 1), Hijri.fromGregorian(2026, 2, 18))
    }

    @Test
    fun monthsAreTwentyNineOrThirtyDaysAndYearsHaveTwelve() {
        // Walk four years day by day: the day number must only ever step by one or restart at 1,
        // and a restart must land on the next month. This catches a boundary the closed-form
        // arithmetic could plausibly get wrong without any single date looking odd.
        var previous = Hijri.fromGregorian(2024, 1, 1)
        // The walk starts mid-month, so the first month we see the end of is a partial one and its
        // length says nothing. -1 marks it as not yet countable.
        var lengthSoFar = -1
        val jdnStart = Hijri.gregorianToJdn(2024, 1, 1)
        for (i in 1..(365 * 4)) {
            val d = Hijri.fromJulianDayNumber(jdnStart + i)
            if (d.day == previous.day + 1) {
                if (lengthSoFar > 0) lengthSoFar++
            } else {
                assertEquals("month should restart at day 1, after ${previous}", 1, d.day)
                if (lengthSoFar > 0) {
                    assertTrue(
                        "month ${previous.month}/${previous.year} ran $lengthSoFar days",
                        lengthSoFar in 29..30,
                    )
                }
                val expectedMonth = if (previous.month == 12) 1 else previous.month + 1
                assertEquals("month after $previous", expectedMonth, d.month)
                if (previous.month == 12) assertEquals(previous.year + 1, d.year)
                lengthSoFar = 1
            }
            assertTrue("month out of range in $d", d.month in 1..12)
            previous = d
        }
    }

    @Test
    fun theOffsetShiftsWholeDaysAndRollsOverMonthEnds() {
        assertEquals(HijriDate(1448, 3, 21), Hijri.fromGregorian(2026, 9, 4))
        assertEquals(HijriDate(1448, 3, 22), Hijri.fromGregorian(2026, 9, 4, offsetDays = 1))
        assertEquals(HijriDate(1448, 3, 20), Hijri.fromGregorian(2026, 9, 4, offsetDays = -1))

        // 17 February 2026 is the last day of Shaʿban 1447; nudging forward must roll into
        // Ramadan rather than inventing a 30th of Shaʿban.
        val eveOfRamadan = Hijri.fromGregorian(2026, 2, 17)
        assertEquals(8, eveOfRamadan.month)
        assertEquals(HijriDate(1447, 9, 1), Hijri.fromGregorian(2026, 2, 17, offsetDays = 1))
    }

    @Test
    fun ramadanIsMonthNine() {
        assertTrue(HijriDate(1448, 9, 1).isRamadan)
        assertEquals("Ramadan", HijriDate(1448, 9, 1).monthName)
        assertEquals("رمضان", HijriDate(1448, 9, 1).monthArabic)
        assertTrue(!HijriDate(1448, 8, 1).isRamadan)
    }

    @Test
    fun gregorianJulianDayNumbersAreContiguous() {
        // A day is a day across a month end, a year end, and a leap day.
        assertEquals(Hijri.gregorianToJdn(2026, 1, 31) + 1, Hijri.gregorianToJdn(2026, 2, 1))
        assertEquals(Hijri.gregorianToJdn(2026, 12, 31) + 1, Hijri.gregorianToJdn(2027, 1, 1))
        assertEquals(Hijri.gregorianToJdn(2024, 2, 28) + 1, Hijri.gregorianToJdn(2024, 2, 29))
        assertEquals(Hijri.gregorianToJdn(2024, 2, 29) + 1, Hijri.gregorianToJdn(2024, 3, 1))
    }
}
