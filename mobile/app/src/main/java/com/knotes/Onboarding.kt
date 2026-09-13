package com.knotes

import android.content.Context
import java.io.File

/**
 * State and rules for the first-run wizard.
 *
 * <h4>The one thing this must not get wrong</h4>
 * A wizard that greets someone who has been using the app for a year is worse than no wizard: it
 * offers to set up folders that are already full of their notes, and its defaults are ready to
 * overwrite what they chose. "Has the wizard run?" is therefore NOT the question — a flag that has
 * never been written looks identical on a fresh install and on an upgrade from a version that
 * predates the flag. The question is "has this app ever been configured?", which
 * [SettingsStore.isConfigured] answers by looking for settings that only exist once someone has
 * saved them. See [shouldShowWelcome].
 */
object Onboarding {

    private const val PREFS = "knotes_ui_state"
    private const val K_WELCOME_DONE = "welcome_done"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether to open the wizard.
     *
     * True only when the wizard has not been completed AND nothing has ever been configured. An
     * upgrade lands on the second condition and is marked done without ever being shown one — see
     * the class note.
     */
    fun shouldShowWelcome(context: Context): Boolean {
        if (prefs(context).getBoolean(K_WELCOME_DONE, false)) return false
        if (SettingsStore(context).isConfigured()) {
            markWelcomeDone(context)   // an existing install; it has nothing to be onboarded about
            return false
        }
        return true
    }

    fun markWelcomeDone(context: Context) {
        prefs(context).edit().putBoolean(K_WELCOME_DONE, true).apply()
    }

    /** Re-opens the wizard from Settings. The tour is also a reference, not only a first run. */
    fun replayWelcome(context: Context) {
        prefs(context).edit().putBoolean(K_WELCOME_DONE, false).apply()
    }
}

/**
 * What is actually at a configured path right now.
 *
 * The wizard shows this beside every folder field, because the single most common way to
 * mis-configure this app is a path that looks right and does not exist — a folder on a card that
 * isn't mounted, a name with a typo, `/sdcard/notes` where the folder is `/sdcard/Notes`. The
 * symptom is an empty app with no error, which is indistinguishable from "no notes yet".
 */
enum class FolderState {
    /** Nothing typed. */
    BLANK,

    /** The path does not exist. Offer to create it — that is the usual fix. */
    MISSING,

    /** The path exists but is a file. Creating is not the fix; the path is wrong. */
    NOT_A_FOLDER,

    /** A folder, with nothing in it. Normal for a fresh setup. */
    EMPTY,

    /** A folder with contents — the strongest sign the path is the one that was meant. */
    HAS_FILES,
}

/** Reads what is at [path]. Touches the disk, so call it off the main thread where it matters. */
fun folderState(path: String): FolderState {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return FolderState.BLANK
    val f = File(trimmed)
    if (!f.exists()) return FolderState.MISSING
    if (!f.isDirectory) return FolderState.NOT_A_FOLDER
    // listFiles() is null on a directory that can't be read — unreadable is not the same as empty,
    // but from the wizard's point of view both mean "nothing to show", and EMPTY says that without
    // claiming more than is known.
    return if ((f.listFiles()?.size ?: 0) > 0) FolderState.HAS_FILES else FolderState.EMPTY
}

/**
 * Creates [path] and its parents.
 *
 * Returns true when the folder exists afterwards — including when it already did, since the
 * caller's question is "is it there now?", not "did I make it". Never throws: a failure here is a
 * permissions or storage problem to be reported in the wizard, not a crash on first run.
 */
fun createFolder(path: String): Boolean {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return false
    val f = File(trimmed)
    if (f.isDirectory) return true
    return runCatching { f.mkdirs() }.getOrDefault(false) && f.isDirectory
}
