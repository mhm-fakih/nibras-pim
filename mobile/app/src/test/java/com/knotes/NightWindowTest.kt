package com.knotes

import com.knotes.player.NightWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class NightWindowTest {

    @Test
    fun wrapsPastMidnight() {
        assertTrue(NightWindow.contains(21, 21, 5))
        assertTrue(NightWindow.contains(23, 21, 5))
        assertTrue(NightWindow.contains(0, 21, 5))
        assertTrue(NightWindow.contains(4, 21, 5))
        assertFalse(NightWindow.contains(5, 21, 5))
        assertFalse(NightWindow.contains(12, 21, 5))
        assertFalse(NightWindow.contains(20, 21, 5))
    }

    @Test
    fun sameDayWindowAndEmptyWindow() {
        assertTrue(NightWindow.contains(1, 0, 6))
        assertFalse(NightWindow.contains(6, 0, 6))
        assertFalse(NightWindow.contains(9, 9, 9))
    }

    @Test
    fun nightEndsAtTheNextEndHour() {
        val lateEvening = GregorianCalendar(2026, Calendar.OCTOBER, 27, 23, 10)
        assertEquals(GregorianCalendar(2026, Calendar.OCTOBER, 28, 5, 0).timeInMillis, NightWindow.nextEnd(lateEvening, 5))
        val earlyMorning = GregorianCalendar(2026, Calendar.OCTOBER, 28, 2, 0)
        assertEquals(GregorianCalendar(2026, Calendar.OCTOBER, 28, 5, 0).timeInMillis, NightWindow.nextEnd(earlyMorning, 5))
    }
}
