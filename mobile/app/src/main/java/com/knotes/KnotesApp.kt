package com.knotes

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.knotes.nibras.PushCenter
import com.knotes.nibras.SyncCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point: registers a process-wide connectivity listener so every offline outbox
 * (created records, calendar events) drains as soon as a network appears — regardless of which
 * screen (if any) is open. The previous drain lived inside CalendarViewModel and died with it.
 * The same reconnect also kicks off a full backend sync (if enabled), so the app catches up
 * immediately instead of waiting for the next scheduled tick.
 */
class KnotesApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            PushCenter.drainAll(this@KnotesApp)
            if (SettingsStore(this@KnotesApp).value.syncActive) {
                scope.launch { SyncCenter.syncNow(this@KnotesApp) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Below Android 13 the chosen UI language has to be put on the shared resources by hand,
        // or receivers and widgets would keep drawing in the phone's language.
        com.knotes.util.AppLanguage.applyToProcess(this)
        // A force-stop (by hand or by an OEM battery manager) cancels the app's alarms; re-arm the
        // user's own alarms whenever the process starts again.
        runCatching { com.knotes.alarm.AlarmScheduler.rearm(this) }
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            getSystemService(ConnectivityManager::class.java)
                ?.registerNetworkCallback(request, networkCallback)
        }
    }
}
