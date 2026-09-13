package com.knotes

import com.knotes.player.Transcripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Finding and reading the desktop player's `.ai.txt` transcription sidecars. */
class TranscriptsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(name: String, text: String = "x"): File =
        File(tmp.root, name).apply { writeText(text) }

    @Test
    fun sidecarsAreFoundByLanguage() {
        val media = write("260701_0624.mp3")
        write("260701_0624.ar.ai.txt", "مرحبا")
        write("260701_0624.en.ai.txt", "hello")
        assertEquals(listOf("ar", "en"), Transcripts.list(media).map { it.lang })
    }

    @Test
    fun anUntaggedSidecarIsFoundToo() {
        val media = write("260701_0624.mp3")
        write("260701_0624.ai.txt", "no language in the name")
        assertEquals(listOf(Transcripts.UNTAGGED), Transcripts.list(media).map { it.lang })
    }

    @Test
    fun otherFilesInTheFolderAreLeftAlone() {
        val media = write("260701_0624.mp3")
        write("260701_0624.ar.ai.txt")
        write("unrelated.en.ai.txt")          // another recording's transcript
        write("260701_0624.srt")              // subtitles, handled elsewhere
        write("260701_0624.txt")              // a plain text file, not a transcript
        write("260701_0624.ar.ai.txt.bak")    // not the suffix
        assertEquals(listOf("ar"), Transcripts.list(media).map { it.lang })
    }

    @Test
    fun aMediaFileWithNoTranscriptsGetsAnEmptyList() {
        assertEquals(emptyList<Any>(), Transcripts.list(write("lonely.mp3")))
    }

    @Test
    fun textIsReadWithoutItsBomOrCarriageReturns() {
        val f = write("a.ar.ai.txt", "﻿في البداية\r\nثم\r\n\r\n")
        assertEquals("في البداية\nثم", Transcripts.read(f))
    }

    @Test
    fun anUnreadableTranscriptCostsItsTextAndNothingMore() {
        assertEquals("", Transcripts.read(File(tmp.root, "missing.ar.ai.txt")))
    }

    @Test
    fun theLanguageTagDecidesDirectionBeforeTheTextDoes() {
        // A machine transcript routinely opens with a mis-heard Latin token. An Arabic transcript
        // is still Arabic when its first word came out wrong, so the tag wins.
        assertTrue(Transcripts.isRtl("ar", "Whisper: مرحبا بكم"))
        assertTrue(Transcripts.isRtl("AR", "مرحبا"))
        assertTrue(!Transcripts.isRtl("en", "مرحبا بكم"))
        assertTrue(!Transcripts.isRtl("en", "hello"))
    }

    @Test
    fun anUntaggedTranscriptIsJudgedByItsText() {
        assertTrue(Transcripts.isRtl(Transcripts.UNTAGGED, "مرحبا بكم في هذا الدرس"))
        assertTrue(!Transcripts.isRtl(Transcripts.UNTAGGED, "welcome to this lesson"))
    }

    @Test
    fun `deleting a sidecar removes it and the list stops offering it`() {
        val dir = createTempDir()
        val media = File(dir, "260701_0624.mp3").apply { writeText("x") }
        val ar = File(dir, "260701_0624.ar.ai.txt").apply { writeText("عربي") }
        File(dir, "260701_0624.en.ai.txt").writeText("english")

        assertTrue(Transcripts.delete(ar))
        assertFalse(ar.exists())
        assertEquals(listOf("en"), Transcripts.list(media).map { it.lang })
    }

    @Test
    fun `delete refuses anything that is not an ai txt sidecar`() {
        // The media file sits in the same folder under a name one character different, and this
        // function is reached from a UI callback — the guard is what stops it ever being a general
        // file delete.
        val dir = createTempDir()
        val media = File(dir, "260701_0624.mp3").apply { writeText("x") }
        val srt = File(dir, "260701_0624.srt").apply { writeText("1") }
        val note = File(dir, "260701_0624.ai.txt.bak").apply { writeText("keep") }

        assertFalse(Transcripts.delete(media))
        assertFalse(Transcripts.delete(srt))
        assertFalse(Transcripts.delete(note))
        assertTrue(media.exists())
        assertTrue(srt.exists())
        assertTrue(note.exists())
    }

    @Test
    fun `deleting one that is already gone reports success rather than failing`() {
        // The panel deletes and then re-reads the folder; a second tap on a stale chip should be a
        // no-op, not an error the user has to make sense of.
        val dir = createTempDir()
        val gone = File(dir, "260701_0624.ar.ai.txt")
        assertTrue(Transcripts.delete(gone))
    }
}
