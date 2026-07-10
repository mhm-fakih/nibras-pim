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

    private var base = ""
    private var username = ""

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

    fun add(type: String, date: LocalDate, start: LocalTime, end: LocalTime, summary: String) {
        val event = CalendarEvent(type, "", date, start, end, summary.trim())
        store.add(event)
        viewModelScope.launch {
            val synced = runCatching { api.pushCalendar(base, username, listOf(entryOf(event))) }.getOrDefault(false)
            if (synced) {
                store.remove(event)
                _message.value = "Saved to calendar"
                if (base.isNotEmpty()) load(base, username)
            } else {
                _message.value = "Saved — will sync when online"
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
    )

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
    }
}
