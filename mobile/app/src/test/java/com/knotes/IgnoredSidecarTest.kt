package com.knotes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notes folder is a Syncthing share that also holds the app's own SQLite database. Those files
 * are not notes and must not appear as "needs metadata" items — the same treatment a dotfile gets.
 */
class IgnoredSidecarTest {

    @Test
    fun ignoresTheDatabaseAndAllItsCompanions() {
        listOf(
            "notes.sqlite",
            "notes.sqlite-wal",
            "notes.sqlite-shm",
            "notes.sqlite-journal",
        ).forEach { assertTrue("must ignore $it", isIgnoredSidecar(it)) }
    }

    /** Android storage is case-insensitive in practice and the file is written both ways. */
    @Test
    fun theMatchIsCaseInsensitive() {
        listOf("Notes.SQLite", "NOTES.SQLITE-WAL", "Notes.Sqlite-journal")
            .forEach { assertTrue("must ignore $it", isIgnoredSidecar(it)) }
    }

    /** The rule is a prefix, so companions SQLite adds later are covered without another release. */
    @Test
    fun coversFutureCompanionsToo() {
        assertTrue(isIgnoredSidecar("notes.sqlite-something-new"))
    }

    @Test
    fun doesNotSwallowRealNotesOrOtherFiles() {
        listOf(
            "2026_08_11-123715~J~p2~X.md",
            "2026_08_11-123715~~p3~.md",
            "my notes.sqlite backup.md",   // contains it, but doesn't start with it
            "shopping.txt",
            "report.pdf",
        ).forEach { assertFalse("must not ignore $it", isIgnoredSidecar(it)) }
    }

    /** 'trash' was already covered by the housekeeping-folder rule; confirm it still is. */
    @Test
    fun trashIsStillTreatedAsHousekeeping() {
        listOf("trash", "Trash", ".trash", "trashed", "deleted", "merged", "imported")
            .forEach { assertTrue("must treat $it as housekeeping", isAuxDir(it)) }
    }
}
