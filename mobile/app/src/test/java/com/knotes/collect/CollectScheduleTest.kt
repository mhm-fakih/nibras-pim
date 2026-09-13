package com.knotes.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** When the daily automatic collection next runs. */
class CollectScheduleTest {

    private fun at(hour: Int, minute: Int, day: Int = 4): Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun readBack(millis: Long): Triple<Int, Int, Int> = Calendar.getInstance().apply {
        timeInMillis = millis
    }.let { Triple(it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    @Test
    fun beforeTheTimeItRunsToday() {
        assertEquals(Triple(4, 15, 0), readBack(CollectScheduler.nextRunMillis(15, 0, at(9, 30))))
    }

    @Test
    fun afterTheTimeItWaitsForTomorrow() {
        assertEquals(Triple(5, 15, 0), readBack(CollectScheduler.nextRunMillis(15, 0, at(16, 0))))
    }

    @Test
    fun theTimeItselfCountsAsPast() {
        // An alarm armed for a moment that has already arrived fires instantly, so saving Settings
        // at exactly 15:00 would run a collection on the spot. Tomorrow is the right answer.
        assertEquals(Triple(5, 15, 0), readBack(CollectScheduler.nextRunMillis(15, 0, at(15, 0))))
    }

    @Test
    fun theResultIsAlwaysInTheFuture() {
        listOf(0 to 0, 3 to 15, 15 to 0, 23 to 55).forEach { (h, m) ->
            listOf(at(0, 0), at(12, 0), at(23, 59)).forEach { now ->
                assertTrue(
                    "next run for $h:$m was not in the future",
                    CollectScheduler.nextRunMillis(h, m, now) > now,
                )
            }
        }
    }

    @Test
    fun anOutOfRangeTimeIsClampedRatherThanRejected() {
        val (_, hour, minute) = readBack(CollectScheduler.nextRunMillis(99, 99, at(0, 30)))
        assertEquals(23, hour)
        assertEquals(59, minute)
    }
}
