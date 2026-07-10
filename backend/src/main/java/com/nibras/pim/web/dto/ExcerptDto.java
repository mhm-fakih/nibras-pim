package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

public record ExcerptDto(
        Long id,
        Long version,
        String displayTitle,
        String title,
        String summary,
        Integer chapter,
        String chapters,
        String description,
        String notes,
        Integer priority,
        Boolean bookmarked,
        // the book it belongs to
        Long bookId,
        LookupDto book,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
