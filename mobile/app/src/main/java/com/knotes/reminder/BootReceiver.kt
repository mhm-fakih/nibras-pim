package com.knotes.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.knotes.SettingsStore
import com.knotes.alarm.AlarmScheduler
import com.knotes.collect.CollectScheduler
import com.knotes.prayer.PrayerScheduler
import com.knotes.prayer.SilenceState
import com.knotes.status.ChimeScheduler
import com.knotes.status.StatusService

/**
 * Re-arms reminder alarms after a reboot, and after the app is updated, using the cached plan list.
 *
 * The update case was missing: installing a new version cancels every alarm the app holds, exactly
 * as a reboot does, and nothing re-armed them until an alarm was next edited — so after each update
 * the alarms simply did not go off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        PlansRepository.reschedule(context, SettingsStore(context).value)
        PlansWidget.refresh(context)
        BookmarksWidget.refresh(context)
        KpiWidget.refresh(context)
        TodayTasksWidget.refresh(context)
        TasksBoardWidget.refresh(context)
        // Re-assert the persistent status notification and the quarter-hour chime.
        // An update is no licence to start a foreground service from the background; the status
        // notification comes back when the app is next opened.
        runCatching { StatusService.sync(context) }
        ChimeScheduler.sync(context)
        // Prayer alarms, and the ringer if a prayer silence was cut short by the restart — the
        // alarm that would have ended it did not survive the reboot either.
        SilenceState.restoreIfInterrupted(context)
        PrayerScheduler.sync(context)
        com.knotes.checklist.ChecklistReminders.sync(context)
        // The user's own alarms. A reboot clears them like every other exact alarm, and an alarm
        // that silently stops existing after a restart is the worst failure this feature has.
        AlarmScheduler.rearm(context)
        PrayerWidget.refresh(context)
        // The daily collection alarm, which the reboot also cleared.
        CollectScheduler.sync(context)
    }
}
