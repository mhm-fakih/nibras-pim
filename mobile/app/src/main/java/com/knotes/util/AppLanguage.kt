package com.knotes.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own UI language, independent of the phone's: English, Arabic or French.
 *
 * <h4>How it is applied</h4>
 * On Android 13+ through the platform's per-app language ([LocaleManager]) — the system persists it,
 * restarts the activities, and every context in the process (receivers, widgets, notifications)
 * resolves strings in it. It also appears under the system's "App languages" page, via
 * `res/xml/locales_config.xml`. Below 13 there is no such API, so the choice is kept in prefs and
 * applied by [wrap] (each activity's base context) and [applyToProcess] (the shared application
 * resources, which receivers and widget factories read through).
 *
 * <h4>Why Arabic is `ar-u-nu-latn`</h4>
 * Plain `ar` makes `String.format` and friends write Arabic-Indic digits (٢:١٩). Plenty of this app
 * formats numbers into data — `.tmk` timestamps, file names, server payloads — and a digit shape
 * change there corrupts files other tools read. Latin digits are also what most Arabic speakers in
 * the region use day to day. The `nu-latn` extension keeps the Arabic UI and the RTL layout and
 * changes only the digits.
 *
 * <h4>RTL</h4>
 * Nothing to do per screen: the manifest declares `supportsRtl`, so an Arabic configuration gives
 * Compose a right-to-left [androidx.compose.ui.unit.LayoutDirection] and the whole UI mirrors.
 */
object AppLanguage {

    /** The selectable languages. [label] is each language's own name, never translated. */
    enum class Lang(val tag: String, val label: String) {
        ENGLISH("en", "English"),
        ARABIC("ar-u-nu-latn", "عربي"),
        FRENCH("fr", "Français"),
    }

    /** The language chosen in the app, or null when none was chosen (the phone's language applies). */
    fun current(context: Context): Lang? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Read back from the platform, so a change made in the system's App-languages page
            // shows here too rather than disagreeing with what is on screen.
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            stored(context)
        }
        return tag?.let(::fromTag)
    }

    /** Switch the UI to [lang] now. The activity is recreated so every screen redraws in it. */
    fun set(activity: Activity, lang: Lang) {
        prefs(activity).edit().putString(KEY, lang.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The platform recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(lang.tag)
        } else {
            applyToProcess(activity.applicationContext)
            activity.recreate()
        }
    }

    /** An activity's base context in the chosen language — for `attachBaseContext` below Android 13. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * Below Android 13, put the chosen locale on the process's shared resources, which broadcast
     * receivers, widget factories and services resolve strings through. Called from the
     * Application at start and on every change.
     */
    @Suppress("DEPRECATION")
    fun applyToProcess(app: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val tag = stored(app) ?: return
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        res.updateConfiguration(config, res.displayMetrics)
    }

    private fun fromTag(tag: String): Lang? {
        val language = Locale.forLanguageTag(tag).language
        return Lang.entries.firstOrNull { Locale.forLanguageTag(it.tag).language == language }
    }

    private fun stored(context: Context): String? = prefs(context).getString(KEY, null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("knotes_language", Context.MODE_PRIVATE)

    private const val KEY = "tag"
}
