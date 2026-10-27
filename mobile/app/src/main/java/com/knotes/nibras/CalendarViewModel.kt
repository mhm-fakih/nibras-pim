package com.knotes.nibras

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.knotes.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Backs the Calendar page: fetches Journal (J) and Plans (P) records and maps them to events,
 * and lets the user add new entries that are saved locally (offline-first) and pushed to
 * mobilePushCalendar. Displayed events are the fetched ones plus any still-pending local ones.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val api = NibrasApi()
    private val store = CalendarStore(app)

    // Drain the offline outbox automatically as soon as a network becomes available — no need to
    // reopen the page. Guarded so it only acts once we know where to push and have something to send.
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (base.isNotEmpty() && store.pending.value.isNotEmpty()) load(base, username)
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivity?.registerNetworkCallback(request, networkCallback)
        }
    }

    val pending: StateFlow<List<CalendarEvent>> = store.pending

    private val _fetched = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val fetched: StateFlow<List<CalendarEvent>> = _fetched.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private var base = ""
    private var username = ""

    /** The entry type ("J"/"P") last used in the "New entry" dialog — persists across app restarts. */
    var lastType: String
        get() = store.lastType
        set(value) { store.lastType = value }

    fun load(base: String, username: String) {
        this.base = base
        this.username = username
        viewModelScope.launch {
            _loading.value = true
            // Retry any entries that were created while offline before fetching the server view.
            flushPending()
            val records = runCatching {
                api.fetchType(base, "J", username) + api.fetchType(base, "P", username)
            }.getOrNull()
            if (records != null) _fetched.value = records.mapNotNull { it.toCalendarEvent() }
            _loading.value = false
        }
    }

    fun add(type: String, date: LocalDate, start: LocalTime, end: LocalTime, summary: String, description: String = "") {
        lastType = type
        val event = CalendarEvent(type, "", date, start, end, summary.trim(), description.trim())
        store.add(event)
        viewModelScope.launch {
            val synced = runCatching { api.pushCalendar(base, username, listOf(entryOf(event))) }.getOrDefault(false)
            if (synced) {
                store.remove(event)
                _message.value = str(R.string.server_calendar_saved)
                if (base.isNotEmpty()) load(base, username)
            } else {
                _message.value = str(R.string.server_saved_sync_when_online)
            }
            // Reflect the new event in the "Upcoming plans" widget right away: this bridges the
            // knotes_calendar store into knotes_plans (which the widget reads) and refreshes it —
            // works whether the event just synced or is still pending offline.
            runCatching {
                com.knotes.reminder.PlansRepository.refresh(
                    getApplication(), com.knotes.SettingsStore(getApplication()).value)
            }
        }
    }

    /**
     * Add a typed week at once (the batch dialog): every entry lands on the first matching weekday
     * on or after [from]. With [weeks] > 1 each planner becomes a weekly series ending
     * `from + weeks - 1 day`, one record per course and weekday rather than a row per week —
     * journal entries don't repeat on the server, so they are added once.
     */
    fun addBatch(entries: List<WeekBatch.Entry>, defaultType: String, from: LocalDate, weeks: Int) {
        if (entries.isEmpty()) return
        val until = if (weeks > 1) from.plusWeeks(weeks.toLong()).minusDays(1) else null
        val events = entries.map { e ->
            val type = e.type ?: defaultType
            CalendarEvent(
                type, "", WeekBatch.firstDate(from, e.day), e.start, e.end, e.title.trim(),
                repeatWeeklyUntil = until.takeIf { type == "P" },
            )
        }
        store.addAll(events)
        viewModelScope.launch {
            val synced = runCatching { api.pushCalendar(base, username, events.map(::entryOf)) }.getOrDefault(false)
            if (synced) {
                events.forEach(store::remove)
                _message.value = str(R.string.server_calendar_batch_saved, events.size)
                if (base.isNotEmpty()) load(base, username)
            } else {
                _message.value = str(R.string.server_saved_sync_when_online)
            }
            runCatching {
                com.knotes.reminder.PlansRepository.refresh(
                    getApplication(), com.knotes.SettingsStore(getApplication()).value)
            }
        }
    }

    /**
     * Edit an existing (already-synced) entry — same push path as [add], but keeps the server id so
     * `mobilePushCalendar` updates it in place (both Journal and Planner support update-by-id
     * server-side) instead of creating a duplicate. Used for the drag-to-resize/move gestures on the
     * grid and for the "tap an event to edit it" dialog.
     */
    fun update(
        original: CalendarEvent,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        summary: String = original.summary,
        description: String = original.description,
    ) {
        val event = original.copy(
            date = date, start = start, end = end,
            summary = summary.trim(), description = description.trim(),
        )
        // Reflect immediately (both the fetched view and the outbox), so the edit is visible right
        // away and survives the app closing before it syncs.
        _fetched.value = _fetched.value.map { if (it.type == event.type && it.id == event.id) event else it }
        store.upsert(event)
        viewModelScope.launch {
            // A virtual occurrence of a recurring planner has no record behind it, and its id is a
            // composite the push endpoint can't accept — so editing one creates the record first.
            // Deliberately NOT queued offline: without a real id the outbox would push a composite
            // that the server would reject, and the edit would look saved while being dropped.
            val resolved = resolveOccurrence(event) ?: run {
                store.remove(event)
                _fetched.value = _fetched.value.map { if (it.localKey == event.localKey) original else it }
                _message.value = str(R.string.server_calendar_repeat_offline)
                return@launch
            }
            val synced = runCatching { api.pushCalendar(base, username, listOf(entryOf(resolved))) }.getOrDefault(false)
            if (synced) {
                store.remove(event)
                _message.value = str(R.string.server_saved)
                if (base.isNotEmpty()) load(base, username)
            } else {
                _message.value = str(R.string.server_saved_sync_when_online)
            }
            runCatching {
                com.knotes.reminder.PlansRepository.refresh(
                    getApplication(), com.knotes.SettingsStore(getApplication()).value)
            }
        }
    }

    /**
     * Gives [event] a real server id if it is a computed occurrence of a recurring planner; returns
     * it unchanged otherwise. Null means the record couldn't be created (offline or refused).
     */
    private suspend fun resolveOccurrence(event: CalendarEvent): CalendarEvent? {
        if (!event.virtual) return event
        if (base.isEmpty() || event.seriesId.isBlank() || event.recurrenceId.isBlank()) return null
        val id = api.materialiseOccurrence(base, event.seriesId, event.recurrenceId) ?: return null
        return event.copy(id = id, virtual = false)
    }

    /** Push every still-pending local entry; drop each one that the server accepts. */
    private suspend fun flushPending() {
        if (base.isEmpty()) return
        store.pending.value.forEach { event ->
            val ok = runCatching { api.pushCalendar(base, username, listOf(entryOf(event))) }.getOrDefault(false)
            if (ok) store.remove(event)
        }
    }

    private fun entryOf(event: CalendarEvent) = CalendarEntry(
        type = event.type,
        id = event.id,
        startDate = formatServerDateTime(LocalDateTime.of(event.date, event.start)),
        endDate = formatServerDateTime(LocalDateTime.of(event.date, event.end)),
        summary = event.summary,
        description = event.description,
        repeatWeeklyUntil = event.repeatWeeklyUntil?.toString() ?: "",
    )

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }
}
