package com.knotes

import com.knotes.ui.snap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The value grid behind the settings sliders' − / + buttons.
 *
 * The buttons exist because a phone-width track can't be tapped accurately; that only helps if the
 * value they nudge from is itself on the grid. A drag that lands at 0.7749 and a "+" that adds 0.05
 * would settle on 0.8249 — a number the label never shows and the user can never reach twice.
 */
class StepSliderTest {

    private fun eq(expected: Float, actual: Float) =
        assertEquals(expected.toDouble(), actual.toDouble(), 1e-4)

    @Test
    fun snapsToTheNearestStep() {
        eq(1.00f, snap(1.02f, 0.75f, 0.05f))
        eq(1.05f, snap(1.04f, 0.75f, 0.05f))
    }

    /**
     * The grid is measured from the minimum, not from zero. Speed runs 0.75–1.25 in steps of 0.05:
     * from zero those coincide, but a range like 0.8–3.0 in steps of 0.1 does not, and a value
     * off-grid is one the buttons can never return to.
     */
    @Test
    fun theGridStartsAtTheMinimumNotAtZero() {
        eq(0.80f, snap(0.83f, 0.8f, 0.1f))
        eq(0.90f, snap(0.86f, 0.8f, 0.1f))
    }

    @Test
    fun anExactValueIsLeftAlone() {
        eq(1.00f, snap(1.00f, 0.75f, 0.05f))
        eq(-20f, snap(-20f, -20f, 1f))
    }

    /** Whole-number ranges (seconds, decibels) round to the nearest unit in both directions. */
    @Test
    fun wholeNumberRangesRoundBothWays() {
        eq(12f, snap(11.6f, 0f, 1f))
        eq(11f, snap(11.4f, 0f, 1f))
        eq(-5f, snap(-4.6f, -20f, 1f))
    }

    /** A stepless slider is left continuous rather than collapsing every value onto its minimum. */
    @Test
    fun aZeroStepIsANoOp() {
        eq(1.234f, snap(1.234f, 0f, 0f))
    }

    /** Stepping repeatedly must not drift: each result is itself on the grid. */
    @Test
    fun repeatedStepsStayOnTheGrid() {
        var v = 0.75f
        repeat(10) { v = snap(v + 0.05f, 0.75f, 0.05f) }
        eq(1.25f, v)
    }
}
