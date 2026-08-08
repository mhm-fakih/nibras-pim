package com.knotes.nibras

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide, persisted record of the background sync's last outcome: when the last full sync
 * succeeded and whether the backend was reachable at the last check. Read by the home screen
 * header and the status notification; written by [SyncCenter].
 */
object SyncStatusStore {

    private val _lastSyncMillis = MutableStateFlow(0L)
    val lastSyncMillis: StateFlow<Long> = _lastSyncMillis.asStateFlow()

    private val _online = MutableStateFlow<Boolean?>(null)
    val online: StateFlow<Boolean?> = _online.asStateFlow()

    private var seeded = false

    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        _lastSyncMillis.value = prefs(context).getLong(K_LAST_SYNC, 0L)
    }

    fun recordOnline(context: Context, online: Boolean) {
        seed(context)
        _online.value = online
    }

    fun recordSyncSuccess(context: Context, atMillis: Long = System.currentTimeMillis()) {
        seed(context)
        _lastSyncMillis.value = atMillis
        prefs(context).edit().putLong(K_LAST_SYNC, atMillis).apply()
    }

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("knotes_sync_status", Context.MODE_PRIVATE)

    private const val K_LAST_SYNC = "last_sync_millis"
}
