package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TaskDto(
        Long id,
        Long version,
        String displayTitle,
        // editable
        String summary,
        String name,
        String description,
        String notes,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Integer percentCompleted,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long statusId,
        Long departmentId,
        Long courseId,
        Long goalId,
        List<Long> tagIds,
        // read-only projections
        LookupDto status,
        LookupDto department,
        LookupDto course,
        LookupDto goal,
        List<TagDto> tags,
        Boolean hasCover,
        LocalDateTime completedOn,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
