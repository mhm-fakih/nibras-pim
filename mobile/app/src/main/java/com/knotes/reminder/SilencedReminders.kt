package com.knotes.reminder

import android.content.Context

/**
 * Events whose *remaining* reminders the user has switched off — "I'm already at the venue, stop
 * telling me about it".
 *
 * <h3>Silenced per occurrence, not per event</h3>
 * The stored token is the event's key **and its start time**. A plan's key alone is the same for
 * every occurrence of a repeating plan, so silencing by key would quietly kill next week's reminder
 * too — the user asked to stop being reminded about the meeting they are currently sitting in, not
 * about that meeting forever.
 *
 * <h3>Which is also why it can be pruned</h3>
 * Because a token names one occurrence, it stops being meaningful the moment that occurrence has
 * started: no reminder for it can ever fire again. [prune] drops those, so the set stays the size
 * of "events silenced today" rather than growing for the life of the install.
 *
 * Silencing has to be recorded rather than only cancelling the pending alarms, because
 * [ReminderScheduler.scheduleAll] cancels and re-arms *everything* on every refresh — a cancelled
 * alarm would simply come back on the next sync.
 */
object SilencedReminders {

    private const val PREFS = "knotes_silenced_reminders"
    private const val K_TOKENS = "tokens"

    /** Kept a little past the start so a "now" reminder can't be re-armed by a refresh mid-event. */
    private const val GRACE_MS = 12 * 60 * 60 * 1000L

    private fun token(key: String, startMillis: Long) = "$key@$startMillis"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun tokens(context: Context): MutableSet<String> =
        prefs(context).getStringSet(K_TOKENS, emptySet()).orEmpty().toMutableSet()

    /** Stop the remaining reminders for this occurrence. */
    fun silence(context: Context, key: String, startMillis: Long) {
        val updated = tokens(context).apply { add(token(key, startMillis)) }
        prefs(context).edit().putStringSet(K_TOKENS, updated).apply()
    }

    fun isSilenced(context: Context, key: String, startMillis: Long): Boolean =
        token(key, startMillis) in tokens(context)

    /** Let an occurrence's reminders fire again (nothing in the UI calls this yet). */
    fun unsilence(context: Context, key: String, startMillis: Long) {
        val updated = tokens(context).apply { remove(token(key, startMillis)) }
        prefs(context).edit().putStringSet(K_TOKENS, updated).apply()
    }

    /** Drop tokens for occurrences that have already started (plus [GRACE_MS]). */
    fun prune(context: Context, now: Long = System.currentTimeMillis()) {
        val kept = tokens(context).filter { t ->
            val start = t.substringAfterLast('@', "").toLongOrNull() ?: return@filter false
            start + GRACE_MS > now
        }.toSet()
        prefs(context).edit().putStringSet(K_TOKENS, kept).apply()
    }
}
