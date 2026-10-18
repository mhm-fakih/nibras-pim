package com.knotes.player

import androidx.annotation.StringRes
import com.knotes.COMPANION_SUFFIX
import com.knotes.R
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
    RANDOM_OLD,

    /**
     * The same pick, but confined to the folder tree around the file being played — for working
     * through one pipeline rather than the whole library. See [NextTrack.parentScope] for exactly
     * which folder that is.
     */
    RANDOM_OLD_PARENT,

    /**
     * The same scope as [RANDOM_OLD_PARENT], picked from the other end of the clock: a file that
     * arrived *recently* and has never been bookmarked — for hearing what just came in, rather
     * than digging through what was forgotten.
     */
    RANDOM_NEW_PARENT;

    /** True for the modes that pick at random rather than walking the folder in order. */
    val isRandom: Boolean get() = this == RANDOM_OLD || this == RANDOM_OLD_PARENT || this == RANDOM_NEW_PARENT

    /** True for the modes confined to the folder around what is playing. */
    val isParentScoped: Boolean get() = this == RANDOM_OLD_PARENT || this == RANDOM_NEW_PARENT

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
     * The folder [PlayNextMode.RANDOM_OLD_PARENT] searches, given the file being played.
     *
     * Normally the file's own folder, walked recursively. The exception is a note's companion
     * folder (`<stem>.d`): that folder belongs to exactly one note, so confining the search to it
     * would offer the two or three files attached to the note you are already listening to. The
     * point of the mode is the opposite — sweeping the *sibling* companion folders, the whole
     * pipeline of notes with attachments — so from inside one, the search starts a level up.
     *
     * Returns null when the file has no usable folder at all.
     */
    fun parentScope(current: File): File? {
        val dir = current.parentFile ?: return null
        return if (dir.name.endsWith(COMPANION_SUFFIX)) dir.parentFile ?: dir else dir
    }

    /**
     * Which end of the clock a random pick comes from.
     *
     * The two are not symmetric in the obvious way. "Old" means *forgotten* — downloaded, never
     * heard, gone stale — so it wants an absolute age threshold. "New" means *just arrived*, which
     * is inherently relative: a folder whose newest file is a year old still has a newest file,
     * and that is the one you meant. So OLDEST tests against [OLD_DAYS] and falls back to
     * relative, while NEWEST is relative from the start.
     */
    enum class Pick { OLDEST, NEWEST }

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
    ): File? = randomUnbookmarked(roots, Pick.OLDEST, exclude, now, random)

    /**
     * A random media file under [roots] that has no bookmarks, drawn from the newest end — the
     * recent arrivals you have not marked up.
     *
     * There is no age threshold here, only a ranking: see [Pick]. The pick comes from a *pool*
     * rather than being simply "the newest file", because a folder into which twenty files were
     * copied in one pass — all sharing a timestamp to the second, which is exactly what Syncthing
     * produces — would otherwise hand back the same arbitrary one forever.
     *
     * The pool is the newer **half**, capped at [FALLBACK_POOL], and never smaller than one. A
     * fixed-size pool was the first attempt and it is wrong at the size that matters: this mode is
     * usually scoped to one folder of note attachments, where a flat "newest 25" is every file
     * there is, and the mode silently degrades into plain shuffle. A proportion keeps the
     * recency bias at every scope size.
     *
     * Returns null only when the scope holds nothing eligible at all.
     */
    fun randomNewUnbookmarked(
        roots: List<File>,
        exclude: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): File? = randomUnbookmarked(roots, Pick.NEWEST, exclude, now, random)

    private fun randomUnbookmarked(
        roots: List<File>,
        pick: Pick,
        exclude: Set<String>,
        now: Long,
        random: kotlin.random.Random,
    ): File? {
        val candidates = MediaLibrary.mediaFilesUnder(roots)
            .filter { it.absolutePath !in exclude && !Bookmarks.hasBookmarks(it) }
        if (candidates.isEmpty()) return null

        // Ordered by when each file was RECORDED, read from its name — not by mtime. These folders
        // are Syncthing shares and Syncthing rewrites mtimes on transfer, so mtime says when this
        // phone received a file. Ranking on it made "newest" hand back old recordings while
        // unbookmarked ones from the same day sat in the folder, because an old file re-synced this
        // morning outranked them. See MediaDates.
        val dated = candidates.map { it to MediaDates.recordedMillis(it) }
        val pool = when (pick) {
            Pick.OLDEST -> {
                val cutoff = now - TimeUnit.DAYS.toMillis(OLD_DAYS.toLong())
                dated.filter { (_, at) -> at in 1 until cutoff }.map { it.first }
                    .ifEmpty { dated.sortedBy { it.second }.take(FALLBACK_POOL).map { it.first } }
            }
            // Files whose date cannot be read at all fall back to mtime inside MediaDates, and an
            // unreadable/zero one sorts to the bottom — the right place for it, since nothing is
            // known about when it arrived.
            Pick.NEWEST -> dated.sortedByDescending { it.second }
                .take(((candidates.size + 1) / 2).coerceIn(1, FALLBACK_POOL))
                .map { it.first }
        }
        return pool.randomOrNull(random)
    }
}

/**
 * What to say when the ⏭ button finds nothing.
 *
 * The button used to do nothing at all in this case — no movement, no message — which is
 * indistinguishable from a tap that missed. Worse, the three reasons it can come back empty want
 * three different responses from the user: a scope that was never configured is fixed in Settings,
 * a folder with nothing unbookmarked left in it means move on, and a library that is exhausted
 * means the mode has done its job.
 *
 * @param scopeConfigured whether there was anywhere to search at all — a configured media folder
 *   for the library-wide mode, or a file playing for the folder-scoped ones.
 */
internal fun noPickMessage(mode: PlayNextMode, scopeConfigured: Boolean): String = when {
    !scopeConfigured && mode.isParentScoped ->
        "Nothing is playing, so there is no folder to pick from."
    !scopeConfigured ->
        "No media folders are set — add one in Settings."
    mode == PlayNextMode.RANDOM_NEW_PARENT ->
        "Nothing new here — every file in this folder is bookmarked or already played."
    mode.isParentScoped ->
        "Nothing left here — every file in this folder is bookmarked or already played."
    else ->
        "Nothing left in your library — every file is bookmarked or already played."
}

/**
 * The same choice as [noPickMessage], as a string resource for the UI (localized). [noPickMessage]
 * stays as the English reference the unit tests read; keep the two in step.
 */
@StringRes
internal fun noPickMessageRes(mode: PlayNextMode, scopeConfigured: Boolean): Int = when {
    !scopeConfigured && mode.isParentScoped -> R.string.player_next_nothing_playing_no_folder
    !scopeConfigured -> R.string.player_next_no_media_folders
    mode == PlayNextMode.RANDOM_NEW_PARENT -> R.string.player_next_nothing_new_here
    mode.isParentScoped -> R.string.player_next_nothing_left_here
    else -> R.string.player_next_nothing_left_library
}
