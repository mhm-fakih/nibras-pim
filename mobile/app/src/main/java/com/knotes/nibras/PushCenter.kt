package com.knotes.nibras

import android.content.Context
import com.knotes.Note
import com.knotes.SettingsStore
import com.knotes.reminder.PlansRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * One place that turns local captures into backend records: queues them in [RecordOutbox]
 * (offline-first) and drains every outbox — records AND the calendar one — whenever asked,
 * typically right after queuing and again when connectivity returns ([com.knotes.KnotesApp]).
 * The server saves everything with bookmarked=true, so pushed records round-trip on the next
 * exportJson pull.
 */
object PushCenter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainLock = Mutex()   // one drain at a time; late callers just re-run after

    /** Queue a local note for creation on the backend, then try to send right away. */
    fun pushNote(context: Context, note: Note) {
        val item = PendingCreate(
            key = note.file.name,
            ecode = (note.type ?: DEFAULT_ECODE).uppercase(),
            summary = summaryOf(note.content),
            description = note.content,
            priority = note.priority,
            department = note.category.orEmpty(),
        )
        RecordOutbox.queue(context, item)
        drainAll(context)
    }

    /** Fire-and-forget drain of every outbox; safe to call opportunistically. */
    fun drainAll(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { drain(app) } }
    }

    /** Push every queued record and calendar entry; drop each one the server accepts. */
    private suspend fun drain(context: Context) = drainLock.withLock {
        RecordOutbox.seed(context)
        val settings = SettingsStore(context).value
        val base = settings.nibrasBaseUrl.trim()
        val calendar = CalendarStore(context)
        if (base.isEmpty() ||
            (RecordOutbox.pending.value.isEmpty() && calendar.pending.value.isEmpty())
        ) {
            return
        }
        NibrasApi.configure(base, settings.nibrasUsername, settings.nibrasPassword)
        val api = NibrasApi()

        // Records, one at a time so a single failure doesn't hold the rest hostage.
        RecordOutbox.pending.value.forEach { item ->
            val ok = runCatching { api.pushCreates(base, listOf(item)) }.getOrDefault(false)
            if (ok) {
                RecordOutbox.remove(context, item)
                RecordOutbox.markPushed(context, item.key)
            }
        }

        // Calendar outbox — same behaviour as the Calendar page's flushPending, but available
        // process-wide so events created offline sync even if that page is never reopened.
        var calendarSynced = false
        calendar.pending.value.forEach { event ->
            val entry = CalendarEntry(
                type = event.type,
                id = event.id,
                startDate = formatServerDateTime(LocalDateTime.of(event.date, event.start)),
                endDate = formatServerDateTime(LocalDateTime.of(event.date, event.end)),
                summary = event.summary,
            )
            val ok = runCatching {
                api.pushCalendar(base, settings.nibrasUsername, listOf(entry))
            }.getOrDefault(false)
            if (ok) {
                calendar.remove(event)
                calendarSynced = true
            }
        }
        if (calendarSynced) {
            // Pending events were already in the plans store (first-class pre-sync); refresh so
            // the widget/reminders switch over to the server copies without duplicating.
            runCatching { PlansRepository.refresh(context, settings) }
        }
    }

    /** First non-blank line as the record title, clipped to a sane length. */
    private fun summaryOf(content: String): String =
        content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?.take(120) ?: "(untitled)"

    /** Untyped notes go up as Notes (IndexCard). */
    private const val DEFAULT_ECODE = "N"
}
