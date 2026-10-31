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
        Boolean urgent,
        Boolean dashboard,
        Long statusId,
        Long typeId,
        // An exact priority ("show me the p3s"), distinct from the highPriority preset (">= 3").
        Integer priority,
        Long tagId,
        Long goalId,
        Long writingId,
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
        Boolean recentUpdated,
        // Tasks' Active / Not active filter (work_status.marked_as_active); null = both.
        Boolean activeStatus,
        // Tasks' GTD context (@desk, @car…), the work_location lookup.
        Long contextId) {

    /**
     * Just a free-text query, every other filter unset. Spelling out 20-odd nulls at a call site is
     * both unreadable and a silent hazard — two same-typed neighbours transposed still compiles.
     */
    public static FilterParams forQuery(String q) {
        return new FilterParams(q, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
