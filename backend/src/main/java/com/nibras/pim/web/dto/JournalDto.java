package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record JournalDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String description,
        String notes,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer energyLevel,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Long typeId,
        Long departmentId,
        Long courseId,
        List<Long> tagIds,
        LookupDto type,
        LookupDto department,
        LookupDto course,
        List<TagDto> tags,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
