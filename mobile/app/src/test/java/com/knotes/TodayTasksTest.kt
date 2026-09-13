package com.knotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The name-only filter behind the Today's tasks widget.
 *
 * Answering from the file name is the whole point — the widget redraws on the main thread and must
 * not read every note in the folder to find three — so these check that the name alone is enough,
 * and that it is not too generous.
 */
class TodayTasksTest {

    private val format = "yyyy_MM_dd-HHmmss"

    /** A canonical note name written at [millis], with the given type and priority. */
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

    private fun isTodayTask(fileName: String) =
        isTaskNameSince(fileName, "T", 4, format, midnight())

    @Test
    fun aTaskWrittenTodayIsFound() {
        assertTrue(isTodayTask(name(midnight() + 9 * 3600_000L, "T", 4)))
        // With a category, which the widget's own saves carry.
        assertTrue(isTodayTask(name(midnight() + 9 * 3600_000L, "T", 4, category = "P")))
    }

    @Test
    fun yesterdaysTaskIsNot() {
        assertFalse(isTodayTask(name(midnight() - 60_000L, "T", 4)))
        assertFalse(isTodayTask(name(midnight() - 5L * 24 * 3600_000L, "T", 4)))
    }

    @Test
    fun midnightItselfCounts() {
        // The cut-off is inclusive: a task written at 00:00:00 belongs to today, not yesterday.
        assertTrue(isTodayTask(name(midnight(), "T", 4)))
    }

    @Test
    fun anotherTypeOrPriorityIsNot() {
        val today = midnight() + 3600_000L
        assertFalse("a work note is not a task", isTodayTask(name(today, "W", 4)))
        assertFalse("p3 is not the top priority", isTodayTask(name(today, "T", 3)))
        assertFalse("an untyped note is not a task", isTodayTask(name(today, "", 4)))
    }

    @Test
    fun theTypeMatchIsCaseInsensitive() {
        // Notes arrive through Syncthing from several machines; a lower-case code is still the
        // same type, and dropping it would silently lose the task.
        assertTrue(isTodayTask(name(midnight() + 3600_000L, "t", 4)))
    }

    @Test
    fun theDeDupSuffixDoesNotHideATask() {
        // Two notes saved in the same second get "-2" on the second one. That is part of the file
        // name, not of the time, and the timestamp still has to parse through it.
        val stamp = SimpleDateFormat(format, Locale.US).format(Date(midnight() + 3600_000L))
        assertTrue(isTodayTask("$stamp-2~T~p4~.md"))
        assertTrue(isTodayTask("$stamp-17~T~p4~.md"))
    }

    @Test
    fun filesThatAreNotNotesAreIgnored() {
        val stamp = SimpleDateFormat(format, Locale.US).format(Date(midnight() + 3600_000L))
        assertFalse("a foreign markdown file", isTodayTask("README.md"))
        assertFalse("a legacy .txt note", isTodayTask("$stamp~T~p4~.txt"))
        assertFalse("no priority token", isTodayTask("$stamp~T~~.md"))
        assertFalse("a companion folder", isTodayTask("$stamp~T~p4~.d"))
        assertFalse("the app's own database", isTodayTask("notes.sqlite"))
        assertFalse("empty", isTodayTask(""))
    }

    @Test
    fun aNoteStillResolvesWhenTheConfiguredFormatDoesNotFit() {
        // The canonical name shape is fixed whatever the timestamp-format setting says, so a note
        // must not become invisible just because someone changed that setting. Without the
        // fallback this would report that nothing had been written today, ever.
        val today = name(midnight() + 3600_000L, "T", 4)
        assertTrue(isTaskNameSince(today, "T", 4, "dd/MM/yyyy HH:mm", midnight()))
        // And the fallback must not make everything match: yesterday is still yesterday.
        assertFalse(
            isTaskNameSince(name(midnight() - 60_000L, "T", 4), "T", 4, "dd/MM/yyyy HH:mm", midnight()),
        )
    }

    @Test
    fun startOfTodayIsMidnightThisMorning() {
        val start = startOfToday()
        val now = System.currentTimeMillis()
        assertTrue("should not be in the future", start <= now)
        assertTrue("should be within the last 24 hours", now - start < 24 * 3600_000L)
        val c = Calendar.getInstance().apply { timeInMillis = start }
        assertTrue(
            "should land on midnight, was ${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}",
            c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0 &&
                c.get(Calendar.SECOND) == 0 && c.get(Calendar.MILLISECOND) == 0,
        )
    }
}
