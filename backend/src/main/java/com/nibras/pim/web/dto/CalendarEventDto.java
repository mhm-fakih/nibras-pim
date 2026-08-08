package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/**
 * A Journal/Planner entry positioned on the full-page calendar. {@code typeId}/{@code typeStyle}
 * let the dashboard builder's "calendar" component colour each event by its own type's configured
 * chip colour (Settings → Parameter colours) — journal and planner each keep their own type lookup,
 * so the same type name (e.g. "family") can carry a different colour per module.
 *
 * <p>An event may be <b>virtual</b>: a computed occurrence of a recurring planner series that has no
 * row of its own yet (see {@link com.nibras.pim.service.PlannerSeriesService}). Those carry a null
 * {@code id} — there is nothing to link to or edit until the client materialises them via
 * {@code POST /api/calendar/occurrences/materialise}. Anything that needs a record id must check
 * {@code virtual} first; {@code seriesId} + {@code recurrenceId} is what identifies the occurrence
 * until then.
 *
 * <p>A materialised occurrence is a real row and so has an {@code id}, but still carries
 * {@code seriesId}/{@code recurrenceId} so the UI can mark it as part of a series and offer
 * "delete this occurrence" rather than a plain delete.
 */
public record CalendarEventDto(
        String module,        // "journals" | "planners"
        Long id,              // null for a virtual occurrence
        String title,
        String description,
        LocalDateTime start,
        LocalDateTime end,
        String typeName,
        Long typeId,
        String typeStyle,
        /** Set on both virtual and materialised occurrences: the series master's id. */
        Long seriesId,
        /** The slot this occurrence stands for — its ORIGINAL start, unchanged by dragging it. */
        LocalDateTime recurrenceId,
        /** True when there is no row behind this event yet. */
        boolean virtual) {

    /** A plain, non-recurring event. */
    public static CalendarEventDto plain(String module, Long id, String title, String description,
                                         LocalDateTime start, LocalDateTime end,
                                         String typeName, Long typeId, String typeStyle) {
        return new CalendarEventDto(module, id, title, description, start, end,
                typeName, typeId, typeStyle, null, null, false);
    }
}
