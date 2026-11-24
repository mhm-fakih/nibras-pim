package com.knotes.nibras

import android.content.Context
import com.knotes.flash.FlashCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How many people the Contacts page lists, for the rail's "K (n)" cell — the same count as the
 * letter modules above it show. Seeded from the saved copy (so it shows offline), then set whenever
 * the list is fetched, from the rail's refresh or from the page itself.
 */
object ContactsCount {

    private val _count = MutableStateFlow<Int?>(null)
    val count: StateFlow<Int?> = _count.asStateFlow()

    fun set(n: Int?) {
        if (n != null) _count.value = n
    }

    /** From the saved copy; blocking file read, call off the main thread. */
    fun seed(context: Context) = set(FlashCache(context).contacts()?.size)

    /** Fetches the list, saves it for the page, and counts it; quiet when the server is unreachable. */
    suspend fun refresh(context: Context, baseUrl: String) {
        runCatching { NibrasApi().fetchContactsRaw(baseUrl) }.onSuccess { raw ->
            val cache = FlashCache(context)
            cache.saveRaw(FlashCache.CONTACTS, raw)
            set(cache.contacts()?.size)
        }
    }
}
