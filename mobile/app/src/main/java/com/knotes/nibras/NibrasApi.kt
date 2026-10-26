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

    /**
     * Free-text search against the BACKEND — records that may not be on the phone at all, unlike
     * every other read here, which serves already-bookmarked ones out of the export.
     *
     * Results come back in the export's own shape, so they parse into the same [NibrasRecord] the
     * browser screen already renders. [ecode] blank searches every enabled module.
     */
    @Throws(ApiException::class)
    suspend fun searchBackend(base: String, q: String, ecode: String = "", limit: Int = 25):
            List<NibrasRecord> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        val url = "${base.trimEnd('/')}$PATH_MOBILE_SEARCH" +
            "?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
            "&ecode=${java.net.URLEncoder.encode(ecode, "UTF-8")}&limit=$limit"
        val data: JSONArray = JSONObject(get(url)).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).map { i -> data.getJSONObject(i).toRecord(ecode) }
    }

    /**
     * Flash cards in one state. The server caps NEW to its working set and returns REPEAT and KEPT
     * whole — the phone does not decide how much of the bank it is shown.
     */
    @Throws(ApiException::class)
    suspend fun fetchFlashcards(base: String, state: String): List<com.knotes.flash.Flashcard> =
        withContext(Dispatchers.IO) {
            val arr = JSONArray(fetchFlashcardsRaw(base, state))
            (0 until arr.length()).mapNotNull { i ->
                com.knotes.flash.Flashcard.fromJson(arr.getJSONObject(i))
            }
        }

    /**
     * The same call, as the JSON the server actually sent.
     *
     * For the offline cache, which stores the response verbatim so the copy it serves is parsed by
     * exactly the code that parses a live one — see [com.knotes.flash.FlashCache].
     */
    @Throws(ApiException::class)
    suspend fun fetchFlashcardsRaw(base: String, state: String): String =
        withContext(Dispatchers.IO) {
            get("${base.trimEnd('/')}$PATH_FLASHCARDS?state=" + URLEncoder.encode(state, "UTF-8"))
        }

    /** How much of the job is left, so the page can say what it is holding back. */
    @Throws(ApiException::class)
    suspend fun fetchFlashcardSummaryRaw(base: String): String =
        withContext(Dispatchers.IO) { get("${base.trimEnd('/')}$PATH_FLASHCARDS/summary") }

    @Throws(ApiException::class)
    suspend fun fetchFlashcardSummary(base: String): com.knotes.flash.FlashSummary =
        withContext(Dispatchers.IO) {
            val o = JSONObject(fetchFlashcardSummaryRaw(base))
            com.knotes.flash.FlashSummary(
                total = o.optInt("total"),
                remaining = o.optInt("remaining"),
                reviewed = o.optInt("reviewed"),
                repeat = o.optInt("repeat"),
                kept = o.optInt("kept"),
            )
        }

    /**
     * Resources' key points, live from the server.
     *
     * Bullets arrive already split, so the phone and the web page cannot disagree about what counts
     * as a line — and neither carries a copy of the tidying rules.
     */
    @Throws(ApiException::class)
    suspend fun fetchHighlights(base: String, limit: Int = 200): List<com.knotes.flash.Highlight> =
        withContext(Dispatchers.IO) {
            val arr = JSONArray(fetchHighlightsRaw(base, limit))
            (0 until arr.length()).mapNotNull { i ->
                com.knotes.flash.Highlight.fromJson(arr.getJSONObject(i))
            }
        }

    /** The same call, verbatim, for the offline cache. */
    @Throws(ApiException::class)
    suspend fun fetchHighlightsRaw(base: String, limit: Int = 200): String =
        withContext(Dispatchers.IO) { get("${base.trimEnd('/')}$PATH_HIGHLIGHTS?limit=$limit") }

    /**
     * Bookmarked people and the work still open with each.
     *
     * One call for the whole page: the server groups the tasks per contact, so a screen of sixteen
     * people is one request rather than one per heading.
     */
    @Throws(ApiException::class)
    suspend fun fetchContactsRaw(base: String): String =
        withContext(Dispatchers.IO) { get("${base.trimEnd('/')}$PATH_CONTACTS/with-tasks") }

    /** Record a decision. The same call puts a card back, by sending NEW. */
    suspend fun pushFlashcardState(base: String, id: Long, state: String): Boolean =
        withContext(Dispatchers.IO) {
            putAuthed("${base.trimEnd('/')}$PATH_FLASHCARDS/$id/state",
                JSONObject().put("state", state))
        }

    /**
     * Flags a record on the server so the next sync pulls it down (or unflags it). The exports
     * carry bookmarked records only, so this IS "send this to my phone".
     */
    suspend fun pushBookmark(base: String, ecode: String, id: String, bookmarked: Boolean = true):
            Boolean = withContext(Dispatchers.IO) {
        val item = JSONObject().put("ecode", ecode).put("id", id).put("bookmarked", bookmarked)
        postAuthed("${base.trimEnd('/')}$PATH_MOBILE_PUSH_BOOKMARK",
            JSONObject().put("data", JSONArray().put(item)))
    }

    /**
     * The vocabulary the server says this client should be working from: enabled modules and the
     * departments of priority >= 2, already filtered and ordered by the backend.
     *
     * One request, and NO client-side filtering — that is the point. The phone used to fetch
     * `/api/modules` and `/api/departments?bookmarked=true` and apply its own rules, which meant it
     * could disagree with the Admin page's "synced to clients" list, the very list that exists to
     * make such drift visible. Both now come from the one service behind that page.
     */
    private suspend fun fetchTaxonomy(base: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { JSONObject(get("${base.trimEnd('/')}$PATH_CLIENT_TAXONOMY")) }.getOrNull()
    }

    /** Enabled modules as (code, label) — the capture "types", in the server's order. */
    suspend fun fetchModules(base: String): List<Tag> = withContext(Dispatchers.IO) {
        val arr = fetchTaxonomy(base)?.optJSONArray("modules") ?: return@withContext emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("code").isNotBlank() }
            .map {
                Tag(
                    it.optString("code").trim(),
                    it.optString("label").ifBlank { it.optString("moduleKey") }
                        .ifBlank { it.optString("code") },
                )
            }
    }

    /**
     * Each enabled module's repo folder layout, by code (bucket_by_type / bucket_by_100), so the
     * phone finds a record's files where the server put them. Null when the server can't be reached
     * or predates these fields — the caller keeps what it had.
     */
    suspend fun fetchFolderLayouts(base: String): Map<String, com.knotes.FolderLayout>? = withContext(Dispatchers.IO) {
        val arr = fetchTaxonomy(base)?.optJSONArray("modules") ?: return@withContext null
        val mods = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("code").isNotBlank() }
        if (mods.none { it.has("bucketBy100") }) return@withContext null
        mods.associate {
            it.optString("code").trim() to com.knotes.FolderLayout(
                byType = it.optBoolean("bucketByType"),
                by100 = it.optBoolean("bucketBy100"),
            )
        }
    }

    /**
     * Departments of priority >= 2 as (code, name) — the capture "categories", in the server's
     * order. Not the bookmarked ones: priority is what decides whether a department is worth a slot
     * on a phone keypad, and bookmarking means something else entirely.
     */
    suspend fun fetchDepartments(base: String): List<Tag> = withContext(Dispatchers.IO) {
        val arr = fetchTaxonomy(base)?.optJSONArray("departments") ?: return@withContext emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("code").isNotBlank() }
            .map {
                Tag(
                    it.optString("code").trim(),
                    it.optString("name").ifBlank { it.optString("summary") }
                        .ifBlank { it.optString("code") },
                )
            }
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
                        .put("summary", it.summary)
                        .put("description", it.description),
                )
            }
            postAuthed("${base.trimEnd('/')}$PATH_MOBILE_PUSH_CALENDAR", JSONObject().put("data", data))
        }

    /**
     * Turns one occurrence of a recurring planner into a real record and returns its id.
     *
     * A virtual occurrence is computed, not stored: its exported id is the composite
     * `<seriesId>@<recurrenceId>`, which is not something the push endpoints can accept. Editing one
     * therefore has to create the record first — and because the endpoint is idempotent, calling it
     * again for the same slot returns the record made the first time rather than a duplicate.
     *
     * Returns null when offline or refused, so the caller can say so instead of silently discarding
     * the edit.
     */
    suspend fun materialiseOccurrence(base: String, seriesId: String, recurrenceId: String): String? =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("seriesId", seriesId.toLongOrNull() ?: return@withContext null)
                .put("recurrenceId", recurrenceId)
            runCatching {
                val body = postAuthedBody("${base.trimEnd('/')}$PATH_MATERIALISE", payload)
                    ?: return@withContext null
                JSONObject(body).optLong("id").takeIf { it > 0 }?.toString()
            }.getOrNull()
        }

    /** As [postAuthed], but hands back the response body (null when the call failed). */
    private fun postAuthedBody(url: String, payload: JSONObject): String? {
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
        return resp.use { if (it.isSuccessful) it.body?.string() else null }
    }

    /** Creates records of ANY exportable type (bookmarked=true server-side so they round-trip).
     *  Envelope: {data:[{ecode, summary, description, priority, department}]}. */
    suspend fun pushCreates(base: String, items: List<PendingCreate>): Boolean =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext true
            val data = JSONArray()
            items.forEach {
                val o = JSONObject()
                    .put("ecode", it.ecode)
                    .put("summary", it.summary)
                    .put("description", it.description)
                    .put("department", it.department)
                it.priority?.let { p -> o.put("priority", p) }
                data.put(o)
            }
            postAuthed("${base.trimEnd('/')}$PATH_MOBILE_PUSH_CREATE", JSONObject().put("data", data))
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
    /** PUT twin of [postAuthed] — same one-shot re-login on a 401. */
    private fun putAuthed(url: String, payload: JSONObject): Boolean {
        fun build(): Request {
            val b = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .put(payload.toString().toRequestBody(jsonMedia))
            token?.let { b.header("Authorization", "Bearer $it") }
            return b.build()
        }
        return runCatching {
            var resp = client.newCall(build()).execute()
            if (resp.code == 401 && doLogin()) {
                resp.close()
                resp = client.newCall(build()).execute()
            }
            resp.use { it.isSuccessful }
        }.getOrDefault(false)
    }

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
    /**
     * A request that failed, carrying the HTTP status when there was one.
     *
     * The point is the message the user ends up reading: "search failed, check your sign-in" sent
     * someone hunting through Settings when the real answer was that the server was running an
     * older build with no such endpoint. A 404 and a 401 are different problems with different
     * fixes, and the UI can only say so if the status survives this far.
     */
    class ApiException(val status: Int?, message: String) : RuntimeException(message)

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
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code} for $url")
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
            bookmarked = optBoolean("bookmarked", false),
            seriesId = str("seriesId"),
            recurrenceId = str("recurrenceId"),
            virtual = optBoolean("virtual", false),
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
        const val PATH_MATERIALISE = "/api/calendar/occurrences/materialise"
        const val PATH_MOBILE_PUSH_CREATE = "/sync/mobilePushCreate"
        const val PATH_MOBILE_SEARCH = "/sync/mobileSearch"
        const val PATH_MOBILE_PUSH_BOOKMARK = "/sync/mobilePushBookmark"
        const val PATH_CLIENT_TAXONOMY = "/sync/clientTaxonomy"
        const val PATH_FLASHCARDS = "/api/flashcards"
        const val PATH_HIGHLIGHTS = "/api/highlights"
        // The base path is the CRUD list now; the grouped view this page wants is a sub-path.
        const val PATH_CONTACTS = "/api/contacts"
    }
}
