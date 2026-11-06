package com.knotes.util

/**
 * Reads a homework note (the ~/new/knotes-hw folder, spec in its CLAUDE.md): which questions are
 * answered, and so whether the assignment is solved, partly solved or not started.
 *
 * An answer is a highlighted option. In reading mode a tap highlights one sentence, and the option
 * letter with its full stop is a sentence of its own, so tapping `A.` writes `==A.==` at the start
 * of the option line. A highlight anywhere else on an option line (a tap on the option text) also
 * chooses that option. Two highlighted options on one question are no answer. A letter typed under
 * "## My answers" (`4. D`) counts too; a highlight wins over it.
 *
 * The two older open-question assignments have no options: they count as solved once something is
 * written under "## My answer".
 */
object Homework {

    enum class State { SOLVED, PARTIAL, UNSOLVED }

    data class Status(
        /** Questions found (12 for a multiple-choice assignment, 0 for the old open format). */
        val questions: Int,
        /** Question number → chosen letter, for the questions with exactly one answer. */
        val answers: Map<Int, Char>,
        /** Questions with more than one option highlighted. */
        val ambiguous: Set<Int>,
        /** The tutor has marked it (a "## Feedback" section is there). */
        val marked: Boolean,
        /** The score line under "### Score", if marked. */
        val score: String?,
        val state: State,
    ) {
        val answered: Int get() = answers.size
    }

    private val QUESTION = Regex("""^(\d{1,2})\.\s+\S""")
    private val OPTION = Regex("""^(?:==)?([A-D])\.""")
    private val TYPED = Regex("""^(\d{1,2})\.\s*\**([A-Da-d])\b""")

    fun status(source: String): Status {
        val lines = source.lines()
        val section = sections(lines)
        val qLines = section["questions"] ?: emptyList()
        val chosen = linkedMapOf<Int, MutableSet<Char>>()
        var current = 0
        var questions = 0
        for (line in qLines) {
            val l = line.trim()
            val q = QUESTION.find(l)
            if (q != null && OPTION.find(l) == null) {
                current = q.groupValues[1].toInt()
                questions++
                continue
            }
            val o = OPTION.find(l) ?: continue
            if (current == 0) continue
            if (Highlights.split(l).second.any { it }) {
                chosen.getOrPut(current) { linkedSetOf() }.add(o.groupValues[1][0])
            }
        }

        val answers = sortedMapOf<Int, Char>()
        val ambiguous = sortedSetOf<Int>()
        chosen.forEach { (q, set) -> if (set.size == 1) answers[q] = set.first() else ambiguous += q }
        // Typed letters fill the questions no highlight answered.
        for (line in section["my answers"] ?: emptyList()) {
            val t = TYPED.find(line.trim()) ?: continue
            val q = t.groupValues[1].toInt()
            if (q !in chosen) answers[q] = t.groupValues[2][0].uppercaseChar()
        }

        val marked = section.containsKey("feedback")
        val score = section["score"]?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

        val state = if (questions == 0) {
            // The old open-question format: written answer or nothing.
            val written = (section["my answer"] ?: emptyList()).any { it.isNotBlank() }
            if (written) State.SOLVED else State.UNSOLVED
        } else {
            val n = answers.keys.count { it in 1..questions }
            when {
                n == 0 -> State.UNSOLVED
                n >= questions -> State.SOLVED
                else -> State.PARTIAL
            }
        }
        return Status(questions, answers, ambiguous, marked, score, state)
    }

    /** Lines under each `##`/`###` heading, keyed by the lower-cased heading text. */
    private fun sections(lines: List<String>): Map<String, List<String>> {
        val out = linkedMapOf<String, MutableList<String>>()
        var key: String? = null
        for (line in lines) {
            val h = Regex("""^#{2,3}\s+(.+?)\s*$""").find(line)
            if (h != null) {
                key = h.groupValues[1].lowercase()
                out.getOrPut(key) { mutableListOf() }
                continue
            }
            // The tag line ends the note's body.
            if (line.trim().startsWith("#") && !line.trim().startsWith("##")) continue
            key?.let { out[it]!!.add(line) }
        }
        return out
    }
}
