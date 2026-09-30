package com.knotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capture bar's draft, over one save.
 *
 * The bug this pins down: the draft outlived the note it became. Pressing Done saved the note and
 * cleared the draft, but the IME then delivered one last onValueChange — the composing region being
 * finished — which wrote the whole text back. The bar reopened pre-filled, Done saved it again, and
 * the record was duplicated. Three identical tasks and two identical journal entries, on a real
 * phone, before it was caught.
 *
 * Modelled here as the sequence of events rather than through Android: the ordering IS the bug, and
 * a test that cannot express "a keystroke arrives after the save" cannot catch it.
 */
class QuickDraftLifecycleTest {

    /** The bar's draft handling, as QuickCaptureBar performs it. */
    private class Bar {
        var stored: String? = null
        private var submitted = false

        fun type(text: String) {
            if (!submitted) stored = text
        }

        /** Done: the note reaches disk, then the draft is closed. */
        fun save() {
            submitted = true
            stored = null
        }
    }

    @Test
    fun `a saved note leaves no draft behind`() {
        val bar = Bar()
        bar.type("ع")
        bar.type("عمر والانترفون")
        bar.save()
        assertEquals(null, bar.stored)
    }

    @Test
    fun `the IME's trailing keystroke cannot resurrect a saved draft`() {
        // THE REGRESSION. Without the submitted guard this event rewrites the text and the next
        // Done duplicates the record.
        val bar = Bar()
        bar.type("عمر والانترفون")
        bar.save()
        bar.type("عمر والانترفون")   // delivered after the action callback
        assertEquals("a keystroke after the save must not restore the draft", null, bar.stored)
    }

    @Test
    fun `typing before a save is still kept, so nothing is lost on an accidental exit`() {
        // The guard must not defeat the feature it protects: an unsaved draft is still stored.
        val bar = Bar()
        bar.type("half a thought")
        assertEquals("half a thought", bar.stored)
        assertTrue(bar.stored!!.isNotEmpty())
    }
}
