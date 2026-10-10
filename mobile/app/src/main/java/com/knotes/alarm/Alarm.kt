package com.knotes.alarm

import com.knotes.prayer.Prayer
import java.time.DayOfWeek

/** What decides when an alarm fires. */
enum class AlarmKind {
    /** A wall-clock time, on chosen days — the ordinary alarm. */
    CLOCK,

    /** A stretch of time from when it was started — the kitchen timer. */
    COUNTDOWN,

    /** An offset from a prayer time, which moves with the prayer every day. */
    PRAYER,
}

/**
 * One alarm.
 *
 * A single type with a [kind] rather than a sealed hierarchy: these are stored as JSON, edited in
 * one form and listed together, and every variant shares the sound, volume and repeat settings. A
 * hierarchy would buy type-safety on three fields and cost a converter at every boundary.
 *
 * The fields a kind does not use are simply ignored, which is also what makes the edit form able to
 * switch kind without losing what you typed under the other one.
 */
data class Alarm(
    val id: String,
    val label: String = "",
    val enabled: Boolean = true,
    val kind: AlarmKind = AlarmKind.CLOCK,

    // --- CLOCK -------------------------------------------------------------
    val hour: Int = 7,
    val minute: Int = 0,
    /**
     * Days it repeats on. EMPTY means once, at the next occurrence of [hour]:[minute] — which is
     * what a phone alarm does when you pick no days, and is a different thing from "never".
     */
    val days: Set<DayOfWeek> = emptySet(),

    // --- COUNTDOWN ---------------------------------------------------------
    /** How long it runs for. The presets are in [COUNTDOWN_PRESETS_MIN]; any value is allowed. */
    val durationMin: Int = 10,
    /**
     * When the running countdown is due, or null when it is not running. A countdown is armed by
     * starting it, not by existing, so this is what tells the two apart.
     */
    val dueAtMillis: Long? = null,

    // --- PRAYER ------------------------------------------------------------
    val prayer: Prayer = Prayer.FAJR,
    /** Minutes relative to the prayer. NEGATIVE is before, which is the common case. */
    val offsetMin: Int = -30,

    // --- what it does when it fires ----------------------------------------
    /** Sound to play; blank means the system's default alarm sound. */
    val soundUri: String = "",
    /** How many times to play it through. */
    val repeatCount: Int = 1,
    /** 0-100, applied to the alarm stream for this alarm only and then put back. */
    val volumePercent: Int = 80,
) {
    /** True when this alarm has somewhere to fire — a countdown must have been started. */
    fun armable(): Boolean = enabled && (kind != AlarmKind.COUNTDOWN || dueAtMillis != null)

    companion object {
        /** The offered durations. A custom number of minutes is always allowed beside these. */
        val COUNTDOWN_PRESETS_MIN = listOf(5, 10, 15, 20, 30, 60, 90, 120)

        /** Offsets offered for a prayer alarm; any number of minutes may be typed instead. */
        val PRAYER_OFFSETS_MIN = listOf(-60, -45, -30, -20, -15, -10, -5, 0, 5, 10, 15, 30)
    }
}

/** A human label for a duration in minutes: 90 -> "1h 30m". */
fun minutesLabel(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

/** "30m before Fajr", "at Maghrib", "10m after Isha". */
fun offsetLabel(prayer: Prayer, offsetMin: Int): String = when {
    offsetMin == 0 -> "at ${prayer.label}"
    offsetMin < 0 -> "${minutesLabel(-offsetMin)} before ${prayer.label}"
    else -> "${minutesLabel(offsetMin)} after ${prayer.label}"
}
