package com.knotes.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The description typed when collecting heads the note — and so titles it on every card. */
class CollectDescriptionTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun collect(description: String): String {
        val src = tmp.newFolder()
        val dest = tmp.newFolder()
        val shot = File(src, "shot.jpg").apply { writeText("x") }
        val found = listOf(FoundFile(shot, "DCIM", "2026_10_18-120000_shot.jpg", "name"))
        val (folder, results) = FileCollector.collect(
            found, dest.path, "yyyy_MM_dd-HHmmss", "N", 2, "A", description,
        )
        assertTrue(results.single().ok)
        val note = File(folder.parentFile, folder.name.removeSuffix(".d") + ".md")
        return note.readText()
    }

    @Test
    fun `the description comes first, then the file list`() {
        val body = collect("  Receipts from the trip\nkept for the claim  ")
        assertTrue(body.startsWith("Receipts from the trip\nkept for the claim\n\nCollected 1 file\n\n"))
        assertTrue(body.contains("- 2026_10_18-120000_shot.jpg"))
    }

    @Test
    fun `no description collects exactly as before`() {
        assertEquals("Collected 1 file\n\n- 2026_10_18-120000_shot.jpg\n", collect("   "))
    }
}
