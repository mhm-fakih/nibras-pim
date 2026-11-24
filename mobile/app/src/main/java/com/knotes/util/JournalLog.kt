package com.knotes.util

/**
 * The day's journal as one note: each entry appended under a `[HH:mm]` stamp, a blank line apart,
 * the same layout the web's Quick board writes into the day's board journal.
 *
 * One note a day rather than one per entry: a day read top to bottom, in one place, instead of a
 * dozen two-line files that the notes list and Syncthing both have to carry.
 */
object JournalLog {

    /** A stamp at the start of a paragraph opens an entry. 24-hour, Western digits in any locale. */
    private val STAMP = Regex("""^\[(\d{2}:\d{2})] ?(.*)$""")

    data class Entry(val time: String?, val text: String)

    fun entry(time: String, body: String): String = "[$time] ${body.trim().replace("\r\n", "\n")}"

    /** [existing] with the new entry below it. A blank existing text just becomes the entry. */
    fun append(existing: String?, time: String, body: String): String {
        val before = existing.orEmpty().trimEnd()
        return if (before.isEmpty()) entry(time, body) else before + "\n\n" + entry(time, body)
    }

    /**
     * The entries of a journal note, in the order written. Only a stamp at the start of the text or
     * after a blank line opens an entry, so a line inside an entry that happens to begin with
     * "[10:00]" stays part of it. Text before the first stamp (a note written before entries were
     * stamped) is an entry of its own with no time.
     */
    fun entries(content: String): List<Entry> {
        val out = mutableListOf<Entry>()
        var time: String? = null
        val buf = StringBuilder()
        var prevBlank = true
        fun flush() {
            val t = buf.toString().trim()
            if (t.isNotEmpty() || time != null) out += Entry(time, t)
            buf.clear()
        }
        for (line in content.replace("\r\n", "\n").split('\n')) {
            val m = if (prevBlank) STAMP.matchEntire(line) else null
            if (m != null) {
                flush()
                time = m.groupValues[1]
                buf.append(m.groupValues[2])
            } else {
                if (buf.isNotEmpty() || line.isNotBlank()) buf.append('\n').append(line)
            }
            prevBlank = line.isBlank()
        }
        flush()
        return out
    }
}
