package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

public record CourseDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String code,
        String notes,
        Integer priority,
        Boolean bookmarked,
        Long departmentId,
        LookupDto department,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
