package com.knotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The name-only filter behind the Today's journal widget.
 *
 * Same predicate as the tasks widget, asked a different question: **null priority, meaning any**.
 * A task list is a do-this-first ranking so that widget filters on the top priority; a day's
 * journal is whatever was written today, and filtering it would hide entries for a reason nobody
 * applies to a diary. That one nullable argument is the entire difference between the two widgets'
 * data, which is why it is worth a test of its own.
 */
class TodayJournalTest {

    private val format = "yyyy_MM_dd-HHmmss"

    private fun name(millis: Long, type: String, priority: Int, category: String = ""): String {
        val stamp = SimpleDateFormat(format, Locale.US).format(Date(millis))
        return "$stamp~$type~p$priority~$category.md"
    }

    private fun midnight(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun isTodayJournal(fileName: String) =
        isTaskNameSince(fileName, "J", null, format, midnight())

    @Test
    fun `a journal entry written today is found at every priority`() {
        val nine = midnight() + 9 * 3600_000L
        for (p in 1..5) {
            assertTrue("p$p should be listed", isTodayJournal(name(nine, "J", p)))
        }
    }

    @Test
    fun `a task written today is not a journal entry`() {
        // The two widgets sit on the same home screen reading the same folder; the type is the only
        // thing keeping their lists apart.
        assertFalse(isTodayJournal(name(midnight() + 3600_000L, "T", 4)))
    }

    @Test
    fun `yesterday's journal is gone`() {
        // The widget empties itself overnight — that is what makes it today's writing rather than
        // an archive that only grows.
        assertFalse(isTodayJournal(name(midnight() - 60_000L, "J", 2)))
    }

    @Test
    fun `a note that is not in the canonical form is ignored`() {
        assertFalse(isTodayJournal("notes about the day.md"))
        assertFalse(isTodayJournal("J~p2~.md"))
    }

    @Test
    fun `the tasks widget still filters on its priority`() {
        // Guards the change that made priority nullable: passing a priority must still exclude the
        // others, or today's tasks would silently become today's everything.
        val nine = midnight() + 9 * 3600_000L
        assertTrue(isTaskNameSince(name(nine, "T", 4), "T", 4, format, midnight()))
        assertFalse(isTaskNameSince(name(nine, "T", 2), "T", 4, format, midnight()))
    }
}
