package com.nibras.pim.web.dto;

import com.nibras.pim.domain.SavedSearch;
import com.nibras.pim.service.SavedSearchService;

/**
 * API shape for a user-defined report. {@code module} is the frontend key (e.g. "tasks"); it maps to
 * the legacy single-letter {@code entity} code stored on the row. {@code entityName} is the JPQL
 * entity the query runs against (display hint for the editor).
 */
public record SavedSearchDto(
        Long id,
        String module,
        String entityName,
        String queryType,
        String query,
        String countQuery,
        String summary,
        String code,
        String reportType,
        String orderNumber,
        Boolean bookmarked,
        Boolean onMobile,
        Integer priority,
        String notes) {

    public static SavedSearchDto of(SavedSearch s) {
        return new SavedSearchDto(
                s.getId(),
                SavedSearchService.moduleFor(s.getEntity()),
                SavedSearchService.entityNameFor(s.getEntity()),
                s.getQueryType(),
                s.getQuery(),
                s.getCountQuery(),
                s.getSummary(),
                s.getCode(),
                s.getReportType(),
                s.getOrderNumber(),
                s.getBookmarked(),
                s.getOnMobile(),
                s.getPriority(),
                s.getNotes());
    }

    /** Applies this DTO onto an entity (create/update). Maps module key -> legacy entity letter. */
    public void applyTo(SavedSearch s) {
        s.setEntity(SavedSearchService.letterFor(module));
        s.setQueryType(queryType);
        s.setQuery(query);
        s.setCountQuery(countQuery);
        s.setSummary(summary);
        s.setCode(code);
        s.setReportType(reportType);
        s.setOrderNumber(orderNumber);
        s.setBookmarked(bookmarked);
        s.setOnMobile(onMobile);
        s.setPriority(priority);
        s.setNotes(notes);
    }
}
