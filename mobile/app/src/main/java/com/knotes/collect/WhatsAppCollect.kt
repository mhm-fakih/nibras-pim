package com.knotes.collect

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The WhatsApp collector: COPIES the media WhatsApp received since the last collection into a
 * note titled [NOTE_TITLE], leaving WhatsApp's own folder untouched (the folder collector moves;
 * moving WhatsApp's files would leave holes in its chats).
 *
 * "New" is decided by modified time against the moment of the last successful collection, kept
 * here per kind. The first collection, with nothing to compare against, takes today's files —
 * never the whole backlog; the Collect page can reach further back for one run ([Range]).
 */
object WhatsAppCollect {

    /**
     * What a WhatsApp file is, for choosing what to copy. Decided by the WhatsApp folder it sits
     * in (`WhatsApp Voice Notes`, `WhatsApp Audio`, …), which is how WhatsApp itself sorts them —
     * a voice note and a forwarded song are both .opus/.mp3, only the folder tells them apart —
     * and by the extension when the folder says nothing.
     */
    enum class Kind { VOICE, AUDIO, DOCUMENTS, VIDEOS, IMAGES, STATUSES }

    fun kindOf(f: FoundFile): Kind {
        val folder = f.sourceLabel.lowercase()
        return when {
            // First: a status is a photo or a video, but it is chosen as a status.
            "status" in folder -> Kind.STATUSES
            "voice" in folder -> Kind.VOICE
            "audio" in folder -> Kind.AUDIO
            "document" in folder -> Kind.DOCUMENTS
            "video" in folder || "gif" in folder -> Kind.VIDEOS
            "image" in folder || "photo" in folder -> Kind.IMAGES
            else -> when (f.source.extension.lowercase()) {
                "opus" -> Kind.VOICE
                "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "amr" -> Kind.AUDIO
                "mp4", "m4v", "mkv", "webm", "3gp", "mov", "avi" -> Kind.VIDEOS
                "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" -> Kind.IMAGES
                else -> Kind.DOCUMENTS
            }
        }
    }

    private const val K_KINDS = "kinds"
    private fun lastKey(k: Kind) = "last_collected_${k.name.lowercase()}"

    /** The kinds ticked to copy (all of them until changed). */
    fun selectedKinds(context: Context): Set<Kind> =
        prefs(context).getString(K_KINDS, null)
            ?.split(',')?.mapNotNull { n -> Kind.entries.firstOrNull { it.name == n } }?.toSet()
            ?: Kind.entries.toSet()

    fun saveSelectedKinds(context: Context, kinds: Set<Kind>) {
        prefs(context).edit().putString(K_KINDS, kinds.joinToString(",") { it.name }).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    const val NOTE_TITLE = "Whatsapp new files"

    /**
     * How far back a scan reaches. [SINCE_LAST] is the normal one (each kind from its own last
     * copy); the others override it for one run from the Collect page — catching up after a
     * holiday, or taking the whole backlog once.
     */
    enum class Range { SINCE_LAST, TODAY, DAYS_7, DAYS_30, ALL }

    /** The cut-off [range] means at [now]; null for [Range.SINCE_LAST] (per-kind marks). */
    fun cutoff(range: Range, now: Long = System.currentTimeMillis()): Long? = when (range) {
        Range.SINCE_LAST -> null
        Range.TODAY -> startOfToday(now)
        Range.DAYS_7 -> startOfToday(now) - TimeUnit.DAYS.toMillis(6)
        Range.DAYS_30 -> startOfToday(now) - TimeUnit.DAYS.toMillis(29)
        Range.ALL -> 0L
    }

    private fun startOfToday(now: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val PREFS = "collect_whatsapp"
    private const val K_LAST = "last_collected_millis"

    /**
     * Where WhatsApp keeps its media: the scoped-storage location used since Android 11, then the
     * old top-level one, then WhatsApp Business. The first that exists wins.
     */
    fun candidates(): List<File> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        return listOf(
            "$root/Android/media/com.whatsapp/WhatsApp/Media",
            "$root/WhatsApp/Media",
            "$root/Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        ).map(::File)
    }

    /** The configured folder if set, else the first of [candidates] that exists (or the first). */
    fun mediaDir(configured: String): File =
        configured.trim().takeIf { it.isNotEmpty() }?.let(::File)
            ?: candidates().firstOrNull { it.isDirectory }
            ?: candidates().first()

    /**
     * When files of [kind] were last collected (epoch ms), or 0 for never. Kept PER KIND: leaving
     * images unticked today must not make today's images count as collected — they are still new
     * the day images are ticked again. (A mark from before kinds existed applies to all of them.)
     */
    fun lastCollected(context: Context, kind: Kind): Long =
        prefs(context).getLong(lastKey(kind), prefs(context).getLong(K_LAST, 0L))

    /** Files of [kind] newer than this are "new": its last collection, or the start of today. */
    fun since(context: Context, kind: Kind, now: Long = System.currentTimeMillis()): Long =
        lastCollected(context, kind).takeIf { it > 0 } ?: startOfToday(now)

    /** The cut-off for every kind — what the scan uses; each file is then held to its own kind's. */
    fun sinceByKind(context: Context, now: Long = System.currentTimeMillis()): Map<Kind, Long> =
        Kind.entries.associateWith { since(context, it, now) }

    /**
     * Record a collection of [kinds]. Stored as the time the SCAN started, not when the copy
     * finished, so a file that arrives while the copy runs is still new next time.
     */
    fun markCollected(context: Context, kinds: Set<Kind>, scanStartedAt: Long) {
        val e = prefs(context).edit()
        kinds.forEach { e.putLong(lastKey(it), scanStartedAt) }
        e.apply()
    }

    /**
     * Every file under [dir] that is new for its kind — modified after that kind's own mark.
     * Scans from the earliest mark, then holds each file to its kind's. Off the main thread.
     */
    fun scanNew(
        context: Context,
        dir: File,
        format: String,
        now: Long = System.currentTimeMillis(),
        range: Range = Range.SINCE_LAST,
    ): List<FoundFile> {
        cutoff(range, now)?.let { from -> return FileCollector.scanChangedSince(dir, from - 1, format) }
        val sinceBy = sinceByKind(context, now)
        return FileCollector.scanChangedSince(dir, sinceBy.values.min(), format)
            .filter { it.source.lastModified() > sinceBy.getValue(kindOf(it)) }
    }

    /**
     * The unattended copy (the daily alarm): what is new of [kinds], copied into a "[NOTE_TITLE]"
     * note. Marks those kinds collected only if every file copied. Null when nothing was new.
     */
    fun copyNow(
        context: Context,
        kinds: Set<Kind>,
        newDir: String,
        format: String,
        type: String?,
        priority: Int,
        category: String?,
        heading: (Int) -> String,
    ): Pair<File, List<CollectedFile>>? {
        if (kinds.isEmpty()) return null
        val startedAt = System.currentTimeMillis()
        val found = scanNew(context, mediaDir(""), format, startedAt).filter { kindOf(it) in kinds }
        if (found.isEmpty()) return null
        val result = FileCollector.collect(
            found, newDir, format, type, priority, category,
            description = NOTE_TITLE, heading = heading, copy = true,
        )
        if (result.second.all { it.ok }) markCollected(context, kinds, startedAt)
        return result
    }
}
