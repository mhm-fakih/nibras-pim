package com.knotes

import com.knotes.reminder.PhoneStatusWidget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Volumes widget's bar colours.
 *
 * The bug these exist for: Android does not give every stream the same number of steps — a phone
 * typically gives the ring stream 7 and music 15 — so a rule phrased as "% of max" coloured them by
 * different yardsticks. One notch above the middle is 5/7 = 71% on the ring (orange under the old
 * >60% rule) but 9/15 = 60% on music (green). The ringtone bar therefore sat orange at a level
 * nobody would call high.
 *
 * So the tests that matter are the CROSS-STREAM ones: the same position on two different scales has
 * to produce the same colour.
 */
class VolumeLevelColorTest {

    private val green = 0xFF81C784.toInt()
    private val orange = 0xFFFFB74D.toInt()
    private val red = 0xFFE57373.toInt()

    private fun color(cur: Int, max: Int) = PhoneStatusWidget.levelColor(cur, max)

    /** Ring = 7 steps, music = 15. The pairing below is the exact case that was reported. */
    private val RING = 7
    private val MUSIC = 15

    @Test
    fun oneNotchAboveTheMiddleIsGreenOnBothScales() {
        assertEquals("ring 5/7", green, color(5, RING))
        assertEquals("music 9/15", green, color(9, MUSIC))
    }

    @Test
    fun theMiddleIsGreenOnBothScales() {
        assertEquals("ring 4/7", green, color(4, RING))
        assertEquals("music 8/15", green, color(8, MUSIC))
    }

    @Test
    fun oneNotchBelowMaximumIsOrangeOnBothScales() {
        assertEquals("ring 6/7", orange, color(6, RING))
        assertEquals("music 14/15", orange, color(14, MUSIC))
    }

    @Test
    fun maximumIsRedOnBothScales() {
        assertEquals("ring 7/7", red, color(RING, RING))
        assertEquals("music 15/15", red, color(MUSIC, MUSIC))
    }

    @Test
    fun silentIsGreen() {
        assertEquals(green, color(0, RING))
        assertEquals(green, color(0, MUSIC))
    }

    /** A two-step stream (some call streams are tiny) must not divide by zero or go red at half. */
    @Test
    fun copesWithAVeryCoarseStream() {
        assertEquals(green, color(0, 2))
        assertEquals(green, color(1, 2))
        assertEquals(red, color(2, 2))
    }

    @Test
    fun neverDividesByZero() {
        assertEquals(red, color(0, 0))
    }

    /**
     * The whole point, stated directly: at the same fraction of the way up two different scales,
     * the colour must agree. Checked across every step of the coarse scale.
     */
    @Test
    fun equivalentPositionsAgreeAcrossScales() {
        for (step in 0..RING) {
            val equivalent = step * MUSIC / RING
            assertEquals(
                "ring $step/$RING vs music $equivalent/$MUSIC",
                color(step, RING),
                color(equivalent, MUSIC),
            )
        }
    }

    // ---- streams whose slider does not start at zero ---------------------------------------

    /**
     * The call and alarm streams on a real device (a Galaxy A07) report Min 1, Max 15: Android
     * will not let either be silenced from its slider, so index 1 IS the bottom of that slider.
     * Drawing them from zero puts the bar — and this colour — above where the system slider sits.
     *
     * [PhoneStatusWidget.levelColor] takes positions on the stream's own range, so the caller
     * subtracts the minimum. These check that the shifted range behaves.
     */
    @Test
    fun theBottomOfAStreamThatStartsAtOneIsTreatedAsTheBottom() {
        // cur = min = 1 on a 1..15 stream -> position 0 of a 14-step span.
        assertEquals(green, color(0, 14))
    }

    @Test
    fun theTopOfAShiftedRangeIsStillRed() {
        // cur = max = 15 on a 1..15 stream -> position 14 of 14.
        assertEquals(red, color(14, 14))
    }

    /**
     * The measured case: call volume 11 of 1..15 is 10/14 = 71% — where Android's slider shows it.
     * Read from zero it was 11/15 = 73%, which crosses nothing here but shifts every bar up.
     */
    @Test
    fun aMidHighShiftedLevelIsNotYetNearTheTop() {
        assertEquals(green, color(10, 14))
    }
}
