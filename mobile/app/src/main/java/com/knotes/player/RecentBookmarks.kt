package com.knotes.player

import java.io.File

/**
 * A media file that has bookmarks, with just enough detail to offer it as a "carry on from here"
 * row.
 *
 * [bookmarkedAt] is the sidecar's mtime — when a bookmark was last *dropped*, which is the order
 * the user thinks in ("what was I listening to?"). That is a different question from the file's own
 * mtime (when it was downloaded) and from the saved playback position (where the player stopped).
 */
data class BookmarkedFile(
    val file: File,
    val bookmarkedAt: Long,
    val last: Bookmark?,
    val count: Int,
)

/**
 * Finds the media files that have bookmark sidecars, most-recently-bookmarked first.
 *
 * The player screen already shows the bookmarks *inside* the file being played. This is the level
 * above: which files have bookmarks at all, so resuming a half-finished lecture is one tap from the
 * library instead of a walk back down the folder tree.
 */
object RecentBookmarks {

    /** Sidecar extensions [Bookmarks] can read — `.tmk`, plus the legacy `.txt` it still migrates. */
    private val SIDECAR_EXT = setOf("tmk", "txt")

    /** Walks [roots] recursively. Returns at most [limit] entries, newest bookmark first. */
    fun scan(roots: List<File>, limit: Int = 50): List<BookmarkedFile> =
        roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && fileExt(it.name) in SIDECAR_EXT } }
            .mapNotNull(::entryFor)
            // A media file can have both a .tmk and a legacy .txt; keep the newer of the two rows.
            .groupBy { it.file.absolutePath }
            .map { (_, dupes) -> dupes.maxBy { it.bookmarkedAt } }
            .sortedByDescending { it.bookmarkedAt }
            .take(limit)

    /** The bookmarked files directly inside [dir] (not recursive), newest bookmark first. */
    fun inFolder(dir: File, limit: Int = 5): List<BookmarkedFile> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && fileExt(it.name) in SIDECAR_EXT }
            .mapNotNull(::entryFor)
            .groupBy { it.file.absolutePath }
            .map { (_, dupes) -> dupes.maxBy { it.bookmarkedAt } }
            .sortedByDescending { it.bookmarkedAt }
            .take(limit)

    /**
     * Resolves a sidecar to its media file and reads its bookmarks. Returns null when the sidecar
     * has no media file beside it (a stale sidecar, or — for `.txt` — just an ordinary text file
     * that happens to sit in the library), or when nothing in it parses as a bookmark.
     */
    private fun entryFor(sidecar: File): BookmarkedFile? {
        val dir = sidecar.parentFile ?: return null
        val stem = sidecar.nameWithoutExtension
        val media = (dir.listFiles() ?: emptyArray()).firstOrNull {
            it.isFile && it.nameWithoutExtension == stem && isMediaFile(it.name)
        } ?: return null
        val marks = Bookmarks.load(media)
        if (marks.isEmpty()) return null
        return BookmarkedFile(
            file = media,
            bookmarkedAt = sidecar.lastModified(),
            // The furthest-in bookmark is the one worth resuming from, not the newest-written one.
            last = marks.maxByOrNull { it.positionMs },
            count = marks.size,
        )
    }
}
