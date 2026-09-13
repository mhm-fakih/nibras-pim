package com.knotes.util

/**
 * What a file *is*, judged from its name, for the sake of showing it.
 *
 * This is a display concern only — nothing here decides how a file opens (that stays with
 * `isMediaFile`, which answers a narrower question: does the app's own player handle it). The two
 * lists overlap but must not be merged: a `.srt` is a text file to look at and not a media file to
 * play, and a future format the player learns should not silently change which glyph is drawn.
 *
 * Unknown extensions land on [OTHER] rather than being guessed at. A wrong icon is worse than a
 * generic one — it says something definite about a file that happens to be false.
 */
enum class FileKind {
    AUDIO, VIDEO, IMAGE, PDF, DOC, SHEET, SLIDES, TEXT, ARCHIVE, CODE, OTHER
}

private val KIND_BY_EXT: Map<String, FileKind> = buildMap {
    fun put(kind: FileKind, vararg exts: String) = exts.forEach { put(it, kind) }

    put(FileKind.AUDIO, "mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "wav", "flac", "wma", "amr", "mid")
    put(FileKind.VIDEO, "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "flv", "wmv")
    put(FileKind.IMAGE, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg")
    put(FileKind.PDF, "pdf", "djvu")
    put(FileKind.DOC, "doc", "docx", "odt", "rtf", "epub", "mobi", "azw3")
    put(FileKind.SHEET, "xls", "xlsx", "ods", "csv", "tsv")
    put(FileKind.SLIDES, "ppt", "pptx", "odp")
    put(FileKind.TEXT, "txt", "md", "markdown", "log", "srt", "vtt", "tmk")
    put(FileKind.ARCHIVE, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk")
    put(FileKind.CODE, "java", "kt", "py", "js", "ts", "tsx", "sh", "sql", "json", "xml", "yml", "yaml", "html", "css")
}

/**
 * The [FileKind] of a file name.
 *
 * Goes through [FileNames.ext], so an Arabic name carrying a trailing bidi mark — `تسجيل.mp3‏`,
 * which is how such names actually arrive from the server and from Syncthing — is recognised
 * rather than falling to [FileKind.OTHER].
 */
fun fileKind(name: String): FileKind = KIND_BY_EXT[FileNames.ext(name)] ?: FileKind.OTHER
