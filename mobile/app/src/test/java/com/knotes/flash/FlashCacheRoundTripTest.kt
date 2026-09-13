package com.knotes.flash

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline cache writes a card and reads it back with the SAME parser that reads a live server
 * response, so the two representations have to be one representation.
 *
 * This is the failure the round trip exists to catch: `toJson` writing `date`/`text` (the Kotlin
 * property names) where `fromJson` reads `writtenOn`/`passage` (the server's DTO names). Nothing
 * throws — the card comes back with an empty passage, and only when the app is offline. Asserting
 * the field names literally, as well as the round trip, so a rename on either side fails here
 * rather than on a bus.
 */
class FlashCacheRoundTripTest {

    private val card = Flashcard(
        id = 4231,
        noteId = 13448,
        recordRef = "N13448",
        date = "2026-03-14",
        text = "الصبر مفتاح الفرج، وما بعد العسر إلا يسر.",
        rtl = true,
        topic = "r",
    )

    @Test
    fun `a card survives being written and read back`() {
        val back = Flashcard.fromJson(card.toJson())
        assertNotNull(back)
        assertEquals(card, back)
    }

    @Test
    fun `the field names are the server's, not the Kotlin property names`() {
        val o = card.toJson()
        assertTrue("passage, not text", o.has("passage"))
        assertTrue("writtenOn, not date", o.has("writtenOn"))
        assertEquals(card.text, o.getString("passage"))
        assertEquals(card.date, o.getString("writtenOn"))
    }

    @Test
    fun `a whole list round-trips in the array shape the cache stores`() {
        // What FlashCache.saveCards writes, and what FlashCache.cards reads.
        val cards = listOf(card, card.copy(id = 9, recordRef = "N77", text = "Plain english.", rtl = false))
        val arr = JSONArray().apply { cards.forEach { put(it.toJson()) } }
        val back = (0 until arr.length()).mapNotNull { Flashcard.fromJson(arr.getJSONObject(it)) }
        assertEquals(cards, back)
    }

    @Test
    fun `a card with no date does not come back with the string null`() {
        // optString returns the literal "null" for a JSON null — the reason fromJson checks isNull.
        val back = Flashcard.fromJson(card.copy(date = "").toJson())
        assertEquals("", back?.date)
    }
}
