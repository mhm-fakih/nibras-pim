package com.knotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * When a second tap of the bookmark button bumps the previous mark's priority, and when it makes a
 * new one.
 *
 * The rule used to be "was the tap quick?" alone, which loses bookmarks: two taps a few seconds
 * apart at genuinely different points in the audio are both quick, so the second silently
 * re-labelled the first and the spot you meant to mark was never recorded. The playhead has to
 * still be AT the previous mark as well.
 */
class BookmarkDoubleTapTest {

    /** The decision as `addBookmark` makes it. */
    private fun bumps(
        msSinceLastTap: Long,
        prevPosMs: Long,
        newPosMs: Long,
        windowSecs: Int = 5,
        speed: Float = 1.0f,
    ): Boolean {
        val windowMs = windowSecs * 1000L
        val near = abs(newPosMs - prevPosMs) <= (windowMs * speed).toLong() + 1_000L
        return msSinceLastTap <= windowMs && near
    }

    @Test
    fun `a genuine double tap in the same place bumps the priority`() {
        // Tapped twice within a second; the playhead has barely moved.
        assertTrue(bumps(msSinceLastTap = 600, prevPosMs = 60_000, newPosMs = 60_600))
    }

    @Test
    fun `two quick taps at different points make two bookmarks, not one bumped`() {
        // THE BUG. Both taps are inside the 5s window, but a minute of audio separates them — the
        // second is a new mark, and treating it as a priority bump threw it away.
        assertFalse(bumps(msSinceLastTap = 3_000, prevPosMs = 60_000, newPosMs = 120_000))
    }

    @Test
    fun `a tap after a seek is a new bookmark even if it came quickly`() {
        // Scrubbing elsewhere and marking is the same shape: quick, but nowhere near.
        assertFalse(bumps(msSinceLastTap = 1_200, prevPosMs = 600_000, newPosMs = 30_000))
    }

    @Test
    fun `a slow second tap in the same place is a new bookmark`() {
        // The temporal guard still stands on its own — pausing to think, then tapping again, means
        // a second mark at that spot rather than a bump.
        assertFalse(bumps(msSinceLastTap = 20_000, prevPosMs = 60_000, newPosMs = 60_100))
    }

    @Test
    fun `the tolerance follows playback speed`() {
        // 5s of listening at 1.5x advances 7.5s; at 1x the same distance is out of range. Without
        // the scaling, double-tapping would stop working for anyone who speeds playback up.
        assertTrue(bumps(msSinceLastTap = 4_000, prevPosMs = 60_000, newPosMs = 67_000, speed = 1.5f))
        assertFalse(bumps(msSinceLastTap = 4_000, prevPosMs = 60_000, newPosMs = 67_000, speed = 1.0f))
    }
}
