package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WritingDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String description,
        String mainHighlights,
        String shortDescription,
        String notes,
        String slug,
        String source,
        LocalDateTime bornOn,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
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
        LocalDateTime lastUpdated) {
}
