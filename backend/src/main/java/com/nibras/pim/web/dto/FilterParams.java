package com.nibras.pim.web.dto;

import java.time.LocalDate;

/** Shared query filters for every entity list endpoint (incl. report presets). */
public record FilterParams(
        String q,
        LocalDate createdOn,
        // A single "day" filter, date-aware per module: journals→startDate, planners→startDate,
        // everything else→dateCreated (so it means "records created that day").
        LocalDate onDay,
        LocalDate from,
        LocalDate to,
        Boolean bookmarked,
        Long statusId,
        Long typeId,
        Long tagId,
        // report presets (all nullable; only applied when true)
        Boolean noStatus,
        Boolean noType,
        Boolean noDepartment,
        Boolean noPriority,
        Boolean hasTags,
        Boolean overdue,
        Boolean dueSoon,
        Boolean highPriority,
        Boolean recentCreated,
        Boolean recentUpdated) {
}
