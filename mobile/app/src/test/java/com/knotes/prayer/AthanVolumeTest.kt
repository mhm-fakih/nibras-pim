package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The athan volume is a level of the phone's ALARM stream, set while the athan plays — not a share
 * of wherever the alarm slider was left. See PrayerSettings.alarmStepFor.
 */
class AthanVolumeTest {

    @Test
    fun `the ends of the slider are the ends of the stream`() {
        assertEquals(7, PrayerSettings.alarmStepFor(100, 1, 7))
        assertEquals(1, PrayerSettings.alarmStepFor(1, 1, 7))
    }

    @Test
    fun `a percentage is that share of the stream's range`() {
        // A stream of 0..15 steps, as many phones have for alarms.
        assertEquals(4, PrayerSettings.alarmStepFor(25, 0, 15))
        assertEquals(8, PrayerSettings.alarmStepFor(50, 0, 15))
        assertEquals(12, PrayerSettings.alarmStepFor(80, 0, 15))
    }

    @Test
    fun `anything above zero is audible`() {
        // 1% of 0..15 rounds to step 0, which is silence; the athan was asked to play.
        assertEquals(1, PrayerSettings.alarmStepFor(1, 0, 15))
    }

    @Test
    fun `it rises with the setting and stays inside the stream`() {
        var last = 0
        for (p in 1..100) {
            val step = PrayerSettings.alarmStepFor(p, 0, 15)
            assertTrue(step >= last)
            assertTrue(step in 1..15)
            last = step
        }
        assertEquals(15, PrayerSettings.alarmStepFor(400, 0, 15))
        assertEquals(1, PrayerSettings.alarmStepFor(-40, 0, 15))
    }

    @Test
    fun `the gain brings full down to the athan's step`() {
        // A step 20 dB under full is a tenth of the amplitude.
        assertEquals(0.1f, PrayerSettings.athanGain(-30f, -10f, -50f)!!, 1e-4f)
        assertEquals(1f, PrayerSettings.athanGain(-5f, -5f, -50f)!!, 1e-6f)
    }

    @Test
    fun `nonsense levels fall back to setting the step`() {
        assertEquals(null, PrayerSettings.athanGain(Float.NEGATIVE_INFINITY, -5f, -50f))
        assertEquals(null, PrayerSettings.athanGain(-2f, -5f, -50f))
        assertEquals(null, PrayerSettings.athanGain(Float.NaN, 0f, -50f))
    }

    @Test
    fun `a flat curve falls back to setting the step`() {
        // What the Samsung reported: step 2 barely a dB under full, the lowest step not much more.
        assertEquals(null, PrayerSettings.athanGain(-0.96f, 0f, -2f))
    }
}
