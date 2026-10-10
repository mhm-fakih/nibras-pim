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
        Integer orderInWriting,
        Boolean isNewSection,
        Long typeId,
        Long statusId,
        Long departmentId,
        Long courseId,
        Long writingId,
        /** The resource this note is about (index_card.book_id). */
        Long bookId,
        List<Long> tagIds,
        LookupDto type,
        LookupDto status,
        LookupDto department,
        LookupDto course,
        LookupDto book,
        List<TagDto> tags,
        Boolean hasCover,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) implements Versioned {
}
