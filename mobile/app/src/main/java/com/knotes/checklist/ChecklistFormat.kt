package com.knotes.checklist

/**
 * The `.md` file a week lives in — readable as it stands on the desktop, and the format the backend
 * will mirror later. One file per week, `2026-W43.md`:
 *
 * ```
 * # Week 43 · 2026-10-19 – 2026-10-25
 *
 * ## 1 Start of day
 * | Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
 * |---|---|---|---|---|---|---|---|
 * | 1.1 Check phone charge level | ✓ | ◐ |  |  |  |  |  |
 *
 * ## 6 Weekly
 * | Item | Week |
 * |---|---|
 * | 6.1 Call the family | ✓ |
 * ```
 *
 * Each `##` heading is a section, numbered; each item starts with its code (`1.1`), which is what
 * its ticks belong to. A table whose only mark column is `Week` makes the section the weekly one.
 * A file written before codes existed reads fine: it is numbered by position — group 1, 2, 3 in
 * order, items .1, .2 … — and the next save writes the codes in.
 * Marks are `✓` done, `◐` partial, blank not done — and reading is lenient (see [Mark.parse]) so a
 * hand-edited file with an `x` still counts. A `|` inside an item is written `\|`.
 *
 * Reading never throws: a line it does not understand is skipped, so a file damaged by a bad edit
 * or a sync conflict loses that line, not the week.
 */
object ChecklistFormat {

    private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun write(week: ChecklistWeek): String = buildString {
        append("# Week ").append(week.id.week).append(" · ")
            .append(week.id.monday).append(" – ").append(week.id.sunday).append('\n')
        week.sections.forEach { s ->
            append("\n## ").append(s.code).append(' ').append(s.title).append('\n')
            val cols = if (s.weekly) listOf("Week") else DAY_NAMES
            append("| Item | ").append(cols.joinToString(" | ")).append(" |\n")
            append("|---|").append(cols.joinToString("") { "---|" }).append('\n')
            s.items.forEach { item ->
                append("| ").append(s.codeOf(item)).append(' ').append(escape(item.text)).append(" |")
                item.marks.forEach { m -> append(' ').append(m.symbol).append(if (m.symbol.isEmpty()) "|" else " |") }
                append('\n')
            }
        }
    }

    fun read(id: WeekId, text: String): ChecklistWeek {
        val sections = mutableListOf<ChecklistSection>()
        var title: String? = null
        var code: Int? = null
        var weekly = false
        var items = mutableListOf<ChecklistItem>()
        var sawHeader = false

        fun flush() {
            title?.let { t ->
                // Items without a code (an older file, a hand-added row) take the next free number.
                var next = items.mapNotNull { it.code.takeIf { c -> c > 0 } }.maxOrNull() ?: 0
                val numbered = items.map { if (it.code > 0) it else it.copy(code = ++next) }
                sections += ChecklistSection(code ?: 0, t, weekly, numbered)
            }
            title = null; code = null; weekly = false; items = mutableListOf(); sawHeader = false
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("## ") -> {
                    flush()
                    val head = line.removePrefix("## ").trim()
                    val m = NUMBERED.matchEntire(head)
                    code = m?.groupValues?.get(1)?.toInt()
                    title = m?.groupValues?.get(2)?.trim() ?: head
                }
                title == null || !line.startsWith("|") -> Unit            // title line, blank, prose
                line.matches(Regex("""^\|[\s\-:|]+$""")) -> Unit           // the |---|---| rule
                !sawHeader -> {
                    sawHeader = true
                    val cols = cells(line)
                    weekly = cols.size == 2 && cols[1].equals("Week", ignoreCase = true)
                }
                else -> {
                    val cols = cells(line)
                    val cell = cols.firstOrNull()?.let(::unescape)?.trim().orEmpty()
                    val m = ITEM_CODE.matchEntire(cell)
                    // A code for another group (or none) is ignored here; the item still belongs to
                    // the table it is written in, and gets a number of this group's if it needs one.
                    val itemCode = m?.takeIf { code == null || it.groupValues[1].toInt() == code }?.groupValues?.get(2)?.toInt() ?: 0
                    val name = (if (m != null) m.groupValues[3] else cell).trim()
                    if (name.isNotEmpty()) {
                        val width = if (weekly) 1 else DAYS
                        val marks = List(width) { i -> cols.getOrNull(i + 1)?.let(Mark::parse) ?: Mark.NONE }
                        items += ChecklistItem(itemCode, name, marks)
                    }
                }
            }
        }
        flush()
        // Sections without a number (an older file) are numbered after those that have one, in order.
        var next = sections.map { it.code }.filter { it > 0 }.maxOrNull() ?: 0
        val numbered = sections.map { if (it.code > 0) it else it.copy(code = ++next) }
        return ChecklistWeek(id, numbered).sorted()
    }

    private val NUMBERED = Regex("""^(\d+)\s+(.+)$""")
    private val ITEM_CODE = Regex("""^(\d+)\.(\d+)\s+(.+)$""")

    /** Split a table row on unescaped pipes, dropping the empty edges `| a | b |` produces. */
    private fun cells(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length && line[i + 1] == '|') { cur.append("\\|"); i += 2; continue }
            if (c == '|') { out += cur.toString().trim(); cur.clear() } else cur.append(c)
            i++
        }
        out += cur.toString().trim()
        return out.drop(1).dropLast(1)       // the empty strings before the first and after the last pipe
    }

    private fun escape(text: String) = text.replace("|", "\\|")
    private fun unescape(text: String) = text.replace("\\|", "|")
}
