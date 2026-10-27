package com.knotes.nibras

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * The calendar's batch entry: a week typed as lines, one entry per line (and per day on it).
 *
 *     p mon 10:00 Calculus
 *     p mon,wed 8:00-9:30 Physics
 *     p tue-thu 14 Lab
 *     j fri 2:30pm Group meeting
 *
 * The leading `p` (planner) or `j` (journal) is optional; without it the line takes the dialog's
 * type. Days are English, French or Arabic names (or their first three letters), joined by `,` for
 * a list or `-` for a run. Times are 24-hour `H`, `H:MM`, or 12-hour with am/pm; without an end the
 * entry lasts the default length. Everything after the time is the title.
 */
object WeekBatch {

    data class Entry(val type: String?, val day: DayOfWeek, val start: LocalTime, val end: LocalTime, val title: String)

    enum class Problem { NO_DAY, NO_TIME, BAD_END, NO_TITLE }

    /** One input line: its entries, or what's wrong with it. */
    data class Line(val number: Int, val text: String, val entries: List<Entry>, val problem: Problem?)

    fun parse(text: String, defaultMinutes: Int = 60): List<Line> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) null else parseLine(i + 1, line, defaultMinutes)
        }

    /** The first date on or after [from] that falls on [day] — where a weekday line lands. */
    fun firstDate(from: LocalDate, day: DayOfWeek): LocalDate = from.with(TemporalAdjusters.nextOrSame(day))

    private fun parseLine(number: Int, line: String, defaultMinutes: Int): Line {
        val tokens = line.split(Regex("\\s+")).toMutableList()
        fun fail(p: Problem) = Line(number, line, emptyList(), p)

        val type = when (tokens.first().lowercase()) {
            "p" -> "P"
            "j" -> "J"
            else -> null
        }
        if (type != null) tokens.removeAt(0)

        val days = tokens.firstOrNull()?.let(::days) ?: return fail(Problem.NO_DAY)
        tokens.removeAt(0)

        // "8:00-9:30", or with the dash as words of its own: "8:00 - 9:30", "8:00 -9:30".
        var timeText = tokens.firstOrNull() ?: return fail(Problem.NO_TIME)
        tokens.removeAt(0)
        while (tokens.isNotEmpty() && (timeText.endsWith("-") || tokens.first().startsWith("-"))) {
            timeText += tokens.removeAt(0)
        }
        val parts = timeText.split("-")
        val start = parseTime(parts[0]) ?: return fail(Problem.NO_TIME)
        val end = when {
            parts.size == 1 -> start.plusMinutes(defaultMinutes.toLong()).let { if (it.isBefore(start)) LocalTime.of(23, 59) else it }
            parts.size == 2 -> parseTime(parts[1]) ?: return fail(Problem.BAD_END)
            else -> return fail(Problem.BAD_END)
        }
        if (!end.isAfter(start)) return fail(Problem.BAD_END)

        val title = tokens.joinToString(" ").trim()
        if (title.isEmpty()) return fail(Problem.NO_TITLE)
        return Line(number, line, days.map { Entry(type, it, start, end, title) }, null)
    }

    /** "mon", "mon,wed", "mon-fri", "lun", "الاثنين" → the days, in week order; null if not days. */
    private fun days(token: String): List<DayOfWeek>? {
        val out = linkedSetOf<DayOfWeek>()
        for (piece in token.split(",", "،", "/").filter { it.isNotBlank() }) {
            val range = piece.split("-")
            when (range.size) {
                1 -> out += dayOf(range[0]) ?: return null
                2 -> {
                    val a = dayOf(range[0]) ?: return null
                    val b = dayOf(range[1]) ?: return null
                    var d = a
                    while (true) {
                        out += d
                        if (d == b) break
                        d = d.plus(1)
                    }
                }
                else -> return null
            }
        }
        return out.takeIf { it.isNotEmpty() }?.toList()
    }

    private val NAMES: Map<String, DayOfWeek> = buildMap {
        val english = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val french = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
        DayOfWeek.entries.forEachIndexed { i, d ->
            put(english[i], d); put(english[i].take(3), d)
            put(french[i], d); put(french[i].take(3), d)
        }
        put("tues", DayOfWeek.TUESDAY); put("thur", DayOfWeek.THURSDAY); put("thurs", DayOfWeek.THURSDAY)
        listOf(
            "الاثنين" to DayOfWeek.MONDAY, "الإثنين" to DayOfWeek.MONDAY, "اثنين" to DayOfWeek.MONDAY,
            "الثلاثاء" to DayOfWeek.TUESDAY, "ثلاثاء" to DayOfWeek.TUESDAY,
            "الأربعاء" to DayOfWeek.WEDNESDAY, "الاربعاء" to DayOfWeek.WEDNESDAY, "أربعاء" to DayOfWeek.WEDNESDAY, "اربعاء" to DayOfWeek.WEDNESDAY,
            "الخميس" to DayOfWeek.THURSDAY, "خميس" to DayOfWeek.THURSDAY,
            "الجمعة" to DayOfWeek.FRIDAY, "جمعة" to DayOfWeek.FRIDAY,
            "السبت" to DayOfWeek.SATURDAY, "سبت" to DayOfWeek.SATURDAY,
            "الأحد" to DayOfWeek.SUNDAY, "الاحد" to DayOfWeek.SUNDAY, "أحد" to DayOfWeek.SUNDAY, "احد" to DayOfWeek.SUNDAY,
        ).forEach { (k, v) -> put(k, v) }
    }

    private fun dayOf(name: String): DayOfWeek? = NAMES[name.trim().trimEnd('.').lowercase()]

    private val TIME = Regex("^(\\d{1,2})(?:[:h.](\\d{2}))?\\s*(am|pm|a|p)?$", RegexOption.IGNORE_CASE)

    private fun parseTime(raw: String): LocalTime? {
        val m = TIME.find(raw.trim()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3].lowercase()) {
            "am", "a" -> { if (h !in 1..12) return null; if (h == 12) h = 0 }
            "pm", "p" -> { if (h !in 1..12) return null; if (h != 12) h += 12 }
        }
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }
}
