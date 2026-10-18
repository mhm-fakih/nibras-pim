package com.knotes.player

import java.io.File

/** A bookmark: a position in the media plus optional text. */
data class Bookmark(val positionMs: Long, val text: String)

/**
 * Per-file bookmarks persisted next to the media file as a chapter/lyrics-style `.tmk`
 * (`[mm:ss] text` or `[h:mm:ss] text`, one per line). The sidecar has the same base name as the
 * media file with a `.tmk` extension (e.g. `lecture.mp3` → `lecture.tmk`). A legacy `.txt`
 * sidecar is still read (and migrated to `.tmk` on the next save).
 */
object Bookmarks {

    // Minutes take any number of digits: a Sony ICD recorder writes `[00004:58.00]`, and with the
    // old 1–2 digit limit such a file parsed as empty — after which the next bookmark's save wrote
    // over every mark in it.
    private val LINE = Regex("^\\[(?:(\\d+):)?(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)$")

    /** Byte-order mark some writers put first; Kotlin's trim() doesn't count it as whitespace. */
    private const val BOM = '﻿'

    fun sidecar(media: File): File = File(media.parentFile, media.nameWithoutExtension + ".tmk")

    /** The old `.txt` sidecar, read for backward compatibility. */
    fun legacySidecar(media: File): File =
        File(media.parentFile, media.nameWithoutExtension + ".txt")

    /**
     * Whether [media] has a bookmark sidecar with anything in it.
     *
     * Two `stat` calls rather than [load]'s read-and-parse, because the "random old unbookmarked"
     * picker asks this of every file in the library — reading each sidecar would turn one tap into
     * thousands of file reads. An empty sidecar (every bookmark since deleted) counts as
     * unbookmarked, which is what it means.
     */
    fun hasBookmarks(media: File): Boolean =
        sidecar(media).length() > 0L || legacySidecar(media).length() > 0L

    fun load(media: File): List<Bookmark> {
        val f = sidecar(media).takeIf { it.exists() } ?: legacySidecar(media)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull(::parse).sortedBy { it.positionMs }
        }.getOrDefault(emptyList())
    }

    /**
     * Write [list] as the media's `.tmk`.
     *
     * Lines already in the `.tmk` that aren't bookmarks this parser understands are carried over
     * below the marks rather than dropped: a file this app can't fully read must never be
     * overwritten into something smaller than it was. (Only from the `.tmk` itself — a legacy
     * `.txt` beside the media may be an unrelated transcript, not bookmarks.)
     */
    fun save(media: File, list: List<Bookmark>) {
        runCatching {
            val target = sidecar(media)
            val kept = if (target.exists()) {
                target.readLines().map { it.trim().trimStart(BOM) }
                    .filter { it.isNotEmpty() && parse(it) == null }
            } else emptyList()
            val lines = list.sortedBy { it.positionMs }.map { line(it) } + kept
            target.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n") + "\n")
        }
    }

    /**
     * Apply [change] to the bookmarks as they are on disk NOW, save, and return the result.
     *
     * The player screen used to save a list it loaded when the track opened. Anything written in
     * the meantime — the service's notification/auto-pause bookmark, the desktop player via sync —
     * was silently erased by the next tap, and a list that had loaded empty overwrote the whole file.
     */
    fun update(media: File, change: (List<Bookmark>) -> List<Bookmark>): List<Bookmark> {
        val updated = change(load(media)).sortedBy { it.positionMs }
        save(media, updated)
        return updated
    }

    private fun line(b: Bookmark): String =
        "[" + formatTime(b.positionMs) + "]" + if (b.text.isBlank()) "" else " " + b.text.trim()

    fun parse(raw: String): Bookmark? {
        val m = LINE.matchEntire(raw.trim().trimStart(BOM)) ?: return null
        val h = m.groupValues[1].toLongOrNull() ?: 0L
        val min = m.groupValues[2].toLong()
        val sec = m.groupValues[3].toLong()
        val frac = m.groupValues[4]
        val fracMs = if (frac.isNotEmpty()) frac.padEnd(3, '0').take(3).toLong() else 0L
        val ms = (h * 3600 + min * 60 + sec) * 1000 + fracMs
        return Bookmark(ms, m.groupValues[5].trim())
    }
}
