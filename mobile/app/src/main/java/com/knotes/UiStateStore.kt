package com.knotes

import android.content.Context

/**
 * Small pieces of UI state that outlive the process: which panels the user left open.
 *
 * These are preferences in everything but name — "I don't write notes from this screen, keep the
 * form out of my way" is a decision, not a scroll position — so they belong on disk rather than in
 * a saved-instance bundle, which a cold start throws away. Deliberately separate from
 * [SettingsStore]: nothing here is worth a row in the Settings screen, and mixing them would mean
 * every panel toggle rewrote the whole settings blob.
 */
object UiStateStore {

    private const val PREFS = "knotes_ui_state"
    private const val K_COMPOSER_EXPANDED = "composer_expanded"
    private const val K_PLAYER_PANEL = "player_panel"
    private const val K_PRAYER_PANEL = "prayer_panel"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the home screen's "New note" form is expanded. Open by default, as it always was. */
    fun composerExpanded(context: Context): Boolean =
        prefs(context).getBoolean(K_COMPOSER_EXPANDED, true)

    fun setComposerExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(K_COMPOSER_EXPANDED, expanded).apply()
    }

    /**
     * Which panel the player screen has open under its transport, or null for "collapsed".
     *
     * Null and absent are different answers and must stay so: absent means nobody has chosen, and
     * the caller opens Bookmarks; null means the panels were deliberately closed. A single
     * `getString(..., null)` would conflate them and quietly re-open a panel the user shut.
     */
    fun playerPanel(context: Context): String? =
        if (prefs(context).contains(K_PLAYER_PANEL)) prefs(context).getString(K_PLAYER_PANEL, null) else ABSENT

    fun setPlayerPanel(context: Context, panel: String?) {
        prefs(context).edit().putString(K_PLAYER_PANEL, panel).apply()
    }

    /**
     * Which settings panel the prayer page has open, or null for "collapsed".
     *
     * Same [ABSENT]/null distinction as [playerPanel]: absent means nobody has chosen and the page
     * opens with everything collapsed, showing only the times; null means the user shut the panel
     * and it must stay shut.
     */
    fun prayerPanel(context: Context): String? =
        if (prefs(context).contains(K_PRAYER_PANEL)) prefs(context).getString(K_PRAYER_PANEL, null) else ABSENT

    fun setPrayerPanel(context: Context, panel: String?) {
        prefs(context).edit().putString(K_PRAYER_PANEL, panel).apply()
    }

    /** Sentinel for "never chosen", distinct from a stored null (= panels closed). */
    const val ABSENT = "\u0000absent"
}
