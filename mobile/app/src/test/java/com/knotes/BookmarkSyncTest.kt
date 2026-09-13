package com.knotes

import com.knotes.nibras.BookmarkSync
import com.knotes.nibras.PendingPush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The star's two meanings, and the bookkeeping behind the backend one.
 *
 * The interesting cases are all about a tap the user takes back. An un-bookmark may or may not have
 * reached the server by the time it is undone, and the row is deliberately left on screen in the
 * meantime — so the rules have to converge on the user's last decision either way, and must not
 * leave two opposing writes queued whose arrival order picks the winner.
 */
class BookmarkSyncTest {

    // ---- what the star shows -------------------------------------------------------------

    @Test
    fun localModeFillsTheStarOnlyForALocalBookmark() {
        assertTrue(BookmarkSync.starFilled(StarAction.LOCAL_BOOKMARK, locallyBookmarked = true, pendingUnbookmark = false))
        assertFalse(BookmarkSync.starFilled(StarAction.LOCAL_BOOKMARK, locallyBookmarked = false, pendingUnbookmark = false))
    }

    /** Every record on the phone is bookmarked on the server, so the default is a filled star. */
    @Test
    fun backendModeStartsFilledRegardlessOfTheLocalShortlist() {
        assertTrue(BookmarkSync.starFilled(StarAction.BACKEND_UNBOOKMARK, locallyBookmarked = false, pendingUnbookmark = false))
        assertTrue(BookmarkSync.starFilled(StarAction.BACKEND_UNBOOKMARK, locallyBookmarked = true, pendingUnbookmark = false))
    }

    @Test
    fun backendModeEmptiesTheStarOnceTheRecordIsRetired() {
        assertFalse(BookmarkSync.starFilled(StarAction.BACKEND_UNBOOKMARK, locallyBookmarked = true, pendingUnbookmark = true))
    }

    // ---- the offline queue ----------------------------------------------------------------

    @Test
    fun queuingRecordsTheFlagForTheRecord() {
        val q = BookmarkSync.queue(emptyList(), "R", "12", bookmarked = false)
        assertEquals(1, q.size)
        assertEquals(PendingPush("R", "12", "false"), q.single())
    }

    /**
     * The load-bearing one: un-bookmark then undo, both offline, must leave a single "true" —
     * not a false-then-true pair the server could apply in either order.
     */
    @Test
    fun anUndoReplacesTheQueuedFlagInsteadOfAppendingToIt() {
        var q = BookmarkSync.queue(emptyList(), "R", "12", bookmarked = false)
        q = BookmarkSync.queue(q, "R", "12", bookmarked = true)

        assertEquals(1, q.size)
        assertEquals("true", q.single().text)
    }

    @Test
    fun otherRecordsQueuedFlagsAreLeftAlone() {
        var q = BookmarkSync.queue(emptyList(), "R", "12", bookmarked = false)
        q = BookmarkSync.queue(q, "T", "12", bookmarked = false)   // same id, different type
        q = BookmarkSync.queue(q, "R", "13", bookmarked = false)
        q = BookmarkSync.queue(q, "R", "12", bookmarked = true)

        assertEquals(3, q.size)
        assertEquals("true", q.first { it.ecode == "R" && it.id == "12" }.text)
        assertEquals("false", q.first { it.ecode == "T" && it.id == "12" }.text)
    }

    @Test
    fun unqueuingDropsOnlyThatRecord() {
        var q = BookmarkSync.queue(emptyList(), "R", "12", bookmarked = false)
        q = BookmarkSync.queue(q, "R", "13", bookmarked = false)

        val left = BookmarkSync.unqueue(q, "R", "12")
        assertEquals(listOf("13"), left.map { it.id })
    }

    // ---- forgetting a landed un-bookmark ----------------------------------------------------

    /** The record is gone from the server's export — the un-bookmark took effect, stop tracking it. */
    @Test
    fun pruneForgetsARecordTheFetchNoLongerReturns() {
        val kept = BookmarkSync.prune(setOf("R:12"), "R", stillPresent = setOf("R:99"))
        assertTrue(kept.isEmpty())
    }

    /** Still exported means the sync hasn't happened yet — that window IS the undo. */
    @Test
    fun pruneKeepsARecordTheServerIsStillSending() {
        val kept = BookmarkSync.prune(setOf("R:12"), "R", stillPresent = setOf("R:12", "R:99"))
        assertEquals(setOf("R:12"), kept)
    }

    /** A fetch of one type says nothing about another — pruning across types would lose the undo. */
    @Test
    fun pruneIsScopedToTheFetchedType() {
        val kept = BookmarkSync.prune(setOf("R:12", "T:12"), "R", stillPresent = emptySet())
        assertEquals(setOf("T:12"), kept)
    }

    @Test
    fun pruneOnAnEmptySetIsANoOp() {
        assertTrue(BookmarkSync.prune(emptySet(), "R", setOf("R:1")).isEmpty())
    }
}
