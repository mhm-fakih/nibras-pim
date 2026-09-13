package com.knotes.player

import java.io.File

/** A sidecar transcription file, tagged with the language taken from its name. */
data class Transcript(val lang: String, val file: File)

/**
 * Sidecar `.ai.txt` transcriptions sitting beside a media file, as the desktop audio-player writes
 * them. Same convention, so the same files are found on both:
 *
 * ```
 * 260701_0624.mp3
 * 260701_0624.ar.ai.txt   -> language "ar"
 * 260701_0624.en.ai.txt   -> language "en"
 * 260701_0624.ai.txt      -> language "txt"  (no language tag)
 * ```
 *
 * Note the ordering — the language sits *before* `.ai.txt`, not after it. That is what is on disk
 * (every one of the 1,196 on this phone), and matching it is the whole point: a convention of our
 * own would find nothing.
 *
 * Unlike the `.srt` subtitles in [Subtitles] these carry NO timestamps, so there is nothing to sync
 * to the playhead — the whole text is shown at once, the way lyrics are.
 *
 * **Not editable here.** The desktop player edits them in place, because that is where someone sits
 * down to correct a machine transcript. On a phone the realistic outcome of an editable panel is a
 * stray keystroke silently rewriting a file that syncs everywhere, so the text is read and never
 * rewritten. Deleting a whole sidecar is a different matter — see [delete].
 */
object Transcripts {

    private const val SUFFIX = ".ai.txt"

    /** Language given to a sidecar carrying no language tag at all. */
    const val UNTAGGED = "txt"

    /** Transcriptions found beside [media], one per matching file, ordered by language. */
    fun list(media: File): List<Transcript> {
        val stem = media.nameWithoutExtension
        val parent = media.parentFile ?: return emptyList()
        val files = parent.listFiles { f ->
            f.isFile && f.name.length > SUFFIX.length && f.name.endsWith(SUFFIX, ignoreCase = true)
        }?.toList() ?: return emptyList()

        return files.mapNotNull { f ->
            val base = f.name.dropLast(SUFFIX.length)
            val lang = when {
                base == stem -> UNTAGGED
                // "$stem." alone is not a language — `foo..ai.txt` names nothing.
                base.startsWith("$stem.") && base.length > stem.length + 1 ->
                    base.substring(stem.length + 1)
                else -> null
            }
            lang?.let { Transcript(it, f) }
        }.sortedBy { it.lang.lowercase() }
    }

    /**
     * The transcription text: BOM stripped, line endings normalised, trailing blank lines trimmed.
     * Empty when the file cannot be read — a transcript that will not open costs its panel, not the
     * player.
     */
    fun read(file: File): String = runCatching {
        file.readText()
            .removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trimEnd()
    }.getOrDefault("")

    /**
     * Delete one sidecar transcription.
     *
     * A whole-file delete, unlike editing, is a thing a phone can be trusted with: it is one act
     * with one visible result, and a bad machine transcript is exactly the sort of file you want to
     * be rid of at the moment you are listening to it and can hear that it is wrong.
     *
     * Guarded to `.ai.txt` sidecars so that whatever is passed in, this cannot be turned into a
     * general file delete — the media file itself sits in the same folder under a name one
     * character different.
     *
     * @return true if the file is gone (including if it was already)
     */
    fun delete(file: File): Boolean {
        if (!file.name.endsWith(SUFFIX, ignoreCase = true)) return false
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    /**
     * Whether a transcript should be laid out right-to-left.
     *
     * The language TAG decides it when it names a right-to-left language, and the text only gets a
     * vote when the tag says nothing. Machine transcripts routinely open with a stray Latin token —
     * a mis-heard name, a bracketed marker — and an Arabic transcript is still Arabic when its
     * first word came out wrong.
     */
    fun isRtl(lang: String, text: String): Boolean {
        if (lang.lowercase() in RTL_LANGS) return true
        if (lang != UNTAGGED) return false
        return com.knotes.util.Bidi.isRtlLine(text.take(SNIFF_CHARS)) ?: false
    }

    /** Enough of the text to settle the question without reading a whole lecture. */
    private const val SNIFF_CHARS = 400

    private val RTL_LANGS =
        setOf("ar", "he", "fa", "ur", "ps", "ckb", "yi", "dv", "sd", "ug")
}
