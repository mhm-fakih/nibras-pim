package com.knotes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.knotes.SettingsStore
import com.knotes.status.ChimeScheduler
import com.knotes.status.StatusService

/** Re-arms reminder alarms after a reboot (the OS clears them) using the cached plan list. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        PlansRepository.reschedule(context, SettingsStore(context).value)
        PlansWidget.refresh(context)
        BookmarksWidget.refresh(context)
        KpiWidget.refresh(context)
        // Re-assert the persistent status notification and the quarter-hour chime.
        StatusService.sync(context)
        ChimeScheduler.sync(context)
    }
}
