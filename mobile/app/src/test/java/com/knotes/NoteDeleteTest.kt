package com.knotes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Deleting a note is gated on its companion folder being empty.
 *
 * The reason is that the `<stem>.d` folder is only reachable *through* the note: nothing else in
 * the app or on disk names those files. Delete the `.md` and the attachments become an orphan
 * folder whose name is a timestamp, which is how a photo goes missing for a year. So a note with
 * files is refused outright, and emptying the folder is a separate, deliberate act.
 */
class NoteDeleteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun noteIn(dir: File, body: String = "body\n"): Note {
        val f = File(dir, NoteFiles.buildName("2026_08_16-101010", "J", 2, "X"))
        f.writeText(body)
        return Note(
            file = f, timestamp = "2026_08_16-101010", type = "J", priority = 2,
            category = "X", content = body, lastModified = f.lastModified(),
            createdMillis = f.lastModified(), isRtl = false,
        )
    }

    @Test
    fun deletesANoteThatHasNoCompanionFolder() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)

        assertTrue(deleteNoteFile(note.file))
        assertFalse(note.file.exists())
    }

    /** An empty folder strands nothing — the note goes, and the folder goes with it. */
    @Test
    fun deletesANoteWhoseCompanionFolderIsEmptyAndTakesTheFolderToo() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)
        val companion = companionDirOf(note.file).also { it.mkdirs() }

        assertTrue(deleteNoteFile(note.file))
        assertFalse(note.file.exists())
        assertFalse("the empty companion folder is cleaned up", companion.exists())
    }

    @Test
    fun refusesANoteThatHasAttachedFiles() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)
        val companion = companionDirOf(note.file).also { it.mkdirs() }
        File(companion, "scan.jpg").writeText("photo")

        assertFalse("a note with files must not be deletable", deleteNoteFile(note.file))
        assertTrue("the note is untouched", note.file.exists())
        assertTrue("and so is its file", File(companion, "scan.jpg").exists())
    }

    /** A file one level down still counts — the check is about stranding, not about the card. */
    @Test
    fun refusesWhenTheOnlyFileIsInASubfolder() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)
        val nested = File(companionDirOf(note.file), "sub").also { it.mkdirs() }
        File(nested, "buried.txt").writeText("x")

        assertTrue(companionHasContent(note.file))
        assertFalse(deleteNoteFile(note.file))
        assertTrue(note.file.exists())
    }

    /** The card's file list only shows top-level files, so the two checks disagree by design. */
    @Test
    fun theCardsFileListAndTheDeleteGateMeasureDifferentThings() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)
        val nested = File(companionDirOf(note.file), "sub").also { it.mkdirs() }
        File(nested, "buried.txt").writeText("x")

        assertEquals(emptyList<String>(), companionFileNames(note.file))
        assertTrue(companionHasContent(note.file))
    }

    /** Emptying the folder is what unblocks it — the whole point of refusing rather than cascading. */
    @Test
    fun deletingBecomesPossibleOnceTheFolderIsEmptied() {
        val dir = tmp.newFolder("notes")
        val note = noteIn(dir)
        val companion = companionDirOf(note.file).also { it.mkdirs() }
        val attachment = File(companion, "scan.jpg").also { it.writeText("photo") }

        assertFalse(deleteNoteFile(note.file))
        attachment.delete()
        assertTrue(deleteNoteFile(note.file))
        assertFalse(note.file.exists())
    }
}
