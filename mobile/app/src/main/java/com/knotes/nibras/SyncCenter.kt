package com.knotes.nibras

import android.content.Context
import android.widget.Toast
import com.knotes.SettingsStore
import com.knotes.status.StatusService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide "is the backend up, and are we current with it" check, independent of any open
 * screen. [checkOnline] is the cheap heartbeat used for the frequent connectivity poll;
 * [syncNow] is the fuller operation (pull every record type into [NibrasCache], drain the
 * record-creation outbox) used on the coarser sync cadence. Both update [SyncStatusStore] and
 * repost the status notification, so every trigger — the scheduled loop, a network reconnect, a
 * widget title tap, the in-app refresh button — leaves the home screen and notification current.
 * Guarded by a mutex so an overlapping manual "sync now" tap and a scheduled tick never run at once.
 */
object SyncCenter {

    private val api = NibrasApi()
    private val syncLock = Mutex()

    /** Heartbeat only — cheap, used for the frequent online/offline poll. */
    suspend fun checkOnline(context: Context): Boolean {
        val settings = SettingsStore(context).value
        val base = settings.nibrasBaseUrl.trim()
        if (base.isEmpty()) return false
        NibrasApi.configure(base, settings.nibrasUsername, settings.nibrasPassword)
        val online = runCatching { api.heartbeat(base) }.getOrDefault(false)
        SyncStatusStore.recordOnline(context, online)
        StatusService.refresh(context)
        return online
    }

    /**
     * Full sync: heartbeat, drain the outbox, then pull every record type into the cache. Always
     * shows a short toast with the outcome (offline / synced / failed) — whether triggered by a
     * scheduled tick, a reconnect, a manual tap, or a widget title tap.
     */
    suspend fun syncNow(context: Context): Boolean = syncLock.withLock {
        val settings = SettingsStore(context).value
        val base = settings.nibrasBaseUrl.trim()
        if (base.isEmpty()) return@withLock false
        NibrasApi.configure(base, settings.nibrasUsername, settings.nibrasPassword)

        val online = runCatching { api.heartbeat(base) }.getOrDefault(false)
        SyncStatusStore.recordOnline(context, online)
        StatusService.refresh(context)
        if (!online) {
            toast(context, "Offline — can't sync")
            return@withLock false
        }

        PushCenter.drainAll(context)

        val cache = NibrasCache(context)
        // Count what actually landed. Every per-type failure used to be swallowed by getOrNull()
        // and the enclosing runCatching therefore always succeeded — so a wrong password (which the
        // heartbeat can't catch, /page/heartbeatJson is unauthenticated) 401'd every fetch, cached
        // nothing, and still reported "Synced".
        val fetched = java.util.concurrent.atomic.AtomicInteger()
        val unauthorized = java.util.concurrent.atomic.AtomicBoolean(false)
        runCatching {
            coroutineScope {
                NIBRAS_TYPES.forEach { type ->
                    launch(Dispatchers.IO) {
                        val result = runCatching { api.fetchType(base, type.code, settings.nibrasUsername) }
                        val recs = result.getOrNull()
                        if (recs != null) {
                            cache.save(type.code, recs)
                            fetched.incrementAndGet()
                        } else if (result.exceptionOrNull()?.message?.contains("401") == true) {
                            unauthorized.set(true)
                        }
                    }
                }
            }
        }

        val ok = fetched.get() > 0
        when {
            unauthorized.get() && !ok ->
                toast(context, "Sign-in failed — check the username and password in Settings")
            !ok -> toast(context, "Sync failed")
            else -> {
                SyncStatusStore.recordSyncSuccess(context)
                toast(context, "Synced")
            }
        }
        StatusService.refresh(context)
        ok
    }

    private suspend fun toast(context: Context, message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
