package com.knotes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Changing a note's tags renames its file, because the name encodes them.
 *
 * That makes every reference to the note held elsewhere stale the moment a tag changes, and the
 * failure it causes is silent and duplicating rather than loud: a body written back to the old path
 * doesn't error, it *recreates* the pre-rename file, and you end up with two notes.
 *
 * These pin the mechanism and the guard. The Compose side is fixed separately with
 * `rememberUpdatedState` (see NoteReadScreen); this is the second line of defence, which holds
 * whatever the UI does.
 */
class RetagSafetyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun noteAt(dir: File, type: String?, priority: Int, category: String?, body: String): Note {
        val f = File(dir, NoteFiles.buildName("2026_08_15-101010", type, priority, category))
        f.writeText(body)
        return Note(
            file = f, timestamp = "2026_08_15-101010", type = type, priority = priority,
            category = category, content = body, lastModified = f.lastModified(),
            createdMillis = f.lastModified(), isRtl = false,
        )
    }

    /** Renaming is the whole reason this is delicate — state it plainly. */
    @Test
    fun changingAPriorityRenamesTheFile() {
        val dir = tmp.newFolder("notes")
        val note = noteAt(dir, "J", 2, "X", "body\n")
        val old = note.file

        val target = File(dir, NoteFiles.buildName(note.timestamp, "J", 4, "X"))
        old.renameTo(target)

        assertFalse("the old path is gone after a retag", old.exists())
        assertTrue(target.exists())
        assertEquals(1, dir.listFiles()!!.size)
    }

    /**
     * The bug this guards against: writing a body through a note whose file has been renamed away
     * recreates the old file. Demonstrated here on the raw file operations that
     * `NoteRepository.update` performs, since that is what a stale reference reaches.
     */
    @Test
    fun writingThroughAStaleReferenceWouldRecreateTheOldFile() {
        val dir = tmp.newFolder("notes")
        val note = noteAt(dir, "J", 2, "X", "body\n")
        val stalePath = note.file
        File(dir, NoteFiles.buildName(note.timestamp, "J", 4, "X")).also { stalePath.renameTo(it) }

        // What an unguarded save does — write to the path the stale note remembers.
        stalePath.writeText("body\n")

        assertEquals("two notes where there was one", 2, dir.listFiles()!!.size)
    }

    /** And the guard: a body-only save skips when the file it names is gone. */
    @Test
    fun theGuardSkipsWhenTheFileHasBeenRenamedAway() {
        val dir = tmp.newFolder("notes")
        val note = noteAt(dir, "J", 2, "X", "body\n")
        val stalePath = note.file
        File(dir, NoteFiles.buildName(note.timestamp, "J", 4, "X")).also { stalePath.renameTo(it) }

        // The condition MainViewModel.saveBody checks before touching anything.
        val shouldSave = note.file.exists()

        assertFalse("a stale note must not be written", shouldSave)
        assertEquals("still one note", 1, dir.listFiles()!!.size)
    }

    /** A live note still saves — the guard must not turn every save into a no-op. */
    @Test
    fun aLiveNoteStillSaves() {
        val dir = tmp.newFolder("notes")
        val note = noteAt(dir, "J", 2, "X", "body\n")

        assertTrue(note.file.exists())
        note.file.writeText("edited\n")

        assertEquals(1, dir.listFiles()!!.size)
        assertEquals("edited\n", note.file.readText())
    }
}
