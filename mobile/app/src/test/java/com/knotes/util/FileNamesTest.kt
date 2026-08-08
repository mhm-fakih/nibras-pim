package com.knotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.Normalizer

/**
 * Regression cover for "a file with an Arabic name doesn't open on mobile".
 *
 * Every case here is invisible in ASCII: the same reader-identical name written two ways, or an
 * extension made unrecognisable by a mark that renders as nothing at all.
 */
class FileNamesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Right-to-left mark. Renders as nothing; copies along with Arabic text; ends up in filenames. */
    private val RLM = "‏"

    private val arabicPdf = "بيان الارشاد والتوجيه.pdf"

    @Test
    fun trailingBidiMarkDoesNotHideTheExtension() {
        // The whole bug in one assertion: with File.extension this is "pdf‏", which matches no
        // MIME type; for an mp3 it also means isMediaFile() is false and the in-app player is
        // skipped in favour of an ACTION_VIEW that nothing can resolve.
        assertEquals("pdf", FileNames.ext(arabicPdf + RLM))
        assertEquals("mp3", FileNames.ext("محاضرة.mp3" + RLM))
    }

    @Test
    fun extensionIsStillTheOrdinaryOneForOrdinaryNames() {
        assertEquals("pdf", FileNames.ext(arabicPdf))
        assertEquals("md", FileNames.ext("2026_08_08-101500~W~p3~A.md"))
        assertEquals("pdf", FileNames.ext("REPORT.PDF"))
        assertEquals("", FileNames.ext("no-extension"))
    }

    /**
     * Arabic has essentially no canonical composition — NFC/NFD on harakat is a no-op, so it is
     * NOT the axis that differs. What does differ is the presentation block: U+FEFB is the
     * lam-alef ligature written as one character, U+0644 U+0627 is the same word written as two.
     * That is a compatibility equivalence, which is why matchKey uses NFKC.
     */
    @Test
    fun presentationFormAndPlainArabicShareAMatchKey() {
        val ligature = "بلاغ.pdf".replace("لا", "ﻻ")
        val plain = "بلاغ.pdf"
        assertTrue("precondition: the two spellings differ byte-wise", ligature != plain)
        assertTrue(
            "NFC must NOT fold these — that is why matchKey uses NFKC",
            Normalizer.normalize(ligature, Normalizer.Form.NFC) !=
                Normalizer.normalize(plain, Normalizer.Form.NFC),
        )
        assertEquals(FileNames.matchKey(plain), FileNames.matchKey(ligature))
    }

    @Test
    fun resolveFindsTheFileWhenTheStoredNameCarriesABidiMark() {
        val dir = tmp.newFolder("1728")
        val onDisk = java.io.File(dir, arabicPdf).apply { writeText("x") }

        // What the server's files_list column holds, versus what Syncthing wrote.
        val resolved = FileNames.resolve(dir, arabicPdf + RLM)

        assertNotNull("stored name with a bidi mark must still resolve", resolved)
        // It must resolve to the filesystem's spelling — the requested one is not a real path.
        assertEquals(onDisk.name, resolved!!.name)
        assertTrue(resolved.exists())
    }

    @Test
    fun resolvePrefersTheExactNameAndReportsAGenuineMiss() {
        val dir = tmp.newFolder("1729")
        java.io.File(dir, arabicPdf).writeText("x")

        assertEquals(arabicPdf, FileNames.resolve(dir, arabicPdf)?.name)
        assertNull(FileNames.resolve(dir, "شيء آخر.pdf"))
    }
}
