package com.knotes.player

import java.io.File

/** A sidecar subtitle file, tagged with the language inferred from its name. */
data class SubtitleTrack(val lang: String, val file: File)

/** One timed subtitle line. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

/**
 * SubRip (`.srt`) sidecar subtitles that sit next to a media file and share its base name:
 * `lecture.mp3` is matched by `lecture.fr.srt` (language `fr`), `lecture.en.srt` (`en`), or a bare
 * `lecture.srt` (labelled `sub`). Cues are parsed ourselves so they can be shown over audio too,
 * not just video.
 */
object Subtitles {

    private val TIME = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    private val TAG = Regex("<[^>]+>")

    /** Subtitle tracks found beside [media], one per matching `.srt` file. */
    fun list(media: File): List<SubtitleTrack> {
        val base = media.nameWithoutExtension
        val parent = media.parentFile ?: return emptyList()
        val files = parent.listFiles { f ->
            f.isFile && fileExt(f.name) == "srt" &&
                (f.nameWithoutExtension == base || f.nameWithoutExtension.startsWith("$base."))
        }?.toList() ?: return emptyList()
        return files.map { f ->
            val stem = f.nameWithoutExtension
            val lang = if (stem == base) "sub" else stem.substring(base.length + 1)
            SubtitleTrack(lang, f)
        }.sortedBy { it.lang }
    }

    /** Parses an `.srt` file into time-ordered cues (empty on any error). */
    fun parse(file: File): List<Cue> = runCatching {
        file.readText().replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).mapNotNull { block ->
            val lines = block.trim().lines()
            val timeIdx = lines.indexOfFirst { it.contains("-->") }
            if (timeIdx < 0) return@mapNotNull null
            val (start, end) = parseTimeLine(lines[timeIdx]) ?: return@mapNotNull null
            val body = lines.drop(timeIdx + 1).joinToString("\n").trim()
            if (body.isEmpty()) null else Cue(start, end, body.replace(TAG, ""))
        }.sortedBy { it.startMs }
    }.getOrDefault(emptyList())

    /** Text of the cue active at [posMs], or null when none is showing. */
    fun cueAt(cues: List<Cue>, posMs: Long): String? =
        cues.firstOrNull { posMs in it.startMs..it.endMs }?.text

    /**
     * The cue at [posMs] as a one-line bookmark label, or "" when there is none.
     *
     * A bare timestamp says nothing when you come back to a list of bookmarks; the sentence is the
     * only thing that records why you marked it, and the subtitle file has already done the typing.
     *
     * Cues wrap across two or three lines in the file and carry the odd tag, while a bookmark list
     * shows one row — hence the flattening and the clip. Clipped on a word boundary, because half a
     * word reads as corruption rather than as an abbreviation.
     */
    fun labelAt(cues: List<Cue>, posMs: Long): String {
        val raw = cueAt(cues, posMs) ?: return ""
        val flat = raw.replace(TAG, " ").replace(SPACES, " ").trim()
        if (flat.length <= LABEL_MAX) return flat
        val cut = flat.take(LABEL_MAX)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > LABEL_MAX / 2) cut.take(lastSpace) else cut).trimEnd() + "…"
    }

    /** The same, read straight from the media file's own sidecars — for callers with no UI state. */
    fun labelFor(media: File, posMs: Long): String {
        val track = list(media).firstOrNull() ?: return ""
        return labelAt(parse(track.file), posMs)
    }

    /** Long enough for a spoken sentence, short enough to stay on one row of the bookmark list. */
    private const val LABEL_MAX = 90
    private val SPACES = Regex("\\s+")

    private fun parseTimeLine(line: String): Pair<Long, Long>? {
        val stamps = TIME.findAll(line).map { m ->
            val h = m.groupValues[1].toLong()
            val min = m.groupValues[2].toLong()
            val s = m.groupValues[3].toLong()
            val frac = m.groupValues[4].padEnd(3, '0').take(3).toLong()
            (h * 3600 + min * 60 + s) * 1000 + frac
        }.toList()
        return if (stamps.size >= 2) stamps[0] to stamps[1] else null
    }
}
