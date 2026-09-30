package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TaskDto(
        Long id,
        Long version,
        String displayTitle,
        // editable
        String summary,
        String name,
        String description,
        String mainHighlights,
        String notes,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Integer percentCompleted,
        /** Expected duration in MINUTES (legacy `planned_duration`, narrowed by V6). */
        Integer plannedDuration,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long statusId,
        Long departmentId,
        Long courseId,
        Long goalId,
        List<Long> tagIds,
        /** People this task is about. Tagging by CONTACT, alongside tagging by tag. */
        List<Long> contactIds,
        Boolean urgent,
        Integer orderNumber,
        Boolean recurring,
        String recurrencePreset,
        Integer recurrenceInterval,
        String recurrenceCron,
        Boolean fixedSchedule,
        // read-only projections
        LookupDto status,
        LookupDto department,
        LookupDto course,
        LookupDto goal,
        List<TagDto> tags,
        List<ContactRefDto> contacts,
        Boolean hasCover,
        LocalDateTime completedOn,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) implements Versioned {
}
