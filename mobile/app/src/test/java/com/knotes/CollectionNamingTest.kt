package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A collection is a note plus its attachments, so the two names have to agree exactly:
 * `<stem>.md` beside `<stem>.d`. Nothing enforces that at runtime — the desktop simply doesn't
 * pair them if they drift, and the collection reads as a stray folder.
 *
 * The collector builds the folder name FROM the note name for that reason; these pin the shape of
 * both, and that the desktop's own rules accept the result.
 */
class CollectionNamingTest {

    private val token = "2026_08_11-123715"

    @Test
    fun theNoteNameIsTheCanonicalNoteFormat() {
        val name = NoteFiles.buildName(token, "J", 2, "X")

        assertEquals("2026_08_11-123715~J~p2~X.md", name)
        assertTrue("must satisfy the shared note-name rule", isFormattedNote(name))
    }

    @Test
    fun theCompanionFolderIsTheNoteStemPlusD() {
        val note = java.io.File("/tmp", NoteFiles.buildName(token, "J", 2, "X"))

        assertEquals("2026_08_11-123715~J~p2~X.d", companionDirOf(note).name)
    }

    /** The example from the request, end to end. */
    @Test
    fun theRequestedPairing() {
        val note = java.io.File("/tmp", NoteFiles.buildName(token, "J", 2, "X"))
        val folder = companionDirOf(note)

        assertEquals("2026_08_11-123715~J~p2~X.md", note.name)
        assertEquals("2026_08_11-123715~J~p2~X.d", folder.name)
        assertEquals(note.name.removeSuffix(".md"), folder.name.removeSuffix(".d"))
    }

    @Test
    fun aDeDupSuffixKeepsBothNamesInStep() {
        // Two collections in the same second: the token gains "-2" and BOTH names follow it.
        val note = java.io.File("/tmp", NoteFiles.buildName("$token-2", "J", 2, "X"))

        assertEquals("2026_08_11-123715-2~J~p2~X.md", note.name)
        assertEquals("2026_08_11-123715-2~J~p2~X.d", companionDirOf(note).name)
        assertTrue(isFormattedNote(note.name))
    }

    @Test
    fun blankTagsStillProduceAReadableNote() {
        val name = NoteFiles.buildName(token, null, 3, null)

        assertEquals("2026_08_11-123715~~p3~.md", name)
        assertTrue("the desktop counts a blank-tagged note as a note", isFormattedNote(name))
    }
}
