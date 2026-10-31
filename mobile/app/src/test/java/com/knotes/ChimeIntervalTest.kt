package com.knotes

import com.knotes.status.ChimeReceiver
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which quarter-hour ticks play the chime, for each interval offered in Settings. */
class ChimeIntervalTest {

    /** The chiming times (as "H:MM") between 08:00 and 12:00 for a window starting at [startHour]. */
    private fun chimes(startHour: Int, intervalMin: Int): List<String> =
        (8 * 60..12 * 60 step 15)
            .filter { ChimeReceiver.shouldChime(it, startHour, 22, intervalMin) }
            .map { "${it / 60}:${"%02d".format(it % 60)}" }

    @Test
    fun everyQuarterHour() {
        assertEquals(17, chimes(8, 15).size)
    }

    @Test
    fun everyHalfHour() {
        assertEquals(listOf("8:00", "8:30", "9:00", "9:30", "10:00", "10:30", "11:00", "11:30", "12:00"), chimes(8, 30))
    }

    @Test
    fun everyHour() {
        assertEquals(listOf("8:00", "9:00", "10:00", "11:00", "12:00"), chimes(8, 60))
    }

    @Test
    fun everyTwoHoursCountsFromTheStartHour() {
        assertEquals(listOf("8:00", "10:00", "12:00"), chimes(8, 120))
        assertEquals(listOf("9:00", "11:00"), chimes(9, 120))
    }

    @Test
    fun nothingOutsideTheWindow() {
        assertEquals(emptyList<String>(), chimes(13, 60))
    }
}
