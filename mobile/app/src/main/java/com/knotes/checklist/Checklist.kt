package com.knotes.checklist

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Daily and weekly checklists, tracked one ISO week at a time.
 *
 * A week is a list of sections ("Start of day", "Study", …). A daily section's items carry seven
 * marks, Monday to Sunday; the weekly section's items carry one, for the week as a whole. Each week
 * owns its own item list, so items can be added, renamed and dropped all day long without rewriting
 * history: last week's file still says what last week was.
 *
 * Pure Kotlin on purpose — no Android types — so the file format, which the desktop and later the
 * backend will read too, is pinned by plain unit tests (see ChecklistFormatTest).
 */
enum class Mark(val symbol: String) {
    NONE(""),
    PARTIAL("◐"),
    DONE("✓"),
    ;

    /** What a tap does: not done → done → partial → not done. Done first: it is the common case. */
    fun next(): Mark = when (this) {
        NONE -> DONE
        DONE -> PARTIAL
        PARTIAL -> NONE
    }

    /** Contribution to a day's completion: a partial tick counts half. */
    val score: Double get() = when (this) {
        NONE -> 0.0
        PARTIAL -> 0.5
        DONE -> 1.0
    }

    companion object {
        /** Lenient on read: `x`/`X`/`v` also mean done and `/`/`~`/`½` partial, for hand-edited files. */
        fun parse(cell: String): Mark = when (cell.trim()) {
            "✓", "✔", "x", "X", "v", "V" -> DONE
            "◐", "/", "~", "½" -> PARTIAL
            else -> NONE
        }
    }
}

/**
 * One item. [code] is its number within its group — item `1.3` is code 3 of group 1 — and it is the
 * item's identity: the ticks belong to the code, not to the wording, so renaming keeps them, and the
 * same code means the same item from week to week (and, later, on the backend).
 */
data class ChecklistItem(val code: Int, val text: String, val marks: List<Mark>)

/** A group, numbered by [code] (1, 2, 3 …); its items' codes are `code.n`. */
data class ChecklistSection(val code: Int, val title: String, val weekly: Boolean, val items: List<ChecklistItem>) {
    /** How many marks each item carries in this section. */
    val width: Int get() = if (weekly) 1 else DAYS

    /** `1.3` for the item with code 3 in group 1. */
    fun codeOf(item: ChecklistItem) = "$code.${item.code}"

    internal fun nextItemCode() = (items.maxOfOrNull { it.code } ?: 0) + 1
}

/** An ISO week: Monday-based, numbered within its week-based year (week 1 holds the first Thursday). */
data class WeekId(val year: Int, val week: Int) : Comparable<WeekId> {
    val monday: LocalDate
        get() = LocalDate.of(year, 1, 4)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday: LocalDate get() = monday.plusDays(6)

    fun plus(weeks: Long): WeekId = of(monday.plusWeeks(weeks))

    /** `2026-W43` — the file name stem, and what sorts correctly as text. */
    override fun toString(): String = "%04d-W%02d".format(java.util.Locale.ROOT, year, week)

    override fun compareTo(other: WeekId): Int = compareValuesBy(this, other, { it.year }, { it.week })

