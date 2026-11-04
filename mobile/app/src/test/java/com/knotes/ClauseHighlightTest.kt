package com.knotes

import com.knotes.util.Clauses
import com.knotes.util.Highlights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Sentence splitting and `==…==` markers.
 *
 * Both are load-bearing for the same reason: a highlight is stored **as text**, so if the phone and
 * the browser disagree about where a sentence starts or how a marker is written, the same tap saves
 * a different string on each — and a marker written wrongly shows up as a stray `=` in the note.
 */
class ClauseHighlightTest {

    private fun texts(spans: List<com.knotes.util.TextSpan>) = spans.map { it.text }

    /** Tap the sentence containing [phrase] (its first occurrence in the reading text). */
    private fun tap(source: String, phrase: String): String {
        val at = Highlights.strip(source).indexOf(phrase)
        assertTrue("'$phrase' not in the text", at >= 0)
        return Highlights.toggleSentenceAt(source, at)
    }

    // ------------------------------------------------------------------ sentences

    @Test
    fun splitsSentencesAtTerminatorsIncludingArabicOnes() {
        assertEquals(
            listOf("One.", "Two!", "ثلاثة؟", "أربعة؛", "Five…"),
            texts(Clauses.sentenceSpans("One. Two! ثلاثة؟ أربعة؛ Five…")),
        )
    }

    @Test
    fun commasDoNotSplitASentence() {
        assertEquals(
            listOf("First part, second part; and the rest.", "Next."),
            texts(Clauses.sentenceSpans("First part, second part; and the rest. Next.")),
        )
    }

    @Test
    fun aDotInsideAWordOrNumberIsNotASentenceEnd() {
        assertEquals(
            listOf("Pi is 3.14 and notes.md lives at example.com/a.b today.", "Done."),
            texts(Clauses.sentenceSpans("Pi is 3.14 and notes.md lives at example.com/a.b today. Done.")),
        )
    }

    @Test
    fun aRunOfTerminatorsEndsTheSentenceOnce() {
        assertEquals(
            listOf("Really?!", "Wait...", "Go."),
            texts(Clauses.sentenceSpans("Really?! Wait... Go.")),
        )
    }

    /** Spans must index back into the ORIGINAL string — that is how a tap finds its sentence. */
    @Test
    fun spansPointAtTheirOwnTextInTheSource() {
        val plain = "Alpha, beta.\nGamma; delta. قال، ثم سكت."
        for (span in Clauses.sentenceSpans(plain)) {
            assertEquals(span.text, plain.substring(span.start, span.end))
        }
    }

    @Test
    fun aLineBreakEndsASentenceAndIsNotSwallowed() {
        val plain = "First line\nSecond line"
        val spans = Clauses.sentenceSpans(plain)
        assertEquals(listOf("First line", "Second line"), texts(spans))
        assertEquals('\n', plain[spans[0].end])
    }

    // ------------------------------------------------------------------ markers

    @Test
    fun aTapHighlightsTheWholeSentenceAndASecondTapClearsIt() {
        val original = "Alpha, beta, gamma. Next one."
        val marked = tap(original, "beta")
        assertEquals("==Alpha, beta, gamma.== Next one.", marked)
        assertEquals(original, tap(marked, "beta"))
    }

    @Test
    fun strippingRemovesMarkersButNoText() {
        assertEquals("Alpha, beta, gamma.", Highlights.strip("Alpha, ==beta,== gamma."))
    }

    @Test
    fun marksTheRightSentenceWhenEarlierOnesAreAlreadyMarked() {
        var s = "One. Two. Three. Two."
        s = tap(s, "One")
        s = tap(s, "Three")
        assertEquals("==One.== Two. ==Three.== Two.", s)
        assertEquals(setOf("One.", "Three."), Highlights.markedTexts(s))
    }

    /** The bug this rewrite is about: a sentence already holding a clause highlight. */
    @Test
    fun aSentenceWithAnOldClauseHighlightBecomesOneHighlightNotNestedMarkers() {
        val old = "Alpha, ==beta,== gamma. Next."
        val on = tap(old, "gamma")
        assertEquals("==Alpha, beta, gamma.== Next.", on)
        assertEquals("Alpha, beta, gamma. Next.", tap(on, "Alpha"))
    }

    @Test
    fun twoOldClauseHighlightsCoveringTheSentenceCountAsHighlighted() {
        val old = "==Alpha,== ==beta.== Next."
        assertEquals("Alpha, beta. Next.", tap(old, "beta"))
    }

