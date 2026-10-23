package com.knotes.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ChecklistTimesTest {

    @Test
    fun `a time before the title is read and left out of the reminder`() {
        assertEquals(LocalTime.of(7, 30), ChecklistTimes.timeOf("07:30 Start of day"))
        assertEquals(LocalTime.of(21, 5), ChecklistTimes.timeOf("21.05 Evening"))
        assertEquals("Start of day", ChecklistTimes.plainTitle("7:30 Start of day"))
    }

    @Test
    fun `no time or an impossible one means no reminder`() {
        assertNull(ChecklistTimes.timeOf("Start of day"))
        assertNull(ChecklistTimes.timeOf("25:00 Late"))
        assertNull(ChecklistTimes.timeOf("07:30"))
        assertEquals("25:00 Late", ChecklistTimes.plainTitle("25:00 Late"))
    }

    @Test
    fun `the time survives the file and only daily groups remind`() {
        val id = WeekId.of(LocalDate.of(2026, 10, 23))
        val text = """
            ## 1 07:30 Start of day
            | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
            |---|---|---|---|---|---|---|---|
            | 1.1 Journal | | | | | ✓ | | |

            ## 2 Mid-day
            | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
            |---|---|---|---|---|---|---|---|
            | 2.1 Quran | | | | | | | |

            ## 3 20:00 Weekly
            | Item | Week |
            |---|---|
            | 3.1 Call | |
        """.trimIndent()
        val week = ChecklistFormat.read(id, ChecklistFormat.write(ChecklistFormat.read(id, text)))
        val due = ChecklistTimes.dueOn(week, LocalDate.of(2026, 10, 23))
        assertEquals(1, due.size)
        assertEquals("Start of day", due[0].title)
        assertEquals(LocalTime.of(7, 30), due[0].at.toLocalTime())
        // Friday is day 4: group 1 is filled in that day, not on Thursday.
        assertTrue(ChecklistTimes.filledIn(week, 1, 4))
        assertFalse(ChecklistTimes.filledIn(week, 1, 3))
    }
}
