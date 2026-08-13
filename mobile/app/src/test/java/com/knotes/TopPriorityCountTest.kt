package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Top priority widget's title count reads the FILE NAMES
 * ([NoteRepository.countByPriority]) while its list reads parsed notes, so the two agree only for
 * as long as the name is where the priority actually lives. These tests pin that.
 *
 * The count itself needs a Context and so belongs on a device; what is checkable here — and what
 * would actually break the count — is whether `NoteFiles.parse` reports the same priority the note
 * model ends up carrying, across the name shapes this folder really contains.
 */
class TopPriorityCountTest {

    /** The same predicate `countByPriority` applies, minus the filesystem. */
    private fun counts(name: String): Boolean =
        isFormattedNote(name) && NoteFiles.parse(name).priority == TOP

    private val TOP = 4

    @Test
    fun countsACanonicalPriorityFourNote() {
        assertTrue(counts("2026_08_11-101500~N~p4~A.md"))
    }

    @Test
    fun ignoresOtherPriorities() {
        listOf(
            "2026_08_11-101500~N~p1~A.md",
            "2026_08_11-101500~N~p3~A.md",
            "2026_08_11-101500~N~p5~A.md",
        ).forEach { assertTrue("must not count $it", !counts(it)) }
    }

    /** p40 must not read as p4 — an anchored match, not a prefix one. */
    @Test
    fun doesNotConfuseATwoDigitPriority() {
        assertEquals(40, NoteFiles.parse("2026_08_11-101500~N~p40~A.md").priority)
        assertTrue(!counts("2026_08_11-101500~N~p40~A.md"))
    }

    /** Foreign files Syncthing drops in the folder are not notes and must not be counted. */
    @Test
    fun ignoresFilesThatArentNotes() {
        listOf(
            "notes.txt",
            "IMG_20260811_101500.jpg",
            "p4.md",
            "2026_08_11-101500.md",
            ".stfolder",
        ).forEach { assertTrue("must not count $it", !counts(it)) }
    }

    @Test
    fun countsANoteWithNoTypeOrCategory() {
        // The type and category segments are optional; the priority token is what matters here.
        assertTrue(counts("2026_08_11-101500~~p4~.md"))
    }

    @Test
    fun countsALegacyTxtNote() {
        assertEquals(4, NoteFiles.parse("2026_08_11-101500~N~p4~A.txt").priority)
    }
}
