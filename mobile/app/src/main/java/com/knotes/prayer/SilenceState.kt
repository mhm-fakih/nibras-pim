package com.knotes.prayer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * Remembers the ringer mode we took away, so it can be given back.
 *
 * On disk rather than in memory because the gap between silencing and restoring is a live alarm:
 * the process can be killed, or the phone rebooted, in the middle of it. A forgotten restore means
 * a phone that stays silent indefinitely and a user who has no idea why — much the worse failure,
 * so the saved mode is written before the ringer is touched and cleared only once it is back.
 */
object SilenceState {

    private const val PREFS = "knotes_prayer_state"
    private const val K_SAVED_MODE = "saved_ringer_mode"
    private const val K_UNTIL = "silence_until"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the OS lets this app change the ringer at all (Do Not Disturb access). */
    fun canControlRinger(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    /** Whether a silence we started is still supposed to be running. */
    fun isSilencing(context: Context): Boolean = prefs(context).contains(K_SAVED_MODE)

    /** When the running silence is due to end, or 0. */
    fun silenceUntil(context: Context): Long = prefs(context).getLong(K_UNTIL, 0L)

    /**
     * Drop the ringer to silent until [untilMillis], remembering what it was.
     *
     * A second call while a silence is already running does NOT overwrite the saved mode — two
     * prayers close together would otherwise save "silent" as the mode to restore and leave the
     * phone that way for good.
     */
    fun begin(context: Context, untilMillis: Long): Boolean {
        if (!canControlRinger(context)) return false
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return runCatching {
            if (!isSilencing(context)) {
                prefs(context).edit().putInt(K_SAVED_MODE, am.ringerMode).apply()
            }
            prefs(context).edit().putLong(K_UNTIL, untilMillis).apply()
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        }.getOrDefault(false)
    }

    /** Put the ringer back as it was. Safe to call when nothing was silenced. */
    fun end(context: Context) {
        val saved = prefs(context).let { if (it.contains(K_SAVED_MODE)) it.getInt(K_SAVED_MODE, -1) else null }
        prefs(context).edit().remove(K_SAVED_MODE).remove(K_UNTIL).apply()
        if (saved == null || saved < 0) return
        if (!canControlRinger(context)) return
        runCatching {
            context.getSystemService(AudioManager::class.java)?.ringerMode = saved
        }
    }

    /**
     * After a reboot, put the ringer back if a silence was interrupted.
     *
     * The alarm that would have ended it is gone — the OS clears alarms across a restart — so
     * without this the phone would keep the silence we imposed until the next one happened to end.
     */
    fun restoreIfInterrupted(context: Context) {
        if (isSilencing(context)) end(context)
    }
}
