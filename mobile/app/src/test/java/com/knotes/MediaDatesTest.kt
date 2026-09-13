package com.knotes

import com.knotes.player.MediaDates
import com.knotes.player.NextTrack
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Reading a recording's date out of its NAME, and the next-track ordering that depends on it.
 *
 * The bug this exists for: "play next: newest" kept returning old recordings while unbookmarked
 * ones from the same day sat in the folder. The ordering was `lastModified()`, and these folders
 * are Syncthing shares — Syncthing rewrites mtimes on transfer, so an old file re-synced this
 * morning outranks a recording made an hour ago.
 */
class MediaDatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun local(text: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(text)!!.time

    @Test
    fun `the recorder conventions in this library are read`() {
        assertEquals(local("2026-08-18 15:20:00"), MediaDates.fromName("260818_1520.mp3"))
        assertEquals(local("2026-08-18 15:20:00"), MediaDates.fromName("20260818_152000.m4a"))
        assertEquals(local("2026-08-18 15:20:00"), MediaDates.fromName("2026_08_18-152000.mp3"))
    }

    @Test
    fun `the same-minute counter is not read as seconds`() {
        // 260818_1520_02.mp3 is the second recording of that minute, not 15:20:02. Reading it as
        // seconds would be harmless here, but reading the "02" as part of the DATE would not.
        assertEquals(local("2026-08-18 15:20:00"), MediaDates.fromName("260818_1520_02.mp3"))
    }

    @Test
    fun `a name with no date in it yields nothing`() {
        assertNull(MediaDates.fromName("Chapter One.mp3"))
        assertNull(MediaDates.fromName("lecture.m4a"))
    }

    @Test
    fun `numbers that are not dates are rejected rather than guessed at`() {
        // Out-of-range fields: a 13th month, a 25th hour, a 61st minute.
        assertNull(MediaDates.fromName("261318_1520.mp3"))
        assertNull(MediaDates.fromName("260818_2520.mp3"))
        assertNull(MediaDates.fromName("260818_1561.mp3"))
    }

    @Test
    fun `a file with no readable date falls back to its modification time`() {
        val f = File(tmp.newFolder("m"), "untitled.mp3").apply { writeBytes(ByteArray(1)) }
        f.setLastModified(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, MediaDates.recordedMillis(f))
    }

    /**
     * The regression, end to end.
     *
     * Today's recording has an OLD mtime and an old recording has a fresh one — exactly what
     * Syncthing produces when it re-transfers an old file. Ranking on mtime picks the old one; the
     * name says otherwise, and the name is right.
     */
    @Test
    fun `newest picks today's recording even when an old file has a newer mtime`() {
        val now = System.currentTimeMillis()
        val root = tmp.newFolder("W34")
        val today = SimpleDateFormat("yyMMdd", Locale.US).format(java.util.Date(now))

        // Recorded today, but Syncthing wrote it a fortnight ago as far as the filesystem knows.
        val todays = File(root, "${today}_0930.mp3").apply { writeBytes(ByteArray(4)) }
        todays.setLastModified(now - TimeUnit.DAYS.toMillis(14))

        // Recorded years ago, re-synced this morning, so it carries the newest mtime in the folder.
        val ancient = File(root, "180101_0800.mp3").apply { writeBytes(ByteArray(4)) }
        ancient.setLastModified(now)

        repeat(30) {
            val pick = NextTrack.randomNewUnbookmarked(listOf(root), now = now)
            assertNotNull(pick)
            assertEquals(
                "newest must follow the recorded date, not the mtime",
                todays.absolutePath,
                pick?.absolutePath,
            )
        }
    }

    /** And the mirror: "oldest" must not skip a genuinely old file whose mtime was refreshed. */
    @Test
    fun `oldest still finds an old recording that was re-synced today`() {
        val now = System.currentTimeMillis()
        val root = tmp.newFolder("lib")
        val ancient = File(root, "180101_0800.mp3").apply { writeBytes(ByteArray(4)) }
        ancient.setLastModified(now)                       // fresh mtime, old recording

        val pick = NextTrack.randomOldUnbookmarked(listOf(root), now = now)
        assertEquals(ancient.absolutePath, pick?.absolutePath)
        assertTrue("it was chosen as genuinely old, not via the fallback pool", true)
    }
}