    companion object {
        fun of(date: LocalDate): WeekId =
            WeekId(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        private val NAME = Regex("""^(\d{4})-W(\d{2})$""")
        fun parse(stem: String): WeekId? =
            NAME.matchEntire(stem)?.let { WeekId(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
    }
}

data class ChecklistWeek(val id: WeekId, val sections: List<ChecklistSection>) {

    /** 0..1 completion of [day] (0 = Monday) over the daily items; null when there are none. */
    fun dayScore(day: Int): Double? {
        val marks = sections.filter { !it.weekly }.flatMap { s -> s.items.map { it.marks[day] } }
        return if (marks.isEmpty()) null else marks.sumOf { it.score } / marks.size
    }

    /** 0..1 completion of the weekly items; null when there are none. */
    fun weeklyScore(): Double? {
        val marks = sections.filter { it.weekly }.flatMap { s -> s.items.map { it.marks[0] } }
        return if (marks.isEmpty()) null else marks.sumOf { it.score } / marks.size
    }

    // ---- edits: each returns a new week, so the screen can just save whatever comes back.
    // Positions (section, item) are indices into the sorted lists the screen draws; identity is the
    // code, which no edit here ever changes on an existing item.

    fun cycle(section: Int, item: Int, slot: Int): ChecklistWeek = editItem(section, item) {
        it.copy(marks = it.marks.toMutableList().also { m -> m[slot] = m[slot].next() })
    }

    fun rename(section: Int, item: Int, text: String): ChecklistWeek = editItem(section, item) {
        it.copy(text = clean(text).ifEmpty { it.text })
    }

    /** A new item, numbered after the group's highest code — a removed item's code is never reused. */
    fun addItem(section: Int, text: String): ChecklistWeek {
        val t = clean(text)
        if (t.isEmpty()) return this
        return editSection(section) {
            it.copy(items = it.items + ChecklistItem(it.nextItemCode(), t, List(it.width) { Mark.NONE }))
        }
    }

    fun removeItem(section: Int, item: Int): ChecklistWeek =
        editSection(section) { it.copy(items = it.items.filterIndexed { i, _ -> i != item }) }

    fun renameSection(section: Int, title: String): ChecklistWeek =
        editSection(section) { it.copy(title = clean(title).ifEmpty { it.title }) }

    /** A new daily group, numbered after the highest group code. */
    fun addSection(title: String): ChecklistWeek {
        val t = clean(title)
        if (t.isEmpty()) return this
        val code = (sections.maxOfOrNull { it.code } ?: 0) + 1
        return copy(sections = sections + ChecklistSection(code, t, false, emptyList())).sorted()
    }

    /** Only an empty section can go — dropping a group with items in it is one tap too easy. */
    fun removeSection(section: Int): ChecklistWeek =
        if (sections[section].items.isNotEmpty()) this
        else copy(sections = sections.filterIndexed { i, _ -> i != section })

    /** The same items, same codes, for another week, every mark cleared — how a week is planned. */
    fun carriedTo(other: WeekId): ChecklistWeek = ChecklistWeek(
        other,
        sections.map { s -> s.copy(items = s.items.map { it.copy(marks = List(s.width) { Mark.NONE }) }) },
    )

    /** Groups by code, items by code within each: code order IS the order. */
    fun sorted(): ChecklistWeek = copy(
        sections = sections.sortedBy { it.code }.map { s -> s.copy(items = s.items.sortedBy { it.code }) },
    )

    /**
     * The week as editable text — what "Edit as text" opens; [applyOutline] is the way back:
     * ```
     * ## 1 Start of day
     * 1.1 Vcn. Trans.
     * 1.2 tmk++
     *
     * ## 6 Weekly [weekly]
     * 6.1 Plan watering
     * ```
     */
    fun toOutline(): String = sections.joinToString("\n\n") { s ->
        buildString {
            append("## ").append(s.code).append(' ').append(s.title)
            if (s.weekly) append(' ').append(WEEKLY_MARK)
            s.items.forEach { append('\n').append(s.codeOf(it)).append(' ').append(it.text) }
        }
    }

    /**
     * Rebuild the groups and items from [text] (the [toOutline] format). **Ticks follow the code**:
     * a `1.3` line keeps item 1.3's ticks whatever its wording now says, and lands in group 1
     * wherever in the text it was written — moving an item means giving it another code.
     *
     * - `## 4 Title` declares group 4; `## Title` (no number) a new group, numbered next.
     *   ` [weekly]` after the title makes a weekly group.
     * - `2.5 Text` is item 5 of group 2; a group that has items but no heading keeps its old title.
     * - A line with no code is a new item in the group above it, numbered next; pasted bullets and
     *   list numbering (`- `, `• `, `1. `) are stripped first.
     * - A code used twice: the second line becomes a new item rather than sharing ticks.
     * - An item moved between a daily and a weekly group starts blank — seven day-ticks have no
     *   meaning as one week-tick.
     */
    fun applyOutline(text: String): ChecklistWeek {
        val oldSections = sections.associateBy { it.code }
        val oldItems = HashMap<String, List<Mark>>()
        sections.forEach { s -> s.items.forEach { oldItems[s.codeOf(it)] = it.marks } }

        data class Head(var code: Int?, val title: String, val weekly: Boolean)
        data class Line(val section: Int?, val code: Int?, val text: String, val head: Head?)

        val heads = mutableListOf<Head>()
        val lines = mutableListOf<Line>()          // in text order
        var currentHead: Head? = null
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            HEAD.matchEntire(line)?.let { m ->
                var title = m.groupValues[2].trim()
                val weekly = title.endsWith(WEEKLY_MARK, ignoreCase = true)
                if (weekly) title = title.dropLast(WEEKLY_MARK.length).trim()
                if (title.isEmpty()) return@forEach
                val h = Head(m.groupValues[1].toIntOrNull(), clean(title), weekly)
                heads += h; currentHead = h
                return@forEach
            }
            CODED.matchEntire(line)?.let { m ->
                val t = clean(m.groupValues[3])
                if (t.isNotEmpty()) lines += Line(m.groupValues[1].toInt(), m.groupValues[2].toInt(), t, null)
                return@forEach
            }
            val t = clean(BULLET.replace(line, ""))
            if (t.isNotEmpty()) lines += Line(null, null, t, currentHead)
        }

        // Number the headings that had none, after every group code in use — including the ones this
        // edit deleted, so a new group never takes over a number the week was just using.
        var nextGroup = (heads.mapNotNull { it.code } + lines.mapNotNull { it.section } + sections.map { it.code })
            .maxOrNull() ?: 0
        heads.filter { it.code == null }.forEach { it.code = ++nextGroup }

        // Groups: declared headings first (a duplicate number keeps the first title), then any group
        // that only appears through its items' codes, under its old title.
        val groups = LinkedHashMap<Int, ChecklistSection>()
        heads.forEach { h -> groups.getOrPut(h.code!!) { ChecklistSection(h.code!!, h.title, h.weekly, emptyList()) } }
        lines.mapNotNull { it.section }.forEach { c ->
            groups.getOrPut(c) {
                val old = oldSections[c]
                ChecklistSection(c, old?.title ?: "$GROUP_FALLBACK $c", old?.weekly ?: false, emptyList())
            }
        }
        if (groups.isEmpty() && lines.isNotEmpty()) {
            val first = sections.firstOrNull { !it.weekly }
            val c = first?.code ?: 1
            groups[c] = ChecklistSection(c, first?.title ?: GROUP_FALLBACK, false, emptyList())
        }

        val taken = HashSet<String>()
        fun add(section: Int, code: Int?, text: String) {
            val g = groups.getValue(section)
            val keep = code != null && taken.add("$section.$code")
            // A new item is numbered past every code in use — and past the codes this edit deleted,
            // so a fresh item never takes over a code the week was just using for something else.
            val n = if (keep) code!! else maxOf(g.nextItemCode(), (oldSections[section]?.nextItemCode() ?: 1))
            taken += "$section.$n"
            val marks = if (keep) oldItems["$section.$n"]?.takeIf { it.size == g.width } else null
            groups[section] = g.copy(items = g.items + ChecklistItem(n, text, marks ?: List(g.width) { Mark.NONE }))
        }
        // Coded lines first, so the new ones are numbered after every code the text keeps.
        lines.filter { it.code != null }.forEach { add(it.section!!, it.code, it.text) }
        lines.filter { it.code == null }.forEach { l ->
            add(l.head?.code ?: groups.keys.first(), null, l.text)
        }
        return copy(sections = groups.values.toList()).sorted()
    }

    private fun editSection(section: Int, f: (ChecklistSection) -> ChecklistSection) =
        copy(sections = sections.mapIndexed { i, s -> if (i == section) f(s) else s })

    private fun editItem(section: Int, item: Int, f: (ChecklistItem) -> ChecklistItem) =
        editSection(section) { s -> s.copy(items = s.items.mapIndexed { i, it -> if (i == item) f(it) else it }) }

    companion object {
        const val WEEKLY_TITLE = "Weekly"

        /** Follows a group's name in the text outline to make it a weekly group. */
        const val WEEKLY_MARK = "[weekly]"

        private const val GROUP_FALLBACK = "Group"

        private val HEAD = Regex("""^#+\s*(?:(\d+)\s+)?(.*)$""")
        private val CODED = Regex("""^(\d+)\.(\d+)\s+(.+)$""")

        /** A leading bullet, checkbox or list number on a pasted line: `- `, `• `, `[ ] `, `1. `, `2) `. */
        private val BULLET = Regex("""^(?:[-*•·]\s+|\[[ xX]?]\s*|\d{1,3}[.)]\s+)+""")

        /** A first week: the groups asked for, numbered 1…n, then the weekly group; all empty. */
        fun starter(id: WeekId, dailyTitles: List<String>, weeklyTitle: String = WEEKLY_TITLE) = ChecklistWeek(
            id,
            dailyTitles.mapIndexed { i, t -> ChecklistSection(i + 1, t, false, emptyList()) } +
                ChecklistSection(dailyTitles.size + 1, weeklyTitle, true, emptyList()),
        )

        /** One line, nothing absurdly long. */
        fun clean(text: String): String = text.replace('\n', ' ').replace('\r', ' ').trim().take(200)
    }
}

const val DAYS = 7
