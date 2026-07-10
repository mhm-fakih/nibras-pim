package com.knotes.nibras

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A calendar entry as POSTed to mobilePushCalendar (envelope: {data:[{ecode,id,startDate,endDate,summary}]}). */
data class CalendarEntry(
    val type: String,   // "J" or "P" (sent as ecode)
    val id: String,     // "" for a new entry
    val startDate: String,
    val endDate: String,
    val summary: String,
)

/** A calendar entry placed on the day grid. */
data class CalendarEvent(
    val type: String,        // "J" or "P"
    val id: String,          // server id, or "" when added locally and not yet synced
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val summary: String,
)

private val LOCAL_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd'T'HH:mm",
    "yyyy-MM-dd HH:mm",
)
private val DATE_TOSTRING = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
private val SERVER_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

/** Best-effort parse of a server datetime (epoch millis, ISO with Z/offset, plain, or Date.toString). */
fun parseServerDateTime(raw: String?): LocalDateTime? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    s.toLongOrNull()?.let { return LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
    runCatching { return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault()) }
    runCatching { return OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
    runCatching { return LocalDateTime.parse(s) }
    for (p in LOCAL_PATTERNS) {
        runCatching { return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p, Locale.US)) }
    }
    runCatching { return LocalDateTime.parse(s, DATE_TOSTRING) }
    return null
}

fun formatServerDateTime(dt: LocalDateTime): String = dt.format(SERVER_OUT)

/** Map a fetched J/P record to a single-day calendar event (clamped to its start day). */
fun NibrasRecord.toCalendarEvent(): CalendarEvent? {
    val start = parseServerDateTime(startDate) ?: return null
    val endRaw = parseServerDateTime(endDate)
    val end = when {
        endRaw == null -> start.plusHours(1)
        endRaw.toLocalDate() != start.toLocalDate() || endRaw.isBefore(start) ->
            start.toLocalDate().atTime(LocalTime.of(23, 59))
        else -> endRaw
    }
    return CalendarEvent(
        type = type,
        id = id,
        date = start.toLocalDate(),
        start = start.toLocalTime(),
        end = end.toLocalTime(),
        summary = summary.ifBlank { description },
    )
}