    @Test
    fun anOldHighlightSpanningTwoSentencesIsSplitCleanly() {
        val old = "==One. Two.== Three."
        val s = tap(old, "Two")
        assertEquals("==One.== Two. Three.", s)
        assertEquals("One. Two. Three.", tap(s, "One"))
    }

    @Test
    fun adjacentHighlightedSentencesWithoutASpaceMerge() {
        var s = "Yes?No. End."
        s = tap(s, "Yes")
        s = tap(s, "No")
        assertEquals("==Yes?No.== End.", s)
        s = tap(s, "Yes")
        assertEquals("Yes?==No.== End.", s)
    }

    @Test
    fun arabicSentenceRoundTrip() {
        val original = "قال الشيخ، رحمه الله، كلاما طويلا. ثم سكت."
        val on = tap(original, "رحمه")
        assertEquals("==قال الشيخ، رحمه الله، كلاما طويلا.== ثم سكت.", on)
        assertEquals(original, tap(on, "كلاما"))
    }

    /** Text that itself contains "=" must never be corrupted: highlight it cleanly or not at all. */
    @Test
    fun equalsSignsInTheTextAreNeverCorrupted() {
        for (original in listOf("x = y. Next.", "a == b is true. Next.", "Ends with =\nNext.", "=start. Next.", "==lonely. Next.")) {
            val plain = Highlights.strip(original)
            var s = original
            repeat(3) {
                s = Highlights.toggleSentenceAt(s, 0)
                assertEquals("text changed for: $original -> $s", plain, Highlights.strip(s))
            }
        }
    }

    @Test
    fun aRangeOutsideTheTextChangesNothing() {
        val original = "Alpha, beta."
        assertEquals(original, Highlights.toggle(original, 5, 999))
        assertEquals(original, Highlights.toggle(original, 4, 4))
        assertEquals("", Highlights.toggleSentenceAt("", 0))
    }

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

    /**
     * Thousands of random taps over random notes — with old clause highlights, Arabic, "=" in the
     * text, blank lines, runs of terminators. After every tap the reading text must be exactly what
     * it was (a leaked or eaten "=" would change it), and a second tap on the same spot must turn
     * that sentence back off.
     */
    @Test
    fun randomTapsNeverLeaveAStrayEquals() {
        val words = listOf("alpha", "beta", "قال", "الله", "x=1", "a == b", "3.14", "notes.md", "=", "==", "end", "?")
        val seps = listOf(" ", ", ", "، ", ". ", "? ", "! ", "؟ ", "\n", "\n\n", "... ", " — ", "")
        val rnd = Random(20261103)
        var taps = 0
        repeat(500) {
            val sb = StringBuilder()
            repeat(rnd.nextInt(1, 14)) {
                val w = words[rnd.nextInt(words.size)]
                // Sometimes wrap a word in an old highlight, the way clause taps used to.
                if (rnd.nextInt(4) == 0 && !w.contains('=')) sb.append("==").append(w).append("==") else sb.append(w)
                sb.append(seps[rnd.nextInt(seps.size)])
            }
            var s = sb.toString()
            val plain = Highlights.strip(s)
            if (plain.isEmpty()) return@repeat
            repeat(15) {
                val at = rnd.nextInt(plain.length)
                val once = Highlights.toggleSentenceAt(s, at)
                val twice = Highlights.toggleSentenceAt(once, at)
                taps += 2
                assertEquals("reading text changed\nfrom: $s\nto:   $once", plain, Highlights.strip(once))
                assertEquals("reading text changed\nfrom: $once\nto:   $twice", plain, Highlights.strip(twice))
                // Without a literal "=" in the text a tap is never refused. With one, a tap may be
                // refused (left unchanged) when markers around it couldn't be read back — that is
                // the guard working; what matters is that the text above never changed.
                if ('=' !in plain) {
                    assertTrue("tap refused on: $s", once != s && twice != once)
                }
                if (once != s && twice != once) {
                    val hit = Clauses.sentenceAt(plain, at)!!
                    val (_, f1) = Highlights.split(once)
                    val (_, f2) = Highlights.split(twice)
                    for (i in hit.start until hit.end) {
                        if (!plain[i].isWhitespace()) assertFalse("still on after two taps: $twice", f1[i] && f2[i])
                    }
                }
                // Every "=" the file gained is part of a real marker pair: 4 per highlighted run.
                fun extra(src: String): Int {
                    val (p, f) = Highlights.split(src)
                    return src.count { it == '=' } - p.count { it == '=' } - 4 * Highlights.runs(p, f).size
                }
                if (once != s) assertEquals("odd marker in: $once", 0, extra(once))
                s = if (rnd.nextBoolean()) once else twice
            }
        }
        assertTrue(taps > 10_000)
    }
}
