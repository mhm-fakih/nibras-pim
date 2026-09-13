package com.knotes

import com.knotes.player.NextTrack
import com.knotes.player.PlayNextMode
import com.knotes.player.noPickMessage
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

    // ---- RANDOM_OLD_PARENT's search scope -------------------------------------------------

    /** An ordinary folder is its own scope — the file's neighbours are what you meant. */
    @Test
    fun theParentScopeOfAnOrdinaryFolderIsThatFolder() {
        val dir = tmp.newFolder("podcasts")
        val f = media(dir, "episode", 10)

        assertEquals(dir.absolutePath, NextTrack.parentScope(f)?.absolutePath)
    }

    /**
     * The case the mode exists for: inside a note's `.d` folder the scope climbs one level, so the
     * search reaches the OTHER notes' attachments. Confined to the `.d` it would only ever offer
     * the files of the note already playing.
     */
    @Test
    fun theParentScopeOfACompanionFolderIsTheFolderHoldingTheNotes() {
        val notes = tmp.newFolder("notes")
        val companion = File(notes, "2026_08_17-101010~J~p2~X.d")
        val f = media(companion, "voice", 10)

        assertEquals(notes.absolutePath, NextTrack.parentScope(f)?.absolutePath)
    }

    /** And from there the recursive walk really does reach a sibling note's files. */
    @Test
    fun theParentScopeReachesASiblingNotesAttachments() {
        val notes = tmp.newFolder("notes")
        val mine = File(notes, "2026_08_17-101010~J~p2~X.d")
        val theirs = File(notes, "2026_06_01-090000~J~p2~X.d")
        val playing = media(mine, "playing", 10)
        val other = media(theirs, "forgotten", 400)

        val scope = NextTrack.parentScope(playing)!!
        val pick = NextTrack.randomOldUnbookmarked(
            listOf(scope), exclude = setOf(playing.absolutePath), now = now,
        )
        assertEquals(other.absolutePath, pick?.absolutePath)
    }

    /** The scope is a fence, not a hint: nothing outside it can be picked. */
    @Test
    fun theParentScopeNeverLeavesItsFolder() {
        val library = tmp.newFolder("library")
        val inside = File(library, "podcasts")
        val outside = File(library, "audiobooks")
        val playing = media(inside, "playing", 10)
        media(inside, "neighbour", 400)
        val far = media(outside, "elsewhere", 400)

        val scope = NextTrack.parentScope(playing)!!
        repeat(20) {
            val pick = NextTrack.randomOldUnbookmarked(
                listOf(scope), exclude = setOf(playing.absolutePath), now = now, random = Random(it),
            )
            assertTrue("picked outside the scope", pick == null || pick.absolutePath != far.absolutePath)
        }
    }

    /** A companion folder sitting at the filesystem root has nowhere to climb to; it stays put. */
    @Test
    fun aCompanionFolderWithNoParentFallsBackToItself() {
        val companion = tmp.newFolder("orphan.d")
        val f = media(companion, "voice", 10)

        assertEquals(companion.parentFile.absolutePath, NextTrack.parentScope(f)?.absolutePath)
    }

    // ---- RANDOM_NEW_PARENT: the same scope, the other end of the clock ----------------------

    /** The point of the mode: what arrived last, not what has sat longest. */
    @Test
    fun theNewestPickFavoursRecentFilesOverStaleOnes() {
        val root = tmp.newFolder("library")
        val fresh = media(root, "just-arrived", 1)
        media(root, "ancient", 400)

        repeat(20) {
            val pick = NextTrack.randomNewUnbookmarked(listOf(root), now = now)
            assertEquals(fresh.absolutePath, pick?.absolutePath)
        }
    }

    /**
     * "New" is relative, unlike "old". A folder whose newest file is two years old still has a
     * newest file, and an age threshold here would make the mode do nothing at all.
     */
    @Test
    fun theNewestPickStillReturnsSomethingInAnEntirelyOldFolder() {
        val root = tmp.newFolder("library")
        val newest = media(root, "least-ancient", 300)
        media(root, "ancient", 900)

        repeat(20) {
            assertEquals(newest.absolutePath, NextTrack.randomNewUnbookmarked(listOf(root), now = now)?.absolutePath)
        }
    }

    /** Same exclusion rule as the old-end pick: a bookmark means it is already being worked on. */
    @Test
    fun theNewestPickSkipsBookmarkedAndExcludedFiles() {
        val root = tmp.newFolder("library")
        media(root, "newest-but-studied", 1, bookmarked = true)
        val playing = media(root, "newest-but-playing", 2)
        val take = media(root, "takeable", 3)
        media(root, "older", 200)

        repeat(20) {
            val pick = NextTrack.randomNewUnbookmarked(
                listOf(root), exclude = setOf(playing.absolutePath), now = now,
            )
            assertEquals(take.absolutePath, pick?.absolutePath)
        }
    }

    /**
     * A pool, not "the single newest file": Syncthing copies a folder in one pass and every file
     * lands on the same second, so a strict maximum would hand back one arbitrary file forever.
     */
    @Test
    fun theNewestPickSpreadsAcrossFilesThatArrivedTogether() {
        val root = tmp.newFolder("library")
        val together = (1..5).map { media(root, "batch$it", 2) }

        val picks = (1..40).mapNotNull {
            NextTrack.randomNewUnbookmarked(listOf(root), now = now, random = Random(it))
        }.map { it.absolutePath }.toSet()
        assertTrue("only ${picks.size} of the batch was ever offered", picks.size > 1)
        assertTrue("picked outside the batch", picks.all { p -> together.any { it.absolutePath == p } })
    }

    /** The scope is the mode's whole point, and it is the same fence the old-end pick honours. */
    @Test
    fun theNewestPickStaysInsideTheParentScope() {
        val notes = tmp.newFolder("notes")
        val mine = File(notes, "2026_08_17-101010~J~p2~X.d")
        val theirs = File(notes, "2026_06_01-090000~J~p2~X.d")
        val elsewhere = tmp.newFolder("audiobooks")
        val playing = media(mine, "playing", 10)
        val sibling = media(theirs, "new-arrival", 1)
        media(elsewhere, "newer-still", 0)

        val scope = NextTrack.parentScope(playing)!!
        repeat(20) {
            val pick = NextTrack.randomNewUnbookmarked(
                listOf(scope), exclude = setOf(playing.absolutePath), now = now,
            )
            assertEquals(sibling.absolutePath, pick?.absolutePath)
        }
    }

    @Test
    fun theNewestPickReturnsNullWhenEverythingIsBookmarked() {
        val root = tmp.newFolder("library")
        media(root, "one", 1, bookmarked = true)
        media(root, "two", 2, bookmarked = true)

        assertNull(NextTrack.randomNewUnbookmarked(listOf(root), now = now))
    }

    /**
     * The pool is a proportion, so the older half of a scope is unreachable however big the scope
     * is. A fixed-size pool passed the small-folder tests above and failed this one.
     */
    @Test
    fun theNewestPickNeverReachesTheOlderHalfOfTheScope() {
        val root = tmp.newFolder("library")
        val byAge = (1..10).map { media(root, "f$it", it.toLong()) }
        val olderHalf = byAge.drop(5).map { it.absolutePath }.toSet()

        val picks = (1..60).mapNotNull {
            NextTrack.randomNewUnbookmarked(listOf(root), now = now, random = Random(it))
        }
        assertTrue("nothing was picked at all", picks.isNotEmpty())
        assertTrue("reached into the older half", picks.none { it.absolutePath in olderHalf })
    }

    // ---- what the ⏭ button says when it finds nothing ---------------------------------------

    /**
     * The button used to do nothing at all when a pick came back empty, which is indistinguishable
     * from a tap that missed. The three empty cases want three different responses from the user,
     * so one message for all of them would not have been much better than silence.
     */
    @Test
    fun anUnconfiguredScopeIsToldApartFromAnExhaustedOne() {
        val noLibrary = noPickMessage(PlayNextMode.RANDOM_OLD, scopeConfigured = false)
        val exhausted = noPickMessage(PlayNextMode.RANDOM_OLD, scopeConfigured = true)

        assertTrue("does not point at Settings", noLibrary.contains("Settings"))
        assertTrue("blames the library instead of the setup", !exhausted.contains("Settings"))
    }

    /** A folder-scoped mode with nothing playing has no anchor — that is not "your library is empty". */
    @Test
    fun aFolderScopedModeWithNothingPlayingSaysSo() {
        val message = noPickMessage(PlayNextMode.RANDOM_OLD_PARENT, scopeConfigured = false)

        assertTrue(message, message.contains("Nothing is playing"))
        assertTrue("wrongly sent the user to Settings", !message.contains("Settings"))
    }

    /** An exhausted folder and an exhausted library are different facts about different scopes. */
    @Test
    fun anExhaustedFolderIsNotReportedAsAnExhaustedLibrary() {
        val folder = noPickMessage(PlayNextMode.RANDOM_OLD_PARENT, scopeConfigured = true)
        val library = noPickMessage(PlayNextMode.RANDOM_OLD, scopeConfigured = true)

        assertTrue(folder, folder.contains("folder"))
        assertTrue(library, library.contains("library"))
    }

    /** Every mode answers, including the newest one — a `when` that missed one would go silent. */
    @Test
    fun everyModeHasSomethingToSayInBothCases() {
        PlayNextMode.entries.forEach { mode ->
            listOf(true, false).forEach { configured ->
                val message = noPickMessage(mode, configured)
                assertTrue("$mode/$configured said nothing useful", message.length > 15)
            }
        }
    }
}
