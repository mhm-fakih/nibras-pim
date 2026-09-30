package com.knotes.ui

import android.content.Context

/**
 * What was typed into a widget's capture bar but not yet saved.
 *
 * The bar is a one-line input over the home screen, and every way out of it that is not "Done"
 * used to take the text with it: a tap on the scrim, Back, Home, or the system reclaiming an
 * activity declared `noHistory`. Typing a paragraph into the journal bar and brushing the wallpaper
 * lost the paragraph, with nothing anywhere to recover it from.
 *
 * Blocking the stray tap is the first defence and it is not enough on its own — it only covers the
 * one gesture. This covers the rest: the draft is written on every keystroke and read back when the
 * bar reopens, so the only thing that clears it is a note actually being saved.
 *
 * Kept per capture type, because a half-written task and a half-written journal entry are different
 * thoughts and must not overwrite one another.
 */
object QuickDraft {

    fun get(context: Context, type: String): String =
        prefs(context).getString(key(type), "").orEmpty()

    fun put(context: Context, type: String, text: String) {
        // apply(), not commit(): this runs on every keystroke and must never block the field.
        prefs(context).edit().putString(key(type), text).apply()
    }

    /**
     * Called once the note is on disk — the draft has become a record and is no longer pending.
     *
     * **commit(), not apply().** This one runs as the activity finishes, and the activity is
     * declared `noHistory`, so the process may be reclaimed before an asynchronous write is
     * flushed. A draft that outlives its own save is not a cosmetic bug: the bar reopens
     * pre-filled, Done saves the same text again, and the note is duplicated. It happened —
     * three identical tasks and two identical journal entries on 2026-09-28. One synchronous
     * write of one small file, at the one moment it has to be right, is the correct trade.
     */
    fun clear(context: Context, type: String) {
        @Suppress("ApplySharedPref")
        prefs(context).edit().remove(key(type)).commit()
    }

    private fun key(type: String) = "draft_" + type.uppercase()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("knotes_quick_draft", Context.MODE_PRIVATE)
}
