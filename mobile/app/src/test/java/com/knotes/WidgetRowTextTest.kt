package com.knotes

import com.knotes.reminder.widgetRowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Top priority widget's row text.
 *
 * The bug these guard against was invisible in code review and obvious on a home screen: every row
 * stopped after twenty characters however wide the widget was, because the text was cut in Kotlin
 * instead of by the TextView that knows the width. The tests are therefore mostly about what this
 * function must NOT do.
 */
class WidgetRowTextTest {

    /** The whole point: no cut at a width this function cannot possibly know. */
    @Test
    fun textIsNotTruncatedToSomeFixedShortLength() {
        val note = "Follow up with the supplier about the delayed shipment before Friday"

        assertEquals(note, widgetRowText(note))
    }

    /** A row is one line. A multi-line note must not push the row open or lose its later text. */
    @Test
    fun aMultiLineNoteBecomesOneLine() {
        val text = widgetRowText("Call the bank\nask about the transfer fee")

        assertEquals("Call the bank ask about the transfer fee", text)
        assertTrue("newline survived into the row", !text.contains('\n'))
    }

    /** Windows line endings arrive here from files synced off a desktop. */
    @Test
    fun carriageReturnsAreFlattenedToo() {
        assertEquals("one two", widgetRowText("one\r\ntwo"))
    }

    /**
     * Flattening an indented or blank-line-separated note produces long runs of spaces, and on a
     * widget those runs are spent width — the row would show three words and a gap.
     */
    @Test
    fun runsOfWhitespaceCollapse() {
        assertEquals("Groceries milk bread", widgetRowText("Groceries\n\n    milk\n    bread"))
    }

    /** Highlight markers are editing syntax; a widget row is a label, as the note cards are. */
    @Test
    fun highlightMarkersAreStripped() {
        assertEquals("Pay the invoice today", widgetRowText("Pay the ==invoice== today"))
    }

    @Test
    fun blankAndWhitespaceOnlyContentGiveAnEmptyRow() {
        assertEquals("", widgetRowText(""))
        assertEquals("", widgetRowText("   \n\n  "))
    }

    /**
     * The remaining cap is a binder-transaction guard, not a display limit: a note body is
     * unbounded, and every row crosses to the home screen through a transaction shared by all of
     * them. It must bound the row without being anywhere near a width a reader would notice.
     */
    @Test
    fun anEnormousNoteIsBoundedButNotShortenedToAWidth() {
        val huge = "word ".repeat(20_000)

        val text = widgetRowText(huge)

        assertTrue("not bounded — a long note could break the widget", text.length <= 200)
        assertTrue("bounded so tightly it became a display limit again", text.length > 150)
    }
}
