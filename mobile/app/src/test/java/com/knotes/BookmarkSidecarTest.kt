package com.knotes

import com.knotes.player.Bookmark
import com.knotes.player.Bookmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Reading and rewriting the `.tmk` sidecar without losing marks.
 *
 * A sidecar the parser couldn't read used to load as empty, so the player showed the file as
 * unbookmarked — and the next bookmark's save replaced the whole file with that one new mark. And a
 * save always wrote the list the screen had loaded when the track opened, erasing anything written
 * since. What has to hold: foreign-but-valid formats parse, unknown lines survive a save, and an
 * update applies to what is on disk now.
 */
class BookmarkSidecarTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun media(): File = tmp.newFile("260917_1634.mp3")

    @Test
    fun `a Sony recorder sidecar with a BOM and five-digit minutes parses`() {
        val m = media()
        Bookmarks.sidecar(m).writeText("﻿[00004:58.00]\n[00005:16.00]\n[00061:01.50]\n")
        val marks = Bookmarks.load(m)
        assertEquals(listOf(298_000L, 316_000L, 3_661_500L), marks.map { it.positionMs })
    }

    @Test
    fun `the app's own format still parses`() {
        assertEquals(Bookmark(139_000L, "p2"), Bookmarks.parse("[2:19] p2"))
        assertEquals(Bookmark(3_723_000L, ""), Bookmarks.parse("[1:02:03]"))
    }

    @Test
    fun `lines the parser cannot read survive a save`() {
        val m = media()
        Bookmarks.sidecar(m).writeText("[0:10] first\nchapter notes kept by hand\n")
        Bookmarks.update(m) { it + Bookmark(20_000L, "second") }
        val text = Bookmarks.sidecar(m).readText()
        assertTrue(text, text.contains("chapter notes kept by hand"))
        assertEquals(listOf(10_000L, 20_000L), Bookmarks.load(m).map { it.positionMs })
    }

    @Test
    fun `an update applies to the file as it is now, not to an earlier read`() {
        val m = media()
        Bookmarks.save(m, listOf(Bookmark(5_000L, "a")))
        val stale = Bookmarks.load(m)
        // Written by someone else (the service, the desktop player) after the screen loaded.
        Bookmarks.save(m, stale + Bookmark(9_000L, "from the service"))
        val result = Bookmarks.update(m) { it + Bookmark(12_000L, "tap") }
        assertEquals(listOf(5_000L, 9_000L, 12_000L), result.map { it.positionMs })
        assertEquals(result, Bookmarks.load(m))
    }
}
