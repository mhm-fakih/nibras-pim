package com.knotes.nibras

import com.knotes.StarAction

/**
 * The decisions behind the star, as plain functions.
 *
 * They live outside [NibrasLocalStore] because that class needs an Android `Context` for its
 * SharedPreferences and so can't be exercised in a unit test — while the rules here are exactly
 * the part worth pinning: what the star shows, when a queued flag replaces another, and when a
 * remembered un-bookmark is finally forgotten.
 */
object BookmarkSync {

    /**
     * Whether the star is drawn filled.
     *
     * In [StarAction.LOCAL_BOOKMARK] a filled star means "on my shortlist". In
     * [StarAction.BACKEND_UNBOOKMARK] it means "still bookmarked in Nibras", which every synced
     * record is — so it is filled by default and only empties once the user has retired it.
     */
    fun starFilled(action: StarAction, locallyBookmarked: Boolean, pendingUnbookmark: Boolean): Boolean =
        when (action) {
            StarAction.LOCAL_BOOKMARK -> locallyBookmarked
            StarAction.BACKEND_UNBOOKMARK -> !pendingUnbookmark
        }

    /**
     * Queue a bookmark flag, replacing any flag already queued for the same record.
     *
     * One entry per record is the whole point: queueing both an un-bookmark and its undo would
     * leave the record's final state decided by which request the server happened to process last.
     */
    fun queue(
        current: List<PendingPush>,
        ecode: String,
        id: String,
        bookmarked: Boolean,
    ): List<PendingPush> =
        current.filterNot { it.ecode == ecode && it.id == id } +
            PendingPush(ecode, id, bookmarked.toString())

    /** Drop the queued flag for one record — used when the push has just succeeded instead. */
    fun unqueue(current: List<PendingPush>, ecode: String, id: String): List<PendingPush> =
        current.filterNot { it.ecode == ecode && it.id == id }

    /**
     * Forget the un-bookmarks that have taken effect: entries for the fetched type whose record the
     * server no longer returns. Records of other types are left alone — a fetch of one type says
     * nothing about another — and so is an entry whose record is still being exported, because that
     * is the window in which the user can still undo.
     */
    fun prune(current: Set<String>, code: String, stillPresent: Set<String>): Set<String> {
        val prefix = "$code:"
        return current.filterTo(mutableSetOf()) { !it.startsWith(prefix) || it in stillPresent }
    }
}
