package com.knotes.quran

import android.content.Context
import com.knotes.nibras.NibrasApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The Quran on the phone: the server's surah list and every surah opened, saved so the page works
 * offline, and the bookmarks and notes made here, queued until the server has them.
 *
 * What the page shows is the saved copy with the queue laid over it — a bookmark set offline shows
 * at once, and a note written offline shows under the aya's notes, marked as waiting — so nothing
 * typed in seems lost while the phone has no network. The queue is drained by
 * [com.knotes.nibras.PushCenter] on every reconnect and sync, and by the page itself.
 */
object QuranStore {

    private val _overview = MutableStateFlow<QuranOverview?>(null)
    /** The surah list as last fetched (null until known); `enabled` says whether the module is on. */
    val overview: StateFlow<QuranOverview?> = _overview.asStateFlow()

    private val _pending = MutableStateFlow<List<QuranOp>>(emptyList())
    val pending: StateFlow<List<QuranOp>> = _pending.asStateFlow()

    /** Bumped whenever a surah's saved copy changes, so an open surah re-reads it. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private var seeded = false

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "quran_cache").apply { mkdirs() }

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        _pending.value = QuranOp.listFromJson(prefs(context).getString(K_PENDING, "[]"))
        read(context, OVERVIEW)?.let { raw -> runCatching { QuranJson.overview(raw) }.getOrNull() }
            ?.let { _overview.value = it }
    }

    /** Fetches the surah list (and whether the module is on); quiet when the server is unreachable. */
    suspend fun refreshOverview(context: Context, base: String): Boolean {
        seed(context)
        val raw = runCatching { NibrasApi().fetchQuranRaw(base) }.getOrNull() ?: return false
        val parsed = runCatching { QuranJson.overview(raw) }.getOrNull() ?: return false
        save(context, OVERVIEW, raw)
        _overview.value = parsed
        return true
    }

    /** The saved copy of a surah, with this phone's queued changes laid over it; null if never fetched. */
    fun cachedSurah(context: Context, number: Int): QuranSurah? {
        seed(context)
        val raw = read(context, "surah-$number") ?: return null
        return runCatching { overlay(QuranJson.surah(raw)) }.getOrNull()
    }

    /** Fetches a surah and saves it; null when the server cannot be reached. */
    suspend fun fetchSurah(context: Context, base: String, number: Int): QuranSurah? {
        seed(context)
        val raw = runCatching { NibrasApi().fetchQuranSurahRaw(base, number) }.getOrNull() ?: return null
        val parsed = runCatching { QuranJson.surah(raw) }.getOrNull() ?: return null
        save(context, "surah-$number", raw)
        return overlay(parsed)
    }

    fun savedAt(context: Context, number: Int): Long? =
        File(dir(context), "surah-$number.json").takeIf { it.exists() }?.lastModified()

    /** Which surahs are saved for reading offline. */
    fun savedSurahs(context: Context): Set<Int> =
        dir(context).list().orEmpty().mapNotNull { Regex("^surah-(\\d+)\\.json$").find(it)?.groupValues?.get(1)?.toIntOrNull() }.toSet()

    // ---- changes made here ----------------------------------------------------------------

    fun bookmarkSurah(context: Context, surah: Int, on: Boolean) =
        queueBookmark(context, QuranOp(newRef(), QuranOp.SURAH_BOOKMARK, surah, on = on))

    fun bookmarkAya(context: Context, surah: Int, aya: Int, on: Boolean) =
        queueBookmark(context, QuranOp(newRef(), QuranOp.AYA_BOOKMARK, surah, aya, on = on))

    /** Queues a note to be appended to the aya's notes on the server. */
    @Synchronized
    fun addNote(context: Context, surah: Int, aya: Int, text: String) {
        seed(context)
        val t = text.trim()
        if (t.isEmpty()) return
        val at = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString()
        _pending.value = _pending.value + QuranOp(newRef(), QuranOp.NOTE, surah, aya, text = t, at = at)
        persist(context)
    }

