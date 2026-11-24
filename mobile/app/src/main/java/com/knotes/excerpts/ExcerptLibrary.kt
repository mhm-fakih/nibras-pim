package com.knotes.excerpts

import com.knotes.nibras.NibrasApi
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** One sample in the list: 2–3 pages of an ebk book, and what has been said about them. */
data class SampleRow(
    val id: Long,
    val bookId: Long,
    val book: String?,
    val author: String?,
    val status: String,
    val format: String,
    val pageFrom: Int?,
    val pageTo: Int?,
    val totalPages: Int?,
    val hasText: Boolean,
    val summary: String?,
    val excerptId: Long?,
    val preview: String?,
    /** A signed link to the book's cover (its own, or one made from its first page), relative to the server. */
    val coverLink: String? = null,
)

/** A sample opened: its text (when the book had real text), details, and links to its pages. */
data class SampleDetail(
    val row: SampleRow,
    val text: String?,
    val description: String?,
    val sourceFile: String?,
    /** A signed link to the sample's pages as images ("…&n=1&w=1280"), relative to the server. */
    val pageLink: String?,
    /** A signed link to the sample's own PDF (the 2–3 pages), when there is one. */
    val pdfLink: String?,
    val pages: Int,
    /** A signed link to the whole book file (at the sample's page for a PDF). */
    val sourceLink: String?,
    val error: String?,
)

data class LibraryCounts(val total: Long, val fresh: Long, val promoted: Long, val failed: Long, val books: Long, val sampledBooks: Long)

data class LibraryJob(val running: Boolean, val wanted: Int, val made: Int, val tried: Int, val failed: Int, val current: String?, val finished: Boolean)

data class LibraryOverview(val counts: LibraryCounts, val job: LibraryJob)

/**
 * The web's Excerpt library, read and edited from the phone — online only: the samples are made on
 * the server, from the book files there.
 */
object ExcerptLibrary {
    private val api = NibrasApi()

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
    private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)
    private fun JSONObject.long(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)

    private fun row(o: JSONObject) = SampleRow(
        id = o.getLong("id"),
        bookId = o.optLong("bookId"),
        book = o.str("book"),
        author = o.str("author"),
        status = o.optString("status", "new"),
        format = o.optString("format", ""),
        pageFrom = o.int("pageFrom"),
        pageTo = o.int("pageTo"),
        totalPages = o.int("totalPages"),
        hasText = o.optBoolean("hasText"),
        summary = o.str("summary"),
        excerptId = o.long("excerptId"),
        preview = o.str("preview"),
        coverLink = o.str("coverLink"),
    )

    private fun detail(raw: String): SampleDetail {
        val o = JSONObject(raw)
        return SampleDetail(
            row = row(o.getJSONObject("row")),
            text = o.str("text"),
            description = o.str("description"),
            sourceFile = o.str("sourceFile"),
            pageLink = o.str("pageLink"),
            pdfLink = o.str("pdfLink"),
            pages = o.optInt("pages"),
            sourceLink = o.str("sourceLink"),
            error = o.str("error"),
        )
    }

    suspend fun overview(base: String): LibraryOverview {
        val o = JSONObject(api.excerptLibrary(base, "/overview"))
        val c = o.getJSONObject("counts")
        val j = o.getJSONObject("job")
        return LibraryOverview(
            LibraryCounts(c.optLong("total"), c.optLong("fresh"), c.optLong("promoted"), c.optLong("failed"),
                c.optLong("books"), c.optLong("sampledBooks")),
            LibraryJob(j.optBoolean("running"), j.optInt("wanted"), j.optInt("made"), j.optInt("tried"),
                j.optInt("failed"), j.str("current"), !j.isNull("finished")),
        )
    }

    suspend fun list(base: String, status: String, q: String): List<SampleRow> {
        val query = "?status=$status" + if (q.isBlank()) "" else "&q=" + URLEncoder.encode(q.trim(), "UTF-8")
        val a = JSONArray(api.excerptLibrary(base, query))
        return (0 until a.length()).map { row(a.getJSONObject(it)) }
    }

    suspend fun get(base: String, id: Long): SampleDetail = detail(api.excerptLibrary(base, "/$id"))

    suspend fun save(base: String, id: Long, summary: String, details: String): SampleDetail =
        detail(api.excerptLibrary(base, "/$id", "PUT", JSONObject().put("summary", summary).put("description", details)))

    /** skip, restore, another (a new place in the same book: a new sample) or promote. */
    suspend fun act(base: String, id: Long, action: String): SampleDetail =
        detail(api.excerptLibrary(base, "/$id/$action", "POST"))

    suspend fun generate(base: String, count: Int) {
        api.excerptLibrary(base, "/generate", "POST", JSONObject().put("count", count))
    }

    /**
     * The sample's PDF on the phone, under [filesDir]/excerpt-library/<book id>/, downloaded the
     * first time. Kept afterwards, so marks made in the app's PDF viewer stay with it.
     */
    suspend fun localPdf(base: String, filesDir: String, d: SampleDetail): java.io.File {
        val link = d.pdfLink ?: throw NibrasApi.ApiException(null, "This sample has no PDF.")
        val r = d.row
        val title = (r.book ?: "book ${r.bookId}").replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(60).trim()
        val pages = if (r.pageFrom != null && r.pageTo != null) " p${r.pageFrom}-${r.pageTo}" else " s${r.id}"
        val file = java.io.File(filesDir.trimEnd('/') + "/excerpt-library/${r.bookId}", "$title$pages.pdf")
        if (!file.isFile || file.length() == 0L) api.download(absolute(base, link), file)
        return file
    }

    /**
     * A paragraph as the PDF laid it out, its lines joined again for the phone's narrower width: a
     * word hyphenated across lines is rejoined, the other line breaks become spaces.
     */
    fun reflow(p: String): String =
        p.replace(Regex("(\\p{L})-\\s*\\n\\s*(\\p{Ll})"), "$1$2").replace(Regex("\\s*\\n\\s*"), " ")

    /** A server-relative signed link made absolute. */
    fun absolute(base: String, link: String): String = base.trimEnd('/') + "/" + link.trimStart('/')

    /** "pp. 120–122 of 362", or "34–36%" for an ebook measured in thousandths. */
    fun place(r: SampleRow): String {
        val from = r.pageFrom ?: return ""
        val to = r.pageTo ?: return ""
        if (r.totalPages == 1000 && r.format != "pdf" && r.format != "djvu") return "${from / 10}–${to / 10}%"
        val pp = if (from == to) "p. $from" else "pp. $from–$to"
        return r.totalPages?.let { "$pp / $it" } ?: pp
    }
}
