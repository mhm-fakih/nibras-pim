package com.knotes

import android.content.Context

/**
 * The unsaved text in the home screen's compose box, kept across app restarts.
 *
 * `rememberSaveable` alone is not enough: it survives a rotation or a system-initiated process
 * kill, but not the ordinary case of swiping the app away and coming back — which is exactly when a
 * half-typed note gets lost. This writes the text to disk instead, so the only thing that clears a
 * draft is saving it or deleting it by hand.
 *
 * Scope is deliberately narrow: **new notes only**. While editing an existing note the box holds a
 * copy of that note's saved content, and restoring it later — possibly after the note itself has
 * changed — would resurrect stale text with no indication of where it came from.
 */
object DraftStore {

    private const val PREFS = "knotes_prefs"
    private const val KEY = "note_draft"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): String = prefs(context).getString(KEY, "").orEmpty()

    /**
     * Persists [text], or clears the draft when it is blank. Called as the user types, so it uses
     * `apply()` (async, in-process cached) rather than `commit()`.
     */
    fun save(context: Context, text: String) {
        val editor = prefs(context).edit()
        if (text.isBlank()) editor.remove(KEY) else editor.putString(KEY, text)
        editor.apply()
    }

    fun clear(context: Context) = save(context, "")
}
