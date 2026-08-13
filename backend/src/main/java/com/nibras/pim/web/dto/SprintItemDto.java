package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/** One task's membership of one sprint. */
public record SprintItemDto(
        Long id,
        Long taskId,
        String title,
        boolean completed,
        LocalDateTime completedOn,
        /** Snapshot of the estimate at commit time, in minutes. */
        Integer plannedMinutes,
        /** True when [plannedMinutes] is the fallback rather than the task's own estimate. */
        boolean estimateAssumed,
        /**
         * How many times this task has been committed to a CLOSED sprint and not finished —
         * excluding this membership. The number that answers "why does this keep slipping?".
         */
        int previousMisses,
        Integer sortOrder) {
}
