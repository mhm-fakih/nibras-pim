package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GoalDto(
        Long id,
        Long version,
        String displayTitle,
        String title,
        String summary,
        String description,
        String mainHighlights,
        String notes,
        String code,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Integer percentCompleted,
        LocalDateTime startDate,
        LocalDateTime endDate,
        LocalDateTime completedOn,
        Boolean dashboard,
        Long statusId,
        Long typeId,
        Long departmentId,
        Long courseId,
        List<Long> tagIds,
        LookupDto status,
        LookupDto type,
        LookupDto department,
        LookupDto course,
        List<TagDto> tags,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) implements Versioned {
}
