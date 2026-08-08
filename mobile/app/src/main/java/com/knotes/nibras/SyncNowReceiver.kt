package com.knotes.nibras

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs a full [SyncCenter.syncNow] in response to a tap outside the app — the KPI widget's and
 * the upcoming-plans widget's title, via [pendingIntent]. A `goAsync` keeps the process alive long
 * enough for the (network) work to finish, since a BroadcastReceiver's own process-priority grant
 * ends as soon as [onReceive] returns.
 */
class SyncNowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SyncCenter.syncNow(context.applicationContext) }
            result.finish()
        }
    }

    companion object {
        private const val ACTION_SYNC_NOW = "com.knotes.nibras.SYNC_NOW"

        fun pendingIntent(context: Context, requestCode: Int): android.app.PendingIntent {
            val intent = Intent(context, SyncNowReceiver::class.java).setAction(ACTION_SYNC_NOW)
            return android.app.PendingIntent.getBroadcast(
                context, requestCode, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
