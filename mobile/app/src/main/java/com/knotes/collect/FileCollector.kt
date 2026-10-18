package com.knotes.collect

import com.knotes.NoteFiles
import com.knotes.companionDirOf
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A file discovered in a source folder, with the standardized name it will get. */
data class FoundFile(
    val source: File,
    val sourceLabel: String,
    val newName: String,
    /** "name" if the timestamp came from the file name, "modified" if from the mtime. */
    val timestampFrom: String,
)

/** Outcome of moving one file. */
data class CollectedFile(
    val originalName: String,
    val newName: String,
    val ok: Boolean,
    val error: String? = null,
)

/**
 * Collects files from a configurable set of folders into a single timestamped subfolder,
 * renaming each so a standardized timestamp leads the name — which makes them sort
 * chronologically regardless of their original source or naming scheme.
 *
 * The timestamp is parsed out of the original file name when present (many common camera /
 * screenshot / recorder formats are recognized); otherwise the file's last-modified time is used.
 *
 * A source path starting with `#` is treated as a comment and skipped (it still stays in the
 * settings list, so it's easy to toggle a folder off without deleting it).
 */
object FileCollector {

    // yyyy MM dd HH mm ss with flexible separators (e.g. 2024-01-15-12-30-45, 20240115_123045).
    private val DATETIME =
        Regex("((?:19|20)\\d{2})\\D?(\\d{2})\\D?(\\d{2})\\D{0,3}(\\d{2})\\D?(\\d{2})\\D?(\\d{2})")

    // Date only (no time component): use midnight.
    private val DATEONLY = Regex("((?:19|20)\\d{2})\\D?(\\d{2})\\D?(\\d{2})")

    private data class Stamp(val millis: Long, val range: IntRange)

    fun scan(sourceDirs: List<String>, newDir: String, format: String): List<FoundFile> {
        val newCanonical = runCatching { File(newDir).canonicalPath }.getOrDefault(newDir)
        return sourceDirs.flatMap { raw ->
            val path = raw.trim()
            // A leading '#' comments a path out (like in code), so it's kept in settings but skipped.
            if (path.isEmpty() || path.startsWith("#")) return@flatMap emptyList()
            val dir = File(path)
            // Skip missing dirs and never collect from inside the destination.
            if (!dir.isDirectory) return@flatMap emptyList()
            if (runCatching { dir.canonicalPath }.getOrDefault(path).startsWith(newCanonical)) {
                return@flatMap emptyList()
            }
            val label = dir.name
            (dir.listFiles { f -> f.isFile && !f.name.startsWith(".") } ?: emptyArray())
                .map { f -> toFound(f, label, format) }
        }.sortedBy { it.newName }
    }

    private fun toFound(file: File, label: String, format: String): FoundFile {
        // knotes' own notes already lead with a standardized timestamp and encode tags in the name
        // (e.g. 2026_06_10-143025~W~p3~A.md); keep their name byte-for-byte so the tags survive.
        if (com.knotes.isNoteFile(file.name)) {
            return FoundFile(source = file, sourceLabel = label, newName = file.name, timestampFrom = "name")
        }
        val base = file.nameWithoutExtension
        val ext = file.extension
        val stamp = parseStamp(base)
        val millis = stamp?.millis ?: file.lastModified()
        val standardized = SimpleDateFormat(format, Locale.US).format(Date(millis))
        val remainder = (if (stamp != null) base.removeRange(stamp.range) else base)
            .trim('_', '-', ' ', '.')
            .replace(Regex("[ _\\-.]{2,}"), "_")
        val rest = if (remainder.isBlank()) "" else "_$remainder"
        val extPart = if (ext.isBlank()) "" else ".$ext"
        return FoundFile(
            source = file,
            sourceLabel = label,
            newName = "$standardized$rest$extPart",
            timestampFrom = if (stamp != null) "name" else "modified",
        )
    }

