package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/** A sprint with its committed tasks and the arithmetic the board needs. */
public record SprintDto(
        Long id,
        String summary,
        String notes,
        LocalDateTime startDate,
        LocalDateTime endDate,
        /** Null while the sprint is open; stamped by the review. */
        LocalDateTime closedOn,
        List<SprintItemDto> items,
        /** Minutes the committed tasks are expected to take, estimates and fallbacks together. */
        int plannedMinutes,
        /** Minutes between start and end — the room actually available. Null if either is unset. */
        Integer windowMinutes,
        /**
         * Tasks counted at the fallback because they carry no estimate of their own. Named, not
         * just counted, so the warning can say which ones to go and estimate.
         */
        List<String> unestimated,
        int completedCount,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
