package com.knotes

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The folder inspection behind every step of the first-run tour.
 *
 * This is what the wizard shows under each path, and it is the whole reason the wizard is better
 * than the Settings screen: a path that doesn't exist and a folder that is merely empty produce
 * the same silent, empty app, and only this can tell them apart before the user has spent a week
 * assuming the app is broken.
 */
class OnboardingTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun anEmptyPathIsBlankRatherThanMissing() {
        // "" and "/nope" both fail, but only one of them is the user's fault to fix.
        assertEquals(FolderState.BLANK, folderState(""))
        assertEquals(FolderState.BLANK, folderState("   "))
    }

    @Test
    fun aPathThatIsNotThereIsMissing() {
        assertEquals(FolderState.MISSING, folderState(File(tmp.root, "not-created").absolutePath))
    }

    /** The distinction the wizard exists to draw: nothing there vs. a real but unused folder. */
    @Test
    fun anExistingFolderIsEmptyOrHasFiles() {
        val dir = tmp.newFolder("notes")
        assertEquals(FolderState.EMPTY, folderState(dir.absolutePath))

        File(dir, "2026_08_29-101010~J~p2~X.md").writeText("a note")
        assertEquals(FolderState.HAS_FILES, folderState(dir.absolutePath))
    }

    /** Pointing a folder setting at a file is a wrong path, not a folder waiting to be created. */
    @Test
    fun aFileIsNotAFolderAndIsNotOfferedCreation() {
        val f = tmp.newFile("notes.txt")
        assertEquals(FolderState.NOT_A_FOLDER, folderState(f.absolutePath))
    }

    /** Surrounding whitespace comes free with any paste; it must not read as a different path. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        val dir = tmp.newFolder("library")
        assertEquals(FolderState.EMPTY, folderState("  ${dir.absolutePath}  "))
    }

    @Test
    fun creatingAFolderMakesItAndItsParents() {
        val deep = File(tmp.root, "a/b/c").absolutePath
        assertTrue(createFolder(deep))
        assertEquals(FolderState.EMPTY, folderState(deep))
    }

    /**
     * "Is it there now?" — not "did I make it". A second tap on Create, or a folder that appeared
     * between the check and the tap, must report success rather than an error over a working path.
     */
    @Test
    fun creatingAnExistingFolderSucceedsAndKeepsItsContents() {
        val dir = tmp.newFolder("existing")
        File(dir, "keep.md").writeText("x")

        assertTrue(createFolder(dir.absolutePath))
        assertEquals(FolderState.HAS_FILES, folderState(dir.absolutePath))
        assertEquals("x", File(dir, "keep.md").readText())
    }

    /** A failure here is reported in the wizard, never thrown on the app's very first screen. */
    @Test
    fun creatingAnImpossibleFolderFailsQuietly() {
        val overAFile = File(tmp.newFile("blocker.txt"), "child").absolutePath
        assertFalse(createFolder(overAFile))
        assertFalse(createFolder(""))
    }
}
