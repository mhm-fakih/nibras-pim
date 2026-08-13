package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/** "resources" in the API (book table). */
public record BookDto(
        Long id,
        Long version,
        String displayTitle,
        String title,
        String author,
        String publisher,
        String edition,
        String isbn,
        String description,
        String shortDescription,
        String notes,
        String comments,
        String url,
        String fullText,
        String highlights,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Boolean isRead,
        /** When it was finished. Column existed on the table long before it was ever exposed here. */
        LocalDateTime readOn,
        Integer percentCompleted,
        String resourceYear,
        String pages,
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
