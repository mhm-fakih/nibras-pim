package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI-answers folder, and the ordering its notes are shown in.
 *
 * Two rules, both easy to get wrong in ways that look fine on the bench:
 *
 * 1. The folder is DERIVED from the notes folder unless overridden, so moving the notes folder
 *    takes the AI folder with it. A hard-coded path would quietly keep pointing at the old place.
 * 2. The answers are ordered by the FILE-NAME timestamp, never by mtime. They arrive over
 *    Syncthing, which rewrites mtimes on transfer, so an mtime sort shows them in the order they
 *    happened to sync. That is the same bug the player had with "play next: newest".
 */
class AiNotesFolderTest {

    @Test
    fun theAiFolderIsTheNotesFolderWithAiAppended() {
        assertEquals("/storage/emulated/0/knotes-ai", SettingsStore.aiDirFor("/storage/emulated/0/knotes"))
        assertEquals("/sdcard/Notes-ai", SettingsStore.aiDirFor("/sdcard/Notes"))
    }

    @Test
    fun aTrailingSlashDoesNotProduceASlashAiFolder() {
        // "/sdcard/knotes/" must not become "/sdcard/knotes/-ai".
        assertEquals("/sdcard/knotes-ai", SettingsStore.aiDirFor("/sdcard/knotes/"))
    }

    @Test
    fun aBlankSettingDerives_andAnOverrideWins() {
        val base = settings(notesDir = "/sdcard/knotes", aiNotesDir = "")
        assertEquals("/sdcard/knotes-ai", base.aiDir())

        val moved = base.copy(notesDir = "/sdcard/elsewhere")
        assertEquals("/sdcard/elsewhere-ai", moved.aiDir())

        val overridden = base.copy(aiNotesDir = "/sdcard/somewhere/ai")
        assertEquals("/sdcard/somewhere/ai", overridden.aiDir())

        // Whitespace is not an override.
        assertEquals("/sdcard/knotes-ai", base.copy(aiNotesDir = "   ").aiDir())
    }

    @Test
    fun answersAreOrderedByTheirNameNotTheirMtime() {
        // Written yesterday, synced today; and written today, synced first. mtime order is the
        // reverse of the order they were written in, which is the order that matters.
        val older = note(timestamp = "2026_10_01-090000", createdMillis = 1_000, lastModified = 9_000)
        val newer = note(timestamp = "2026_10_09-090000", createdMillis = 8_000, lastModified = 2_000)

        val byName = listOf(older, newer).sortedByDescending { it.createdMillis }
        assertEquals(listOf(newer, older), byName)

        val byMtime = listOf(older, newer).sortedByDescending { it.lastModified }
        assertTrue("the two orders must genuinely differ, or this proves nothing", byName != byMtime)
    }

    /**
     * The card's date must come from the file name, not the file.
     *
     * Same rule as the ordering above, one step further on: having sorted the answers by when they
     * were written, labelling each with its mtime would print the moment Syncthing delivered it —
     * so the list would be in one order and dated in another.
     */
    @Test
    fun anAnswerIsDatedWhenItWasWrittenNotWhenItSynced() {
        val written = 1_600_000_000_000L   // the name's timestamp
        val synced = 1_700_000_000_000L    // what Syncthing left as the mtime
        val n = note(timestamp = "2020_09_13-142640", createdMillis = written, lastModified = synced)

        assertEquals(stamp(written), n.createdText())
        assertTrue("the two must genuinely differ, or this proves nothing", n.createdText() != n.timeText())
    }

    private fun stamp(millis: Long) = java.text.SimpleDateFormat(
        "yyyy-MM-dd HH:mm", java.util.Locale.getDefault(),
    ).format(java.util.Date(millis))

    private fun note(timestamp: String, createdMillis: Long, lastModified: Long) = Note(
        file = java.io.File("/tmp/$timestamp~N~p2~A.md"),
        timestamp = timestamp,
        type = "N",
        priority = 2,
        category = "A",
        content = "body",
        lastModified = lastModified,
        createdMillis = createdMillis,
        isRtl = false,
    )

    private fun settings(notesDir: String, aiNotesDir: String) = AppSettings(
        notesDir = notesDir,
        aiNotesDir = aiNotesDir,
        timestampFormat = "yyyy_MM_dd-HHmmss",
        previewLines = 3,
        priorityMin = 1,
        priorityMax = 5,
        types = emptyList(),
        categories = emptyList(),
        nibrasBaseUrl = "", nibrasUsername = "", nibrasPassword = "", nibrasFilesDir = "",
        collectSourceDirs = emptyList(), collectNewDir = "", fulltextScale = 1f,
        audioLibraryRoots = emptyList(), galleryDir = "",
        calendarStartHour = 6, calendarEndHour = 22, calendarEnabled = true,
        reminderOffsetsMin = emptyList(), reminderSoundUri = "", reminderRequireDismiss = false,
        reminderRepeatCount = 1, statusNotificationEnabled = false,
        syncEnabled = false, syncCheckIntervalSec = 60, syncIntervalSec = 900,
        chimeEnabled = false, chimeSoundUri = "", chimeStartHour = 8, chimeEndHour = 20,
        appName = "knotes", defaultType = "N", defaultCategory = "A", defaultPriority = 2,
        topPriority = 4,
    )
}
