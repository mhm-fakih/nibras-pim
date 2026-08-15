package com.knotes

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * "Written today" comes from the note's file NAME, never its mtime.
 *
 * The bug this pins: Syncthing rewrites files it hasn't changed, so a sync of a few hundred old
 * notes set every mtime to now and the today-count reported all of them as written today. Every
 * counter (homepage header, status notification, KPI widget) now goes through
 * [NoteFiles.createdMillisOf], so this covers all three.
 */
class TodayCountTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val format = "yyyy_MM_dd-HHmmss"

    private fun noteNamed(stamp: String, mtime: Long): File {
        val f = File(tmp.root, NoteFiles.buildName(stamp, "J", 2, "X"))
        f.writeText("body\n")
        f.setLastModified(mtime)
        return f
    }

    private fun stampFor(millis: Long) = SimpleDateFormat(format, Locale.US).format(Date(millis))

    @Test
    fun aNoteResyncedTodayStillCountsAsWrittenWhenItsNameSays() {
        val lastWeek = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        // Name says last week; mtime says a minute ago — exactly what a Syncthing pass leaves behind.
        val f = noteNamed(stampFor(lastWeek), mtime = System.currentTimeMillis())

        val created = NoteFiles.createdMillisOf(f, format)
        assertEquals(
            "should read the date out of the file name, not the mtime",
            stampFor(lastWeek), stampFor(created),
        )
    }

    @Test
    fun aNoteWrittenTodayCountsAsToday() {
        val now = System.currentTimeMillis()
        val f = noteNamed(stampFor(now), mtime = now)
        assertEquals(stampFor(now), stampFor(NoteFiles.createdMillisOf(f, format)))
    }

    /** The de-dup suffix a same-second second note gets must not defeat the parse. */
    @Test
    fun handlesTheDeDupSuffix() {
        val when_ = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        val f = noteNamed(stampFor(when_) + "-2", mtime = System.currentTimeMillis())
        assertEquals(stampFor(when_), stampFor(NoteFiles.createdMillisOf(f, format)))
    }

    /** A foreign file has no timestamp to read, so its mtime is genuinely all there is. */
    @Test
    fun fallsBackToMtimeWhenTheNameCarriesNoTimestamp() {
        val mtime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val f = File(tmp.root, "some-download.md")
        f.writeText("x")
        f.setLastModified(mtime)
        assertEquals(mtime, NoteFiles.createdMillisOf(f, format))
    }
}
