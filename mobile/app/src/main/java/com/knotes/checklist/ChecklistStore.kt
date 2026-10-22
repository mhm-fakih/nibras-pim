package com.knotes.checklist

import java.io.File

/**
 * Reads and writes the week files in one folder. Plain file IO — call it off the main thread.
 *
 * The folder is the notes folder's `checklists` subfolder by default, so it syncs with the notes;
 * both apps treat that name as a housekeeping folder rather than an item "to settle".
 */
class ChecklistStore(private val dir: File) {

    fun file(id: WeekId) = File(dir, "$id.md")

    fun exists(id: WeekId) = file(id).isFile

    fun load(id: WeekId): ChecklistWeek? =
        file(id).takeIf { it.isFile }
            ?.let { runCatching { ChecklistFormat.read(id, it.readText()) }.getOrNull() }

    /** Written to a temp file and moved into place, so a sync never picks up half a week. */
    fun save(week: ChecklistWeek): Boolean = runCatching {
        dir.mkdirs()
        val target = file(week.id)
        val tmp = File(dir, ".${week.id}.md.tmp")
        tmp.writeText(ChecklistFormat.write(week))
        if (!tmp.renameTo(target)) { target.writeText(tmp.readText()); tmp.delete() }
        true
    }.getOrDefault(false)

    /** Every week that has a file, oldest first. */
    fun weeks(): List<WeekId> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.mapNotNull { WeekId.parse(it.name.removeSuffix(".md")) }
            ?.sorted()
            .orEmpty()

    /** When the file was last written — the screen re-reads when this moves (a sync from the desktop). */
    fun stamp(id: WeekId): Long = file(id).lastModified()

    /**
     * The week to show for [id]: its own file, else — not yet started — the latest earlier week's
     * items carried over with no marks, else the starter groups. Not saved until something changes,
     * so merely looking at an empty future week leaves no file behind.
     */
    fun open(id: WeekId, starter: () -> ChecklistWeek): ChecklistWeek =
        load(id)
            ?: weeks().lastOrNull { it < id }?.let(::load)?.carriedTo(id)
            ?: starter()
}
