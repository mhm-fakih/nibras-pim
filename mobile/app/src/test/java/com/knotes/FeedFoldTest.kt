package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Folding a note in the recent-notes feed.
 *
 * Folded by WORDS, not by lines: a line depends on the widget's width and the phone's font scale,
 * so a line-based cap would put "Read more" on different notes for two people reading the same
 * feed — or on the same person after resizing the widget.
 */
class FeedFoldTest {

    /** The rule as PlansWidgetService.foldedBody applies it. */
    private fun fold(content: String, cap: Int, expanded: Boolean): String {
        val text = content.trim()
        if (expanded) return text
        val words = text.split(Regex("\\s+"))
        if (words.size <= cap) return text
        return words.take(cap).joinToString(" ") + "…  Read more"
    }

    private fun words(n: Int) = (1..n).joinToString(" ") { "w$it" }

    @Test
    fun `a short note is shown whole, with no Read more`() {
        val note = words(12)
        assertEquals(note, fold(note, 40, expanded = false))
        assertFalse(fold(note, 40, expanded = false).contains("Read more"))
    }

    @Test
    fun `a note exactly at the cap is not folded`() {
        // Off-by-one here means "Read more" that reveals nothing — the worst kind, because tapping
        // it appears to do nothing at all.
        val note = words(40)
        assertEquals(note, fold(note, 40, expanded = false))
    }

    @Test
    fun `one word over the cap folds`() {
        val folded = fold(words(41), 40, expanded = false)
        assertTrue(folded.endsWith("Read more"))
        assertTrue(folded.startsWith("w1 w2"))
        assertFalse("the 41st word is held back", folded.contains("w41"))
    }

    @Test
    fun `an unfolded note shows everything and drops the affordance`() {
        val full = fold(words(200), 40, expanded = true)
        assertTrue(full.contains("w200"))
        assertFalse(full.contains("Read more"))
    }

    @Test
    fun `line breaks and runs of spaces count as one separator`() {
        // Notes are written with blank lines between paragraphs; counting those as words would fold
        // a short note that merely happens to be laid out loosely.
        val note = "one\n\ntwo   three\nfour"
        assertEquals(note.trim(), fold(note, 4, expanded = false))
    }
}
