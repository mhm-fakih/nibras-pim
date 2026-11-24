package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/** The journal widget's write: the day's first entry starts the log note, later ones append. */
class JournalAppendTest {

    @Test
    fun firstEntryStartsTheLogAndLaterOnesAppendToIt() {
        val dir = createTempDirectory("journal").toFile()
        var created = 0
        val make = { text: String -> created++; File(dir, "log.md").apply { writeText(text + "\n") } }

        val log = appendJournalEntry(null, "08:05", "first", make)
        val again = appendJournalEntry(log, "12:30", "second\nline", make)

        assertEquals(1, created)
        assertEquals(log, again)
        assertEquals("[08:05] first\n\n[12:30] second\nline\n", log.readText())
        dir.deleteRecursively()
    }

    @Test
    fun aLogFileThatHasGoneIsStartedAgainRatherThanLost() {
        val dir = createTempDirectory("journal").toFile()
        val gone = File(dir, "gone.md")
        val made = appendJournalEntry(gone, "09:00", "kept", { t -> File(dir, "new.md").apply { writeText(t + "\n") } })
        assertEquals("[09:00] kept\n", made.readText())
        dir.deleteRecursively()
    }
}
