package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlannerDto(
        Long id,
        Long version,
        String displayTitle,
        String summary,
        String description,
        String mainHighlights,
        String notes,
        String reality,
        LocalDateTime startDate,
        LocalDateTime endDate,
        // --- Recurrence (see PlannerSeriesService). On a series MASTER these carry the rule; on a
        //     materialised occurrence only seriesId/recurrenceId are set. All are null-guarded in
        //     PlannerService.apply, because EntityForm sends an explicit field allowlist and a
        //     normal edit from another surface omits them entirely.
        Boolean recurring,
        String recurrencePreset,
        Integer recurrenceInterval,
        String recurrenceCron,
        LocalDateTime recurrenceUntil,
        Integer recurrenceCount,
        Long seriesId,
        LocalDateTime recurrenceId,
        Integer priority,
        Boolean bookmarked,
        Boolean isPrivate,
        Integer percentCompleted,
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
        LocalDateTime lastUpdated) implements Versioned {
}
