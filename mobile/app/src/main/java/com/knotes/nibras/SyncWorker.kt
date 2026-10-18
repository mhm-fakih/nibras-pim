package com.knotes.nibras

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.knotes.SettingsStore
import com.knotes.reminder.Widgets
import java.util.concurrent.TimeUnit

/**
 * Periodic backend sync, run by WorkManager instead of by a foreground service.
 *
 * <h3>Why this replaced the service loop</h3>
 * Polling used to happen in a `while` loop inside {@code StatusService}, which meant the app had to
 * keep a foreground service alive purely to hold that loop — and a foreground service must show a
 * notification that Android will not let you hide from the status bar. The sync was paying for a
 * permanent notification it never needed. Nothing else depended on the service: reminders and
 * chimes are AlarmManager alarms with manifest-registered receivers, and they fire with the app
 * closed and the service stopped.
 *
 * <h3>The trade this makes</h3>
 * WorkManager's minimum period is **15 minutes**, and it is a floor rather than a schedule — the OS
 * batches work and may run it later under Doze. So a sync interval set below 15 minutes can no
 * longer be honoured in the background; it still applies while the app is open, where the
 * foreground refresh path is unchanged. In exchange the app survives being swapped out, restarts
 * itself after a reboot, and shows no notification at all.
 *
 * Widgets are redrawn after each run: they read the cache this worker fills, and nothing else would
 * tell them it had changed.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).value
        if (!settings.syncActive || settings.nibrasBaseUrl.isBlank()) {
            return Result.success()   // switched off since this was scheduled; not a failure
        }
        val ok = runCatching { SyncCenter.syncNow(applicationContext) }.getOrDefault(false)
        Widgets.refreshAll(applicationContext)
        // retry() rather than failure(): a sync that couldn't reach the server should be attempted
        // again on WorkManager's backoff, not dropped until the next period comes round.
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "nibras-sync"

        /** WorkManager's own floor. Anything smaller is silently raised to this. */
        const val MIN_PERIOD_MINUTES = 15L

        /** Schedule (or reschedule) periodic sync to match the settings; cancels when switched off. */
        fun sync(context: Context) {
            val settings = SettingsStore(context).value
            val wm = WorkManager.getInstance(context)
            if (!settings.syncActive || settings.nibrasBaseUrl.isBlank()) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val minutes = (settings.syncIntervalSec / 60L).coerceAtLeast(MIN_PERIOD_MINUTES)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)   // no point waking to fail
                        .build(),
                )
                .build()
            // UPDATE, not REPLACE: replacing restarts the period, so an app that re-syncs its
            // schedule on every launch would push the next run 15 minutes away every time and,
            // if launched often enough, never sync at all.
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Run one sync right now, off the periodic schedule (used by the in-app refresh). */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )
        }
    }
}
