package com.knotes

import com.knotes.player.NextTrack
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The "random old unbookmarked" next-track picker.
 *
 * The rules it has to keep are all *exclusions* — bookmarked, recent, just-played — and an
 * exclusion that silently stops working is invisible: the feature still plays something, just the
 * wrong thing. Hence a test per rule rather than one that asserts "it returned a file".
 */
class NextTrackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private fun daysAgo(d: Long) = now - TimeUnit.DAYS.toMillis(d)

    /** A media file [ageDays] old, optionally with a bookmark sidecar beside it. */
    private fun media(dir: File, name: String, ageDays: Long, bookmarked: Boolean = false): File {
        dir.mkdirs()
        val f = File(dir, "$name.mp3")
        f.writeBytes(ByteArray(4))
        f.setLastModified(daysAgo(ageDays))
        if (bookmarked) File(dir, "$name.tmk").writeText("[0:12] something\n")
        return f
    }

    @Test
    fun picksOnlyOldUnbookmarkedFiles() {
        val root = tmp.newFolder("library")
        val old = media(root, "forgotten", 400)
        media(root, "fresh", 3)                                   // too recent
        media(root, "studied", 400, bookmarked = true)            // has bookmarks

        repeat(20) {
            val pick = NextTrack.randomOldUnbookmarked(listOf(root), now = now)
            assertEquals(old.absolutePath, pick?.absolutePath)
        }
    }

    @Test
    fun findsFilesInEveryConfiguredRoot() {
        val a = tmp.newFolder("libA")
        val b = tmp.newFolder("libB")
        media(a, "one", 300)
        val inB = media(b, "two", 300)

        // Enough draws that only picking from the first root would be caught.
        val picks = (1..40).mapNotNull {
            NextTrack.randomOldUnbookmarked(listOf(a, b), now = now, random = Random(it))
        }
        assertTrue("never picked from the second root", picks.any { it.absolutePath == inB.absolutePath })
    }

    @Test
    fun descendsIntoSubfoldersButNotSyncArchives() {
        val root = tmp.newFolder("library")
        val deep = media(File(root, "series/season1"), "episode", 300)
        media(File(root, ".stversions"), "old-copy", 300)         // Syncthing's archive
        media(File(root, ".thumbnails"), "thumb", 300)

        val picks = (1..30).mapNotNull {
            NextTrack.randomOldUnbookmarked(listOf(root), now = now, random = Random(it))
        }
        assertTrue("subfolder never reached", picks.any { it.absolutePath == deep.absolutePath })
        assertTrue("picked out of a pruned folder", picks.all { it.absolutePath == deep.absolutePath })
    }

    @Test
    fun neverReturnsAnExcludedFile() {
        val root = tmp.newFolder("library")
        val a = media(root, "a", 300)
        val b = media(root, "b", 300)

        repeat(20) {
            val pick = NextTrack.randomOldUnbookmarked(
                listOf(root), exclude = setOf(a.absolutePath), now = now,
            )
            assertEquals(b.absolutePath, pick?.absolutePath)
        }
    }

    /** A library filled last month has nothing 90 days old; the option must still do something. */
    @Test
    fun fallsBackToTheOldestWhenNothingIsOldEnough() {
        val root = tmp.newFolder("library")
        val oldest = media(root, "oldest", 20)
        media(root, "newer", 2)

        val picks = (1..30).mapNotNull {
            NextTrack.randomOldUnbookmarked(listOf(root), now = now, random = Random(it))
        }
        assertTrue("fallback returned nothing", picks.isNotEmpty())
        assertTrue(
            "fallback should favour the oldest end",
            picks.any { it.absolutePath == oldest.absolutePath },
        )
    }

    /** An emptied sidecar means the bookmarks were deleted — the file is unbookmarked again. */
    @Test
    fun anEmptySidecarDoesNotCountAsBookmarked() {
        val root = tmp.newFolder("library")
        val f = media(root, "cleared", 300)
        File(root, "cleared.tmk").writeText("")

        assertEquals(f.absolutePath, NextTrack.randomOldUnbookmarked(listOf(root), now = now)?.absolutePath)
    }

    @Test
    fun returnsNullWhenEverythingIsBookmarked() {
        val root = tmp.newFolder("library")
        media(root, "one", 300, bookmarked = true)
        media(root, "two", 300, bookmarked = true)

        assertNull(NextTrack.randomOldUnbookmarked(listOf(root), now = now))
    }

    @Test
    fun nextSiblingWalksTheFolderAlphabeticallyAndStopsAtTheEnd() {
        val root = tmp.newFolder("folder")
        val a = media(root, "a", 1)
        val b = media(root, "b", 1)
        val c = media(root, "c", 1)

        assertEquals(b.absolutePath, NextTrack.nextSibling(a)?.absolutePath)
        assertEquals(c.absolutePath, NextTrack.nextSibling(b)?.absolutePath)
        assertNull(NextTrack.nextSibling(c))
    }
}
