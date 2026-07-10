package com.knotes.nibras

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * On-disk cache of fetched records, one JSON file per type, in the app's internal storage.
 * This is what makes browsing/reading/bookmarking/highlighting work offline: the UI is fed
 * from the cache immediately and refreshed from the server only when it's reachable.
 */
class NibrasCache(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "nibras_cache").apply {
        if (!exists()) mkdirs()
    }

    private fun file(code: String) = File(dir, "$code.json")

    fun save(code: String, records: List<NibrasRecord>) {
        runCatching {
            val arr = JSONArray()
            records.forEach { arr.put(it.toJson()) }
            file(code).writeText(arr.toString())
        }
    }

    fun load(code: String): List<NibrasRecord> {
        val f = file(code)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { recordFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Cached record count, or null if this type has never been cached. */
    fun count(code: String): Int? {
        val f = file(code)
        if (!f.exists()) return null
        return runCatching { JSONArray(f.readText()).length() }.getOrNull()
    }
}

private fun NibrasRecord.toJson(): JSONObject = JSONObject()
    .put("type", type)
    .put("ecode", ecode)
    .put("id", id)
    .put("summary", summary)
    .put("description", description)
    .put("meta", meta)
    .put("context", context)
    .put("date", date)
    .put("textDate", textDate)
    .put("color", color)
    .put("language", language)
    .put("resourceType", resourceType)
    .put("subtype", subtype)
    .put("startDate", startDate)
    .put("endDate", endDate)
    .put("fulltext", fulltext)
    .put("files", JSONArray(files))

private fun recordFromJson(o: JSONObject): NibrasRecord = NibrasRecord(
    type = o.optString("type"),
    ecode = o.optString("ecode"),
    id = o.optString("id"),
    summary = o.optString("summary"),
    description = o.optString("description"),
    meta = o.optString("meta"),
    context = o.optString("context"),
    date = o.optString("date"),
    textDate = o.optString("textDate"),
    color = o.optString("color"),
    language = o.optString("language"),
    resourceType = o.optString("resourceType"),
    subtype = o.optString("subtype"),
    startDate = o.optString("startDate"),
    endDate = o.optString("endDate"),
    fulltext = o.optString("fulltext"),
    files = o.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
)
