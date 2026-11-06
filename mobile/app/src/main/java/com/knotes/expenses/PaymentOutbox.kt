package com.knotes.expenses

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Payments entered on the phone, waiting for the server — and the last few it accepted.
 *
 * An object, like [com.knotes.nibras.RecordOutbox], so the Payments page and the drainer
 * ([com.knotes.nibras.PushCenter], run on every reconnect and every sync) see one list. A payment
 * leaves [pending] only when the server has recorded it; one it refused (a category deleted since,
 * a currency it does not know) stays, with the reason, until it is fixed or removed — money typed
 * in is never dropped silently.
 */
object PaymentOutbox {

    private val _pending = MutableStateFlow<List<PendingPayment>>(emptyList())
    val pending: StateFlow<List<PendingPayment>> = _pending.asStateFlow()

    private val _sent = MutableStateFlow<List<PendingPayment>>(emptyList())
    val sent: StateFlow<List<PendingPayment>> = _sent.asStateFlow()

    private var seeded = false

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        val p = prefs(context)
        _pending.value = PendingPayment.listFromJson(p.getString(K_PENDING, "[]"))
        _sent.value = PendingPayment.listFromJson(p.getString(K_SENT, "[]"))
    }

    /** Add a payment, or replace the one with the same ref (an edit); clears any refusal. */
    @Synchronized
    fun queue(context: Context, item: PendingPayment) {
        seed(context)
        val clean = item.copy(error = null)
        val list = _pending.value
        val at = list.indexOfFirst { it.ref == item.ref }
        _pending.value = if (at >= 0) list.toMutableList().also { it[at] = clean } else list + clean
        save(context)
    }

    @Synchronized
    fun remove(context: Context, ref: String) {
        seed(context)
        _pending.value = _pending.value.filterNot { it.ref == ref }
        save(context)
    }

    /** Send a refused payment again as it is (e.g. after the category was restored on the server). */
    @Synchronized
    fun retry(context: Context, ref: String) {
        seed(context)
        _pending.value = _pending.value.map { if (it.ref == ref) it.copy(error = null) else it }
        save(context)
    }

    @Synchronized
    fun markRefused(context: Context, ref: String, reason: String) {
        seed(context)
        _pending.value = _pending.value.map { if (it.ref == ref) it.copy(error = reason) else it }
        save(context)
    }

    /** The server recorded it: out of the queue, onto the "sent" list (newest first, capped). */
    @Synchronized
    fun markSent(context: Context, ref: String, serverId: Long, now: Long = System.currentTimeMillis()) {
        seed(context)
        val item = _pending.value.firstOrNull { it.ref == ref } ?: return
        _pending.value = _pending.value.filterNot { it.ref == ref }
        _sent.value = (listOf(item.copy(serverId = serverId, sentAt = now, error = null)) +
            _sent.value.filterNot { it.ref == ref }).take(MAX_SENT)
        save(context)
    }

    /** Payments the drainer should try now: queued and not refused. */
    fun sendable(): List<PendingPayment> = _pending.value.filter { it.error == null }

    private fun save(context: Context) {
        // commit(), not apply(): this is money typed in on a phone that may be killed any moment.
        prefs(context).edit()
            .putString(K_PENDING, PendingPayment.listToJson(_pending.value))
            .putString(K_SENT, PendingPayment.listToJson(_sent.value))
            .commit()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("knotes_payment_outbox", Context.MODE_PRIVATE)

    private const val K_PENDING = "pending"
    private const val K_SENT = "sent"
    private const val MAX_SENT = 30
}

/**
 * The server's category and currency lists as last fetched, so the page offers them with no
 * network. The raw JSON is stored and parsed by the same code as a live reply.
 */
class PaymentCache(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "payments_cache").apply {
        if (!exists()) mkdirs()
    }

    private fun file(key: String) = File(dir, "$key.json")

    fun saveCategories(raw: String) = save(CATEGORIES, raw)
    fun saveCurrencies(raw: String) = save(CURRENCIES, raw)

    fun categories(): List<PayCategory>? = read(CATEGORIES)?.let(::parseCategories)
    fun currencies(): PayCurrencies? = read(CURRENCIES)?.let(::parseCurrencies)

    /** When the categories were last fetched, or null if never. */
    fun savedAt(): Long? = file(CATEGORIES).takeIf { it.exists() }?.lastModified()

    private fun save(key: String, raw: String) {
        runCatching {
            // Write then rename, so a kill mid-write cannot leave a half file that parses as nothing.
            val tmp = File(dir, "$key.tmp")
            tmp.writeText(raw)
            tmp.renameTo(file(key))
        }
    }

    private fun read(key: String): String? =
        file(key).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    private companion object {
        const val CATEGORIES = "categories"
        const val CURRENCIES = "currencies"
    }
}
