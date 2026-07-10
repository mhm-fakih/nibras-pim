package com.knotes.player

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val AUDIO_EXT = setOf("mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "wav", "flac", "wma", "amr", "mid")
private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "wmv")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

fun fileExt(name: String): String = name.substringAfterLast('.', "").lowercase()

fun isMediaFile(name: String): Boolean = fileExt(name).let { it in AUDIO_EXT || it in VIDEO_EXT }

fun isVideoFile(name: String): Boolean = fileExt(name) in VIDEO_EXT

fun isImageFile(name: String): Boolean = fileExt(name) in IMAGE_EXT

/** Formats a millisecond position as `M:SS` or `H:MM:SS`. */
fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Zero-padded duration: `MM:SS`, or `HH:MM:SS` once it reaches an hour. */
fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Duration + bookmark count for a media file, used by the file lists. */
data class MediaMeta(val durationMs: Long, val bookmarkCount: Int)

private val durationCache = ConcurrentHashMap<String, Long>()

/** Media duration in ms (via [MediaMetadataRetriever]), cached per path. 0 when unreadable. */
fun mediaDurationMs(file: File): Long {
    durationCache[file.absolutePath]?.let { return it }
    val r = MediaMetadataRetriever()
    val ms = runCatching {
        r.setDataSource(file.absolutePath)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    }.getOrDefault(0L)
    runCatching { r.release() }
    durationCache[file.absolutePath] = ms
    return ms
}

/** Reads a file's duration and bookmark count (call off the main thread — it touches disk). */
fun loadMediaMeta(file: File): MediaMeta = MediaMeta(mediaDurationMs(file), Bookmarks.load(file).size)
