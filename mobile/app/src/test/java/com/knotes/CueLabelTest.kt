package com.knotes

import com.knotes.player.Subtitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Taking the spoken sentence as a bookmark's label.
 *
 * A bare timestamp says nothing when you come back to a list of bookmarks; the line itself is the
 * only thing that records why you marked it, and the subtitle file has already done the typing.
 * What has to hold: the label names the sentence the bookmark POINTS AT, not whatever is being
 * spoken at the moment you tapped — the two differ by the rewind, which is the whole reason the
 * rewind exists.
 */
class CueLabelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun srt(body: String): File =
        File(tmp.newFolder("subs"), "talk.srt").apply { writeText(body.trimIndent()) }

    private val sample = """
        1
        00:00:10,000 --> 00:00:14,000
        The first thing to notice is the shape.

        2
        00:00:20,000 --> 00:00:26,000
        And the second, which matters more,
        is what happens at the edges.
    """

    @Test
    fun `the cue at the bookmarked position is the one taken`() {
        val cues = Subtitles.parse(srt(sample))
        // Tapped at 22s with a 5s rewind: the bookmark lands at 17s, which is BETWEEN the cues.
        assertNull(Subtitles.cueAt(cues, 17_000))
        // Inside the first cue, the first line is what it names — not the one playing at 22s.
        assertEquals("The first thing to notice is the shape.", Subtitles.cueAt(cues, 12_000))
    }

    @Test
    fun `a cue spanning several lines comes back as one`() {
        val cues = Subtitles.parse(srt(sample))
        val cue = Subtitles.cueAt(cues, 22_000)!!
        assertTrue("the file wraps it across two lines", cue.contains("\n"))
        // What the label does with it is flattened to one row — see cueLabel in PlayerScreen.
        val flat = cue.replace(Regex("\\s+"), " ").trim()
        assertEquals("And the second, which matters more, is what happens at the edges.", flat)
    }

    @Test
    fun `no subtitles means no label, not an empty-looking one`() {
        assertNull(Subtitles.cueAt(emptyList(), 12_000))
    }
}
