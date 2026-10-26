package com.knotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings export / import as one JSON file, so a reinstall or a second phone doesn't mean redoing
 * every tab by hand.
 *
 * Copies the raw SharedPreferences entries rather than the parsed [AppSettings]: the settings are
 * spread over several stores (app, prayer, language), and an entry written as-is reads back exactly
 * as the store's own load() would read it. Only the listed stores and keys travel; drafts, player
 * positions, outboxes and other data that happen to share a prefs file stay behind, and so does the
 * server password (see [SettingsStore.BACKUP_KEYS]).
 */
object SettingsBackup {

    private const val FORMAT = "knotes-settings"
    private const val VERSION = 1

    /** Prefs file → the keys it contributes (null = all of them: the file holds settings only). */
    private val STORES: Map<String, Set<String>?> = mapOf(
        "knotes_prefs" to SettingsStore.BACKUP_KEYS,
        "knotes_prayer" to null,
        "knotes_language" to null,
    )

    fun suggestedFileName(): String =
        "knotes-settings-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json"

    fun export(context: Context, uri: Uri): Result<Int> = runCatching {
        var count = 0
        val stores = JSONObject()
        for ((name, keys) in STORES) {
            val entries = JSONObject()
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            for ((key, value) in all.toSortedMap()) {
                if (keys != null && key !in keys) continue
                val (type, json) = when (value) {
                    is String -> "string" to value
                    is Boolean -> "boolean" to value
                    is Int -> "int" to value
                    is Long -> "long" to value
                    is Float -> "float" to value.toDouble()
                    is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() }.sorted())
                    else -> continue
                }
                entries.put(key, JSONObject().put("type", type).put("value", json))
                count++
            }
            stores.put(name, entries)
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exported", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
            .put("stores", stores)
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(root.toString(2).toByteArray(Charsets.UTF_8))
        }
        count
    }

    /**
     * Writes the file's entries over the current ones. Keys the file doesn't have are left alone,
     * so a backup from an older version never wipes a setting it didn't know about. The caller
     * restarts the app afterwards: several screens and services hold their own store instance,
     * read once.
     */
    fun import(context: Context, uri: Uri): Result<Int> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "not a knotes settings file" }
        val stores = root.getJSONObject("stores")
        var count = 0
        for ((name, keys) in STORES) {
            val entries = stores.optJSONObject(name) ?: continue
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            for (key in entries.keys()) {
                if (keys != null && key !in keys) continue
                val e = entries.getJSONObject(key)
                when (e.optString("type")) {
                    "string" -> edit.putString(key, e.getString("value"))
                    "boolean" -> edit.putBoolean(key, e.getBoolean("value"))
                    "int" -> edit.putInt(key, e.getInt("value"))
                    "long" -> edit.putLong(key, e.getLong("value"))
                    "float" -> edit.putFloat(key, e.getDouble("value").toFloat())
                    "stringSet" -> {
                        val arr = e.getJSONArray("value")
                        edit.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                    else -> continue
                }
                count++
            }
            // commit, not apply: the process is about to be restarted.
            edit.commit()
        }
        count
    }

    /** Relaunch the app from scratch so every store re-reads the imported values. */
    fun restartApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
