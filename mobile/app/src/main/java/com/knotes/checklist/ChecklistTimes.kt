package com.knotes.checklist

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A reminder time written in front of a group's title — `## 1 07:30 Start of day` in the file, or
 * `07:30 Start of day` typed when renaming the group. It lives in the title on purpose: the file
 * format, the desktop and the web dashboard need no change, and the time carries to the next week
 * with the group like everything else about it.
 */
object ChecklistTimes {

    private val TIMED = Regex("""^(\d{1,2})[:.](\d{2})\s+(.+)$""")

    /** The time at the head of [title], or null when it has none (or an impossible one). */
    fun timeOf(title: String): LocalTime? = TIMED.matchEntire(title.trim())?.let { m ->
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }

    /** [title] without its time — what the reminder says. */
    fun plainTitle(title: String): String =
        if (timeOf(title) == null) title.trim() else TIMED.matchEntire(title.trim())!!.groupValues[3].trim()

    /** One daily group's reminder on [date]. */
    data class Due(val at: LocalDateTime, val sectionCode: Int, val title: String)

    /**
     * Every reminder [week] asks for on [date], earliest first. Daily groups only: a weekly group
     * has no "today's items" to fill in, so a daily nudge for it would only be noise.
     */
    fun dueOn(week: ChecklistWeek, date: LocalDate): List<Due> =
        week.sections.filter { !it.weekly }.mapNotNull { s ->
            timeOf(s.title)?.let { t -> Due(date.atTime(t), s.code, plainTitle(s.title)) }
        }.sortedBy { it.at }

    /** True when [day]'s column (0 = Monday) has every item of group [code] marked, partial or done. */
    fun filledIn(week: ChecklistWeek, code: Int, day: Int): Boolean {
        val s = week.sections.firstOrNull { it.code == code } ?: return false
        return s.items.isNotEmpty() && s.items.all { it.marks.getOrNull(day) != Mark.NONE }
    }
}
