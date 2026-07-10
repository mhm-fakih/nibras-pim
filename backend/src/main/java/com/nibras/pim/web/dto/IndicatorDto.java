package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

public record IndicatorDto(
        Long id,
        Long version,
        String displayTitle,
        String code,
        String name,
        String description,
        String frequency,
        String scale,
        String query,
        String path,
        String extensions,
        String script,
        String category,
        String color,
        Integer priority,
        Boolean bookmarked,
        String summary,
        String notes,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
