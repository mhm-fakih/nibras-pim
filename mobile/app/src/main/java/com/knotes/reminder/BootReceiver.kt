package com.knotes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.knotes.SettingsStore
import com.knotes.collect.CollectScheduler
import com.knotes.prayer.PrayerScheduler
import com.knotes.prayer.SilenceState
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
        TodayTasksWidget.refresh(context)
        // Re-assert the persistent status notification and the quarter-hour chime.
        StatusService.sync(context)
        ChimeScheduler.sync(context)
        // Prayer alarms, and the ringer if a prayer silence was cut short by the restart — the
        // alarm that would have ended it did not survive the reboot either.
        SilenceState.restoreIfInterrupted(context)
        PrayerScheduler.sync(context)
        PrayerWidget.refresh(context)
        // The daily collection alarm, which the reboot also cleared.
        CollectScheduler.sync(context)
    }
}
