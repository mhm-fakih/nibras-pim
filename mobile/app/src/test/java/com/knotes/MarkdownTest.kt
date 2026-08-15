package com.knotes

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.knotes.util.Clauses
import com.knotes.util.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown in the note reader.
 *
 * The property that matters most is not which things get styled — it is that styling changes **no
 * offsets**. The reader maps a tap to a clause by character position and stores highlights as text,
 * so a renderer that stripped `**` would shift every position after it and highlight the wrong
 * words. Hence the markers stay and are merely dimmed.
 */
class MarkdownTest {

    private fun boldRanges(text: String) =
        Markdown.spans(text).filter { it.style.fontWeight == FontWeight.Bold }

    @Test
    fun boldIsStyledIncludingItsMarkers() {
        val text = "a **strong** word"
        val bold = boldRanges(text).first()
        assertEquals(text.indexOf("**"), bold.start)
        assertEquals(text.indexOf("** word") + 2, bold.end)
        assertEquals("**strong**", text.substring(bold.start, bold.end))
    }

    @Test
    fun italicIsStyledInBothSpellings() {
        assertTrue(Markdown.spans("an *emphatic* word").any { it.style.fontStyle == FontStyle.Italic })
        assertTrue(Markdown.spans("an _emphatic_ word").any { it.style.fontStyle == FontStyle.Italic })
    }

    /** `**x**` must not also read as italic `*` pairs — that would double-style and look wrong. */
    @Test
    fun boldIsNotAlsoItalic() {
        assertTrue(Markdown.spans("**strong**").none { it.style.fontStyle == FontStyle.Italic })
    }

    @Test
    fun headingsAreBoldAndLarger() {
        val spans = Markdown.spans("# Title\nbody")
        val heading = spans.first { it.style.fontWeight == FontWeight.Bold }
        assertEquals(0, heading.start)
        assertEquals("# Title", "# Title\nbody".substring(heading.start, heading.end))
    }

    /** Backticks mean "as written": a path with an underscore is not italic. */
    @Test
    fun emphasisInsideCodeIsLeftAlone() {
        val text = "run `my_file_name.sh` now"
        assertTrue(Markdown.spans(text).none { it.style.fontStyle == FontStyle.Italic })
    }

    @Test
    fun aLoneAsteriskIsNotEmphasis() {
        assertTrue(Markdown.spans("2 * 3 = 6").none { it.style.fontStyle == FontStyle.Italic })
        assertTrue(boldRanges("a ** b").isEmpty())
    }

    /**
     * The load-bearing property: styling must not move a single character, or the reader's clause
     * offsets and stored highlights stop matching the text.
     */
    @Test
    fun everySpanIndexesIntoTheUnchangedText() {
        val text = "# Heading\nA **bold** clause, and *another* one.\nمع **نص** عربي."
        for (span in Markdown.spans(text)) {
            assertTrue("span out of bounds", span.start in 0..text.length && span.end in 0..text.length)
            assertTrue("inverted span", span.start < span.end)
        }
        // And the clause boundaries are unaffected by the presence of markup.
        val clauses = Clauses.clauseSpans(text)
        for (c in clauses) {
            assertEquals(c.text, text.substring(c.start, c.end))
        }
    }

    @Test
    fun plainTextProducesNoSpans() {
        assertTrue(Markdown.spans("just an ordinary note about nothing").isEmpty())
    }
}
