package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/** A Journal/Planner entry positioned on the full-page calendar. */
public record CalendarEventDto(
        String module,        // "journals" | "planners"
        Long id,
        String title,
        LocalDateTime start,
        LocalDateTime end) {
}
