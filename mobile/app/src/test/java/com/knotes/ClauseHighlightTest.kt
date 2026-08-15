package com.knotes

import com.knotes.util.Clauses
import com.knotes.util.Highlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clause splitting and `==…==` markers.
 *
 * Both are load-bearing for the same reason: a highlight is stored **as text**, so if the phone and
 * the browser disagree about where a clause starts or how a marker is written, the same tap saves a
 * different string on each and highlights stop matching across clients.
 */
class ClauseHighlightTest {

    private fun texts(spans: List<com.knotes.util.TextSpan>) = spans.map { it.text }

    @Test
    fun splitsSentencesAtTerminatorsIncludingArabicOnes() {
        assertEquals(
            listOf("One.", "Two!", "ثلاثة؟", "أربعة؛"),
            texts(Clauses.sentenceSpans("One. Two! ثلاثة؟ أربعة؛")),
        )
    }

    @Test
    fun splitsSentencesFurtherAtClauseBreaks() {
        assertEquals(
            listOf("First part,", "second part;", "and the rest."),
            texts(Clauses.clauseSpans("First part, second part; and the rest.")),
        )
    }

    @Test
    fun keepsArabicCommaWithItsClause() {
        assertEquals(
            listOf("قال الشيخ،", "رحمه الله،", "كلاما طويلا."),
            texts(Clauses.clauseSpans("قال الشيخ، رحمه الله، كلاما طويلا.")),
        )
    }

    /** Spans must index back into the ORIGINAL string — that is how a tap finds its clause. */
    @Test
    fun spansPointAtTheirOwnTextInTheSource() {
        val plain = "Alpha, beta.\nGamma; delta."
        for (span in Clauses.clauseSpans(plain)) {
            assertEquals(span.text, plain.substring(span.start, span.end))
        }
    }

    @Test
    fun aLineBreakEndsAClauseAndIsNotSwallowed() {
        val plain = "First line\nSecond line"
        assertEquals(listOf("First line", "Second line"), texts(Clauses.clauseSpans(plain)))
        // The newline itself stays in the source between the two spans, so rendering keeps the break.
        val spans = Clauses.clauseSpans(plain)
        assertEquals('\n', plain[spans[0].end])
    }

    @Test
    fun markWrapsTheClauseAndUnmarkPutsItBack() {
        val original = "Alpha, beta, gamma."
        val marked = Highlights.mark(original, "beta,")
        assertEquals("Alpha, ==beta,== gamma.", marked)
        assertTrue("beta," in Highlights.markedTexts(marked))
        assertEquals(original, Highlights.unmark(marked, "beta,"))
    }

    /** What the reader sees never contains the markers. */
    @Test
    fun strippingRemovesMarkersButNoText() {
        assertEquals("Alpha, beta, gamma.", Highlights.strip("Alpha, ==beta,== gamma."))
    }

    /**
     * The regression this design exists to avoid: an earlier highlight shifts every later position
     * by four characters, so a marker placed by searching the raw source would land on the wrong
     * words. [Highlights.mark] searches the stripped text and maps back.
     */
    @Test
    fun marksTheRightClauseWhenEarlierOnesAreAlreadyMarked() {
        var s = "one, two, three, four."
        s = Highlights.mark(s, "one,")
        s = Highlights.mark(s, "three,")
        assertEquals("==one,== two, ==three,== four.", s)
        assertEquals(setOf("one,", "three,"), Highlights.markedTexts(s))
    }

    /** Highlighting, then un-highlighting, must leave the note byte-identical. */
    @Test
    fun aRoundTripLeavesTheNoteUnchanged() {
        val original = "قال الشيخ، رحمه الله، كلاما طويلا. ثم سكت."
        var s = original
        val clauses = texts(Clauses.clauseSpans(original))
        clauses.forEach { s = Highlights.mark(s, it) }
        assertEquals(clauses.toSet(), Highlights.markedTexts(s))
        clauses.forEach { s = Highlights.unmark(s, it) }
        assertEquals(original, s)
    }

    /** A card is a plain label: the markers must not reach it, the way they don't on the desktop. */
    @Test
    fun aCardPreviewShowsNoMarkers() {
        val note = Note(
            file = java.io.File("/tmp/2026_08_15-101010~J~p2~X.md"),
            timestamp = "2026_08_15-101010",
            type = "J", priority = 2, category = "X",
            content = "Alpha, ==beta,== gamma.\nsecond line",
            lastModified = 0L, createdMillis = 0L, isRtl = false,
        )
        assertEquals("Alpha, beta, gamma.", note.preview(1))
        assertTrue("search should see through the markers", "beta, gamma" in note.searchText())
    }

    @Test
    fun markingAnAbsentClauseChangesNothing() {
        val original = "Alpha, beta."
        assertEquals(original, Highlights.mark(original, "gamma"))
        assertFalse("gamma" in Highlights.markedTexts(original))
    }

    /** The clauses of a marked note are the same as the clauses of the plain one. */
    @Test
    fun markersDoNotDisturbClauseBoundaries() {
        val plain = "Alpha, beta, gamma."
        val marked = Highlights.mark(plain, "beta,")
        assertEquals(
            texts(Clauses.clauseSpans(plain)),
            texts(Clauses.clauseSpans(Highlights.strip(marked))),
        )
    }
}
