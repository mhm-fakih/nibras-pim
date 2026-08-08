package com.nibras.pim.web.dto;

import java.time.LocalDateTime;

/** One soft-deleted record, for the Trash page. {@code mergedIntoId} is set only when the record
 * was absorbed by a merge rather than deleted outright. */
public record TrashEntryDto(String module, long id, String title, LocalDateTime deletedOn,
                            Long mergedIntoId, String mergedIntoTitle) {
}
