package com.knotes.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSnippetsTest {

    @Test
    fun blankQueryFindsNothing() {
        assertEquals(0, SearchSnippets.find("anything", "  ").total)
    }

    @Test
    fun caseInsensitiveWithTheTextsOwnCase() {
        val r = SearchSnippets.find("Some text about Patience here.", "patience")
        assertEquals(1, r.total)
        assertEquals(SearchSnippets.Snippet("Some text about ", "Patience", " here."), r.snippets[0])
    }

    @Test
    fun cutsAtWordsAndMarksTheCuts() {
        val text = "word ".repeat(40) + "needle" + " tail".repeat(40)
        val s = SearchSnippets.find(text, "needle", radius = 20).snippets.single()
        assertTrue(s.before.startsWith("…"))
        assertTrue(s.after.endsWith("…"))
        assertTrue(!s.before.removePrefix("…").startsWith("ord"))
    }

    @Test
    fun highlightMarkersAreNotPartOfTheText() {
        val s = SearchSnippets.find("before ==the key== after", "the key").snippets.single()
        assertEquals("before ", s.before)
        assertEquals(" after", s.after)
    }

    @Test
    fun linesFoldAndNearbyMatchesShareAnExcerpt() {
        val r = SearchSnippets.find("a cat\nand a cat\n\nthen a cat", "cat")
        assertEquals(3, r.total)
        assertEquals(1, r.snippets.size)
        assertTrue(!r.snippets[0].after.contains("\n"))
    }

    @Test
    fun limitCapsTheExcerptsNotTheCount() {
        val text = (1..10).joinToString(" ") { "x".repeat(200) + " hit" }
        val r = SearchSnippets.find(text, "hit", limit = 3)
        assertEquals(10, r.total)
        assertEquals(3, r.snippets.size)
    }

    @Test
    fun arabic() {
        val r = SearchSnippets.find("قال الإمام: الصبر مفتاح الفرج", "الصبر")
        assertEquals("الصبر", r.snippets.single().match)
    }
}
