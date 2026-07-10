package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlannerDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String description,
        String notes,
        String reality,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Integer percentCompleted,
        Long typeId,
        Long statusId,
        Long departmentId,
        Long courseId,
        List<Long> tagIds,
        LookupDto type,
        LookupDto status,
        LookupDto department,
        LookupDto course,
        List<TagDto> tags,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
