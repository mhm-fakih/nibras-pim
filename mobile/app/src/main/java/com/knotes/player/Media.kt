package com.knotes.player

import android.media.MediaMetadataRetriever
import com.knotes.util.FileNames
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val AUDIO_EXT = setOf("mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "wav", "flac", "wma", "amr", "mid")
private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "wmv")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

/**
 * The lowercase extension. Delegates to [FileNames.ext] so an Arabic name carrying an invisible
 * bidi mark after the extension (`اسم.mp3‏`) is still recognised as media rather than being
 * treated as an unopenable document, and so case folding does not depend on the device locale.
 */
fun fileExt(name: String): String = FileNames.ext(name)

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

/** Genre/artist/album tags for the library's Genre → Artist → Album browsing. Any tag missing
 *  or unreadable is left null; callers bucket those under "Unknown …" rather than hiding the file. */
data class MediaTags(val genre: String?, val artist: String?, val album: String?)

private val tagsCache = ConcurrentHashMap<String, MediaTags>()

/** Reads a file's genre/artist/album (via [MediaMetadataRetriever]), cached per path. Call off the
 *  main thread — it touches disk, same as [mediaDurationMs]. */
fun mediaTags(file: File): MediaTags {
    tagsCache[file.absolutePath]?.let { return it }
    val r = MediaMetadataRetriever()
    val tags = runCatching {
        r.setDataSource(file.absolutePath)
        MediaTags(
            genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.trim()?.ifBlank { null },
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.ifBlank { null },
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.ifBlank { null },
        )
    }.getOrDefault(MediaTags(null, null, null))
    runCatching { r.release() }
    tagsCache[file.absolutePath] = tags
    return tags
}
