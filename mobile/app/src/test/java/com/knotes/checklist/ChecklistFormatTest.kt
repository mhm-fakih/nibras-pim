package com.knotes.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ChecklistFormatTest {

    @get:Rule val tmp = TemporaryFolder()

    private val w43 = WeekId(2026, 43)

    private fun sample() = ChecklistWeek.starter(w43, listOf("Start of day", "Study"))
        .addItem(0, "Check phone charge level")
        .addItem(1, "Study: Algorithms | ch. 3")       // a pipe in the text must survive the table
        .addItem(2, "Call the family")
        .cycle(0, 0, 0)                                  // Mon: done
        .cycle(0, 0, 1).cycle(0, 0, 1)                   // Tue: partial
        .cycle(2, 0, 0)                                  // weekly: done

    @Test
    fun `iso weeks start on Monday and number like the calendar`() {
        assertEquals(w43, WeekId.of(LocalDate.of(2026, 10, 21)))
        assertEquals(LocalDate.of(2026, 10, 19), w43.monday)
        assertEquals(LocalDate.of(2026, 10, 25), w43.sunday)
        // The year boundary: 1 Jan 2027 is a Friday, so it still belongs to 2026's week 53.
        assertEquals(WeekId(2026, 53), WeekId.of(LocalDate.of(2027, 1, 1)))
        assertEquals(WeekId(2027, 1), WeekId(2026, 53).plus(1))
        assertEquals("2026-W43", w43.toString())
        assertEquals(w43, WeekId.parse("2026-W43"))
    }

    @Test
    fun `a week survives writing and reading back`() {
        val week = sample()
        val text = ChecklistFormat.write(week)
        assertTrue(text.startsWith("# Week 43 · 2026-10-19 – 2026-10-25\n"))
        assertTrue(text.contains("## 1 Start of day\n"))
        assertTrue(text.contains("| 1.1 Check phone charge level | ✓ | ◐ |"))
        assertTrue(text.contains("| 2.1 Study: Algorithms \\| ch. 3 |"))
        assertTrue(text.contains("| Item | Week |"))
        assertEquals(week, ChecklistFormat.read(w43, text))
    }

    @Test
    fun `a hand-edited file is read leniently`() {
        val text = """
            # Week 43
            some note someone typed

            ## Morning
            | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
            |:--|--|--|--|--|--|--|--|
            | Pray | x | / | | V |
            | | x |

            ## Weekly
            | Item | Week |
            |---|---|
            | Laundry | ✔ |
        """.trimIndent()
        val week = ChecklistFormat.read(w43, text)
        assertEquals(listOf("Morning", "Weekly"), week.sections.map { it.title })
        assertEquals(listOf(1, 2), week.sections.map { it.code })      // a file without codes: by position
        assertEquals(1, week.sections[0].items.single().code)
        val pray = week.sections[0].items.single()            // the nameless row is dropped
        assertEquals(listOf(Mark.DONE, Mark.PARTIAL, Mark.NONE, Mark.DONE, Mark.NONE, Mark.NONE, Mark.NONE), pray.marks)
        assertTrue(week.sections[1].weekly)
        assertEquals(Mark.DONE, week.sections[1].items.single().marks.single())
    }

    @Test
    fun `scores count a partial tick as half`() {
        val week = sample()
        assertEquals(0.5, week.dayScore(0)!!, 1e-9)    // Mon: 1 of 2 daily items done
        assertEquals(0.25, week.dayScore(1)!!, 1e-9)   // Tue: one partial of 2
        assertEquals(1.0, week.weeklyScore()!!, 1e-9)
        assertNull(ChecklistWeek.starter(w43, listOf("A")).dayScore(0))
    }

    @Test
    fun `edits keep marks with their code`() {
        val week = sample().rename(0, 0, "Charge the phone")
        assertEquals("Charge the phone", week.sections[0].items[0].text)
        assertEquals(1, week.sections[0].items[0].code)
        assertEquals(Mark.DONE, week.sections[0].items[0].marks[0])
        val added = week.addSection("Evening")
        assertEquals(listOf("Start of day", "Study", "Weekly", "Evening"), added.sections.map { it.title })
        assertEquals(listOf(1, 2, 3, 4), added.sections.map { it.code })
        assertEquals(added, added.removeSection(1))            // has an item: refused
        assertEquals(3, added.removeSection(3).sections.size)   // empty: removed
    }

    @Test
    fun `a new week starts from the latest earlier week with no marks`() {
        val store = ChecklistStore(tmp.newFolder())
        assertTrue(store.save(sample()))
        val next = store.open(w43.plus(1)) { error("should carry the earlier week") }
        assertEquals(w43.plus(1), next.id)
        assertEquals(sample().sections.map { s -> s.items.map { it.text } }, next.sections.map { s -> s.items.map { it.text } })
        assertTrue(next.sections.all { s -> s.items.all { it.marks.all { m -> m == Mark.NONE } } })
        // Looking does not create it.
        assertEquals(listOf(w43), store.weeks())
        assertEquals(sample(), store.load(w43))
    }
}
