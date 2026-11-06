package com.knotes.expenses

import org.json.JSONArray
import org.json.JSONObject

/** A payment category as the server lists it (`GET /api/expenses/categories`). */
data class PayCategory(val id: Long, val name: String)

/** The currencies the server knows, and the one it converts everything to. */
data class PayCurrencies(val default: String, val codes: List<String>)

/**
 * A payment entered on the phone, waiting to be sent to the server — or already sent
 * ([serverId] set), kept for a while so the page can show what went up.
 *
 * [ref] is the phone's own id for it and travels as `clientRef`: the server records a ref once, so
 * a send repeated after a lost reply cannot make the same payment twice.
 */
data class PendingPayment(
    val ref: String,
    val amount: Double,
    /** Blank = the server's default currency. */
    val currency: String,
    /** Local date-time, ISO (`2026-11-05T09:30:00`). */
    val date: String,
    val categoryId: Long?,
    /** As it was named when chosen — shown even when the category list is not at hand. */
    val categoryName: String,
    val summary: String,
    val notes: String,
    /** False = to be paid (budgeted). */
    val paid: Boolean,
    val queuedAt: Long,
    /** Why the server refused it (it stays until edited, retried or removed); null = not refused. */
    val error: String? = null,
    val serverId: Long? = null,
    val sentAt: Long? = null,
) {
    /** The body of `POST /api/expenses`. */
    fun toRequest(): JSONObject = JSONObject()
        .put("amount", amount)
        .put("date", date)
        .put("paid", paid)
        .put("clientRef", ref)
        .apply {
            if (currency.isNotBlank()) put("currency", currency)
            categoryId?.let { put("categoryId", it) }
            if (summary.isNotBlank()) put("summary", summary)
            if (notes.isNotBlank()) put("notes", notes)
        }

    fun toJson(): JSONObject = JSONObject()
        .put("ref", ref)
        .put("amount", amount)
        .put("currency", currency)
        .put("date", date)
        .put("categoryName", categoryName)
        .put("summary", summary)
        .put("notes", notes)
        .put("paid", paid)
        .put("queuedAt", queuedAt)
        .apply {
            categoryId?.let { put("categoryId", it) }
            error?.let { put("error", it) }
            serverId?.let { put("serverId", it) }
            sentAt?.let { put("sentAt", it) }
        }

    companion object {
        fun fromJson(o: JSONObject): PendingPayment? = runCatching {
            PendingPayment(
                ref = o.getString("ref").ifBlank { return null },
                amount = o.getDouble("amount").takeIf { it > 0 } ?: return null,
                currency = str(o, "currency"),
                date = str(o, "date").ifBlank { return null },
                categoryId = if (o.has("categoryId") && !o.isNull("categoryId")) o.getLong("categoryId") else null,
                categoryName = str(o, "categoryName"),
                summary = str(o, "summary"),
                notes = str(o, "notes"),
                paid = o.optBoolean("paid", true),
                queuedAt = o.optLong("queuedAt"),
                error = str(o, "error").ifBlank { null },
                serverId = if (o.has("serverId")) o.getLong("serverId") else null,
                sentAt = if (o.has("sentAt")) o.getLong("sentAt") else null,
            )
        }.getOrNull()

        fun listToJson(items: List<PendingPayment>): String =
            JSONArray().also { a -> items.forEach { a.put(it.toJson()) } }.toString()

        fun listFromJson(text: String?): List<PendingPayment> = runCatching {
            val arr = JSONArray(text ?: "[]")
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** The category list exactly as the server sends it, sorted by name the way it already is. */
fun parseCategories(raw: String): List<PayCategory> = runCatching {
    val arr = JSONArray(raw)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val id = o.optLong("id")
        val name = str(o, "name").ifBlank { str(o, "code") }.ifBlank { "#$id" }
        if (id > 0) PayCategory(id, name) else null
    }
}.getOrDefault(emptyList())

/** `GET /api/expenses/currencies`: `{defaultCurrency, currencies:[{code, …}]}`. */
fun parseCurrencies(raw: String): PayCurrencies? = runCatching {
    val o = JSONObject(raw)
    val arr = o.optJSONArray("currencies") ?: JSONArray()
    val codes = (0 until arr.length()).mapNotNull { str(arr.getJSONObject(it), "code").ifBlank { null } }
    val def = str(o, "defaultCurrency").ifBlank { codes.firstOrNull() ?: "USD" }
    PayCurrencies(def, (listOf(def) + codes).distinct())
}.getOrNull()

/**
 * The amount as typed, or null when it is not a positive number. Accepts a comma as the decimal
 * mark and spaces or apostrophes as thousands separators (`1 500,5`, `1'500.5`).
 */
fun parseAmount(text: String): Double? {
    val t = text.trim().replace(" ", "").replace(" ", "").replace("'", "")
    if (t.isEmpty()) return null
    val normal = when {
        // Both marks: the last one is the decimal point.
        t.contains(',') && t.contains('.') ->
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        t.count { it == ',' } == 1 -> t.replace(',', '.')
        else -> t.replace(",", "")
    }
    return normal.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
}

/** `optString` returns the literal "null" for a JSON null; this does not. */
private fun str(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key)
