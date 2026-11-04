package com.knotes.player

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What the AI_QUEUE mode lists: unbookmarked audio in the AI folder's companion folders, newest first. */
class AiNarrationsTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun listsUnbookmarkedAudioInCompanionFoldersNewestFirstWithTheNoteTitle() {
        val ai = tmp.newFolder("knotes-ai")
        fun note(stem: String, title: String) = File(ai, "$stem.md").writeText("# $title\n\nbody")
        fun audio(path: String) = File(ai, path).apply { parentFile.mkdirs(); writeText("x") }

        note("2027_01_10-090000~A~p2~X", "Older answer")
        note("2027_01_12-090000~A~p2~X", "Newer answer")
        audio("2027_01_10-090000~A~p2~X.d/2027_01_10-090500_narration.mp3")
        audio("2027_01_12-090000~A~p2~X.d/2027_01_12-090500_narration.mp3")
        val marked = audio("2027_01_11-090000~A~p2~X.d/2027_01_11-090500_narration.mp3")
        File(marked.parentFile, marked.nameWithoutExtension + ".tmk").writeText("[0:10] mark")  // bookmarked
        audio("2027_01_13-090000_loose.mp3")                                                   // not in a companion folder
        audio("2027_01_12-090000~A~p2~X.d/clip.mp4")                                          // video

        val list = AiNarrations.list(ai)
        assertEquals(listOf("Newer answer", "Older answer"), list.map { it.title })
        assertEquals(listOf("2027_01_12-090500_narration.mp3", "2027_01_10-090500_narration.mp3"), list.map { it.file.name })
    }

    @Test
    fun aMissingFolderListsNothing() {
        assertEquals(emptyList<AiNarration>(), AiNarrations.list(File(tmp.root, "nope")))
    }
}