    private fun parseStamp(base: String): Stamp? {
        DATETIME.find(base)?.let { m ->
            val (y, mo, d, h, mi, s) = m.destructured
            millisOf(y, mo, d, h, mi, s)?.let { return Stamp(it, m.range) }
        }
        DATEONLY.find(base)?.let { m ->
            val (y, mo, d) = m.destructured
            millisOf(y, mo, d, "0", "0", "0")?.let { return Stamp(it, m.range) }
        }
        return null
    }

    private fun millisOf(y: String, mo: String, d: String, h: String, mi: String, s: String): Long? {
        val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
        val hour = h.toInt(); val min = mi.toInt(); val sec = s.toInt()
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || min !in 0..59 || sec !in 0..59) return null
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, min, sec)
        }.timeInMillis
    }

    /**
     * Moves [found] files into a collection, and writes the note that owns them.
     *
     * The destination is a **companion folder** — `<timestamp>~<type>~p<priority>~<category>.d` —
     * with a matching `.md` beside it carrying the same stem. That is the layout the desktop, the
     * backend importer and this app already agree on for "a note plus its attachments", so a
     * collection stops being a loose folder of files and becomes an ordinary note that happens to
     * have things attached: readable, editable, importable, with no special case anywhere.
     *
     * It used to be `newDir/<timestamp>/` — a bare folder no tool recognised as anything.
     *
     * Returns the companion folder and a per-file result. Files are moved (removed from the
     * source); rename is used when possible, falling back to copy+delete across volumes.
     */
    fun collect(
        found: List<FoundFile>,
        newDir: String,
        format: String,
        type: String?,
        priority: Int,
        category: String?,
        /** What the set is, typed when collecting by hand; heads the note (and so titles it). */
        description: String = "",
    ): Pair<File, List<CollectedFile>> {
        val parent = File(newDir)
        parent.mkdirs()

        // The note name decides the folder name, not the other way round: the folder is defined as
        // "the note's stem + .d", so they can never drift apart.
        var token = SimpleDateFormat(format, Locale.US).format(Date())
        var noteFile = File(parent, NoteFiles.buildName(token, type, priority, category))
        var counter = 2
        while (noteFile.exists() || companionDirOf(noteFile).exists()) {
            // Same de-dup convention as NoteRepository.saveNew — two collections in one second.
            token = SimpleDateFormat(format, Locale.US).format(Date()) + "-" + counter++
            noteFile = File(parent, NoteFiles.buildName(token, type, priority, category))
        }

        val target = companionDirOf(noteFile)
        target.mkdirs()
        val taken = HashSet<String>()
        val results = found.map { f ->
            val dest = uniqueDest(target, f.newName, taken)
            val moved = move(f.source, dest)
            CollectedFile(
                originalName = f.source.name,
                newName = dest.name,
                ok = moved,
                error = if (moved) null else "move failed",
            )
        }
        // The note itself, written after the moves so it can say what actually landed. A failed
        // move is listed too — a collection that silently dropped a file would be worse than one
        // that admits it.
        val ok = results.count { it.ok }
        val body = buildString {
            // The description first: a note is titled by its first line, and "Collected 7 files"
            // says nothing about which seven, or why they were worth keeping together.
            description.trim().takeIf { it.isNotEmpty() }?.let { append(it).append("\n\n") }
            append("Collected ").append(ok).append(if (ok == 1) " file" else " files").append("\n\n")
            results.forEach { r ->
                append("- ").append(r.newName)
                if (!r.ok) append("  (failed: ").append(r.error ?: "unknown").append(")")
                append("\n")
            }
        }
        runCatching { noteFile.writeText(body) }

        return target to results
    }

    private fun uniqueDest(dir: File, name: String, taken: HashSet<String>): File {
        var candidate = name
        if (candidate in taken || File(dir, candidate).exists()) {
            val dot = name.lastIndexOf('.')
            val stem = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 2
            while (true) {
                candidate = "$stem-$i$ext"
                if (candidate !in taken && !File(dir, candidate).exists()) break
                i++
            }
        }
        taken.add(candidate)
        return File(dir, candidate)
    }

    private fun move(src: File, dest: File): Boolean {
        if (src.renameTo(dest)) return true
        return runCatching {
            src.copyTo(dest, overwrite = false)
            src.delete()
            true
        }.getOrDefault(false)
    }
}
