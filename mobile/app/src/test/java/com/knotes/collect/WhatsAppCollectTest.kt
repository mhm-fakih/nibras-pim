package com.knotes.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The WhatsApp collector's scan (recursive, only what changed since the cut-off) and its copy. */
class WhatsAppCollectTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file(root: File, path: String, mtime: Long, text: String = path): File =
        File(root, path).apply { parentFile.mkdirs(); writeText(text); setLastModified(mtime) }

    @Test
    fun findsOnlyNewFilesRecursivelyAndSkipsStickersAndHidden() {
        val media = tmp.newFolder("Media")
        val since = 1_800_000_000_000L
        file(media, "WhatsApp Images/IMG-20270115-WA0001.jpg", since + 1000)
        file(media, "WhatsApp Images/IMG-20270101-WA0009.jpg", since - 1000)          // old
        file(media, "WhatsApp Voice Notes/202703/PTT-20270115-WA0002.opus", since + 2000) // nested
        file(media, "WhatsApp Images/Sent/IMG-20270115-WA0003.jpg", since + 3000)
        file(media, "WhatsApp Stickers/STK-1.webp", since + 4000)                      // skipped
        file(media, ".Statuses/x.jpg", since + 5000)                                   // a viewed status
        file(media, ".Statuses/.nomedia", since + 5000)                                // hidden
        file(media, ".thumbs/t.jpg", since + 5000)                                     // hidden
        file(media, "WhatsApp Images/.nomedia", since + 6000)                          // hidden

        val found = FileCollector.scanChangedSince(media, since, "yyyy_MM_dd-HHmmss")
        assertEquals(
            setOf("IMG-20270115-WA0001.jpg", "PTT-20270115-WA0002.opus", "IMG-20270115-WA0003.jpg", "x.jpg"),
            found.map { it.source.name }.toSet(),
        )
        // Labelled by the top-level WhatsApp folder, however deep the file sits.
        assertEquals("WhatsApp Voice Notes", found.single { it.source.name.startsWith("PTT") }.sourceLabel)
    }

    @Test
    fun copyLeavesTheSourceInPlaceAndWritesTheTitledNote() {
        val media = tmp.newFolder("Media")
        val notes = tmp.newFolder("Notes")
        val src = file(media, "WhatsApp Images/IMG-20270115-WA0001.jpg", 1_800_000_001_000L, "pixels")
        val found = FileCollector.scanChangedSince(media, 0L, "yyyy_MM_dd-HHmmss")

        val (dir, results) = FileCollector.collect(
            found, notes.absolutePath, "yyyy_MM_dd-HHmmss", "N", 2, "X",
            description = WhatsAppCollect.NOTE_TITLE, copy = true,
        )
        assertTrue(results.single().ok)
        assertTrue("source must stay", src.isFile)
        val copied = File(dir, results.single().newName)
        assertEquals("pixels", copied.readText())
        assertEquals(src.lastModified(), copied.lastModified())
        val note = File(dir.parentFile, dir.name.removeSuffix(".d") + ".md")
        assertEquals(WhatsAppCollect.NOTE_TITLE, note.readLines().first())
    }

    @Test
    fun theFolderCollectorStillMoves() {
        val src = tmp.newFolder("Src")
        val notes = tmp.newFolder("Notes")
        val f = file(src, "a.txt", 1_800_000_000_000L)
        val found = FileCollector.scan(listOf(src.absolutePath), notes.absolutePath, "yyyy_MM_dd-HHmmss")
        val (_, results) = FileCollector.collect(found, notes.absolutePath, "yyyy_MM_dd-HHmmss", "N", 2, "X")
        assertTrue(results.single().ok)
        assertTrue("moved, so gone from the source", !f.exists())
    }

    @Test
    fun filesAreSortedIntoKindsByTheirWhatsAppFolder() {
        val media = tmp.newFolder("Media")
        val t = 1_800_000_001_000L
        file(media, "WhatsApp Voice Notes/202703/PTT-20270115-WA0002.opus", t)
        file(media, "WhatsApp Audio/AUD-20270115-WA0003.mp3", t)
        file(media, "WhatsApp Audio/AUD-20270115-WA0004.opus", t)          // forwarded audio, not a voice note
        file(media, "WhatsApp Documents/report.pdf", t)
        file(media, "WhatsApp Video/VID-20270115-WA0005.mp4", t)
        file(media, "WhatsApp Animated Gifs/VID-20270115-WA0006.mp4", t)
        file(media, "WhatsApp Images/IMG-20270115-WA0007.jpg", t)
        file(media, "WhatsApp Images/Sent/IMG-20270115-WA0008.jpg", t)
        file(media, ".Statuses/status-video.mp4", t)
        val kinds = FileCollector.scanChangedSince(media, 0L, "yyyy_MM_dd-HHmmss")
            .associate { it.source.name to WhatsAppCollect.kindOf(it) }
        assertEquals(WhatsAppCollect.Kind.VOICE, kinds["PTT-20270115-WA0002.opus"])
        assertEquals(WhatsAppCollect.Kind.AUDIO, kinds["AUD-20270115-WA0003.mp3"])
        assertEquals(WhatsAppCollect.Kind.AUDIO, kinds["AUD-20270115-WA0004.opus"])
        assertEquals(WhatsAppCollect.Kind.DOCUMENTS, kinds["report.pdf"])
        assertEquals(WhatsAppCollect.Kind.VIDEOS, kinds["VID-20270115-WA0005.mp4"])
        assertEquals(WhatsAppCollect.Kind.VIDEOS, kinds["VID-20270115-WA0006.mp4"])
        assertEquals(WhatsAppCollect.Kind.IMAGES, kinds["IMG-20270115-WA0007.jpg"])
        assertEquals(WhatsAppCollect.Kind.IMAGES, kinds["IMG-20270115-WA0008.jpg"])
        assertEquals(WhatsAppCollect.Kind.STATUSES, kinds["status-video.mp4"])
    }

    @Test
    fun rangeCutoffs() {
        val noon = java.util.Calendar.getInstance().apply { set(2027, 0, 15, 12, 30, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val midnight = java.util.Calendar.getInstance().apply { set(2027, 0, 15, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val day = 86_400_000L
        assertEquals(null, WhatsAppCollect.cutoff(WhatsAppCollect.Range.SINCE_LAST, noon))
        assertEquals(midnight, WhatsAppCollect.cutoff(WhatsAppCollect.Range.TODAY, noon))
        assertEquals(midnight - 6 * day, WhatsAppCollect.cutoff(WhatsAppCollect.Range.DAYS_7, noon))
        assertEquals(0L, WhatsAppCollect.cutoff(WhatsAppCollect.Range.ALL, noon))
    }
}
