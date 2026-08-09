package com.knotes.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The file-level half of the gallery annotator: renaming, and the geometry the burner relies on.
 * Ported from the desktop `scr-annotator`, so these cases mirror its `test_naming.py`.
 *
 * The drawing itself needs an Android canvas and belongs on a device; what is testable here is
 * everything that decides *which file* and *which pixels*.
 */
class AnnotationsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(name: String): File =
        File(tmp.root, name).apply { writeText("not really an image") }

    // ---------------------------------------------------------------- renaming

    @Test
    fun aLabelBecomesTheFilenameSuffix() {
        val f = file("Screenshot 2026-08-09.png")
        val renamed = Renamer.rename(f, "Screenshot 2026-08-09", "invoice")

        assertEquals("Screenshot 2026-08-09 invoice.png", renamed.name)
        assertTrue(renamed.exists())
        assertTrue("the old name must be gone", !f.exists())
    }

    /**
     * The rule that makes relabelling usable: a second label REPLACES the first. Appending would
     * give "…invoice receipt.png" after two edits, and the name would grow every time you changed
     * your mind.
     */
    @Test
    fun relabellingReplacesRatherThanAppends() {
        val f = file("shot invoice.png")
        val renamed = Renamer.rename(f, "shot", "receipt")

        assertEquals("shot receipt.png", renamed.name)
    }

    @Test
    fun aBlankLabelRestoresTheBareName() {
        val f = file("shot invoice.png")
        assertEquals("shot.png", Renamer.rename(f, "shot", "").name)
    }

    @Test
    fun anUnchangedLabelIsANoOp() {
        val f = file("shot invoice.png")
        val same = Renamer.rename(f, "shot", "invoice")

        assertEquals("shot invoice.png", same.name)
        assertTrue(same.exists())
    }

    @Test
    fun aTakenNameStepsAsideInsteadOfOverwriting() {
        file("shot receipt.png")                    // already there, someone else's
        val f = file("other.png")

        val renamed = Renamer.rename(f, "other", "receipt")

        // Two screenshots can legitimately deserve the same label; losing one would be silent.
        assertEquals("other receipt.png", renamed.name)

        val third = file("third.png")
        val bumped = Renamer.rename(third, "other", "receipt")
        assertEquals("other receipt 2.png", bumped.name)
    }

    /**
     * A separator becomes a space, not nothing. Deleting it would join the words either side —
     * "invoice/receipt" turning into "invoicereceipt" — which is a worse name than either.
     */
    @Test
    fun charactersIllegalInAFilenameBecomeSpaces() {
        val f = file("shot.png")
        assertEquals("shot a b c.png", Renamer.rename(f, "shot", "a/b:c*?\"<>|").name)

        val g = file("other.png")
        assertEquals("other invoice receipt.png", Renamer.rename(g, "other", "invoice/receipt").name)
    }

    @Test
    fun aMultiDotNameKeepsOnlyItsRealExtension() {
        assertEquals("a.b.c" to ".png", Renamer.splitExtension("a.b.c.png"))
        // A dotfile has no extension to split off.
        assertEquals(".hidden" to "", Renamer.splitExtension(".hidden"))
    }

    // ---------------------------------------------------------------- geometry

    @Test
    fun aDragIsNormalisedWhicheverWayItWent() {
        // Dragging up-and-left must give the same rectangle as down-and-right.
        assertEquals(listOf(10f, 20f, 30f, 40f), normalise(30f, 40f, 10f, 20f))
        assertEquals(listOf(10f, 20f, 30f, 40f), normalise(10f, 20f, 30f, 40f))
    }

    @Test
    fun aHighlightTakesItsWidthFromTheDragAndItsHeightFromTheNib() {
        val h = Mark.Highlight(x0 = 100f, x1 = 40f, y = 200f)
        val (l, t, r, b) = h.box()

        assertEquals(40f, l)     // normalised despite the right-to-left drag
        assertEquals(100f, r)
        // Fixed band around y — the drag's vertical position is a centre line, not an edge, which
        // is what stops a highlight looking like a wobbly rectangle.
        assertEquals(200f - AnnotationStyle.HL_HEIGHT_PX / 2, t)
        assertEquals(200f + AnnotationStyle.HL_HEIGHT_PX / 2, b)
    }
}
