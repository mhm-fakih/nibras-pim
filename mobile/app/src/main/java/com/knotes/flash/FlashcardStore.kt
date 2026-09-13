package com.knotes.flash

import android.content.Context

/**
 * The two things about the flash-card page that are this phone's own business: how big the text is
 * drawn, and which list was last open.
 *
 * Decisions are NOT here. They live in the backend, so the same card is not waiting to be reviewed
 * again in the browser. What is left is genuinely local — a reading preference on this screen, on
 * this device — and deliberately separate from the Resources full-text scale, which is a different
 * setting for a different kind of reading.
 */
class FlashcardStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Text scale for the card body, as a multiplier. Survives a restart. */
    var fontScale: Float
        get() = prefs.getFloat(K_FONT, 1.4f).coerceIn(MIN_FONT, MAX_FONT)
        set(value) {
            prefs.edit().putFloat(K_FONT, value.coerceIn(MIN_FONT, MAX_FONT)).apply()
        }

    /**
     * Text scale for the highlights gallery — its OWN setting.
     *
     * A flash card is one short passage and a resource's highlights are a long list; the size that
     * suits one is rarely the size that suits the other, and sharing a single number would have
     * each page quietly resizing the other.
     */
    var highlightFontScale: Float
        get() = prefs.getFloat(K_HL_FONT, 1.15f).coerceIn(MIN_FONT, MAX_FONT)
        set(value) {
            prefs.edit().putFloat(K_HL_FONT, value.coerceIn(MIN_FONT, MAX_FONT)).apply()
        }

    /** Which list the page was last looking at. */
    var tab: String
        get() = prefs.getString(K_TAB, null) ?: "QUEUE"
        set(value) {
            prefs.edit().putString(K_TAB, value).apply()
        }

    companion object {
        private const val PREFS = "knotes_flashcards"
        private const val K_FONT = "font_scale"
        private const val K_HL_FONT = "highlight_font_scale"
        private const val K_TAB = "tab"

        const val MIN_FONT = 0.9f
        const val MAX_FONT = 2.6f
    }
}
