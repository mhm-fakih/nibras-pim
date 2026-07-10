package com.knotes

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.knotes.nibras.PushCenter

/**
 * Application entry point: registers a process-wide connectivity listener so every offline outbox
 * (created records, calendar events) drains as soon as a network appears — regardless of which
 * screen (if any) is open. The previous drain lived inside CalendarViewModel and died with it.
 */
class KnotesApp : Application() {

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            PushCenter.drainAll(this@KnotesApp)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            getSystemService(ConnectivityManager::class.java)
                ?.registerNetworkCallback(request, networkCallback)
        }
    }
}
