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
}
