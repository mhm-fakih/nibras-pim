package com.nibras.pim.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A report definition as the builder edits it: columns and filters arrive as a real list and map
 * rather than the JSON strings they are stored as, so the client never parses or escapes anything.
 */
public record ReportDto(
        Long id,
        String name,
        String moduleKey,
        /** Ordered field names. "cover" renders the record's cover image. */
        List<String> columns,
        /** Filters in the same shape the list endpoints take: q, statusId, typeId, bookmarked, … */
        Map<String, Object> filters,
        /**
         * HQL naming the rows: {@code from Book where type.code = 'art' order by id desc}. When
         * present it replaces {@code filters}, {@code sortField} and {@code sortDesc} — the query
         * defines both which records appear and the order they appear in.
         */
        String query,
        String sortField,
        Boolean sortDesc,
        Integer pageSize,
        String notes,
        LocalDateTime dateCreated,
        LocalDateTime lastUpdated) {
}
