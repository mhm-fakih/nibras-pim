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
