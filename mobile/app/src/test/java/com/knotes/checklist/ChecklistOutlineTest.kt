package com.knotes.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Edit as text": the week as a coded outline, and back — ticks follow the codes. */
class ChecklistOutlineTest {

    private val w = WeekId(2026, 43)

    private fun week() = ChecklistWeek.starter(w, listOf("Start of day", "Study"))
        .addItem(0, "Pray").addItem(0, "Check phone charge").addItem(1, "Algorithms").addItem(2, "Laundry")
        .cycle(0, 0, 0)                          // 1.1 Pray: done Monday
        .cycle(1, 0, 2).cycle(1, 0, 2)           // 2.1 Algorithms: partial Wednesday
        .cycle(2, 0, 0)                          // 3.1 Laundry (weekly): done

    @Test
    fun `the outline numbers groups and items`() {
        assertEquals(
            "## 1 Start of day\n1.1 Pray\n1.2 Check phone charge\n\n## 2 Study\n2.1 Algorithms\n\n## 3 Weekly [weekly]\n3.1 Laundry",
            week().toOutline(),
        )
    }

    @Test
    fun `an unchanged outline changes nothing`() {
        assertEquals(week(), week().applyOutline(week().toOutline()))
    }

    @Test
    fun `ticks follow the code, not the wording or the line's position`() {
        val edited = week().applyOutline(
            """
            ## 2 Study
            2.1 Algorithms and data structures
            1.1 Morning prayer
            ## 1 Start of day
            1.2 Check phone charge
            ## 3 Weekly [weekly]
            3.1 Laundry
            """.trimIndent(),
        )
        val start = edited.sections[0]
        assertEquals("Start of day", start.title)
        assertEquals(listOf("Morning prayer", "Check phone charge"), start.items.map { it.text })  // 1.1 sorts first
        assertEquals(Mark.DONE, start.items[0].marks[0])                     // renamed, same code: kept
        assertEquals(Mark.PARTIAL, edited.sections[1].items.single().marks[2])
        assertEquals("Algorithms and data structures", edited.sections[1].items.single().text)
    }

    @Test
    fun `uncoded lines are new items numbered past every code, bullets stripped`() {
        val edited = week().applyOutline("## 1 Start of day\n1.1 Pray\n- Stretch\n2. Water\n\n## Evening\n• Read")
        val start = edited.sections.first { it.code == 1 }
        // 1.2 was deleted in this edit, so the new items start at 1.3.
        assertEquals(listOf(1 to "Pray", 3 to "Stretch", 4 to "Water"), start.items.map { it.code to it.text })
        assertTrue(start.items[1].marks.all { it == Mark.NONE })
        val evening = edited.sections.last()
        assertEquals(4, evening.code)                                       // numbered after 1-3
        assertEquals("1" to "Read", evening.items.single().code.toString() to evening.items.single().text)
    }

    @Test
    fun `a code moved to another group carries its ticks - daily to weekly resets them`() {
        val edited = week().applyOutline("## 1 Start of day\n## 2 Study\n2.1 Algorithms\n## 3 Weekly [weekly]\n3.1 Laundry\n3.2 Pray")
        assertTrue(edited.sections.first { it.code == 3 }.items.first { it.text == "Pray" }.marks.all { it == Mark.NONE })
        val sameKind = week().applyOutline("## 1 Start of day\n1.1 Pray\n## 2 Study\n2.1 Algorithms")
        assertEquals(Mark.DONE, sameKind.sections[0].items.single().marks[0])
    }

    @Test
    fun `a group only named through its items keeps its old title, and duplicate codes do not share ticks`() {
        val edited = week().applyOutline("1.1 Pray\n1.1 Pray again")
        val start = edited.sections.single()
        assertEquals("Start of day", start.title)
        assertEquals(listOf(1, 3), start.items.map { it.code })
        assertEquals(Mark.DONE, start.items[0].marks[0])
        assertTrue(start.items[1].marks.all { it == Mark.NONE })
    }

    @Test
    fun `an old file without codes is numbered by position`() {
        val old = "## Start of day\n| Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |\n|---|---|---|---|---|---|---|---|\n" +
            "| Pray | ✓ | | | | | | |\n| Read | | | | | | | |\n\n## Weekly\n| Item | Week |\n|---|---|\n| Laundry | ✓ |\n"
        val week = ChecklistFormat.read(w, old)
        assertEquals("## 1 Start of day\n1.1 Pray\n1.2 Read\n\n## 2 Weekly [weekly]\n2.1 Laundry", week.toOutline())
        assertEquals(Mark.DONE, week.sections[0].items[0].marks[0])
        assertEquals(week, ChecklistFormat.read(w, ChecklistFormat.write(week)))   // and stays put once written
    }
}
