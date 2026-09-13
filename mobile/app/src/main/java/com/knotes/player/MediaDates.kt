package com.knotes.player

import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * When a media file was actually recorded, taken from its NAME rather than its modification time.
 *
 * <h4>Why not mtime</h4>
 * These folders are Syncthing shares, and Syncthing rewrites a file's mtime when it transfers it —
 * including files it has not changed. So mtime records when this phone last received the file, not
 * when the recording was made: an old recording re-synced this morning looks newer than something
 * recorded an hour ago, and a recording made today can carry an older stamp than files that arrived
 * before it.
 *
 * That is not a hypothetical. It is why "play next: newest" kept handing back old notes while there
 * were unbookmarked recordings from the same day sitting in the folder, and the same confusion runs
 * the other way for "oldest". The KPI widget already avoids mtime for exactly this reason when it
 * counts today's notes.
 *
 * <h4>What is read</h4>
 * The recorder conventions actually present in this library, most specific first:
 *
 * ```
 * 260818_1520.mp3          yyMMdd_HHmm          -> 2026-08-18 15:20
 * 260818_1520_02.mp3       the _NN is a same-minute counter, not part of the date
 * 20260818_152000.m4a      yyyyMMdd_HHmmss
 * 2026_08_18-152000.mp3    the note convention, for media named like notes
 * ```
 *
 * Every field is range-checked, so a bitrate or a track number cannot be mistaken for a date. When
 * nothing parses — a title, a podcast episode — the answer falls back to mtime, which is the best
 * that can be said about a file whose name says nothing.
 */
object MediaDates {

    /**
     * Two-digit years: 70-99 are the 1900s, 00-69 the 2000s.
     *
     * The same pivot POSIX uses. Nothing in a personal recording library predates the 1970s, so the
     * lower half is what matters; the upper half exists only so a stray "99" is not read as 2099
     * and sorted to the top of "newest" forever.
     */
    private const val CENTURY_PIVOT = 70

    private val PATTERNS = listOf(
        // yyyyMMdd_HHmm(ss)  and  yyyy_MM_dd-HHmm(ss)
        Regex("""(?<!\d)(\d{4})[_-]?(\d{2})[_-]?(\d{2})[_\-T ]?(\d{2})(\d{2})(\d{2})?(?!\d)""") to 4,
        // yyMMdd_HHmm(ss)
        Regex("""(?<!\d)(\d{2})(\d{2})(\d{2})[_\-T ](\d{2})(\d{2})(\d{2})?(?!\d)""") to 2,
    )

    /** The recorded instant from the name, or [File.lastModified] when the name carries no date. */
    fun recordedMillis(file: File): Long = fromName(file.name) ?: file.lastModified()

    /** The instant a name encodes, or null if it encodes none. Public for testing. */
    fun fromName(name: String): Long? {
        for ((pattern, yearDigits) in PATTERNS) {
            val m = pattern.find(name) ?: continue
            val g = m.groupValues
            val year = g[1].toInt().let { if (yearDigits == 4) it else twoDigitYear(it) }
            val millis = utc(year, g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0)
            if (millis != null) return millis
        }
        return null
    }

    private fun twoDigitYear(yy: Int): Int = if (yy >= CENTURY_PIVOT) 1900 + yy else 2000 + yy

    /**
     * Range-checked, and read as LOCAL time — a recorder stamps a name with the wall clock in front
     * of it, not UTC. Returns null on anything out of range so a number that merely looks like a
     * date is rejected rather than producing a wild instant that would dominate any sort.
     */
    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long? {
        if (year !in 1970..299_9) return null
        if (month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        val c = Calendar.getInstance(TimeZone.getDefault()).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        return runCatching { c.timeInMillis }.getOrNull()
    }
}
