package com.knotes.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extension → icon mapping behind the file rows on a note card.
 *
 * Worth testing despite being a lookup table, for two reasons that are not about the table: the
 * extension is parsed by [FileNames.ext] (which strips bidi marks — the Arabic-filename case that
 * has broken every other extension test in this app), and [FileKind.OTHER] is a real answer that
 * must survive, not a hole to be plugged with a guess.
 */
class FileKindTest {

    @Test
    fun mapsEachFamilyToItsKind() {
        assertEquals(FileKind.AUDIO, fileKind("lecture.mp3"))
        assertEquals(FileKind.AUDIO, fileKind("book.m4b"))
        assertEquals(FileKind.VIDEO, fileKind("clip.mkv"))
        assertEquals(FileKind.IMAGE, fileKind("scan.HEIC"))
        assertEquals(FileKind.PDF, fileKind("paper.pdf"))
        assertEquals(FileKind.DOC, fileKind("draft.docx"))
        assertEquals(FileKind.SHEET, fileKind("budget.csv"))
        assertEquals(FileKind.SLIDES, fileKind("talk.pptx"))
        assertEquals(FileKind.TEXT, fileKind("notes.md"))
        assertEquals(FileKind.ARCHIVE, fileKind("bundle.zip"))
        assertEquals(FileKind.CODE, fileKind("Main.kt"))
    }

    /** Case comes off the disk however the producer wrote it; the icon must not depend on it. */
    @Test
    fun theExtensionIsCaseInsensitive() {
        assertEquals(FileKind.AUDIO, fileKind("RECORDING.MP3"))
        assertEquals(FileKind.PDF, fileKind("Paper.Pdf"))
    }

    /**
     * The case this app keeps meeting: an Arabic name copied out of a right-to-left context carries
     * an invisible U+200F after the extension. Naive parsing sees "mp3‏", matches nothing, and
     * the recording gets a blank-page icon.
     */
    @Test
    fun aTrailingBidiMarkDoesNotHideTheExtension() {
        assertEquals(FileKind.AUDIO, fileKind("تسجيل.mp3‏"))
    }

    /** An unknown or absent extension is answered honestly rather than guessed at. */
    @Test
    fun anythingUnrecognisedIsOther() {
        assertEquals(FileKind.OTHER, fileKind("dump.qqq"))
        assertEquals(FileKind.OTHER, fileKind("README"))
        assertEquals(FileKind.OTHER, fileKind(""))
    }

    /**
     * A dotted stem is not an extension. `notes.2026.backup` ends in "backup", not "2026" — the
     * naive `split('.')[1]` reading would call this a spreadsheet-ish something.
     */
    @Test
    fun onlyTheLastDotCounts() {
        assertEquals(FileKind.AUDIO, fileKind("2026.08.24-meeting.mp3"))
        assertEquals(FileKind.OTHER, fileKind("notes.2026.backup"))
    }
}
