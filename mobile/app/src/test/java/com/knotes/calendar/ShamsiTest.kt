package com.knotes.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShamsiTest {

    @Test
    fun knownDates() {
        assertEquals(ShamsiDate(1404, 1, 1), Shamsi.of(LocalDate.of(2025, 3, 21)))
        assertEquals(ShamsiDate(1405, 1, 1), Shamsi.of(LocalDate.of(2026, 3, 21)))
        assertEquals(ShamsiDate(1405, 9, 3), Shamsi.of(LocalDate.of(2026, 11, 24)))
        assertEquals(ShamsiDate(1403, 12, 30), Shamsi.of(LocalDate.of(2025, 3, 20)))
        assertEquals(ShamsiDate(1357, 11, 22), Shamsi.of(LocalDate.of(1979, 2, 11)))
    }

    @Test
    fun roundTripsEveryDayForAHundredYears() {
        var d = LocalDate.of(1950, 1, 1)
        repeat(36_525) {
            val s = Shamsi.of(d)
            assertEquals(d, Shamsi.toGregorian(s.year, s.month, s.day))
            d = d.plusDays(1)
        }
    }

    @Test
    fun weekStartsOnSaturday() {
        assertEquals(0, Shamsi.weekdayIndex(LocalDate.of(2026, 3, 21))) // a Saturday
        assertEquals(6, Shamsi.weekdayIndex(LocalDate.of(2026, 3, 27))) // a Friday
    }
}
