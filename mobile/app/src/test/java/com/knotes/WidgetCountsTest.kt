package com.knotes

import com.knotes.reminder.WidgetCounts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one rule that decides a widget count's colour.
 *
 * Worth pinning because the two thresholds run in OPPOSITE directions — too many notes is the
 * problem, too few plans is the problem — and a widget that got one backwards would look entirely
 * plausible while saying the reverse of the truth.
 */
class WidgetCountsTest {

    @Test
    fun topPriorityTurnsRedOnlyWhenThePileIsTooBig() {
        fun colour(n: Int) = WidgetCounts.judged(n, good = n <= WidgetCounts.TOO_MANY_NOTES).color
        assertEquals(WidgetCounts.GOOD, colour(0))
        assertEquals(WidgetCounts.GOOD, colour(WidgetCounts.TOO_MANY_NOTES))
        assertEquals(WidgetCounts.BAD, colour(WidgetCounts.TOO_MANY_NOTES + 1))
        assertEquals(WidgetCounts.BAD, colour(500))
    }

    @Test
    fun upcomingPlansTurnRedWhenThereAreTooFew() {
        // The other way round: an empty week ahead is the warning, not a full one.
        fun colour(n: Int) = WidgetCounts.judged(n, good = n >= WidgetCounts.TOO_FEW_PLANS).color
        assertEquals(WidgetCounts.BAD, colour(0))
        assertEquals(WidgetCounts.BAD, colour(WidgetCounts.TOO_FEW_PLANS - 1))
        assertEquals(WidgetCounts.GOOD, colour(WidgetCounts.TOO_FEW_PLANS))
        assertEquals(WidgetCounts.GOOD, colour(50))
    }

    @Test
    fun sevenIsThePlansThresholdAndTwentyTheNotesOne() {
        assertEquals(7, WidgetCounts.TOO_FEW_PLANS)
        assertEquals(20, WidgetCounts.TOO_MANY_NOTES)
    }

    @Test
    fun aPlainCountCarriesNoJudgement() {
        // Bookmarks and today's tasks are stated, not assessed — neither too many nor too few is a
        // meaningful thing to say about them, so neither green nor red would be honest.
        assertEquals(WidgetCounts.NEUTRAL, WidgetCounts.plain(0).color)
        assertEquals(WidgetCounts.NEUTRAL, WidgetCounts.plain(999).color)
        assertEquals("999", WidgetCounts.plain(999).text)
    }

    @Test
    fun theKpiWidgetAndTheBadgesShareTheSameThresholds() {
        // They are two views of the same numbers on the same home screen; disagreeing about which
        // are worrying would be worse than either being wrong on its own.
        assertEquals(WidgetCounts.TOO_MANY_NOTES, com.knotes.reminder.KpiWidget.TOO_MANY_NOTES)
        assertEquals(WidgetCounts.TOO_FEW_PLANS, com.knotes.reminder.KpiWidget.TOO_FEW_PLANS)
    }
}
