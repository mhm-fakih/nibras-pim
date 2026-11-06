package com.knotes.util

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a note's attached file has been reviewed — worked through in the app, leaving a trace
 * in or beside the file:
 *
 *  - audio / video: it has time marks (the player's `<stem>.tmk` sidecar; a `.txt` beside it may
 *    be a transcript rather than legacy marks, so it doesn't count);
 *  - image: it was annotated (burning marks keeps the original in `.scr-backup/`, here and on the
 *    desktop annotator alike);
 *  - PDF: it carries a markup annotation — highlight, underline, ink, sticky note… (a link is not
 *    one; annotations packed into a compressed object stream are not seen);
 *  - text / markdown: it holds a `==highlight==`.
 *
 * Anything else (archives, office files, the `.tmk` sidecars themselves) can't be reviewed in the
 * app and is [Status.NOT_APPLICABLE]: it is never shown as pending.
 */
object Review {

    enum class Status { REVIEWED, PENDING, NOT_APPLICABLE }

    private val AUDIO_VIDEO = setOf(
        "mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "wav", "flac", "wma", "amr", "mid",
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "wmv",
    )
    private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
    val TEXT = setOf("md", "markdown", "txt")

    /**
     * A markup annotation as it appears in the file: the subtype key, not just the name — tagged
     * PDFs say `/TextDecorationType/Underline` for underlined TEXT, which is not a mark.
     */
    private val PDF_MARK = Regex("""/Subtype\s*/(?:Highlight|Underline|StrikeOut|Squiggly|Ink|FreeText|Text)(?![A-Za-z])""")

    private val HIGHLIGHT = Regex("==(?!=)[^\\n]+?(?<!=)==")

    /** path → (size, mtime, status): a PDF is read once per change, not on every redraw. */
    private val cache = ConcurrentHashMap<String, Triple<Long, Long, Status>>()

    fun status(file: File): Status {
        if (!file.isFile) return Status.NOT_APPLICABLE
        val ext = FileNames.ext(file.name)
        val reviewable = ext in AUDIO_VIDEO || ext in IMAGE || ext == "pdf" || (ext in TEXT && !isBookmarkSidecar(file))
        if (!reviewable) return Status.NOT_APPLICABLE
        // Media and images are judged by a file beside them, which the cache key can't see.
        if (ext in AUDIO_VIDEO) return mark(hasTimeMarks(file))
        if (ext in IMAGE) return mark(File(File(file.parentFile, ".scr-backup"), file.name).isFile)
        val key = file.absolutePath
        val size = file.length()
        val mtime = file.lastModified()
        cache[key]?.let { (s, m, st) -> if (s == size && m == mtime) return st }
        val st = mark(
            runCatching { if (ext == "pdf") pdfHasMarks(file) else HIGHLIGHT.containsMatchIn(file.readText()) }
                .getOrDefault(false),
        )
        cache[key] = Triple(size, mtime, st)
        return st
    }

    private fun mark(done: Boolean) = if (done) Status.REVIEWED else Status.PENDING

    private fun hasTimeMarks(media: File): Boolean {
        val stem = media.nameWithoutExtension
        return File(media.parentFile, "$stem.tmk").let { it.isFile && it.length() > 0 }
    }

    /** A `.tmk`, or a legacy `.txt` that sits beside a media file of the same name. */
    private fun isBookmarkSidecar(file: File): Boolean {
        val ext = FileNames.ext(file.name)
        if (ext == "tmk") return true
        if (ext != "txt") return false
        val stem = file.nameWithoutExtension
        return file.parentFile?.listFiles()?.any {
            it.nameWithoutExtension == stem && FileNames.ext(it.name) in AUDIO_VIDEO
        } ?: false
    }

    /** Streams the file looking for a markup-annotation subtype; stops at the first. */
    private fun pdfHasMarks(file: File): Boolean {
        val buf = ByteArray(1 shl 16)
        var carry = ""
        file.inputStream().buffered().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) return false
                // ISO-8859-1 maps every byte to one char, so a binary chunk decodes safely.
                val chunk = carry + String(buf, 0, n, Charsets.ISO_8859_1)
                if (PDF_MARK.containsMatchIn(chunk)) return true
                carry = chunk.takeLast(40)
            }
        }
    }
}
