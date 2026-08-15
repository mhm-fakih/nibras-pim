package com.knotes.player

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * What happens when the file being played reaches its end.
 *
 * The mode also decides what the ⏭ button does, so "next" always means the same thing whether the
 * track ran out or you asked for it.
 */
enum class PlayNextMode {
    /**
     * Nothing plays by itself — the behaviour the player has always had. The next file in the
     * folder is still *cued* paused, so one tap on play continues; it just doesn't start on its own.
     */
    NONE,

    /** The next file in the same folder, alphabetically, plays automatically. */
    SAME_FOLDER,

    /**
     * A file picked at random from the whole media library that you have never bookmarked and have
     * not touched in a long time — for working through what was downloaded and forgotten.
     */
    RANDOM_OLD;

    companion object {
        fun from(name: String?): PlayNextMode =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Chooses the next file to play.
 *
 * All of this touches the disk — a full library walk in the RANDOM_OLD case — so every function
 * here must be called off the main thread.
 */
object NextTrack {

    /** How stale a file's mtime must be before it counts as "old". */
    const val OLD_DAYS = 90

    /** How many of the oldest files the fallback picks from when nothing is [OLD_DAYS] old yet. */
    private const val FALLBACK_POOL = 25

    /** Media files beside [current], alphabetically (case-insensitive). */
    fun siblings(current: File): List<File> =
        current.parentFile?.listFiles { f -> f.isFile && isMediaFile(f.name) }
            ?.sortedBy { it.name.lowercase() }?.toList() ?: emptyList()

    /** The next file after [current] in its folder, or null if it is the last one. */
    fun nextSibling(current: File): File? {
        val sibs = siblings(current)
        val i = sibs.indexOfFirst { it.absolutePath == current.absolutePath }
        return if (i in 0 until sibs.size - 1) sibs[i + 1] else null
    }

    /**
     * A random media file under [roots] that has no bookmarks and hasn't been modified for
     * [OLD_DAYS], excluding [exclude] (the current file and recent plays).
     *
     * <h4>Why there is a fallback</h4>
     * A library that was filled recently has nothing [OLD_DAYS] old in it, and an option that
     * silently does nothing reads as broken. So when the age filter leaves nothing, the pick comes
     * from the [FALLBACK_POOL] oldest unbookmarked files instead — still "the old end of the
     * library", just relative rather than absolute.
     *
     * Bookmarked files are excluded rather than merely deprioritised because a bookmark is the
     * mark of something already being worked through; this mode is for what *isn't*.
     *
     * Returns null only when the library holds nothing eligible at all.
     */
    fun randomOldUnbookmarked(
        roots: List<File>,
        exclude: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): File? {
        val candidates = MediaLibrary.mediaFilesUnder(roots)
            .filter { it.absolutePath !in exclude && !Bookmarks.hasBookmarks(it) }
        if (candidates.isEmpty()) return null

        val cutoff = now - TimeUnit.DAYS.toMillis(OLD_DAYS.toLong())
        val old = candidates.filter { it.lastModified() in 1 until cutoff }
        val pool = old.ifEmpty {
            candidates.sortedBy { it.lastModified() }.take(FALLBACK_POOL)
        }
        return pool.randomOrNull(random)
    }
}
