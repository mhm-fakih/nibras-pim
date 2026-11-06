package com.knotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkTest {

    private fun note(questions: String, myAnswers: String = "1.\n2.\n3.", feedback: String = "") =
        listOf(
            "001 · Topic", "", "## Case", "", "Some case.", "", "## Questions", "", questions.trim(), "",
            "## My answers", "",
            "Answer by tapping the letter of your choice (A., B., C. or D.) in reading mode: it turns highlighted.",
            "", myAnswers, feedback, "#F.psych",
        ).joinToString("\n")

    private val three = """
1. First question?
A. one
B. two
C. three
D. four

2. Second question:
A. one
B. two
C. three
D. four

3. Third question?
A. one
B. two
C. three
D. four
"""

    @Test
    fun nothingHighlightedIsUnsolved() {
        val s = Homework.status(note(three))
        assertEquals(3, s.questions)
        assertEquals(0, s.answered)
        assertEquals(Homework.State.UNSOLVED, s.state)
    }

    @Test
    fun aHighlightedLetterIsTheAnswer() {
        val q = three.replaceFirst("B. two", "==B.== two")
        val s = Homework.status(note(q))
        assertEquals(mapOf(1 to 'B'), s.answers)
        assertEquals(Homework.State.PARTIAL, s.state)
    }

    @Test
    fun aHighlightedOptionTextAlsoCounts() {
        val q = three.replaceFirst("C. three", "C. ==three==")
        assertEquals(mapOf(1 to 'C'), Homework.status(note(q)).answers)
    }

    @Test
    fun twoHighlightsOnOneQuestionAreNoAnswer() {
        val q = three.replaceFirst("A. one", "==A.== one").replaceFirst("D. four", "==D.== four")
        val s = Homework.status(note(q))
        assertTrue(s.answers.isEmpty())
        assertEquals(setOf(1), s.ambiguous)
    }

    @Test
    fun typedLettersCountAndHighlightsWin() {
        val q = three.replaceFirst("A. one", "==A.== one")
        val s = Homework.status(note(q, myAnswers = "1. C\n2. b\n3. D"))
        assertEquals(mapOf(1 to 'A', 2 to 'B', 3 to 'D'), s.answers)
        assertEquals(Homework.State.SOLVED, s.state)
    }

    @Test
    fun aTapOnTheLetterHighlightsExactlyTheLetter() {
        // What the reader does: the sentence under the tap gets the markers.
        val src = "1. First question?\nA. one thing.\nB. two"
        val plain = Highlights.split(src).first
        val at = plain.indexOf("A.")
        val out = Highlights.toggleSentenceAt(src, at)
        assertEquals("1. First question?\n==A.== one thing.\nB. two", out)
    }

    @Test
    fun feedbackAndScoreAreRead() {
        val s = Homework.status(note(three, feedback = "## Feedback\n\n2026-11-07\n\n### Score\n\n9/12. Strong on terms.\n"))
        assertTrue(s.marked)
        assertEquals("9/12. Strong on terms.", s.score)
    }

    @Test
    fun theOldOpenFormatIsSolvedOnceWritten() {
        assertEquals(Homework.State.UNSOLVED, Homework.status("003 · X\n\n## My answer\n\n#F.psych").state)
        assertEquals(Homework.State.SOLVED, Homework.status("003 · X\n\n## My answer\n\nMy essay.\n\n#F.psych").state)
    }
}
