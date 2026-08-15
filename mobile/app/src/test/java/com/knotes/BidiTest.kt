package com.knotes

import com.knotes.util.Bidi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-line text direction.
 *
 * The bug being pinned: direction used to be `containsMatchIn(ARABIC)` over the whole note, so a
 * single Arabic word anywhere laid the entire English note out right-to-left. Direction is now a
 * property of a line, decided by its first strong letter.
 */
class BidiTest {

    @Test
    fun englishIsLeftToRight() {
        assertFalse(Bidi.isRtlLine("Meeting notes for Tuesday")!!)
    }

    @Test
    fun arabicIsRightToLeft() {
        assertTrue(Bidi.isRtlLine("ملاحظات الاجتماع")!!)
    }

    /** The regression: one Arabic word must not flip an English line. */
    @Test
    fun anEnglishLineMentioningArabicStaysLeftToRight() {
        assertFalse(Bidi.isRtlLine("the word تفسير means exegesis")!!)
    }

    /** And the mirror of it. */
    @Test
    fun anArabicLineMentioningEnglishStaysRightToLeft() {
        assertTrue(Bidi.isRtlLine("قرأت كتاب Design Patterns أمس")!!)
    }

    /** Neutrals don't decide — the first real letter after them does. */
    @Test
    fun digitsAndPunctuationAreNeutral() {
        assertFalse(Bidi.isRtlLine("1. First item")!!)
        assertTrue(Bidi.isRtlLine("1. أول بند")!!)
        assertFalse(Bidi.isRtlLine("(2026) the year")!!)
        assertNull(Bidi.isRtlLine("12345 — 67"))
        assertNull(Bidi.isRtlLine(""))
    }

    /** A line with nothing to judge by inherits its predecessor, so a list can't alternate sides. */
    @Test
    fun aNeutralLineInheritsThePreviousDirection() {
        val text = "مقدمة\n123\nخاتمة"
        val lines = Bidi.lines(text)
        assertEquals(3, lines.size)
        assertTrue("the digits line should follow the Arabic above it", lines[1].rtl)
    }

    @Test
    fun aMixedNoteGetsADirectionPerLine() {
        val text = "Shopping list\nقائمة التسوق\nmilk and bread"
        val lines = Bidi.lines(text)
        assertEquals(listOf(false, true, false), lines.map { it.rtl })
    }

    /**
     * Ranges must tile the whole string with no gap: a newline left outside every styled range
     * renders as its own differently-aligned empty paragraph.
     */
    @Test
    fun lineRangesCoverEveryCharacter() {
        val text = "one\nاثنان\nthree"
        val lines = Bidi.lines(text)
        assertEquals(0, lines.first().start)
        assertEquals(text.length, lines.last().end)
        for (i in 1 until lines.size) {
            assertEquals("gap before line $i", lines[i - 1].end, lines[i].start)
        }
    }

    @Test
    fun everyLineIsJustified() {
        val styled = Bidi.directional("English line\nسطر عربي")
        val aligns = styled.paragraphStyles.map { it.item.textAlign }
        assertEquals(2, aligns.size)
        assertTrue("all lines justified", aligns.all { it == androidx.compose.ui.text.style.TextAlign.Justify })
    }

    /**
     * Titles and file names go through NoteFiles.isRtl, which must answer with the same rule —
     * this is what was flipping English card titles that happened to mention an Arabic word.
     */
    @Test
    fun titlesUseTheSameFirstStrongLetterRule() {
        assertFalse(NoteFiles.isRtl("Meeting about تفسير next week"))
        assertTrue(NoteFiles.isRtl("اجتماع about tafsir next week"))
        assertFalse(NoteFiles.isRtl("2026 plans"))
        assertFalse(NoteFiles.isRtl(""))
    }

    @Test
    fun aTrailingNewlineDoesNotLoseTheLastLine() {
        assertEquals(listOf(false, false), Bidi.lines("hello\n").map { it.rtl })
    }
}
