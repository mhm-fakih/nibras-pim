package com.knotes.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalLogTest {

    @Test
    fun firstEntryIsJustTheEntry() {
        assertEquals("[08:05] woke early", JournalLog.append("", "08:05", "  woke early \n"))
        assertEquals("[08:05] woke early", JournalLog.append(null, "08:05", "woke early"))
    }

    @Test
    fun laterEntriesGoBelowABlankLineApart() {
        val day = JournalLog.append("[08:05] woke early\n", "12:30", "line one\nline two")
        assertEquals("[08:05] woke early\n\n[12:30] line one\nline two", day)
    }

    @Test
    fun entriesSplitAtStampsOnly() {
        val day = "[08:05] woke early\n[09:00] not a stamp, mid-entry\n\n[12:30] غداء\nمع العائلة"
        assertEquals(
            listOf(
                JournalLog.Entry("08:05", "woke early\n[09:00] not a stamp, mid-entry"),
                JournalLog.Entry("12:30", "غداء\nمع العائلة"),
            ),
            JournalLog.entries(day),
        )
    }

    @Test
    fun textBeforeTheFirstStampHasNoTime() {
        assertEquals(
            listOf(JournalLog.Entry(null, "written before stamps"), JournalLog.Entry("10:00", "then this")),
            JournalLog.entries("written before stamps\n\n[10:00] then this\n"),
        )
    }
}
