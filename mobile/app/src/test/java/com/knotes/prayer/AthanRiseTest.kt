package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class AthanRiseTest {

    @Test
    fun risesFromThirtyDbDownToFullInEvenDbSteps() {
        val (times, volumes) = PrayerSettings.riseCurve(-30, 16)
        assertEquals(0f, times.first())
        assertEquals(1f, times.last())
        assertEquals(1f, volumes.last())
        assertEquals(-30.0, 20 * log10(volumes.first().toDouble()), 0.01)
        // Even in dB: each step adds the same number of decibels, and it only ever goes up.
        val db = volumes.map { 20 * log10(it.toDouble()) }
        val steps = db.zipWithNext { a, b -> b - a }
        steps.forEach { assertEquals(2.0, it, 0.01) }
        assertTrue(volumes.toList().zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun defaultsAreTwentySecondsFromTenDbDown() {
        assertEquals(20, PrayerSettings().athanRiseSeconds)
        assertEquals(-10, PrayerSettings().athanRiseFromDb)
        val (_, volumes) = PrayerSettings.riseCurve()
        assertEquals(-10.0, 20 * log10(volumes.first().toDouble()), 0.01)
    }
}
