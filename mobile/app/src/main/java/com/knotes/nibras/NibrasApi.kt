package com.knotes.nibras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.knotes.Tag
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Minimal client for the Nibras desktop server (Grails `SyncController`).
 *
 * The server uses a self-signed certificate on the LAN (the Ionic app used
 * `setServerTrustMode('nocheck')`), so this client is intentionally configured to
 * trust all certificates — acceptable for a personal LAN tool; do not reuse it for
 * public-internet traffic.
 *
 * Writes use the server's append actions: `mobilePush` (-> record.notes) and
 * `mobilePushHighlight` (-> record.highlights). Both take
 * `{"data":[{"ecode","id","textToAppend"}, ...]}` and append `textToAppend`.
 */
class NibrasApi {

    private val client: OkHttpClient = buildTrustAllClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Log in with the process-wide credentials (set via {@link #configure}). Returns true on success. */
    private fun doLogin(): Boolean {
        val c = creds ?: return false
        val payload = JSONObject().put("username", c.username).put("password", c.password)
        val request = Request.Builder()
            .url("${c.base}$PATH_LOGIN")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                token = JSONObject(resp.body?.string().orEmpty()).optString("token").ifBlank { null }
                token != null
            }
        }.getOrDefault(false)
    }

    suspend fun heartbeat(base: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(get("${base.trimEnd('/')}$PATH_HEARTBEAT")).optString("result") == "ok"
        }.getOrDefault(false)
    }

    suspend fun fetchType(base: String, code: String, username: String): List<NibrasRecord> =
        withContext(Dispatchers.IO) {
            val id = URLEncoder.encode(username, "UTF-8")
            val ecode = URLEncoder.encode(code, "UTF-8")
            // Type code goes as a path suffix (/sync/exportJsonT), matching the server's action-name
            // style. The backend also accepts ?code=, but the suffix form is the canonical one.
            val url = "${base.trimEnd('/')}$PATH_EXPORT$ecode?id=$id"
            val data: JSONArray = JSONObject(get(url)).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map { i -> data.getJSONObject(i).toRecord(code) }
        }

    /** App modules as (code, label) — the capture "types". Enabled+coded only, in sortOrder. */
    suspend fun fetchModules(base: String): List<Tag> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("${base.trimEnd('/')}$PATH_MODULES"))
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("code").isNotBlank() && (!it.has("enabled") || it.optBoolean("enabled", true)) }
            .sortedBy { it.optInt("sortOrder", 0) }
            .map { Tag(it.optString("code").trim(),
                it.optString("label").ifBlank { it.optString("moduleKey") }.ifBlank { it.optString("code") }) }
    }

    /** Bookmarked departments as (code, name) — the capture "categories". The department name is its
     *  summary column (the backend entity has no separate name column). */
    suspend fun fetchBookmarkedDepartments(base: String): List<Tag> = withContext(Dispatchers.IO) {
        val arr = JSONObject(get("${base.trimEnd('/')}$PATH_DEPARTMENTS")).optJSONArray("content") ?: JSONArray()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("code").isNotBlank() }
            .map { Tag(it.optString("code").trim(),
                it.optString("name").ifBlank { it.optString("summary") }.ifBlank { it.optString("code") }) }
    }

    /** Appends each item's text to the records' notes field. Returns true on HTTP success. */
    suspend fun pushNotes(base: String, items: List<PendingPush>): Boolean =
        push("${base.trimEnd('/')}$PATH_MOBILE_PUSH", items)

    /** Appends each item's text to the records' highlights field (Resources). */
    suspend fun pushHighlights(base: String, items: List<PendingPush>): Boolean =
        push("${base.trimEnd('/')}$PATH_MOBILE_PUSH_HIGHLIGHT", items)

    /** Marks each record complete on the server (same payload shape as the other pushes). */
    suspend fun pushCompletes(base: String, items: List<PendingPush>): Boolean =
        push("${base.trimEnd('/')}$PATH_MOBILE_PUSH_COMPLETE", items)

    /** Creates calendar (Journal/Plans) entries. Envelope mirrors the other pushes but carries the
     *  calendar fields: {data:[{ecode, id, username, startDate, endDate, summary}]}. */
    suspend fun pushCalendar(base: String, username: String, items: List<CalendarEntry>): Boolean =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext true
            val data = JSONArray()
            items.forEach {
                data.put(
                    JSONObject()
                        .put("ecode", it.type)
                        .put("id", it.id)
                        .put("username", username)
                        .put("startDate", it.startDate)
                        .put("endDate", it.endDate)
                        .put("summary", it.summary),
                )
            }
            postAuthed("${base.trimEnd('/')}$PATH_MOBILE_PUSH_CALENDAR", JSONObject().put("data", data))
        }

    private suspend fun push(url: String, items: List<PendingPush>): Boolean =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext true
            val data = JSONArray()
            items.forEach {
                data.put(
                    JSONObject()
                        .put("ecode", it.ecode)
                        .put("id", it.id)
                        .put("textToAppend", it.text),
                )
            }
            postAuthed(url, JSONObject().put("data", data))
        }

    /** POST a JSON payload with the bearer token, re-logging-in and retrying once on a 401. */
    private fun postAuthed(url: String, payload: JSONObject): Boolean {
        fun build(): Request {
            val b = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody(jsonMedia))
            token?.let { b.header("Authorization", "Bearer $it") }
            return b.build()
        }
        var resp = client.newCall(build()).execute()
        if (resp.code == 401 && doLogin()) {
            resp.close()
            resp = client.newCall(build()).execute()
        }
        return resp.use { it.isSuccessful }
    }

    /** GET with the bearer token, re-logging-in and retrying once on a 401. */
    private fun get(url: String): String {
        fun build(): Request {
            val b = Request.Builder().url(url).get()
            token?.let { b.header("Authorization", "Bearer $it") }
            return b.build()
        }
        var resp = client.newCall(build()).execute()
        if (resp.code == 401 && doLogin()) {
            resp.close()
            resp = client.newCall(build()).execute()
        }
        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return body
        }
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun JSONObject.toRecord(code: String): NibrasRecord {
        val type = str("type").ifBlank { code }
        return NibrasRecord(
            type = type,
            ecode = str("ecode").ifBlank { type },
            id = str("id"),
            summary = str("summary"),
            description = str("description"),
            meta = str("meta"),
            context = str("context"),
            date = str("date"),
            textDate = str("textDate"),
            color = str("color"),
            language = str("language"),
            resourceType = str("resourceType"),
            subtype = str("subtype"),
            startDate = str("startDate"),
            endDate = str("endDate"),
            completedOn = str("completedOn"),
            fulltext = str("fullText"),
            files = parseFilesList(str("filesList")),
        )
    }

    private fun buildTrustAllClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private data class Creds(val base: String, val username: String, val password: String)

    companion object {
        // Bearer token + the credentials used to obtain it, shared across every NibrasApi instance
        // (view model, calendar, reminders) so a single login covers all of them and the token can be
        // transparently refreshed on a 401. Set once from settings via configure().
        @Volatile private var token: String? = null
        @Volatile private var creds: Creds? = null

        /** Set the process-wide login credentials (call whenever the server/account settings change). */
        fun configure(base: String, username: String, password: String) {
            val next = Creds(base.trimEnd('/'), username, password)
            if (next != creds) {
                creds = next
                token = null   // force a fresh login for the new credentials
            }
        }

        const val PATH_HEARTBEAT = "/page/heartbeatJson"
        const val PATH_LOGIN = "/api/auth/login"
        const val PATH_EXPORT = "/sync/exportJson"
        const val PATH_MOBILE_PUSH = "/sync/mobilePush"
        const val PATH_MOBILE_PUSH_HIGHLIGHT = "/sync/mobilePushHighlight"
        const val PATH_MOBILE_PUSH_COMPLETE = "/sync/mobilePushComplete"
        const val PATH_MOBILE_PUSH_CALENDAR = "/sync/mobilePushCalendar"
        const val PATH_MODULES = "/api/modules"
        const val PATH_DEPARTMENTS = "/api/departments?bookmarked=true&size=500"
    }
}
