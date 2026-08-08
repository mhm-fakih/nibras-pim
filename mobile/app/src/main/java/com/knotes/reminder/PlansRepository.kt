package com.knotes.reminder

import android.content.Context
import com.knotes.AppSettings
import com.knotes.nibras.CalendarEvent
import com.knotes.nibras.CalendarStore
import com.knotes.nibras.NibrasApi
import com.knotes.nibras.NibrasRecord
import com.knotes.nibras.parseServerDateTime
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Refreshes the cached upcoming-plans view from the server and re-arms everything that depends on it
 * (reminder alarms + the home-screen widget). Safe to call often; a failed fetch leaves the cache
 * untouched.
 */
object PlansRepository {

    suspend fun refresh(context: Context, settings: AppSettings) {
        val api = NibrasApi()
        val base = settings.nibrasBaseUrl
        val user = settings.nibrasUsername
        // Ensure credentials are set when this runs from a background worker / boot (no UI yet).
        NibrasApi.configure(base, user, settings.nibrasPassword)
        val now = System.currentTimeMillis()

        // Server-side events (null when offline — we then keep the previously cached server set).
        val fetched = runCatching {
            api.fetchType(base, "J", user) + api.fetchType(base, "P", user)
        }.getOrNull()
        val serverItems = fetched?.mapNotNull { it.toPlanItem() }
            ?: PlansStore.read(context).filter { it.id.isNotBlank() }

        // Locally-added calendar events not yet synced (so a brand-new plan still reminds offline).
        val pendingItems = CalendarStore(context).pending.value.mapNotNull { it.toPlanItem() }

        // Keep recently-expired plans too (last 2h) so the widget can show them struck through;
        // the panel and reminders ignore past ones on their own.
        val recentWindow = now - 2 * 60 * 60 * 1000L
        // Show both Plans (P) and Journal (J) calendar events — the calendar's add dialog defaults to
        // J, so restricting to P hid every event the user created there.
        val items = (serverItems + pendingItems)
            .filter { (it.type == "P" || it.type == "J") && it.endMillis > recentWindow }
            .distinctBy { it.key }
            .sortedBy { it.startMillis }
            .take(50)

        PlansStore.save(context, items)

        // Tasks with a due date, not yet completed. Split into "still ahead" (gets the same
        // before-it's-due alarms as Plans/Journal) and "already overdue" (an alarm can't fire in
        // the past, so those get an immediate consolidated notification instead — see
        // TaskOverdueNotifier).
        val taskItems = runCatching { api.fetchType(base, "T", user) }.getOrNull()
            ?.filter { it.endDate.isNotBlank() && it.completedOn.isBlank() }
            ?.mapNotNull { it.toTaskDueItem() }
            ?: TasksReminderStore.read(context)
        val (overdueTasks, upcomingTasks) = taskItems.partition { it.startMillis <= now }
        TasksReminderStore.save(context, upcomingTasks)
        TaskOverdueNotifier.notifyIfChanged(context, overdueTasks)

        reschedule(context, settings)
        PlansWidget.refresh(context)
    }

    /** Re-arm alarms from the cached items (used after settings change and on boot). Merges
     *  calendar-event items (Plans/Journal) with upcoming-task-due items — both use the same
     *  before-it's-due offsets, just from different sources. */
    fun reschedule(context: Context, settings: AppSettings) {
        ReminderScheduler.scheduleAll(
            context = context,
            items = PlansStore.read(context) + TasksReminderStore.read(context),
            offsetsMin = settings.reminderOffsetsMin,
            soundUri = settings.reminderSoundUri,
            requireDismiss = settings.reminderRequireDismiss,
            repeatCount = settings.reminderRepeatCount,
        )
    }

    private fun NibrasRecord.toPlanItem(): PlanItem? {
        val start = parseServerDateTime(startDate) ?: return null
        val end = parseServerDateTime(endDate) ?: start.plusHours(1)
        val zone = ZoneId.systemDefault()
        return PlanItem(
            type = type,
            id = id,
            summary = summary.ifBlank { description },
            startMillis = start.atZone(zone).toInstant().toEpochMilli(),
            endMillis = end.atZone(zone).toInstant().toEpochMilli(),
        )
    }

    /** A Task's "due moment" (endDate) — used as both start and end so the same before-it's-due
     *  offset math (and the recently-expired window) applies to task reminders as to events. */
    private fun NibrasRecord.toTaskDueItem(): PlanItem? {
        val due = parseServerDateTime(endDate) ?: return null
        val millis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return PlanItem(
            type = type,
            id = id,
            summary = summary.ifBlank { description },
            startMillis = millis,
            endMillis = millis,
        )
    }

    private fun CalendarEvent.toPlanItem(): PlanItem {
        val zone = ZoneId.systemDefault()
        return PlanItem(
            type = type,
            id = id,
            summary = summary,
            startMillis = LocalDateTime.of(date, start).atZone(zone).toInstant().toEpochMilli(),
            endMillis = LocalDateTime.of(date, end).atZone(zone).toInstant().toEpochMilli(),
        )
    }
}
