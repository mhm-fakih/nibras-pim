package com.knotes.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistsTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val lists = listOf(Playlist("Night", listOf("/a.mp3", "/b.mp3")), Playlist("محاضرات", emptyList()))
        assertEquals(lists, Playlists.decode(Playlists.encode(lists)))
    }

    @Test
    fun namesLoseSeparatorsAndDuplicatesGo() {
        assertEquals("a b", Playlists.cleanName("a\tb\n"))
        val decoded = Playlists.decode("Night\t/a\nnight\t/b\n\t/c")
        assertEquals(listOf(Playlist("Night", listOf("/a"))), decoded)
    }

    @Test
    fun nextWalksInOrderSkippingMissingAndStopsAtTheEnd() {
        val paths = listOf("/1", "/2", "/3", "/4")
        val exists = { p: String -> p != "/3" }
        assertEquals("/1", Playlists.next(paths, "/elsewhere", exists))
        assertEquals("/2", Playlists.next(paths, "/1", exists))
        assertEquals("/4", Playlists.next(paths, "/2", exists))
        assertNull(Playlists.next(paths, "/4", exists))
    }

    @Test
    fun moveClampsAtTheEnds() {
        val paths = listOf("/1", "/2", "/3")
        assertEquals(listOf("/2", "/1", "/3"), Playlists.move(paths, "/2", -1))
        assertEquals(paths, Playlists.move(paths, "/1", -1))
        assertEquals(listOf("/1", "/3", "/2"), Playlists.move(paths, "/2", 1))
    }
}
