package com.knotes.status

import com.knotes.SettingsStore
import com.knotes.isFormattedNote
import java.io.File
import java.util.Calendar

/** Today's vs. total note counts for the status notification. */
data class Counts(val today: Int, val total: Int)

/** Counts notes in the notes folder: total note files, and those modified since local midnight. */
object NoteCount {

    fun read(settingsStore: SettingsStore): Counts {
        val dir = File(settingsStore.value.notesDir)
        if (!dir.isDirectory) return Counts(0, 0)
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var total = 0
        var today = 0
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && isFormattedNote(it.name) }
            .forEach {
                total++
                if (it.lastModified() >= startOfDay) today++
            }
        return Counts(today, total)
    }
}
