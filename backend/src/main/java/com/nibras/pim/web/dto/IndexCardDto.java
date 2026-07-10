package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/** "notes" in the API (index_card table). */
public record IndexCardDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String shortDescription,
        String description,
        String notes,
        String reaction,
        String mainHighlights,
        String pages,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
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
