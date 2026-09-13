package com.knotes.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The kinds of thing the prayer schedule can do at a given moment. */
enum class PrayerEventKind {
    /** A quiet nudge some minutes before the time arrives. */
    REMINDER,

    /** The athan itself, at the time. */
    ATHAN,

    /** Drop the ringer to silent. */
    SILENCE_START,

    /** Put the ringer back where it was. */
    SILENCE_END,

    /** A short countdown of beeps before sunrise closes the window for Fajr. */
    BEEPS,

    /**
     * Nothing happens — this exists ONLY to wake the widget so its countdown can appear.
     *
     * The strip is redrawn when an event fires and at no other time, so without this the countdown
     * would not show up at the start of its window; it would wait for the next thing that happened
     * to be scheduled, which is the 10-minute reminder. A setting saying "within an hour" would
     * then have meant "within ten minutes", and only for prayers that have a reminder at all.
     */
    COUNTDOWN,
}

/** One scheduled moment: what to do, for which prayer, and when. */
data class PrayerEvent(
    val kind: PrayerEventKind,
    val prayer: Prayer,
    val atMillis: Long,
)

/**
 * Expands settings into the individual moments an alarm has to fire at.
 *
 * Pure, so the part that decides *what happens when* can be tested exhaustively while the alarm
 * plumbing above it stays a thin shell that only knows how to arm the next one.
 */
object PrayerEvents {

    /**
     * Every event belonging to one civil day, earliest first.
     *
     * [countdownWakeups] adds a [PrayerEventKind.COUNTDOWN] per shown time. Off by default and
     * switched on only when a prayer widget is actually on a home screen — these alarms do nothing
     * but redraw that widget, and waking a phone that has none would be pure cost.
     */
    fun forDay(
        settings: PrayerSettings,
        day: PrayerDay,
        countdownWakeups: Boolean = false,
    ): List<PrayerEvent> {
        if (!settings.enabled) return emptyList()
        val events = mutableListOf<PrayerEvent>()

        day.slots.forEach { slot ->
            val at = slot.atMillis ?: return@forEach
            val prayer = slot.prayer

            // The pre-prayer nudge covers the rows the user actually keeps on screen.
            if (settings.reminderEnabled && prayer in settings.visible) {
                val lead = settings.reminderLeadMin.coerceIn(PrayerSettings.MIN_LEAD, PrayerSettings.MAX_LEAD)
                events += PrayerEvent(PrayerEventKind.REMINDER, prayer, at - lead * 60_000L)
            }

            // Athan and silence each answer to their own selection rather than to visibility, so a
            // switch means exactly what it says and hiding a row never quietly kills its alarm.
            if (prayer in settings.effectiveAthan) {
                events += PrayerEvent(PrayerEventKind.ATHAN, prayer, at)
            }
            if (settings.silenceEnabled && prayer in settings.effectiveSilence) {
                val minutes = settings.silenceMinutes
                    .coerceIn(PrayerSettings.MIN_SILENCE, PrayerSettings.MAX_SILENCE)
                events += PrayerEvent(PrayerEventKind.SILENCE_START, prayer, at)
                events += PrayerEvent(PrayerEventKind.SILENCE_END, prayer, at + minutes * 60_000L)
            }
        }

        // One wake-up per shown time, at the moment its countdown becomes due. Keyed to `visible`
        // rather than to the reminder or athan selections, because it serves what the STRIP shows:
        // a time you display but have no alarm for still gets its countdown.
        if (countdownWakeups) day.slots.forEach { slot ->
            val at = slot.atMillis ?: return@forEach
            if (slot.prayer !in settings.visible) return@forEach
            val within = settings.countdownWithinMin
                .coerceIn(PrayerSettings.MIN_COUNTDOWN, PrayerSettings.MAX_COUNTDOWN)
            events += PrayerEvent(PrayerEventKind.COUNTDOWN, slot.prayer, at - within * 60_000L)
        }

        // Sunrise ends the time in which Fajr can still be prayed, which is why it gets a countdown
        // of its own rather than sharing the pre-prayer reminder.
        if (settings.beepsEnabled) {
            day.timeOf(Prayer.SUNRISE)?.let { sunrise ->
                val lead = settings.beepsLeadMin.coerceIn(PrayerSettings.MIN_LEAD, PrayerSettings.MAX_LEAD)
                events += PrayerEvent(PrayerEventKind.BEEPS, Prayer.SUNRISE, sunrise - lead * 60_000L)
            }
        }

        return events.sortedBy { it.atMillis }
    }

    /**
     * The soonest event strictly after [nowMillis], or null when nothing is scheduled at all.
     *
     * Yesterday is searched as well as today and tomorrow, and not out of caution: a silence that
     * begins at an Isha of 23:50 ends at 00:10, which belongs to yesterday's list while still lying
     * in the future. Miss it and the ringer stays off all night.
     */
    fun next(
        settings: PrayerSettings,
        zone: ZoneId,
        nowMillis: Long,
        countdownWakeups: Boolean = false,
    ): PrayerEvent? {
        if (!settings.enabled) return null
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        return listOf(today.minusDays(1), today, today.plusDays(1))
            .flatMap { forDay(settings, PrayerCalendar.dayOf(settings, it, zone), countdownWakeups) }
            .filter { it.atMillis > nowMillis }
            .minByOrNull { it.atMillis }
    }
}