    /** A later bookmark of the same surah or aya replaces an unsent earlier one — only the last state matters. */
    @Synchronized
    private fun queueBookmark(context: Context, op: QuranOp) {
        seed(context)
        _pending.value = _pending.value.filterNot { it.kind == op.kind && it.surah == op.surah && it.aya == op.aya } + op
        // Show it in the list at once.
        _overview.value = _overview.value?.let { ov ->
            ov.copy(surahs = ov.surahs.map { r ->
                when {
                    r.number != op.surah -> r
                    op.kind == QuranOp.SURAH_BOOKMARK -> r.copy(bookmarked = op.on)
                    else -> r.copy(bookmarkedAyat = (r.bookmarkedAyat + if (op.on) 1 else -1).coerceAtLeast(0))
                }
            })
        }
        persist(context)
    }

    @Synchronized
    fun discard(context: Context, ref: String) {
        seed(context)
        _pending.value = _pending.value.filterNot { it.ref == ref }
        persist(context)
    }

    fun sendable(): List<QuranOp> = _pending.value.filter { it.error == null }

    /**
     * Sends the queue in order, stopping at the first change the server could not take now (it
     * will be retried). One the server refuses (the aya is gone) is kept with the reason, not
     * dropped. Returns the surahs that changed on the server, to fetch again.
     */
    suspend fun drain(context: Context, base: String): Set<Int> {
        seed(context)
        val api = NibrasApi()
        val changed = mutableSetOf<Int>()
        for (op in sendable()) {
            when (api.sendQuran(base, op)) {
                NibrasApi.QuranSend.DONE -> {
                    synchronized(this) {
                        _pending.value = _pending.value.filterNot { it.ref == op.ref }
                        persist(context)
                    }
                    changed += op.surah
                }
                NibrasApi.QuranSend.REFUSED -> synchronized(this) {
                    _pending.value = _pending.value.map { if (it.ref == op.ref) it.copy(error = "refused") else it }
                    persist(context)
                }
                NibrasApi.QuranSend.LATER -> break
            }
        }
        // The saved copies of the surahs touched are now stale: fetch them again.
        for (n in changed) fetchSurah(context, base, n)
        if (changed.isNotEmpty()) {
            refreshOverview(context, base)
            _version.value = _version.value + 1
        }
        return changed
    }

    /** The surah with the queued changes applied, as the page should show it. */
    private fun overlay(s: QuranSurah): QuranSurah {
        val ops = _pending.value.filter { it.surah == s.number }
        if (ops.isEmpty()) return s
        var out = s
        ops.forEach { op ->
            out = when (op.kind) {
                QuranOp.SURAH_BOOKMARK -> out.copy(bookmarked = op.on)
                QuranOp.AYA_BOOKMARK -> out.copy(ayat = out.ayat.map { if (it.number == op.aya) it.copy(bookmarked = op.on) else it })
                else -> out.copy(ayat = out.ayat.map { if (it.number == op.aya) it.copy(pendingNotes = it.pendingNotes + op.text) else it })
            }
        }
        return out
    }

    private fun newRef() = "phone-" + UUID.randomUUID().toString()

    private fun persist(context: Context) {
        // commit(): a note typed in must survive the app being killed right after.
        prefs(context).edit().putString(K_PENDING, QuranOp.listToJson(_pending.value)).commit()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("knotes_quran_outbox", Context.MODE_PRIVATE)

    private fun save(context: Context, key: String, raw: String) {
        runCatching {
            val tmp = File(dir(context), "$key.tmp")
            tmp.writeText(raw)
            tmp.renameTo(File(dir(context), "$key.json"))
        }
    }

    private fun read(context: Context, key: String): String? =
        File(dir(context), "$key.json").takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    private const val K_PENDING = "pending"
    private const val OVERVIEW = "overview"
}
