package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

public record DepartmentDto(
        Long id,
        Long version,
        String displayTitle,
        String name,
        String code,
        String summary,
        String notes,
        Integer priority,
        Boolean bookmarked,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) implements Versioned {
}
