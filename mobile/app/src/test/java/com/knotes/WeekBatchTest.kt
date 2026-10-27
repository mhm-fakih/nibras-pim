package com.knotes

import com.knotes.nibras.WeekBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime

class WeekBatchTest {

    private fun one(line: String) = WeekBatch.parse(line).single()

    @Test
    fun theBasicLine() {
        val l = one("p mon 10:00 Calculus I")
        assertNull(l.problem)
        val e = l.entries.single()
        assertEquals("P", e.type)
        assertEquals(MONDAY, e.day)
        assertEquals(LocalTime.of(10, 0), e.start)
        assertEquals(LocalTime.of(11, 0), e.end)   // default 60 minutes
        assertEquals("Calculus I", e.title)
    }

    @Test
    fun listsRangesEndsAndTwelveHourTimes() {
        assertEquals(listOf(MONDAY, WEDNESDAY), one("p mon,wed 8:00-9:30 Physics").entries.map { it.day })
        assertEquals(LocalTime.of(9, 30), one("p mon,wed 8:00-9:30 Physics").entries[0].end)
        assertEquals(listOf(TUESDAY, WEDNESDAY, THURSDAY), one("tue-thu 14 Lab").entries.map { it.day })
        assertNull(one("tue-thu 14 Lab").entries[0].type)
        val pm = one("j fri 2:30pm - 4pm Group meeting").entries.single()
        assertEquals("J", pm.type)
        assertEquals(LocalTime.of(14, 30), pm.start)
        assertEquals(LocalTime.of(16, 0), pm.end)
        assertEquals("Group meeting", pm.title)
    }

    @Test
    fun frenchAndArabicDays() {
        assertEquals(FRIDAY, one("p ven 9 Chimie").entries.single().day)
        assertEquals(MONDAY, one("p الاثنين 9:00 رياضيات").entries.single().day)
        assertEquals("رياضيات", one("p الاثنين 9:00 رياضيات").entries.single().title)
    }

    @Test
    fun problemsAndSkippedLines() {
        assertEquals(WeekBatch.Problem.NO_DAY, one("p someday 10 X").problem)
        assertEquals(WeekBatch.Problem.NO_TIME, one("p mon noon X").problem)
        assertEquals(WeekBatch.Problem.BAD_END, one("p mon 10-9 X").problem)
        assertEquals(WeekBatch.Problem.NO_TITLE, one("p mon 10").problem)
        assertEquals(1, WeekBatch.parse("\n# timetable\n\np mon 10 X\n").size)
    }

    @Test
    fun aDayLandsOnOrAfterTheStart() {
        val wednesday = LocalDate.of(2026, 10, 28)
        assertEquals(wednesday, WeekBatch.firstDate(wednesday, WEDNESDAY))
        assertEquals(LocalDate.of(2026, 11, 2), WeekBatch.firstDate(wednesday, MONDAY))
    }
}
